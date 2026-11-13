package com.tcc.verificadorprecos.connection

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificadorApiHttpTest {
    @Test
    fun consultaSchemaJsonDoEnderecoEscolhidoEFechaResposta() {
        Cenario().use {
            it.executar()
            assertEquals("http://192.0.2.10:9000/api/schema/", it.requisicao.url.toString())
            assertEquals("GET", it.requisicao.method)
            assertEquals("application/vnd.oai.openapi+json", it.requisicao.header("Accept"))
            assertTrue(it.corpo.fechado)
        }
    }

    @Test
    fun aceitaOpenApi31MantendoIdentidadeDaApi() {
        Cenario(conteudo = SCHEMA.replace("3.0.3", "3.1.0")).use {
            it.executar()
            assertTrue(it.corpo.fechado)
        }
    }

    @Test
    fun rejeitaCodigosDiferentesDe200MesmoComSchemaValido() {
        listOf(201, 204, 400, 404, 500).forEach { codigo ->
            Cenario(codigo = codigo).use {
                assertThrows(IOException::class.java) { it.executar() }
                assertTrue(it.corpo.fechado)
            }
        }
    }

    @Test
    fun naoSegueRedirectNemAlteraConfiguracaoDoClienteInjetado() {
        Cenario(codigo = 302).use {
            assertTrue(it.cliente.followRedirects)
            assertTrue(it.cliente.followSslRedirects)
            assertThrows(IOException::class.java) { it.executar() }
            assertEquals(1, it.requisicoes)
            assertTrue(it.corpo.fechado)
            assertTrue(it.cliente.followRedirects)
            assertTrue(it.cliente.followSslRedirects)
        }
    }

    @Test
    fun limitaTempoTotalDaChamadaACincoSegundos() {
        Cenario().use {
            it.executar()
            assertEquals(TimeUnit.SECONDS.toNanos(5), it.chamada.timeout().timeoutNanos())
        }
    }

    @Test
    fun rejeitaJsonInvalidoOuDocumentoQueNaoSejaObjeto() {
        listOf("", "<html>servidor</html>", "null", "[]", "{} lixo", "{\"openapi\":").forEach { conteudo ->
            Cenario(conteudo = conteudo).use {
                assertThrows(IOException::class.java) { it.executar() }
                assertTrue(it.corpo.fechado)
            }
        }
    }

    @Test
    fun rejeitaVersaoTituloERotaDeOutroServico() {
        listOf(
            SCHEMA.replace("3.0.3", "2.0"),
            SCHEMA.replace("API Verificador de Preços", "Outra API"),
            SCHEMA.replace("\"title\"", "\"outro\""),
            SCHEMA.replace("{codigo_barra}", "{id}"),
            SCHEMA.replace("\"get\":{}", "\"post\":{}"),
            SCHEMA.replace("\"get\":{}", "\"get\":null"),
            SCHEMA.replace("\"get\":{}", "\"get\":[]")
        ).forEach { conteudo ->
            Cenario(conteudo = conteudo).use {
                assertThrows(IOException::class.java) { it.executar() }
                assertTrue(it.corpo.fechado)
            }
        }
    }

    @Test
    fun rejeitaSchemaMaiorQueUmMiBComTamanhoDeclarado() {
        Cenario(tamanhoInformado = 1024L * 1024 + 1).use {
            assertThrows(IOException::class.java) { it.executar() }
            assertTrue(it.corpo.fechado)
        }
    }

    @Test
    fun rejeitaSchemaMaiorQueUmMiBSemContentLength() {
        Cenario(conteudo = SCHEMA + " ".repeat(1024 * 1024), tamanhoInformado = -1).use {
            assertThrows(IOException::class.java) { it.executar() }
            assertTrue(it.corpo.fechado)
        }
    }

    @Test
    fun aceitaSchemaNoLimiteExatoDeUmMiB() {
        val tamanho = SCHEMA.toByteArray(Charsets.UTF_8).size
        Cenario(conteudo = SCHEMA + " ".repeat(1024 * 1024 - tamanho), tamanhoInformado = -1).use {
            it.executar()
            assertTrue(it.corpo.fechado)
        }
    }

    @Test
    fun propagaFalhaDeTransporte() {
        val erro = IOException("Sem conexão")
        Cenario(falha = erro).use {
            val propagado = assertThrows(IOException::class.java) { it.executar() }

            assertEquals(erro.message, propagado.message)
            assertTrue(generateSequence<Throwable>(propagado) { it.cause }.any { it === erro })
        }
    }

    @Test
    fun cancelarCorrotinaCancelaChamadaEFechaRespostaTardia() = runBlocking {
        val entrou = CompletableDeferred<Call>()
        val liberar = CountDownLatch(1)
        val corpo = CorpoRastreado(SCHEMA)
        val cliente = OkHttpClient.Builder().addInterceptor { chain ->
            entrou.complete(chain.call())
            if (!liberar.await(5, TimeUnit.SECONDS)) throw IOException("Interceptor não liberado")
            resposta(chain.request(), 200, corpo)
        }.build()
        try {
            val trabalho = launch { VerificadorApiHttp(cliente).verificar(ENDERECO) }
            val chamada = withTimeout(5000) { entrou.await() }
            trabalho.cancelAndJoin()
            assertTrue(chamada.isCanceled())
            liberar.countDown()
            assertTrue(corpo.fechamento.await(5, TimeUnit.SECONDS))
        } finally {
            liberar.countDown()
            cliente.dispatcher.executorService.shutdown()
        }
    }

    private class Cenario(
        codigo: Int = 200,
        conteudo: String = SCHEMA,
        tamanhoInformado: Long = conteudo.toByteArray(Charsets.UTF_8).size.toLong(),
        falha: IOException? = null
    ) : Closeable {
        val corpo = CorpoRastreado(conteudo, tamanhoInformado)
        lateinit var requisicao: Request
        lateinit var chamada: Call
        var requisicoes = 0
        val cliente = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true)
            .addInterceptor { chain ->
                requisicao = chain.request()
                chamada = chain.call()
                requisicoes++
                if (falha != null) throw falha
                resposta(requisicao, codigo, corpo)
            }.build()

        fun executar() = runBlocking { VerificadorApiHttp(cliente).verificar(ENDERECO) }

        override fun close() {
            cliente.dispatcher.executorService.shutdown()
        }
    }

    private class CorpoRastreado(
        conteudo: String,
        private val tamanho: Long = conteudo.toByteArray(Charsets.UTF_8).size.toLong()
    ) : ResponseBody() {
        val fechamento = CountDownLatch(1)
        val fechado: Boolean get() = fechamento.count == 0L
        private val fonte = object : ForwardingSource(Buffer().writeUtf8(conteudo)) {
            override fun close() {
                super.close()
                fechamento.countDown()
            }
        }.buffer()

        override fun contentType() = "application/json".toMediaType()
        override fun contentLength(): Long = tamanho
        override fun source(): BufferedSource = fonte
    }

    private companion object {
        val ENDERECO = EnderecoServidor.criar("192.0.2.10", 9000)
        const val SCHEMA = "{\"openapi\":\"3.0.3\",\"info\":{\"title\":\"API Verificador de Preços\"},\"paths\":{\"/api/produtos/{codigo_barra}/\":{\"get\":{}}}}"

        fun resposta(requisicao: Request, codigo: Int, corpo: ResponseBody): Response = Response.Builder()
            .request(requisicao)
            .protocol(Protocol.HTTP_1_1)
            .code(codigo)
            .message("Resposta de teste")
            .header("Location", "http://198.51.100.5:8000/api/schema/")
            .body(corpo)
            .build()
    }
}
