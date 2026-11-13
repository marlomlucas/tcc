package com.tcc.verificadorprecos.viewmodel

import androidx.lifecycle.ViewModelStore
import com.tcc.verificadorprecos.connection.EnderecoServidor
import com.tcc.verificadorprecos.connection.SessaoServidor
import com.tcc.verificadorprecos.data.ProdutoDto
import com.tcc.verificadorprecos.repository.ProdutoRepository
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ProdutoViewModelServidorTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun semServidorNaoConsultaRepository() = cenario(sessaoInicial = null) { c ->
        c.vm.consultarProduto("00123")
        runCurrent()

        assertTrue(c.repository.pedidos.isEmpty())
        assertEquals(
            ProdutoUiState.Erro("Conecte ao servidor antes de consultar produtos."),
            c.vm.uiState.value
        )
    }

    @Test
    fun sessaoAtivaPermiteConsultarEApresentarProduto() = cenario { c ->
        c.vm.consultarProduto("00123")
        runCurrent()
        assertEquals(ProdutoUiState.Carregando, c.vm.uiState.value)

        val produto = produto("00123")
        c.repository.responder(0, produto)
        runCurrent()

        assertEquals(listOf("00123"), c.repository.pedidos.map { it.codigo })
        assertEquals(ProdutoUiState.Sucesso(produto), c.vm.uiState.value)
    }

    @Test
    fun urlDeImagemNaoCondicionaEstadoDeSucessoComNomeEPreco() = cenario { c ->
        c.vm.consultarProduto("00123")
        runCurrent()
        val produto = produto("00123").copy(
            imagem = "http://198.51.100.40:9/fotos/ausente.webp?v=2"
        )

        // O ViewModel recebe a URL sem acessar a mídia; falhas do Coil são validadas na UI.
        c.repository.responder(0, produto)
        runCurrent()

        assertEquals(ProdutoUiState.Sucesso(produto), c.vm.uiState.value)
        val exibido = (c.vm.uiState.value as ProdutoUiState.Sucesso).produto
        assertEquals("Produto 00123", exibido.nome)
        assertEquals("24.90", exibido.preco)
        assertEquals(produto.imagem, exibido.imagem)
    }

    @Test
    fun trocaDeServidorLimpaProdutoAnteriorEPermiteNovaConsulta() = cenario { c ->
        c.vm.consultarProduto("00123")
        runCurrent()
        c.repository.responder(0, produto("00123"))
        runCurrent()

        c.servidor.value = sessaoB
        runCurrent()
        assertEquals(ProdutoUiState.Inicial, c.vm.uiState.value)

        c.vm.consultarProduto("00456")
        runCurrent()
        val novo = produto("00456")
        c.repository.responder(1, novo)
        runCurrent()
        assertEquals(ProdutoUiState.Sucesso(novo), c.vm.uiState.value)
    }

    @Test
    fun perdaDaSessaoLimpaProdutoEImpedeNovaConsulta() = cenario { c ->
        c.vm.consultarProduto("00123")
        runCurrent()
        c.repository.responder(0, produto("00123"))
        runCurrent()

        c.servidor.value = null
        runCurrent()
        assertEquals(ProdutoUiState.Inicial, c.vm.uiState.value)

        c.vm.consultarProduto("00456")
        runCurrent()
        assertEquals(1, c.repository.pedidos.size)
        assertTrue(c.vm.uiState.value is ProdutoUiState.Erro)
    }

    @Test
    fun novaRevisaoDoMesmoEnderecoCancelaPendenteEDescartaRetornoAntigo() = cenario { c ->
        c.vm.consultarProduto("00123")
        runCurrent()
        val pendente = c.repository.pedidos.single()

        c.servidor.value = sessaoA.copy(revisao = 2L)
        runCurrent()

        assertTrue(pendente.job.isCancelled)
        assertEquals(ProdutoUiState.Inicial, c.vm.uiState.value)
        c.repository.responder(0, produto("00123"))
        runCurrent()
        assertEquals(ProdutoUiState.Inicial, c.vm.uiState.value)
    }

    @Test
    fun respostaIncooperativaDoServidorAnteriorNaoSubstituiProdutoAtual() = cenario { c ->
        prepararConsultaEmDoisServidores(c)
        val atual = produto("00456")
        c.repository.responder(1, atual)
        runCurrent()

        c.repository.responder(0, produto("00123"))
        runCurrent()

        assertTrue(c.repository.pedidos.first().job.isCancelled)
        assertEquals(ProdutoUiState.Sucesso(atual), c.vm.uiState.value)
    }

    @Test
    fun ioExceptionDoServidorAnteriorNaoSubstituiProdutoAtual() = cenario { c ->
        prepararConsultaEmDoisServidores(c)
        val atual = produto("00456")
        c.repository.responder(1, atual)
        runCurrent()

        c.repository.falhar(0, IOException("servidor anterior caiu"))
        runCurrent()

        assertEquals(ProdutoUiState.Sucesso(atual), c.vm.uiState.value)
    }

    @Test
    fun erroGenericoDoServidorAnteriorNaoSubstituiProdutoAtual() = cenario { c ->
        prepararConsultaEmDoisServidores(c)
        val atual = produto("00456")
        c.repository.responder(1, atual)
        runCurrent()

        c.repository.falhar(0, IllegalStateException("resposta antiga inválida"))
        runCurrent()

        assertEquals(ProdutoUiState.Sucesso(atual), c.vm.uiState.value)
    }

    private fun TestScope.prepararConsultaEmDoisServidores(cenario: Cenario) {
        cenario.vm.consultarProduto("00123")
        runCurrent()
        cenario.servidor.value = sessaoB
        runCurrent()
        assertEquals(ProdutoUiState.Inicial, cenario.vm.uiState.value)
        cenario.vm.consultarProduto("00456")
        runCurrent()
        assertEquals(ProdutoUiState.Carregando, cenario.vm.uiState.value)
    }

    private fun cenario(
        sessaoInicial: SessaoServidor? = sessaoA,
        acao: suspend TestScope.(Cenario) -> Unit
    ) = runTest {
        val cenario = Cenario(sessaoInicial)
        try {
            acao(cenario)
        } finally {
            cenario.store.clear()
            cenario.repository.concluirPendentes()
            runCurrent()
        }
    }

    private class Cenario(sessaoInicial: SessaoServidor?) {
        val servidor = MutableStateFlow(sessaoInicial)
        val repository = RepositoryPendente()
        val vm = ProdutoViewModel(repository, servidor)
        val store = ViewModelStore().apply { put("produto", vm) }
    }

    private data class Pedido(
        val codigo: String,
        val job: Job,
        val continuacao: Continuation<Response<ProdutoDto>>,
        var concluido: Boolean = false
    )

    /** Simula callbacks de HTTP que ainda retornam depois do cancelamento do Job. */
    private class RepositoryPendente : ProdutoRepository {
        val pedidos = mutableListOf<Pedido>()

        override suspend fun consultarProduto(codigoBarra: String): Response<ProdutoDto> {
            val job = checkNotNull(currentCoroutineContext()[Job])
            return suspendCoroutine { continuacao ->
                pedidos += Pedido(codigoBarra, job, continuacao)
            }
        }

        fun responder(indice: Int, produto: ProdutoDto) {
            val pedido = pedidos[indice]
            check(!pedido.concluido)
            pedido.concluido = true
            pedido.continuacao.resume(Response.success(produto))
        }

        fun falhar(indice: Int, erro: Exception) {
            val pedido = pedidos[indice]
            check(!pedido.concluido)
            pedido.concluido = true
            pedido.continuacao.resumeWithException(erro)
        }

        fun concluirPendentes() {
            pedidos.forEachIndexed { indice, pedido ->
                if (!pedido.concluido) responder(indice, produto(pedido.codigo))
            }
        }
    }

    companion object {
        private val sessaoA = SessaoServidor(EnderecoServidor.criar("192.0.2.10", 8000), 1L)
        private val sessaoB = SessaoServidor(EnderecoServidor.criar("192.0.2.20", 9000), 2L)

        private fun produto(codigo: String) = ProdutoDto(
            codigoBarra = codigo,
            nome = "Produto $codigo",
            descricao = "Produto de teste",
            preco = "24.90",
            dataAtualizacao = "2026-11-12T10:00:00Z"
        )
    }
}
