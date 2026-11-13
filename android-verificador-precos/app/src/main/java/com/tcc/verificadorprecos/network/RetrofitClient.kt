package com.tcc.verificadorprecos.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.tcc.verificadorprecos.connection.EnderecoServidor
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitClient {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val cliente = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(5, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    internal fun criarRetrofit(endereco: EnderecoServidor): Retrofit = Retrofit.Builder()
        .baseUrl(endereco.urlBase)
        .client(cliente)
        .addConverterFactory(
            MoshiConverterFactory.create(moshi)
        )
        .build()

    fun criarProdutoApi(endereco: EnderecoServidor): ProdutoApi =
        criarRetrofit(endereco).create(ProdutoApi::class.java)
}
