package com.tcc.verificadorprecos.data

import com.squareup.moshi.Json

data class ProdutoDto(

    @Json(name = "codigo_barra")
    val codigoBarra: String,

    val nome: String,

    val descricao: String,

    val preco: String,

    @Json(name = "data_atualizacao")
    val dataAtualizacao: String
)