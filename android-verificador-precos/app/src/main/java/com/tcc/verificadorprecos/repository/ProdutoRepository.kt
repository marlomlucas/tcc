package com.tcc.verificadorprecos.repository

import com.tcc.verificadorprecos.data.ProdutoDto
import com.tcc.verificadorprecos.network.ProdutoApi
import com.tcc.verificadorprecos.network.RetrofitClient
import retrofit2.Response

interface ProdutoRepository {

    suspend fun consultarProduto(
        codigoBarra: String
    ): Response<ProdutoDto>
}

class ProdutoRepositoryImpl(
    private val produtoApi: ProdutoApi = RetrofitClient.produtoApi
) : ProdutoRepository {

    override suspend fun consultarProduto(
        codigoBarra: String
    ): Response<ProdutoDto> {

        return produtoApi.consultarProduto(codigoBarra)
    }
}