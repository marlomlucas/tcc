package com.tcc.verificadorprecos.camera

import androidx.camera.core.ImageProxy
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BarcodeAnalyzerTest {

    @Test
    fun leituraNormal_entregaCodigoEFechaProxySomenteNaConclusao() {
        val sessao = SessaoFake()
        val quadro = QuadroFake()

        sessao.analyzer.analyze(quadro.proxy)
        sessao.sucesso("00123")

        assertEquals(listOf("00123"), sessao.codigos)
        assertEquals(0, quadro.fechamentos)
        sessao.concluir()
        assertEquals(1, quadro.fechamentos)
        assertEquals(0, sessao.fechamentosScanner)
        sessao.analyzer.fechar()
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun codigoAusenteOuVazio_naoEntregaLeitura() {
        val sessao = SessaoFake()
        val quadro = QuadroFake()

        sessao.analyzer.analyze(quadro.proxy)
        listOf(null, "", " ").forEach { sessao.sucesso(it) }
        sessao.concluir()
        sessao.analyzer.fechar()

        assertTrue(sessao.codigos.isEmpty())
        assertEquals(1, quadro.fechamentos)
    }

    @Test
    fun falhaAssincrona_registraErroEFechaProxyNaConclusao() {
        val sessao = SessaoFake()
        val quadro = QuadroFake()
        val erro = IllegalStateException("falha de processamento")

        sessao.analyzer.analyze(quadro.proxy)
        sessao.falhar(erro)

        assertSame(erro, sessao.erros.single())
        assertEquals(0, quadro.fechamentos)
        sessao.concluir()
        sessao.analyzer.fechar()
        assertEquals(1, quadro.fechamentos)
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun falhaSincrona_registraErroEFechaProxy() {
        val erro = IllegalArgumentException("falha antes dos callbacks")
        val sessao = SessaoFake(erro)
        val quadro = QuadroFake()

        sessao.analyzer.analyze(quadro.proxy)

        assertSame(erro, sessao.erros.single())
        assertEquals(1, quadro.fechamentos)
        assertTrue(sessao.codigos.isEmpty())
        sessao.analyzer.fechar()
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun analisarDepoisDeFechar_descartaProxySemUsarScanner() {
        val sessao = SessaoFake()
        val quadro = QuadroFake()

        sessao.analyzer.fechar()
        sessao.analyzer.analyze(quadro.proxy)

        assertEquals(0, sessao.processamentos)
        assertEquals(1, quadro.fechamentos)
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun sucessoDepoisDeFechar_naoEntregaCallbackAntigo() {
        val sessao = SessaoFake()
        val quadro = QuadroFake()

        sessao.analyzer.analyze(quadro.proxy)
        sessao.analyzer.fechar()
        sessao.sucesso("12345670")

        assertTrue(sessao.codigos.isEmpty())
        assertEquals(0, quadro.fechamentos)
        sessao.concluir()
        assertEquals(1, quadro.fechamentos)
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun fecharSemAnalisePendente_liberaScannerUmaVez() {
        val sessao = SessaoFake()

        sessao.analyzer.fechar()
        sessao.analyzer.fechar()

        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun fecharComAnalisePendente_aguardaConclusaoELiberaProxyAntesDoScanner() {
        val sessao = SessaoFake()
        val quadro = QuadroFake { sessao.eventos.add("proxy") }

        sessao.analyzer.analyze(quadro.proxy)
        sessao.analyzer.fechar()
        sessao.analyzer.fechar()

        assertTrue(sessao.eventos.isEmpty())
        sessao.concluir()
        assertEquals(listOf("proxy", "scanner"), sessao.eventos)
        assertEquals(1, quadro.fechamentos)
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun conclusaoRepetida_naoFechaProxyOuScannerDuasVezes() {
        val sessao = SessaoFake()
        val quadro = QuadroFake()

        sessao.analyzer.analyze(quadro.proxy)
        sessao.concluir()
        sessao.concluir()
        sessao.analyzer.fechar()
        sessao.analyzer.fechar()

        assertEquals(1, quadro.fechamentos)
        assertEquals(1, sessao.fechamentosScanner)
    }

    @Test
    fun novaSessao_funcionaEnquantoConclusaoDaAntigaNaoEntregaLeitura() {
        val antiga = SessaoFake()
        val nova = SessaoFake()
        val quadroAntigo = QuadroFake()
        val quadroNovo = QuadroFake()

        antiga.analyzer.analyze(quadroAntigo.proxy)
        antiga.analyzer.fechar()
        nova.analyzer.analyze(quadroNovo.proxy)
        antiga.sucesso("11111111")
        nova.sucesso("12345670")
        antiga.concluir()
        nova.concluir()
        nova.analyzer.fechar()

        assertTrue(antiga.codigos.isEmpty())
        assertEquals(listOf("12345670"), nova.codigos)
        assertEquals(1, quadroAntigo.fechamentos)
        assertEquals(1, quadroNovo.fechamentos)
        assertEquals(1, antiga.fechamentosScanner)
        assertEquals(1, nova.fechamentosScanner)
    }

    private class SessaoFake(falhaSincrona: Exception? = null) {
        val codigos = mutableListOf<String>()
        val erros = mutableListOf<Exception>()
        val eventos = mutableListOf<String>()
        var processamentos = 0
        var fechamentosScanner = 0
        lateinit var sucesso: (String?) -> Unit
        lateinit var falhar: (Exception) -> Unit
        lateinit var concluir: () -> Unit

        val analyzer = BarcodeAnalyzer(
            processar = { _, sucesso, falha, conclusao ->
                processamentos++
                if (falhaSincrona != null) throw falhaSincrona
                this.sucesso = sucesso
                falhar = falha
                concluir = conclusao
            },
            liberarScanner = {
                fechamentosScanner++
                eventos.add("scanner")
            },
            registrarErro = { erros.add(it) },
            onCodigoDetectado = { codigos.add(it) }
        )
    }

    private class QuadroFake(aoFechar: () -> Unit = {}) {
        var fechamentos = 0
        val proxy = Proxy.newProxyInstance(
            ImageProxy::class.java.classLoader,
            arrayOf(ImageProxy::class.java)
        ) { objeto, metodo, argumentos ->
            when (metodo.name) {
                "close" -> {
                    fechamentos++
                    aoFechar()
                    null
                }
                "equals" -> objeto === argumentos?.firstOrNull()
                "hashCode" -> System.identityHashCode(objeto)
                "toString" -> "ImageProxy de teste"
                else -> throw AssertionError("Acesso inesperado: ${metodo.name}")
            }
        } as ImageProxy
    }
}
