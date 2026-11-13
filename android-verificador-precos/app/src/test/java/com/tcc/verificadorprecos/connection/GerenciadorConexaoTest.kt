package com.tcc.verificadorprecos.connection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GerenciadorConexaoTest {
    @Test
    fun ausenciaDePreferenciaNaoIniciaVerificacaoNemDescoberta() = cenario { api, _, gerente ->
        assertFalse(gerente.restaurarManual())
        assertEquals(FaseConexao.AGUARDANDO, gerente.estado.value.fase)
        assertEquals(ModoConexao.AUTOMATICO, gerente.estado.value.modo)
        assertNull(gerente.sessao.value)
        assertTrue(api.pedidos.isEmpty())
    }

    @Test
    fun restauraManualSomenteDepoisDeValidarSemRegravarPreferencia() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase

        assertTrue(gerente.restaurarManual())
        runCurrent()

        assertEquals(FaseConexao.VERIFICANDO, gerente.estado.value.fase)
        assertEquals(ModoConexao.MANUAL, gerente.estado.value.modo)
        assertEquals(enderecoA, api.pedidos.single().endereco)
        assertNull(gerente.sessao.value)
        assertTrue(pref.gravacoes.isEmpty())

        api.concluir(0)
        runCurrent()

        assertEquals(FaseConexao.CONECTADO, gerente.estado.value.fase)
        assertEquals(enderecoA, gerente.sessao.value?.endereco)
        assertEquals(gerente.sessao.value, gerente.estado.value.sessao)
        assertEquals(enderecoA.urlBase, gerente.estado.value.manualSalvo)
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun preferenciaInvalidaContinuaManualSemFallbackAutomatico() = cenario { api, pref, gerente ->
        pref.valor = "endereco incorreto"

        assertTrue(gerente.restaurarManual())
        gerente.procurar()
        gerente.apresentarCandidatos(listOf(opcaoA))
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals(ModoConexao.MANUAL, gerente.estado.value.modo)
        assertEquals("endereco incorreto", pref.valor)
        assertEquals(pref.valor, gerente.estado.value.manualSalvo)
        assertTrue(api.pedidos.isEmpty())
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun erroDeLeituraDaPreferenciaEhVisivelENaoLiberaFallback() = cenario { api, pref, gerente ->
        pref.erroLeitura = IllegalStateException("arquivo indisponível")

        assertTrue(gerente.restaurarManual())
        gerente.procurar()
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals(ModoConexao.MANUAL, gerente.estado.value.modo)
        assertTrue(gerente.estado.value.mensagem.orEmpty().contains("arquivo indisponível"))
        assertTrue(api.pedidos.isEmpty())
    }

    @Test
    fun servidorManualSalvoIndisponivelNaoViraAutomatico() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase
        gerente.restaurarManual()
        runCurrent()

        api.falhar(0, IllegalStateException("health sem resposta"))
        runCurrent()
        gerente.procurar()
        gerente.apresentarCandidatos(listOf(opcaoB))
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals(ModoConexao.MANUAL, gerente.estado.value.modo)
        assertNull(gerente.sessao.value)
        assertEquals(enderecoA.urlBase, pref.valor)
        assertEquals(1, api.pedidos.size)
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun unicoCandidatoAutomaticoPrecisaPassarPeloHealth() = cenario { api, pref, gerente ->
        gerente.procurar()
        gerente.apresentarCandidatos(listOf(opcaoA))
        runCurrent()

        assertEquals(FaseConexao.VERIFICANDO, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertEquals(enderecoA, api.pedidos.single().endereco)

        api.concluir(0)
        runCurrent()

        assertEquals(FaseConexao.CONECTADO, gerente.estado.value.fase)
        assertEquals(ModoConexao.AUTOMATICO, gerente.estado.value.modo)
        assertEquals(enderecoA, gerente.sessao.value?.endereco)
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun multiplosCandidatosExigemEscolhaDeUmaOpcaoApresentada() = cenario { api, _, gerente ->
        gerente.procurar()
        gerente.apresentarCandidatos(listOf(opcaoA, opcaoB))
        runCurrent()

        assertEquals(FaseConexao.ESCOLHER_SERVIDOR, gerente.estado.value.fase)
        assertEquals(listOf(opcaoA, opcaoB), gerente.estado.value.candidatos)
        assertTrue(api.pedidos.isEmpty())

        gerente.selecionar(OpcaoServidor("Intruso", EnderecoServidor.criar("192.0.2.99", 8000)))
        runCurrent()
        assertTrue(api.pedidos.isEmpty())

        gerente.selecionar(opcaoB)
        gerente.selecionar(opcaoA)
        runCurrent()
        assertEquals(enderecoB, api.pedidos.single().endereco)
        assertNull(gerente.sessao.value)

        api.concluir(0)
        runCurrent()
        assertEquals(enderecoB, gerente.sessao.value?.endereco)
    }

    @Test
    fun descobertaSemCandidatosTerminaIndisponivel() = cenario { api, _, gerente ->
        gerente.procurar()
        gerente.apresentarCandidatos(emptyList())
        runCurrent()

        assertEquals(FaseConexao.INDISPONIVEL, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertTrue(gerente.estado.value.candidatos.isEmpty())
        assertTrue(api.pedidos.isEmpty())
    }

    @Test
    fun resultadosForaDaBuscaNaoDisparamVerificacao() = cenario { api, _, gerente ->
        gerente.apresentarCandidatos(listOf(opcaoA))
        gerente.procurar()
        gerente.interromperOperacao()
        gerente.apresentarCandidatos(listOf(opcaoA))
        runCurrent()

        assertEquals(FaseConexao.INDISPONIVEL, gerente.estado.value.fase)
        assertTrue(api.pedidos.isEmpty())
    }

    @Test
    fun entradaManualSoEhSalvaEAtivadaDepoisDoHealth() = cenario { api, pref, gerente ->
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()

        assertEquals(FaseConexao.VERIFICANDO, gerente.estado.value.fase)
        assertNull(pref.valor)
        assertNull(gerente.estado.value.manualSalvo)
        assertNull(gerente.sessao.value)
        assertTrue(pref.gravacoes.isEmpty())

        api.concluir(0)
        runCurrent()

        assertEquals(listOf(enderecoA.urlBase), pref.gravacoes)
        assertEquals(enderecoA.urlBase, pref.valor)
        assertEquals(enderecoA.urlBase, gerente.estado.value.manualSalvo)
        assertEquals(enderecoA, gerente.sessao.value?.endereco)
    }

    @Test
    fun tentativaManualInvalidaPreservaPreferenciaAnterior() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase
        gerente.restaurarManual()
        runCurrent()
        api.concluir(0)
        runCurrent()

        gerente.conectarManual("192.0.2.20", "70000")
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals(ModoConexao.MANUAL, gerente.estado.value.modo)
        assertNull(gerente.sessao.value)
        assertNull(gerente.estado.value.sessao)
        assertEquals(enderecoA.urlBase, pref.valor)
        assertEquals(enderecoA.urlBase, gerente.estado.value.manualSalvo)
        assertTrue(pref.gravacoes.isEmpty())
        assertEquals(1, api.pedidos.size)
    }

    @Test
    fun falhaDoNovoManualPreservaPreferenciaAnterior() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase
        gerente.restaurarManual()
        runCurrent()
        api.concluir(0)
        runCurrent()

        gerente.conectarManual("192.0.2.20", "9000")
        runCurrent()
        api.falhar(1, IllegalStateException("não é uma API compatível"))
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertEquals(enderecoA.urlBase, pref.valor)
        assertEquals(enderecoA.urlBase, gerente.estado.value.manualSalvo)
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun falhaAoSalvarNaoDeclaraConexaoNemPreferenciaNova() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase
        gerente.restaurarManual()
        runCurrent()
        api.concluir(0)
        runCurrent()
        pref.erroGravacao = IllegalStateException("disco cheio")

        gerente.conectarManual("192.0.2.20", "9000")
        runCurrent()
        api.concluir(1)
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertEquals(enderecoA.urlBase, pref.valor)
        assertEquals(enderecoA.urlBase, gerente.estado.value.manualSalvo)
        assertTrue(gerente.estado.value.mensagem.orEmpty().contains("salvar"))
        assertTrue(gerente.estado.value.mensagem.orEmpty().contains("disco cheio"))
    }

    @Test
    fun retornoExplicitoAoAutomaticoRemovePreferenciaEIniciaNovaBusca() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase
        gerente.restaurarManual()
        runCurrent()
        api.concluir(0)
        runCurrent()

        gerente.usarAutomatico()

        assertEquals(listOf<String?>(null), pref.gravacoes)
        assertNull(pref.valor)
        assertNull(gerente.estado.value.manualSalvo)
        assertNull(gerente.sessao.value)
        assertEquals(ModoConexao.AUTOMATICO, gerente.estado.value.modo)
        assertEquals(FaseConexao.PROCURANDO, gerente.estado.value.fase)
    }

    @Test
    fun falhaAoRemoverPreferenciaMantemModoManualEExibeErro() = cenario { api, pref, gerente ->
        pref.valor = enderecoA.urlBase
        gerente.restaurarManual()
        runCurrent()
        api.concluir(0)
        runCurrent()
        pref.erroGravacao = IllegalStateException("sem acesso")

        gerente.usarAutomatico()
        gerente.procurar()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals(ModoConexao.MANUAL, gerente.estado.value.modo)
        assertEquals(enderecoA.urlBase, pref.valor)
        assertEquals(enderecoA.urlBase, gerente.estado.value.manualSalvo)
        assertNull(gerente.sessao.value)
        assertTrue(gerente.estado.value.mensagem.orEmpty().contains("sem acesso"))
    }

    @Test
    fun novaBuscaInvalidaConexaoJaValidada() = cenario { api, _, gerente ->
        conectarAutomatico(gerente, api)

        gerente.procurar()

        assertEquals(FaseConexao.PROCURANDO, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertNull(gerente.estado.value.sessao)
        assertTrue(gerente.estado.value.candidatos.isEmpty())
    }

    @Test
    fun interromperPreservaSessaoJaValidada() = cenario { api, _, gerente ->
        conectarAutomatico(gerente, api)
        val sessao = gerente.sessao.value

        gerente.interromperOperacao()
        runCurrent()

        assertEquals(sessao, gerente.sessao.value)
        assertEquals(sessao, gerente.estado.value.sessao)
        assertEquals(FaseConexao.CONECTADO, gerente.estado.value.fase)
    }

    @Test
    fun interromperIgnoraSucessoManualTardioMesmoSemCancelamentoCooperativo() = cenario { api, pref, gerente ->
        api.ignorarCancelamento = true
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()

        gerente.interromperOperacao()
        api.concluir(0)
        runCurrent()

        assertEquals(FaseConexao.INDISPONIVEL, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun novaVerificacaoNaoRecebeSucessoAntigoNemSuaPreferencia() = cenario { api, pref, gerente ->
        api.ignorarCancelamento = true
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()
        gerente.conectarManual("192.0.2.20", "9000")
        runCurrent()

        api.concluir(1)
        runCurrent()
        val nova = gerente.sessao.value
        api.concluir(0)
        runCurrent()

        assertEquals(nova, gerente.sessao.value)
        assertEquals(enderecoB, nova?.endereco)
        assertEquals(FaseConexao.CONECTADO, gerente.estado.value.fase)
        assertEquals(listOf(enderecoB.urlBase), pref.gravacoes)
    }

    @Test
    fun erroAntigoNaoSobrescreveConexaoNova() = cenario { api, pref, gerente ->
        api.ignorarCancelamento = true
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()
        gerente.conectarManual("192.0.2.20", "9000")
        runCurrent()
        api.concluir(1)
        runCurrent()
        val nova = gerente.sessao.value

        api.falhar(0, IllegalStateException("falha antiga"))
        runCurrent()

        assertEquals(nova, gerente.sessao.value)
        assertEquals(FaseConexao.CONECTADO, gerente.estado.value.fase)
        assertNull(gerente.estado.value.mensagem)
        assertEquals(listOf(enderecoB.urlBase), pref.gravacoes)
    }

    @Test
    fun retornoAutomaticoDescartaManualAindaEmVerificacao() = cenario { api, pref, gerente ->
        api.ignorarCancelamento = true
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()

        gerente.usarAutomatico()
        gerente.apresentarCandidatos(listOf(opcaoB))
        runCurrent()
        api.concluir(0)
        runCurrent()

        assertEquals(FaseConexao.VERIFICANDO, gerente.estado.value.fase)
        assertEquals(ModoConexao.AUTOMATICO, gerente.estado.value.modo)
        assertNull(gerente.sessao.value)
        assertEquals(listOf<String?>(null), pref.gravacoes)

        api.concluir(1)
        runCurrent()
        assertEquals(enderecoB, gerente.sessao.value?.endereco)
    }

    @Test
    fun reconectarMesmoEnderecoCriaNovaRevisaoEIgnoraFalhaDaSessaoAntiga() = cenario { api, _, gerente ->
        conectarAutomatico(gerente, api)
        val antiga = requireNotNull(gerente.sessao.value)
        conectarAutomatico(gerente, api)
        val nova = requireNotNull(gerente.sessao.value)

        assertEquals(antiga.endereco, nova.endereco)
        assertNotEquals(antiga.revisao, nova.revisao)
        assertTrue(nova.revisao > antiga.revisao)

        gerente.registrarFalha(antiga, "consulta antiga falhou")
        assertEquals(nova, gerente.sessao.value)
        assertEquals(FaseConexao.CONECTADO, gerente.estado.value.fase)

        gerente.registrarFalha(nova, "consulta atual falhou")
        assertNull(gerente.sessao.value)
        assertNull(gerente.estado.value.sessao)
        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals("consulta atual falhou", gerente.estado.value.mensagem)
    }

    @Test
    fun informarErroInvalidaConexaoAtiva() = cenario { api, _, gerente ->
        conectarAutomatico(gerente, api)

        gerente.informarErro("Permissão de rede local revogada")

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertNull(gerente.sessao.value)
        assertNull(gerente.estado.value.sessao)
        assertEquals("Permissão de rede local revogada", gerente.estado.value.mensagem)
    }

    @Test
    fun informarErroInvalidaVerificacaoPendenteEResultadoTardio() = cenario { api, pref, gerente ->
        api.ignorarCancelamento = true
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()

        gerente.informarErro("Permissão revogada")
        api.falhar(0, IllegalStateException("erro posterior"))
        runCurrent()

        assertEquals(FaseConexao.ERRO, gerente.estado.value.fase)
        assertEquals("Permissão revogada", gerente.estado.value.mensagem)
        assertNull(gerente.sessao.value)
        assertTrue(pref.gravacoes.isEmpty())
    }

    @Test
    fun cancellationExceptionDoVerificadorNaoViraErroDeConexao() = cenario { api, pref, gerente ->
        gerente.conectarManual("192.0.2.10", "8000")
        runCurrent()

        api.falhar(0, CancellationException("operação cancelada"))
        runCurrent()

        assertNull(gerente.sessao.value)
        assertNull(gerente.estado.value.mensagem)
        assertTrue(pref.gravacoes.isEmpty())
        assertNotEquals(FaseConexao.ERRO, gerente.estado.value.fase)
    }

    private fun cenario(
        acao: suspend TestScope.(VerificadorFake, PreferenciaFake, GerenciadorConexao) -> Unit
    ) = runTest {
        val api = VerificadorFake()
        val preferencia = PreferenciaFake()
        val gerente = GerenciadorConexao(api, preferencia, this)
        try {
            acao(api, preferencia, gerente)
        } finally {
            gerente.interromperOperacao()
            api.concluirPendentes()
            runCurrent()
        }
    }

    private fun TestScope.conectarAutomatico(gerente: GerenciadorConexao, api: VerificadorFake) {
        gerente.procurar()
        gerente.apresentarCandidatos(listOf(opcaoA))
        runCurrent()
        api.concluir(api.pedidos.lastIndex)
        runCurrent()
    }

    private data class Pedido(
        val endereco: EnderecoServidor,
        val resposta: CompletableDeferred<Unit> = CompletableDeferred()
    )

    private class VerificadorFake : VerificadorApi {
        val pedidos = mutableListOf<Pedido>()
        var ignorarCancelamento = false

        override suspend fun verificar(endereco: EnderecoServidor) {
            val pedido = Pedido(endereco)
            pedidos += pedido
            if (ignorarCancelamento) {
                withContext(NonCancellable) { pedido.resposta.await() }
            } else {
                pedido.resposta.await()
            }
        }

        fun concluir(indice: Int) {
            pedidos[indice].resposta.complete(Unit)
        }

        fun falhar(indice: Int, erro: Exception) {
            pedidos[indice].resposta.completeExceptionally(erro)
        }

        fun concluirPendentes() {
            pedidos.forEach { it.resposta.complete(Unit) }
        }
    }

    private class PreferenciaFake : PreferenciaServidor {
        var valor: String? = null
        var erroLeitura: Exception? = null
        var erroGravacao: Exception? = null
        val gravacoes = mutableListOf<String?>()

        override fun carregar(): String? {
            erroLeitura?.let { throw it }
            return valor
        }

        override fun salvar(endereco: String?) {
            erroGravacao?.let { throw it }
            valor = endereco
            gravacoes += endereco
        }
    }

    companion object {
        private val enderecoA = EnderecoServidor.criar("192.0.2.10", 8000)
        private val enderecoB = EnderecoServidor.criar("192.0.2.20", 9000)
        private val opcaoA = OpcaoServidor("Servidor A", enderecoA)
        private val opcaoB = OpcaoServidor("Servidor B", enderecoB)
    }
}
