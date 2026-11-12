package com.tcc.verificadorprecos.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContratoDescobertaTest {
    @Test
    fun tipoAceitaSomenteAsVariantesDoServicoDnsSd() {
        listOf(
            "_vprecos._tcp", "_vprecos._tcp.", "._vprecos._tcp",
            "_vprecos._tcp.local.", "_VPRECOS._TCP.LOCAL."
        ).forEach { assertTrue(it, tipoServicoCompativel(it)) }
    }

    @Test
    fun tipoRejeitaOutrosServicosEComparacoesPorPrefixo() {
        listOf(
            "", "_http._tcp.", "_vprecos._udp.local.",
            "prefixo_vprecos._tcp", "_vprecos._tcp.local.exemplo",
            "_vprecos._tcp_extra"
        ).forEach { assertFalse(it, tipoServicoCompativel(it)) }
    }

    @Test
    fun ipv4AceitaEnderecosUnicastTextuais() {
        listOf("192.0.2.10", "198.51.100.200", "203.0.113.2")
            .forEach { assertTrue(it, ipv4Valido(it)) }
    }

    @Test
    fun ipv4RejeitaNomesIpv6EEnderecosNaoUtilizaveis() {
        listOf(
            "", "servidor.local", "2001:db8::1", "::1", "127.0.0.1",
            "0.0.0.0", "169.254.10.20", "224.0.0.251", "255.255.255.255",
            "192.0.2.256", "192.0.2", "192.0.2.10.1", "192.00.2.10",
            "192.0.2.-1", " 192.0.2.10", "192.0.2.10 "
        ).forEach { assertFalse(it, ipv4Valido(it)) }
    }

    @Test
    fun servicoCompativelPreservaNomePortaEVersao() {
        val resolvido = servico()

        val servidor = validarServico(resolvido)

        assertEquals(resolvido.anuncio.nome, servidor.nome)
        assertEquals(listOf("192.0.2.10"), servidor.enderecosIpv4)
        assertEquals(8000, servidor.porta)
        assertEquals("1.0", servidor.versao)
    }

    @Test
    fun appAusenteOuIncorretoEhRejeitado() {
        listOf(null, "outra-aplicacao", "Verificador-Precos", "verificador-precos ")
            .forEach { app ->
                val atributos = servico().atributos.toMutableMap()
                if (app == null) atributos.remove("app") else atributos["app"] = app.toByteArray()

                assertThrows(IllegalArgumentException::class.java) {
                    validarServico(servico().copy(atributos = atributos))
                }
            }
    }

    @Test
    fun versaoAusenteOuIncorretaEhRejeitada() {
        listOf(null, "", "1", "2.0", "1.0 ").forEach { versao ->
            val atributos = servico().atributos.toMutableMap()
            if (versao == null) atributos.remove("version")
            else atributos["version"] = versao.toByteArray()

            assertThrows(IllegalArgumentException::class.java) {
                validarServico(servico().copy(atributos = atributos))
            }
        }
    }

    @Test
    fun tipoIncompativelEhRejeitadoMesmoComTxtCorreto() {
        assertThrows(IllegalArgumentException::class.java) {
            validarServico(servico().copy(anuncio = AnuncioServico("Outro", "_http._tcp.")))
        }
    }

    @Test
    fun portaPrecisaEstarNoIntervaloTcp() {
        listOf(-1, 0, 65536).forEach { porta ->
            assertThrows(IllegalArgumentException::class.java) {
                validarServico(servico().copy(porta = porta))
            }
        }
        listOf(1, 8000, 65535).forEach { porta ->
            assertEquals(porta, validarServico(servico().copy(porta = porta)).porta)
        }
    }

    @Test
    fun preservaTodosOsIpv4ValidosSemEscolherUmSilenciosamente() {
        val servidor = validarServico(
            servico().copy(
                enderecos = listOf("2001:db8::1", "192.0.2.11", "192.0.2.10", "192.0.2.11")
            )
        )

        assertEquals(listOf("192.0.2.10", "192.0.2.11"), servidor.enderecosIpv4)
    }

    @Test
    fun resolucaoSemIpv4ValidoEhRejeitada() {
        listOf(emptyList(), listOf("2001:db8::1"), listOf("127.0.0.1", "invalido"))
            .forEach { enderecos ->
                assertThrows(IllegalArgumentException::class.java) {
                    validarServico(servico().copy(enderecos = enderecos))
                }
            }
    }

    private fun servico() = ServicoResolvido(
        anuncio = AnuncioServico("Verificador de Precos", TIPO_SERVICO_COMPLETO),
        enderecos = listOf("192.0.2.10"),
        porta = 8000,
        atributos = mapOf("app" to "verificador-precos".toByteArray(), "version" to "1.0".toByteArray())
    )
}
