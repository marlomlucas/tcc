package com.tcc.verificadorprecos.network

import com.tcc.verificadorprecos.data.ProdutoDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface ProdutoApi {

    @GET("api/produtos/{codigo_barra}/")
    suspend fun consultarProduto(
        @Path("codigo_barra") codigoBarra: String
    ): Response<ProdutoDto>
}