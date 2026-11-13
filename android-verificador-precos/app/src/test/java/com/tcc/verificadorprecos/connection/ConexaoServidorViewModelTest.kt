package com.tcc.verificadorprecos.connection

import androidx.lifecycle.ViewModelStore
import com.tcc.verificadorprecos.discovery.AnuncioServico
import com.tcc.verificadorprecos.discovery.DescobertaServidorViewModel
import com.tcc.verificadorprecos.discovery.EventosDescoberta
import com.tcc.verificadorprecos.discovery.PlataformaDescoberta
import com.tcc.verificadorprecos.discovery.ServicoResolvido
import com.tcc.verificadorprecos.discovery.TIPO_SERVICO_NSD
import com.tcc.verificadorprecos.viewmodel.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConexaoServidorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun automaticoEsperaFimDosDezSegundosAntesDoHealthEPausaConservaConectado() = cenario { c ->
        c.vm.iniciar()
        c.plataforma.eventos.single().encontrado(anuncio("Servidor A"))
        runCurrent()
        c.plataforma.concluir(0, "192.0.2.10")
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()

        assertEquals(FaseConexao.PROCURANDO, c.vm.estado.value.fase)
        assertTrue(c.api.pedidos.isEmpty())
        assertNull(c.vm.sessao.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(FaseConexao.VERIFICANDO, c.vm.estado.value.fase)
        assertEquals(enderecoA, c.api.pedidos.single().endereco)
        assertNull(c.vm.sessao.value)

        c.api.concluir(0)
        runCurrent()
        val sessao = c.vm.sessao.value
        assertEquals(enderecoA, sessao?.endereco)

        c.vm.pausar()
        c.vm.iniciar()
        runCurrent()
        assertEquals(sessao, c.vm.sessao.value)
        assertEquals(FaseConexao.CONECTADO, c.vm.estado.value.fase)
        assertEquals(1, c.plataforma.eventos.size)
        assertTrue(c.preferencia.gravacoes.isEmpty())
    }

    @Test
    fun multiplosServidoresAoFimDaBuscaExigemEscolha() = cenario { c ->
        c.vm.iniciar()
        val eventos = c.plataforma.eventos.single()
        eventos.encontrado(anuncio("Servidor A"))
        eventos.encontrado(anuncio("Servidor B"))
        runCurrent()
        c.plataforma.concluir(0, "192.0.2.10")
        runCurrent()
        c.plataforma.concluir(1, "192.0.2.20")
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(FaseConexao.ESCOLHER_SERVIDOR, c.vm.estado.value.fase)
        assertEquals(2, c.vm.estado.value.candidatos.size)
        assertTrue(c.api.pedidos.isEmpty())
        assertNull(c.vm.sessao.value)

        val escolha = c.vm.estado.value.candidatos.single { it.nome == "Servidor B" }
        c.vm.selecionar(escolha)
        runCurrent()
        assertEquals(escolha.endereco, c.api.pedidos.single().endereco)
        c.api.concluir(0)
        runCurrent()
        assertEquals(escolha.endereco, c.vm.sessao.value?.endereco)
    }

    @Test
    fun nomesDistintosNoMesmoEnderecoExigemEscolhaSemHealth() = cenario { c ->
        c.vm.iniciar()
        val eventos = c.plataforma.eventos.single()
        eventos.encontrado(anuncio("Servidor A"))
        eventos.encontrado(anuncio("Servidor B"))
        runCurrent()
        c.plataforma.concluir(0, "192.0.2.10")
        runCurrent()
        c.plataforma.concluir(1, "192.0.2.10")
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(FaseConexao.ESCOLHER_SERVIDOR, c.vm.estado.value.fase)
        assertEquals(
            listOf(OpcaoServidor("Servidor A", enderecoA), OpcaoServidor("Servidor B", enderecoA)),
            c.vm.estado.value.candidatos
        )
        assertTrue(c.api.pedidos.isEmpty())
        assertNull(c.vm.sessao.value)
    }

    @Test
    fun manualSalvoTemPrioridadeEIniciarRepetidoNaoDuplicaHealth() = cenario { c ->
        c.preferencia.valor = enderecoA.urlBase

        c.vm.iniciar()
        c.vm.iniciar()
        runCurrent()

        assertTrue(c.plataforma.eventos.isEmpty())
        assertEquals(ModoConexao.MANUAL, c.vm.estado.value.modo)
        assertEquals(enderecoA, c.api.pedidos.single().endereco)
        assertNull(c.vm.sessao.value)

        c.api.concluir(0)
        runCurrent()
        assertEquals(enderecoA, c.vm.sessao.value?.endereco)
        assertTrue(c.preferencia.gravacoes.isEmpty())
    }

    @Test
    fun permissaoNegadaDuranteNsdIgnoraCallbacksEEncerraBusca() = cenario { c ->
        c.vm.iniciar()
        val antigos = c.plataforma.eventos.single()
        antigos.encontrado(anuncio("Servidor A"))
        runCurrent()

        c.vm.permissaoNegada()
        c.plataforma.concluir(0, "192.0.2.10")
        antigos.encontrado(anuncio("Tardio"))
        antigos.erro("erro antigo")
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(FaseConexao.ERRO, c.vm.estado.value.fase)
        assertTrue(c.vm.estado.value.mensagem.orEmpty().contains("rede local"))
        assertNull(c.vm.sessao.value)
        assertTrue(c.api.pedidos.isEmpty())
        assertEquals(1, c.plataforma.resolucoes.size)
        assertEquals(1, c.plataforma.paradas)
    }

    @Test
    fun permissaoNegadaDuranteHealthDescartaSucessoTardioSemSalvar() = cenario { c ->
        c.vm.conectarManual("192.0.2.10", "8000")
        runCurrent()

        c.vm.permissaoNegada()
        c.api.concluir(0)
        runCurrent()

        assertEquals(FaseConexao.ERRO, c.vm.estado.value.fase)
        assertNull(c.vm.sessao.value)
        assertTrue(c.preferencia.gravacoes.isEmpty())
        assertTrue(c.plataforma.eventos.isEmpty())
    }

    @Test
    fun pausarHealthPendenteCancelaENovaBuscaIgnoraCallbacksAnteriores() = cenario { c ->
        c.vm.conectarManual("192.0.2.10", "8000")
        runCurrent()
        c.vm.pausar()
        c.api.concluir(0)
        runCurrent()

        assertEquals(FaseConexao.INDISPONIVEL, c.vm.estado.value.fase)
        assertNull(c.vm.sessao.value)
        assertTrue(c.preferencia.gravacoes.isEmpty())

        c.vm.usarAutomatico()
        val antigos = c.plataforma.eventos.single()
        antigos.encontrado(anuncio("Antigo"))
        runCurrent()
        c.vm.pausar()
        c.vm.iniciar()
        val novos = c.plataforma.eventos.last()
        novos.encontrado(anuncio("Novo"))
        antigos.erro("erro antigo")
        antigos.encontrado(anuncio("Tardio"))
        runCurrent()

        c.plataforma.concluir(0, "192.0.2.10")
        runCurrent()
        c.plataforma.concluir(1, "192.0.2.20")
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(2, c.plataforma.eventos.size)
        assertEquals(listOf("Antigo", "Novo"), c.plataforma.resolucoes.map { it.anuncio.nome })
        assertEquals(FaseConexao.VERIFICANDO, c.vm.estado.value.fase)
        assertEquals("192.0.2.20", c.api.pedidos.last().endereco.ip)
        c.api.concluir(1)
        runCurrent()
        assertEquals("192.0.2.20", c.vm.sessao.value?.endereco?.ip)
    }

    private fun cenario(acao: suspend TestScope.(Cenario) -> Unit) = runTest {
        val cenario = Cenario()
        try {
            acao(cenario)
        } finally {
            cenario.store.clear()
            cenario.api.pedidos.forEach { it.resposta.complete(Unit) }
            runCurrent()
        }
    }

    private class Cenario {
        val plataforma = PlataformaFake()
        val api = VerificadorFake()
        val preferencia = PreferenciaFake()
        val descoberta = DescobertaServidorViewModel(plataforma)
        val vm = ConexaoServidorViewModel(descoberta, api, preferencia)
        val store = ViewModelStore().apply {
            put("descoberta", descoberta)
            put("conexao", vm)
        }
    }

    private data class Resolucao(
        val anuncio: AnuncioServico,
        val callback: (Result<ServicoResolvido>) -> Unit
    )

    private class PlataformaFake : PlataformaDescoberta {
        val eventos = mutableListOf<EventosDescoberta>()
        val resolucoes = mutableListOf<Resolucao>()
        var paradas = 0

        override fun iniciar(eventos: EventosDescoberta) {
            this.eventos += eventos
        }

        override fun resolver(servico: AnuncioServico, resultado: (Result<ServicoResolvido>) -> Unit) {
            resolucoes += Resolucao(servico, resultado)
        }

        override fun parar() {
            paradas++
        }

        fun concluir(indice: Int, ip: String) {
            val resolucao = resolucoes[indice]
            resolucao.callback(Result.success(ServicoResolvido(
                anuncio = resolucao.anuncio,
                enderecos = listOf(ip),
                porta = 8000,
                atributos = mapOf(
                    "app" to "verificador-precos".toByteArray(),
                    "version" to "1.0".toByteArray()
                )
            )))
        }
    }

    private data class Pedido(
        val endereco: EnderecoServidor,
        val resposta: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private class VerificadorFake : VerificadorApi {
        val pedidos = mutableListOf<Pedido>()

        override suspend fun verificar(endereco: EnderecoServidor) {
            val pedido = Pedido(endereco)
            pedidos += pedido
            withContext(NonCancellable) { pedido.resposta.await() }
        }

        fun concluir(indice: Int) {
            pedidos[indice].resposta.complete(Unit)
        }
    }

    private class PreferenciaFake : PreferenciaServidor {
        var valor: String? = null
        val gravacoes = mutableListOf<String?>()

        override fun carregar(): String? = valor

        override fun salvar(endereco: String?) {
            valor = endereco
            gravacoes += endereco
        }
    }

    companion object {
        private val enderecoA = EnderecoServidor.criar("192.0.2.10", 8000)
        private fun anuncio(nome: String) = AnuncioServico(nome, TIPO_SERVICO_NSD)
    }
}
