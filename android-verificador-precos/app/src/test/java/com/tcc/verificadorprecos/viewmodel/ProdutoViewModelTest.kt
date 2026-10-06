package com.tcc.verificadorprecos.viewmodel

import com.tcc.verificadorprecos.data.ProdutoDto
import com.tcc.verificadorprecos.repository.ProdutoRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProdutoViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun consultaProdutoExistente_retornaSucesso() = runTest {

        val produto = ProdutoDto(
            codigoBarra = "12345670",
            nome = "Produto Teste",
            descricao = "Produto utilizado no teste",
            preco = "24.90",
            dataAtualizacao = "2026-10-06T10:00:00Z"
        )

        val repositoryFake = object : ProdutoRepository {

            override suspend fun consultarProduto(
                codigoBarra: String
            ): Response<ProdutoDto> {

                return Response.success(produto)
            }
        }

        val viewModel =
            ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("12345670")

        mainDispatcherRule
            .testDispatcher
            .scheduler
            .advanceUntilIdle()

        val estado = viewModel.uiState.value

        assertTrue(
            estado is ProdutoUiState.Sucesso
        )

        val resultado =
            (estado as ProdutoUiState.Sucesso).produto

        assertEquals(
            "12345670",
            resultado.codigoBarra
        )

        assertEquals(
            "Produto Teste",
            resultado.nome
        )

        assertEquals(
            "24.90",
            resultado.preco
        )
    }

    @Test
    fun codigoVazio_retornaErro() {

        val repositoryFake = object : ProdutoRepository {

            override suspend fun consultarProduto(
                codigoBarra: String
            ): Response<ProdutoDto> {

                throw IllegalStateException(
                    "O repository não deveria ser chamado."
                )
            }
        }

        val viewModel =
            ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("")

        val estado = viewModel.uiState.value

        assertTrue(
            estado is ProdutoUiState.Erro
        )

        assertEquals(
            "Informe um código de barras.",
            (estado as ProdutoUiState.Erro).mensagem
        )
    }

    @Test
    fun codigoInvalido_retornaErro400() = runTest {

        val repositoryFake = object : ProdutoRepository {

            override suspend fun consultarProduto(
                codigoBarra: String
            ): Response<ProdutoDto> {

                return Response.error(
                    400,
                    "{}".toResponseBody(
                        "application/json".toMediaType()
                    )
                )
            }
        }

        val viewModel =
            ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("abc")

        mainDispatcherRule
            .testDispatcher
            .scheduler
            .advanceUntilIdle()

        val estado = viewModel.uiState.value

        assertTrue(
            estado is ProdutoUiState.Erro
        )

        assertEquals(
            "Código de barras inválido.",
            (estado as ProdutoUiState.Erro).mensagem
        )
    }

    @Test
    fun produtoNaoEncontrado_retornaErro404() = runTest {

        val repositoryFake = object : ProdutoRepository {

            override suspend fun consultarProduto(
                codigoBarra: String
            ): Response<ProdutoDto> {

                return Response.error(
                    404,
                    "{}".toResponseBody(
                        "application/json".toMediaType()
                    )
                )
            }
        }

        val viewModel =
            ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("11111111")

        mainDispatcherRule
            .testDispatcher
            .scheduler
            .advanceUntilIdle()

        val estado = viewModel.uiState.value

        assertTrue(
            estado is ProdutoUiState.Erro
        )

        assertEquals(
            "Produto não encontrado.",
            (estado as ProdutoUiState.Erro).mensagem
        )
    }

    @Test
    fun falhaDeConexao_retornaErro() = runTest {

        val repositoryFake = object : ProdutoRepository {

            override suspend fun consultarProduto(
                codigoBarra: String
            ): Response<ProdutoDto> {

                throw IOException(
                    "Servidor indisponível"
                )
            }
        }

        val viewModel =
            ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("12345670")

        mainDispatcherRule
            .testDispatcher
            .scheduler
            .advanceUntilIdle()

        val estado = viewModel.uiState.value

        assertTrue(
            estado is ProdutoUiState.Erro
        )

        assertEquals(
            "Não foi possível conectar ao servidor.",
            (estado as ProdutoUiState.Erro).mensagem
        )
    }

    @Test
    fun limpar_retornaEstadoInicial() = runTest {

        val produto = ProdutoDto(
            codigoBarra = "12345670",
            nome = "Produto Teste",
            descricao = "Produto utilizado no teste",
            preco = "24.90",
            dataAtualizacao = "2026-10-06T10:00:00Z"
        )

        val repositoryFake = object : ProdutoRepository {

            override suspend fun consultarProduto(
                codigoBarra: String
            ): Response<ProdutoDto> {

                return Response.success(produto)
            }
        }

        val viewModel =
            ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("12345670")

        mainDispatcherRule
            .testDispatcher
            .scheduler
            .advanceUntilIdle()

        assertTrue(
            viewModel.uiState.value is ProdutoUiState.Sucesso
        )

        viewModel.limpar()

        assertTrue(
            viewModel.uiState.value is ProdutoUiState.Inicial
        )
    }
}