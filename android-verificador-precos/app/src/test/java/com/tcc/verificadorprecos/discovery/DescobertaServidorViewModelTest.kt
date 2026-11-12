package com.tcc.verificadorprecos.discovery

import com.tcc.verificadorprecos.viewmodel.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DescobertaServidorViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun estadoInicialNaoIniciaBuscaAutomaticamente() = cenario { plataforma, vm ->
        assertEquals(FaseDescoberta.AGUARDANDO, vm.estado.value.fase)
        assertFalse(vm.estado.value.buscando)
        assertTrue(vm.estado.value.servidores.isEmpty())
        assertTrue(plataforma.eventos.isEmpty())
    }

    @Test
    fun inicioExplicitoNaoCriaBuscasSimultaneas() = cenario { plataforma, vm ->
        vm.iniciar()
        vm.iniciar()

        assertTrue(vm.estado.value.buscando)
        assertEquals(FaseDescoberta.PROCURANDO, vm.estado.value.fase)
        assertEquals(1, plataforma.eventos.size)
    }

    @Test
    fun timeoutEncerraBuscaVaziaAosDezSegundos() = cenario { plataforma, vm ->
        vm.iniciar()
        runCurrent()
        advanceTimeBy(9_999)
        runCurrent()
        assertTrue(vm.estado.value.buscando)

        advanceTimeBy(1)
        runCurrent()

        assertFalse(vm.estado.value.buscando)
        assertEquals(FaseDescoberta.SEM_RESULTADOS, vm.estado.value.fase)
        assertEquals(1, plataforma.paradas)
    }

    @Test
    fun resolucaoCompativelPublicaServidorEncontrado() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio())
        runCurrent()
        assertEquals(FaseDescoberta.RESOLVENDO, vm.estado.value.fase)

        plataforma.concluir(0)
        runCurrent()

        assertEquals(FaseDescoberta.ENCONTRADO, vm.estado.value.fase)
        assertTrue(vm.estado.value.buscando)
        assertEquals(listOf("192.0.2.10"), vm.estado.value.servidores.single().enderecosIpv4)
    }

    @Test
    fun resolveUmaVezPorVezEListaTodosOsServidores() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio("A"))
        plataforma.atual.encontrado(anuncio("B"))
        runCurrent()
        assertEquals(listOf("A"), plataforma.resolucoes.map { it.anuncio.nome })

        plataforma.concluir(0)
        runCurrent()
        assertEquals(listOf("A", "B"), plataforma.resolucoes.map { it.anuncio.nome })
        plataforma.concluir(1, ip = "192.0.2.11")
        runCurrent()

        assertEquals(listOf("A", "B"), vm.estado.value.servidores.map { it.nome })
    }

    @Test
    fun anunciosDuplicadosNaoDisparamNovasResolucoes() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio())
        plataforma.atual.encontrado(anuncio().copy(tipo = "._vprecos._tcp"))
        runCurrent()
        plataforma.concluir(0)
        runCurrent()
        plataforma.atual.encontrado(anuncio())
        runCurrent()

        assertEquals(1, plataforma.resolucoes.size)
        assertEquals(1, vm.estado.value.servidores.size)
    }

    @Test
    fun tipoIncompativelNaoEhResolvidoEFicaVisivelComoAviso() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(AnuncioServico("Outro", "_http._tcp."))
        runCurrent()

        assertTrue(plataforma.resolucoes.isEmpty())
        assertTrue(vm.estado.value.servidores.isEmpty())
        assertTrue(vm.estado.value.avisos.any { it.contains("incompatível") })
    }

    @Test
    fun txtIncompativelNaoViraResultadoEFicaVisivelComoAviso() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio())
        runCurrent()
        val resultado = resolvido(anuncio()).copy(atributos = mapOf("app" to "outro".toByteArray()))
        plataforma.resolucoes[0].callback(Result.success(resultado))
        runCurrent()

        assertTrue(vm.estado.value.servidores.isEmpty())
        assertTrue(vm.estado.value.avisos.isNotEmpty())
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(FaseDescoberta.SEM_RESULTADOS, vm.estado.value.fase)
    }

    @Test
    fun falhaDeResolucaoNaoImpedeResolverOProximoServico() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio("A"))
        plataforma.atual.encontrado(anuncio("B"))
        runCurrent()
        plataforma.resolucoes[0].callback(Result.failure(IllegalStateException("sem resposta")))
        runCurrent()
        assertEquals(2, plataforma.resolucoes.size)
        plataforma.concluir(1)
        runCurrent()

        assertEquals(listOf("B"), vm.estado.value.servidores.map { it.nome })
        assertTrue(vm.estado.value.avisos.any { it.contains("sem resposta") })
    }

    @Test
    fun excecaoSincronaAoResolverNaoTravaOsProximosServicos() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.erroResolucao = IllegalStateException("NSD indisponivel")
        plataforma.atual.encontrado(anuncio("A"))
        plataforma.atual.encontrado(anuncio("B"))
        runCurrent()

        assertEquals(listOf("B"), plataforma.resolucoes.map { it.anuncio.nome })
        plataforma.concluir(0)
        runCurrent()

        assertEquals(listOf("B"), vm.estado.value.servidores.map { it.nome })
        assertTrue(vm.estado.value.avisos.any { it.contains("NSD indisponivel") })
    }

    @Test
    fun callbackDuplicadoNaoLiberaResolucaoDeOutroServico() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio("A"))
        plataforma.atual.encontrado(anuncio("B"))
        runCurrent()
        plataforma.concluir(0)
        runCurrent()

        plataforma.concluir(0)
        runCurrent()
        assertEquals(2, plataforma.resolucoes.size)
        plataforma.concluir(1)
        runCurrent()

        assertEquals(listOf("A", "B"), vm.estado.value.servidores.map { it.nome })
    }

    @Test
    fun erroDeDescobertaEncerraRecursosEExibeMensagem() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.erro("Falha NSD 3")
        runCurrent()

        assertEquals(FaseDescoberta.ERRO, vm.estado.value.fase)
        assertFalse(vm.estado.value.buscando)
        assertEquals("Falha NSD 3", vm.estado.value.mensagem)
        assertEquals(1, plataforma.paradas)
    }

    @Test
    fun excecaoAoIniciarTambemEncerraRecursos() = cenario { plataforma, vm ->
        plataforma.erroInicio = SecurityException("permissao negada")
        vm.iniciar()

        assertEquals(FaseDescoberta.ERRO, vm.estado.value.fase)
        assertFalse(vm.estado.value.buscando)
        assertTrue(vm.estado.value.mensagem.orEmpty().contains("permissao negada"))
        assertEquals(1, plataforma.paradas)
    }

    @Test
    fun excecaoAoPararNaoDeixaEstadoBuscando() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.erroParada = IllegalStateException("falha ao parar")
        vm.parar()

        assertEquals(FaseDescoberta.ERRO, vm.estado.value.fase)
        assertFalse(vm.estado.value.buscando)
        assertTrue(vm.estado.value.mensagem.orEmpty().contains("falha ao parar"))
    }

    @Test
    fun perdaRetiraServidorJaResolvido() = cenario { plataforma, vm ->
        vm.iniciar()
        val servico = anuncio()
        plataforma.atual.encontrado(servico)
        runCurrent()
        plataforma.concluir(0)
        runCurrent()

        plataforma.atual.perdido(servico)
        runCurrent()

        assertTrue(vm.estado.value.servidores.isEmpty())
        assertTrue(vm.estado.value.avisos.any { it.contains("deixou") })
    }

    @Test
    fun resultadoDoServicoPerdidoDuranteResolucaoNaoReaparece() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio())
        runCurrent()
        plataforma.atual.perdido(anuncio())
        runCurrent()
        plataforma.concluir(0)
        runCurrent()

        assertTrue(vm.estado.value.servidores.isEmpty())
    }

    @Test
    fun servicoPerdidoEnquantoNaFilaNaoEhResolvido() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio("A"))
        plataforma.atual.encontrado(anuncio("B"))
        runCurrent()
        plataforma.atual.perdido(anuncio("B"))
        runCurrent()
        plataforma.concluir(0)
        runCurrent()

        assertEquals(1, plataforma.resolucoes.size)
        assertEquals(listOf("A"), vm.estado.value.servidores.map { it.nome })
    }

    @Test
    fun reaparecimentoComMesmoNomeNaoLegitimaRespostaAntiga() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio())
        runCurrent()
        plataforma.atual.perdido(anuncio())
        plataforma.atual.encontrado(anuncio())
        runCurrent()
        assertEquals(1, plataforma.resolucoes.size)

        plataforma.concluir(0)
        runCurrent()
        assertTrue(vm.estado.value.servidores.isEmpty())
        assertEquals(2, plataforma.resolucoes.size)
        plataforma.concluir(1, ip = "192.0.2.11")
        runCurrent()

        assertEquals(listOf("192.0.2.11"), vm.estado.value.servidores.single().enderecosIpv4)
    }

    @Test
    fun paradaIgnoraEventosEResolucaoTardios() = cenario { plataforma, vm ->
        vm.iniciar()
        val eventosAntigos = plataforma.atual
        eventosAntigos.encontrado(anuncio())
        runCurrent()
        vm.parar()
        eventosAntigos.encontrado(anuncio("Tardio"))
        eventosAntigos.erro("Erro tardio")
        plataforma.concluir(0)
        runCurrent()

        assertFalse(vm.estado.value.buscando)
        assertEquals(FaseDescoberta.SEM_RESULTADOS, vm.estado.value.fase)
        assertTrue(vm.estado.value.servidores.isEmpty())
        assertEquals(1, plataforma.resolucoes.size)
    }

    @Test
    fun novaBuscaEsperaResolucaoAntigaSemImportarSeusDados() = cenario { plataforma, vm ->
        vm.iniciar()
        val eventosAntigos = plataforma.atual
        eventosAntigos.encontrado(anuncio("Antigo"))
        runCurrent()
        vm.parar()
        vm.iniciar()
        plataforma.atual.encontrado(anuncio("Novo"))
        eventosAntigos.encontrado(anuncio("Tardio"))
        runCurrent()
        assertEquals(1, plataforma.resolucoes.size)

        plataforma.concluir(0)
        runCurrent()
        assertEquals(listOf("Antigo", "Novo"), plataforma.resolucoes.map { it.anuncio.nome })
        assertTrue(vm.estado.value.servidores.isEmpty())
        plataforma.concluir(1, ip = "192.0.2.11")
        runCurrent()

        assertEquals(listOf("Novo"), vm.estado.value.servidores.map { it.nome })
    }

    @Test
    fun novaBuscaRemoveResultadosDaBuscaAnterior() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encontrado(anuncio())
        runCurrent()
        plataforma.concluir(0)
        runCurrent()
        vm.parar()
        assertEquals(1, vm.estado.value.servidores.size)

        vm.iniciar()

        assertTrue(vm.estado.value.servidores.isEmpty())
        assertTrue(vm.estado.value.avisos.isEmpty())
        assertEquals(FaseDescoberta.PROCURANDO, vm.estado.value.fase)
    }

    @Test
    fun informarErroDuranteBuscaEncerraPlataforma() = cenario { plataforma, vm ->
        vm.iniciar()
        vm.informarErro("Permissão de rede local revogada")

        assertEquals(FaseDescoberta.ERRO, vm.estado.value.fase)
        assertFalse(vm.estado.value.buscando)
        assertEquals(1, plataforma.paradas)
    }

    @Test
    fun encerramentoInesperadoPeloSistemaEhInformado() = cenario { plataforma, vm ->
        vm.iniciar()
        plataforma.atual.encerrada()
        runCurrent()

        assertEquals(FaseDescoberta.ERRO, vm.estado.value.fase)
        assertFalse(vm.estado.value.buscando)
        assertEquals(1, plataforma.paradas)
    }

    private fun cenario(acao: TestScope.(PlataformaFake, DescobertaServidorViewModel) -> Unit) = runTest {
        val plataforma = PlataformaFake()
        val vm = DescobertaServidorViewModel(plataforma)
        try {
            acao(plataforma, vm)
        } finally {
            vm.parar()
            runCurrent()
        }
    }

    private data class ResolucaoPendente(
        val anuncio: AnuncioServico,
        val callback: (Result<ServicoResolvido>) -> Unit
    )

    private class PlataformaFake : PlataformaDescoberta {
        val eventos = mutableListOf<EventosDescoberta>()
        val atual get() = eventos.last()
        val resolucoes = mutableListOf<ResolucaoPendente>()
        var paradas = 0
        var erroInicio: RuntimeException? = null
        var erroParada: RuntimeException? = null
        var erroResolucao: RuntimeException? = null

        override fun iniciar(eventos: EventosDescoberta) {
            this.eventos += eventos
            erroInicio?.let { throw it }
        }

        override fun resolver(servico: AnuncioServico, resultado: (Result<ServicoResolvido>) -> Unit) {
            val erro = erroResolucao
            erroResolucao = null
            if (erro != null) throw erro
            resolucoes += ResolucaoPendente(servico, resultado)
        }

        override fun parar() {
            paradas++
            erroParada?.let { throw it }
        }

        fun concluir(indice: Int, ip: String = "192.0.2.10") {
            val pendente = resolucoes[indice]
            pendente.callback(Result.success(resolvido(pendente.anuncio, ip)))
        }
    }

    companion object {
        private fun anuncio(nome: String = "Verificador de Precos") =
            AnuncioServico(nome, TIPO_SERVICO_NSD)

        private fun resolvido(anuncio: AnuncioServico, ip: String = "192.0.2.10") = ServicoResolvido(
            anuncio = anuncio,
            enderecos = listOf(ip),
            porta = 8000,
            atributos = mapOf("app" to "verificador-precos".toByteArray(), "version" to "1.0".toByteArray())
        )
    }
}
