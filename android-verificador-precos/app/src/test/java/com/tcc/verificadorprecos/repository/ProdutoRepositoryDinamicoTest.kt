package com.tcc.verificadorprecos.repository

import com.tcc.verificadorprecos.connection.EnderecoServidor
import com.tcc.verificadorprecos.connection.FaseConexao
import com.tcc.verificadorprecos.connection.GerenciadorConexao
import com.tcc.verificadorprecos.connection.PreferenciaServidor
import com.tcc.verificadorprecos.connection.VerificadorApi
import com.tcc.verificadorprecos.data.ProdutoDto
import com.tcc.verificadorprecos.network.ProdutoApi
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProdutoRepositoryDinamicoTest {
    @Test
    fun criaApiParaSessaoAtualACadaConsultaETrocaEndpoint() = runTest {
        val conexao = conexaoAtiva()
        val enderecos = mutableListOf<EnderecoServidor>()
        val codigos = mutableListOf<String>()
        val repository = ProdutoRepositoryImpl(conexao) { endereco ->
            enderecos += endereco
            api { codigo ->
                codigos += codigo
                Response.success(produto(codigo = codigo))
            }
        }

        repository.consultarProduto("00123")
        repository.consultarProduto("456")
        conexao.conectarManual("198.51.100.5", "9010")
        runCurrent()
        repository.consultarProduto("789")

        assertEquals(listOf(ANTERIOR, ANTERIOR, EnderecoServidor.criar("198.51.100.5", 9010)), enderecos)
        assertEquals(listOf("00123", "456", "789"), codigos)
    }

    @Test
    fun semServidorConectadoNaoCriaApiNemConsultaEndpoint() = runTest {
        val conexao = gerenciador()
        var fabricasChamadas = 0
        val repository = ProdutoRepositoryImpl(conexao) {
            fabricasChamadas++
            api { Response.success(produto()) }
        }

        val erro = runCatching { repository.consultarProduto("00123") }.exceptionOrNull()

        assertTrue(erro is IOException)
        assertEquals(0, fabricasChamadas)
        assertNull(conexao.sessao.value)
    }

    @Test
    fun respostas400E404SaoPreservadasSemInvalidarSessao() = runTest {
        val conexao = conexaoAtiva()
        val sessao = conexao.sessao.value
        listOf(400, 404).forEach { codigo ->
            val original = Response.error<ProdutoDto>(codigo, "{\"erro\":\"produto\"}".toResponseBody())
            val repository = ProdutoRepositoryImpl(conexao) { api { original } }

            assertSame(original, repository.consultarProduto("00123"))
            assertEquals(sessao, conexao.sessao.value)
            assertEquals(FaseConexao.CONECTADO, conexao.estado.value.fase)
        }
    }

    @Test
    fun erroDeTransporteInvalidaSessaoAtivaEPropagaCausa() = runTest {
        val conexao = conexaoAtiva()
        val falha = IOException("Rede indisponível")
        val repository = ProdutoRepositoryImpl(conexao) { api { throw falha } }

        val erro = runCatching { repository.consultarProduto("00123") }.exceptionOrNull()

        assertSame(falha, erro)
        assertNull(conexao.sessao.value)
        assertEquals(FaseConexao.ERRO, conexao.estado.value.fase)
    }

    @Test
    fun erro500OuRedirectInvalidaSessaoSemFallback() = runTest {
        listOf(500, 503, 302).forEach { codigo ->
            val conexao = conexaoAtiva()
            var fabricasChamadas = 0
            val resposta = Response.error<ProdutoDto>(
                "falha".toResponseBody(),
                okhttp3.Response.Builder()
                    .request(Request.Builder().url(ANTERIOR.urlBase).build())
                    .protocol(Protocol.HTTP_1_1)
                    .code(codigo)
                    .message("Resposta de teste")
                    .build()
            )
            val repository = ProdutoRepositoryImpl(conexao) {
                fabricasChamadas++
                api { resposta }
            }

            val erro = runCatching { repository.consultarProduto("00123") }.exceptionOrNull()

            assertTrue(erro is IOException)
            assertEquals(1, fabricasChamadas)
            assertNull(conexao.sessao.value)
            assertEquals(FaseConexao.ERRO, conexao.estado.value.fase)
        }
    }

    @Test
    fun respostaDoServidorAnteriorEhDescartadaSemAfetarNovaSessao() = runTest {
        val conexao = conexaoAtiva()
        val respostaPendente = CompletableDeferred<Response<ProdutoDto>>()
        val repository = ProdutoRepositoryImpl(conexao) { api { respostaPendente.await() } }
        val consulta = async { runCatching { repository.consultarProduto("00123") } }
        runCurrent()

        conexao.conectarManual("198.51.100.5", "9010")
        runCurrent()
        val nova = conexao.sessao.value
        respostaPendente.complete(Response.success(produto()))
        runCurrent()

        assertTrue(consulta.await().exceptionOrNull() is CancellationException)
        assertEquals(nova, conexao.sessao.value)
        assertEquals(FaseConexao.CONECTADO, conexao.estado.value.fase)
    }

    @Test
    fun falhaAntigaNaoInvalidaNovaRevisaoDoMesmoEndereco() = runTest {
        val conexao = conexaoAtiva()
        val anterior = conexao.sessao.value!!
        val respostaPendente = CompletableDeferred<Response<ProdutoDto>>()
        val repository = ProdutoRepositoryImpl(conexao) { api { respostaPendente.await() } }
        val consulta = async { runCatching { repository.consultarProduto("00123") } }
        runCurrent()

        conexao.conectarManual(ANTERIOR.urlBase)
        runCurrent()
        val nova = conexao.sessao.value!!
        respostaPendente.completeExceptionally(IOException("Falha tardia"))
        runCurrent()

        assertTrue(consulta.await().exceptionOrNull() is IOException)
        assertEquals(anterior.endereco, nova.endereco)
        assertTrue(nova.revisao > anterior.revisao)
        assertEquals(nova, conexao.sessao.value)
        assertEquals(FaseConexao.CONECTADO, conexao.estado.value.fase)
    }

    @Test
    fun preservaRespostaOriginalQuandoImagemUsaMesmoEndpointDaApi() = runTest {
        val conexao = conexaoAtiva()
        val produto = produto(imagem = "${ANTERIOR.urlBase}media/img_produtos/00123.webp")
        val original = Response.success(produto)
        val repository = ProdutoRepositoryImpl(conexao) { api { original } }

        val resposta = repository.consultarProduto("00123")

        assertSame(original, resposta)
        assertSame(produto, resposta.body())
        assertEquals(produto.imagem, resposta.body()!!.imagem)
        assertEquals(FaseConexao.CONECTADO, conexao.estado.value.fase)
    }

    @Test
    fun preservaHostIndependenteDoServidorDeImagens() = runTest {
        val conexao = conexaoAtiva()
        listOf(
            "http://192.168.1.40:8080/media/img_produtos/00123.webp",
            "http://198.51.100.40:8080/media/img_produtos/00123.webp"
        ).forEach { imagem ->
            val produto = produto(imagem = imagem)
            val repository = ProdutoRepositoryImpl(conexao) { api { Response.success(produto) } }

            val resposta = repository.consultarProduto("00123")

            assertSame(produto, resposta.body())
            assertEquals(imagem, resposta.body()!!.imagem)
        }
    }

    @Test
    fun preservaPortaDaImagemMesmoQuandoHostCoincideComApi() = runTest {
        val conexao = conexaoAtiva()
        val imagem = "http://${ANTERIOR.ip}:8080/media/img_produtos/00123.webp"
        val repository = ProdutoRepositoryImpl(conexao) {
            api { Response.success(produto(imagem = imagem)) }
        }

        assertEquals(imagem, repository.consultarProduto("00123").body()!!.imagem)
    }

    @Test
    fun preservaServidorDeMidiaHttpOuHttpsComCaminhoProprio() = runTest {
        val conexao = conexaoAtiva()
        listOf(
            "http://imagens.example:8080/catalogo/fotografia.webp",
            "https://cdn.example:8443/fotos/produto.webp"
        ).forEach { imagem ->
            val original = Response.success(produto(imagem = imagem))
            val repository = ProdutoRepositoryImpl(conexao) { api { original } }

            val resposta = repository.consultarProduto("00123")

            assertSame(original, resposta)
            assertEquals(imagem, resposta.body()!!.imagem)
        }
    }

    @Test
    fun preservaParametroDeVersaoEQuerySemRecodificar() = runTest {
        val conexao = conexaoAtiva()
        val imagem = "https://imagens.example:8443/fotos/00123.webp?v=987&nome=a%20b&token=a%2Bb"
        val repository = ProdutoRepositoryImpl(conexao) {
            api { Response.success(produto(imagem = imagem)) }
        }

        assertEquals(imagem, repository.consultarProduto("00123").body()!!.imagem)
    }

    @Test
    fun preservaProdutoSemImagem() = runTest {
        val conexao = conexaoAtiva()
        val produto = produto(imagem = null)
        val repository = ProdutoRepositoryImpl(conexao) { api { Response.success(produto) } }

        val resposta = repository.consultarProduto("00123")

        assertSame(produto, resposta.body())
        assertNull(resposta.body()!!.imagem)
        assertEquals("Produto teste", resposta.body()!!.nome)
        assertEquals("29.90", resposta.body()!!.preco)
    }

    @Test
    fun trocaEndpointDaApiSemModificarUrlDoServidorDeImagens() = runTest {
        val conexao = conexaoAtiva()
        val imagem = "http://198.51.100.40:8080/fotos/00123.webp?v=42"
        val produto = produto(imagem = imagem)
        val enderecos = mutableListOf<EnderecoServidor>()
        val repository = ProdutoRepositoryImpl(conexao) { endereco ->
            enderecos += endereco
            api { Response.success(produto) }
        }

        val primeira = repository.consultarProduto("00123")
        val novoEndereco = EnderecoServidor.criar("198.51.100.5", 9010)
        conexao.conectarManual(novoEndereco.urlBase)
        runCurrent()
        val segunda = repository.consultarProduto("00123")

        assertEquals(listOf(ANTERIOR, novoEndereco), enderecos)
        assertSame(produto, primeira.body())
        assertSame(produto, segunda.body())
        assertEquals(imagem, primeira.body()!!.imagem)
        assertEquals(imagem, segunda.body()!!.imagem)
    }

    private fun TestScope.gerenciador(): GerenciadorConexao = GerenciadorConexao(
        verificador = object : VerificadorApi {
            override suspend fun verificar(endereco: EnderecoServidor) = Unit
        },
        preferencia = object : PreferenciaServidor {
            override fun carregar(): String? = null
            override fun salvar(endereco: String?) = Unit
        },
        escopo = this
    )

    private fun TestScope.conexaoAtiva(): GerenciadorConexao {
        val conexao = gerenciador()
        conexao.conectarManual(ANTERIOR.urlBase)
        runCurrent()
        assertEquals(FaseConexao.CONECTADO, conexao.estado.value.fase)
        return conexao
    }

    private fun api(consultar: suspend (String) -> Response<ProdutoDto>): ProdutoApi = object : ProdutoApi {
        override suspend fun consultarProduto(codigoBarra: String): Response<ProdutoDto> = consultar(codigoBarra)
    }

    private fun produto(codigo: String = "00123", imagem: String? = null): ProdutoDto = ProdutoDto(
        codigoBarra = codigo,
        nome = "Produto teste",
        descricao = "Descrição preservada",
        preco = "29.90",
        dataAtualizacao = "2026-11-12T10:00:00Z",
        imagem = imagem
    )

    private companion object {
        val ANTERIOR = EnderecoServidor.criar("192.0.2.10", 9000)
    }
}
