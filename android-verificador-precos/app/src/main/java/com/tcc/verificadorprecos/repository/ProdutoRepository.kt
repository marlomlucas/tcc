package com.tcc.verificadorprecos.repository

import com.tcc.verificadorprecos.data.ProdutoDto
import com.tcc.verificadorprecos.network.ProdutoApi
import com.tcc.verificadorprecos.network.RetrofitClient
import com.tcc.verificadorprecos.connection.EnderecoServidor
import com.tcc.verificadorprecos.connection.GerenciadorConexao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import retrofit2.Response

interface ProdutoRepository {

    suspend fun consultarProduto(
        codigoBarra: String
    ): Response<ProdutoDto>
}

class ProdutoRepositoryImpl(
    private val conexao: GerenciadorConexao,
    private val criarApi: (EnderecoServidor) -> ProdutoApi = RetrofitClient::criarProdutoApi
) : ProdutoRepository {

    override suspend fun consultarProduto(
        codigoBarra: String
    ): Response<ProdutoDto> {

        val sessao = conexao.sessao.value
            ?: throw IOException("Nenhum servidor está conectado.")
        try {
            val resposta = criarApi(sessao.endereco).consultarProduto(codigoBarra)
            currentCoroutineContext().ensureActive()
            if (conexao.sessao.value != sessao) throw CancellationException("Servidor alterado.")
            if (resposta.code() >= 500 || resposta.code() in setOf(301, 302, 303, 307, 308)) {
                conexao.registrarFalha(sessao, "Falha de comunicação com o servidor. HTTP ${resposta.code()}.")
                throw IOException("O servidor não está disponível para consultas.")
            }
            return resposta
        } catch (erro: CancellationException) {
            throw erro
        } catch (erro: IOException) {
            currentCoroutineContext().ensureActive()
            conexao.registrarFalha(sessao, "Não foi possível comunicar com ${sessao.endereco.urlBase}")
            throw erro
        }
    }
}
