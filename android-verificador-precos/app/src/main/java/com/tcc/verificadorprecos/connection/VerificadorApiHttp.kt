package com.tcc.verificadorprecos.connection

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class VerificadorApiHttp(client: OkHttpClient = OkHttpClient()) : VerificadorApi {
    private val cliente = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val adaptador = Moshi.Builder().build().adapter<Map<String, Any?>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
    )

    override suspend fun verificar(endereco: EnderecoServidor): Unit = suspendCancellableCoroutine { continuacao ->
        val requisicao = Request.Builder()
            .url("${endereco.urlBase}api/schema/")
            .header("Accept", "application/vnd.oai.openapi+json")
            .get()
            .build()
        val chamada = cliente.newCall(requisicao)
        continuacao.invokeOnCancellation { chamada.cancel() }
        chamada.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuacao.isActive) continuacao.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val resultado = runCatching {
                    response.use { resposta ->
                        if (resposta.code != 200) {
                            throw IOException("O servidor respondeu HTTP ${resposta.code} ao verificar a API.")
                        }
                        val corpo = resposta.body ?: throw IOException("O servidor retornou uma resposta vazia.")
                        if (corpo.contentLength() > LIMITE_SCHEMA) throw IOException("O schema excedeu 1 MiB.")
                        val fonte = corpo.source()
                        fonte.request(LIMITE_SCHEMA + 1)
                        if (fonte.buffer.size > LIMITE_SCHEMA) throw IOException("O schema excedeu 1 MiB.")
                        val documento = try {
                            adaptador.fromJson(fonte.readUtf8())
                        } catch (erro: Exception) {
                            throw IOException("O servidor não retornou um OpenAPI JSON válido.", erro)
                        }
                        val info = documento?.get("info") as? Map<*, *>
                        val caminhos = documento?.get("paths") as? Map<*, *>
                        val produto = caminhos?.get("/api/produtos/{codigo_barra}/") as? Map<*, *>
                        if ((documento?.get("openapi") as? String)?.startsWith("3.") != true ||
                            info?.get("title") != "API Verificador de Preços" ||
                            produto?.get("get") !is Map<*, *>
                        ) {
                            throw IOException("O servidor não possui a API esperada do Verificador de Preços.")
                        }
                    }
                }
                if (continuacao.isActive) {
                    resultado.fold(
                        onSuccess = { continuacao.resume(Unit) },
                        onFailure = { continuacao.resumeWithException(it) }
                    )
                }
            }
        })
    }

    private companion object {
        const val LIMITE_SCHEMA = 1024L * 1024L
    }
}
