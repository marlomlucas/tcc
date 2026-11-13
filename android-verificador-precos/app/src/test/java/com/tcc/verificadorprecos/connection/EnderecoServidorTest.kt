package com.tcc.verificadorprecos.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnderecoServidorTest {
    @Test
    fun ipv4SimplesUsaPortaPadrao() {
        val endereco = EnderecoServidor.interpretar(" 192.0.2.10 ")
        assertEquals("192.0.2.10", endereco.ip)
        assertEquals(8000, endereco.porta)
        assertEquals("http://192.0.2.10:8000/", endereco.urlBase)
    }

    @Test
    fun portaVaziaUsaPadraoParaIpv4Simples() {
        assertEquals(8000, EnderecoServidor.interpretar("192.0.2.10", "  ").porta)
    }

    @Test
    fun ipv4SimplesUsaPortaInformada() {
        assertEquals(9001, EnderecoServidor.interpretar("192.0.2.10", " 9001 ").porta)
    }

    @Test
    fun urlSemPortaUsaPortaHttpIgnorandoCampoSeparado() {
        listOf("http://192.0.2.10", "http://192.0.2.10/").forEach {
            assertEquals(80, EnderecoServidor.interpretar(it, "").porta)
            assertEquals(80, EnderecoServidor.interpretar(it, "9000").porta)
        }
    }

    @Test
    fun urlComPortaPrevaleceSobreCampoSeparado() {
        assertEquals("http://192.0.2.10:9100/", EnderecoServidor.interpretar("HTTP://192.0.2.10:9100/", "invalida").urlBase)
    }

    @Test
    fun aceitaLimitesDaPortaTcp() {
        listOf(1, 65535).forEach {
            assertEquals(it, EnderecoServidor.criar("192.0.2.10", it).porta)
        }
    }

    @Test
    fun rejeitaPortasForaDoIntervaloETextoNaoDecimal() {
        listOf("0", "65536", "-1", "+80", "8e3", "80.0", "abc", "999999999999999").forEach {
            assertThrows(IllegalArgumentException::class.java) { EnderecoServidor.interpretar("192.0.2.10", it) }
        }
        listOf(-1, 0, 65536).forEach {
            assertThrows(IllegalArgumentException::class.java) { EnderecoServidor.criar("192.0.2.10", it) }
        }
    }

    @Test
    fun rejeitaDnsIpv6EIpv4AmbiguoOuNaoUtilizavel() {
        listOf("servidor.local", "localhost", "::1", "127.0.0.1", "0.0.0.0", "169.254.1.1", "224.0.0.1", "255.255.255.255", "192.00.2.10", "192.0.2.256", "192.0.2", "3221225994", "").forEach {
            assertThrows(IllegalArgumentException::class.java) { EnderecoServidor.interpretar(it) }
        }
    }

    @Test
    fun rejeitaUrlsComEsquemasOuHostIncompativeis() {
        listOf("https://192.0.2.10/", "ftp://192.0.2.10/", "http://servidor.local/", "http://localhost/", "http://[2001:db8::1]/", "//192.0.2.10/", "192.0.2.10:8000").forEach {
            assertThrows(IllegalArgumentException::class.java) { EnderecoServidor.interpretar(it) }
        }
    }

    @Test
    fun rejeitaCredenciaisCaminhosConsultasEFragmentos() {
        listOf("http://usuario@192.0.2.10/", "http://192.0.2.10/api/", "http://192.0.2.10/../", "http://192.0.2.10/%2f", "http://192.0.2.10/?v=1", "http://192.0.2.10/#", "http://192.0.2.10/\\api").forEach {
            assertThrows(IllegalArgumentException::class.java) { EnderecoServidor.interpretar(it) }
        }
    }

    @Test
    fun rejeitaAutoridadeComPortaVaziaOuInvalida() {
        listOf("http://192.0.2.10:/", "http://192.0.2.10:0/", "http://192.0.2.10:65536/", "http://192.0.2.10:+80/", "http://192.0.2.10:abc/").forEach {
            assertThrows(IllegalArgumentException::class.java) { EnderecoServidor.interpretar(it) }
        }
    }

    @Test
    fun enderecoSalvoPodeSerRestauradoSemPerderPorta() {
        val endereco = EnderecoServidor.criar("198.51.100.10", 9030)
        assertEquals(endereco, EnderecoServidor.interpretar(endereco.urlBase))
    }
}
