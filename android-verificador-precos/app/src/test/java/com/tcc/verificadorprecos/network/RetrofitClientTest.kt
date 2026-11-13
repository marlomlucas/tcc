package com.tcc.verificadorprecos.network

import com.tcc.verificadorprecos.connection.EnderecoServidor
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test

class RetrofitClientTest {
    @Test
    fun criaApisParaOsEnderecosEscolhidosSemServidorPadrao() {
        val primeiro = EnderecoServidor.criar("192.0.2.10", 8000)
        val segundo = EnderecoServidor.criar("198.51.100.5", 9000)
        assertEquals(primeiro.urlBase, RetrofitClient.criarRetrofit(primeiro).baseUrl().toString())
        assertEquals(segundo.urlBase, RetrofitClient.criarRetrofit(segundo).baseUrl().toString())
        assertNotNull(RetrofitClient.criarProdutoApi(primeiro))
        assertNotNull(RetrofitClient.criarProdutoApi(segundo))
    }

    @Test
    fun compartilhaClienteHttpEntreServidores() {
        val primeiro = RetrofitClient.criarRetrofit(EnderecoServidor.criar("192.0.2.10", 8000))
        val segundo = RetrofitClient.criarRetrofit(EnderecoServidor.criar("198.51.100.5", 9000))
        assertSame(primeiro.callFactory(), segundo.callFactory())
    }

    @Test
    fun clienteNaoSegueRedirectsEPossuiLimitesDeTempo() {
        val cliente = RetrofitClient.criarRetrofit(EnderecoServidor.criar("192.0.2.10", 8000)).callFactory() as OkHttpClient
        assertFalse(cliente.followRedirects)
        assertFalse(cliente.followSslRedirects)
        assertEquals(5000, cliente.callTimeoutMillis)
        assertEquals(5000, cliente.connectTimeoutMillis)
        assertEquals(5000, cliente.readTimeoutMillis)
        assertEquals(5000, cliente.writeTimeoutMillis)
    }
}
