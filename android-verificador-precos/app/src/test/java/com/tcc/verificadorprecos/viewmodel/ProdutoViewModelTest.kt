package com.tcc.verificadorprecos.viewmodel

import com.tcc.verificadorprecos.data.ProdutoDto
import com.tcc.verificadorprecos.repository.ProdutoRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

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

    @Test
    fun consultaPendente_mantemCarregandoAteRetornarProduto() = runTest {
        val resposta = CompletableDeferred<Response<ProdutoDto>>()
        val repositoryFake = object : ProdutoRepository {
            override suspend fun consultarProduto(codigoBarra: String) = resposta.await()
        }
        val viewModel = ProdutoViewModel(repositoryFake)
        val produto = criarProduto("00123")

        try {
            viewModel.consultarProduto(produto.codigoBarra)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(ProdutoUiState.Carregando, viewModel.uiState.value)
            resposta.complete(Response.success(produto))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(ProdutoUiState.Sucesso(produto), viewModel.uiState.value)
        } finally {
            viewModel.limpar()
            resposta.complete(Response.success(produto))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        }
    }

    @Test
    fun limparDuranteConsultaPendente_cancelaSemApresentarErro() = runTest {
        val resposta = CompletableDeferred<Response<ProdutoDto>>()
        var cancelada = false
        val repositoryFake = object : ProdutoRepository {
            override suspend fun consultarProduto(codigoBarra: String): Response<ProdutoDto> {
                return try {
                    resposta.await()
                } catch (erro: CancellationException) {
                    cancelada = true
                    throw erro
                }
            }
        }
        val viewModel = ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("00123")
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.limpar()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        val estadoAposLimpar = viewModel.uiState.value
        resposta.complete(Response.success(criarProduto("00123")))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(cancelada)
        assertEquals(ProdutoUiState.Inicial, estadoAposLimpar)
        assertEquals(ProdutoUiState.Inicial, viewModel.uiState.value)
    }

    @Test
    fun limparDuranteConsultaNaoCooperativa_descartaRespostaTardia() = runTest {
        val repositoryFake = RepositoryPendente()
        val viewModel = ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("00123")
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.limpar()
        val estadoAposLimpar = viewModel.uiState.value
        repositoryFake.responder(criarProduto("00123"))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProdutoUiState.Inicial, estadoAposLimpar)
        assertEquals(ProdutoUiState.Inicial, viewModel.uiState.value)
    }

    @Test
    fun duasConsultasComRespostasForaDeOrdem_preservamProdutoMaisRecente() = runTest {
        val repositoryFake = RepositoryPendente()
        val viewModel = ProdutoViewModel(repositoryFake)
        val produtoAntigo = criarProduto("00123")
        val produtoAtual = criarProduto("00456")

        viewModel.consultarProduto(produtoAntigo.codigoBarra)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.consultarProduto(produtoAtual.codigoBarra)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        repositoryFake.responder(produtoAtual)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        val estadoMaisRecente = viewModel.uiState.value
        repositoryFake.responder(produtoAntigo)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("00123", "00456"), repositoryFake.codigos)
        assertEquals(ProdutoUiState.Sucesso(produtoAtual), estadoMaisRecente)
        assertEquals(ProdutoUiState.Sucesso(produtoAtual), viewModel.uiState.value)
    }

    @Test
    fun falhaDeConexaoAntiga_naoSobrescreveProdutoMaisRecente() = runTest {
        verificarFalhaAntigaDescartada(IOException("falha antiga de conexão"))
    }

    @Test
    fun falhaGenericaAntiga_naoSobrescreveProdutoMaisRecente() = runTest {
        verificarFalhaAntigaDescartada(IllegalStateException("falha antiga de conversão"))
    }

    @Test
    fun codigoVazioDuranteConsulta_impedeRetornoDaConsultaAnterior() = runTest {
        val repositoryFake = RepositoryPendente()
        val viewModel = ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("00123")
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.consultarProduto(" \t ")
        val erroAtual = viewModel.uiState.value
        repositoryFake.responder(criarProduto("00123"))
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val esperado = ProdutoUiState.Erro("Informe um código de barras.")
        assertEquals(listOf("00123"), repositoryFake.codigos)
        assertEquals(esperado, erroAtual)
        assertEquals(esperado, viewModel.uiState.value)
    }

    @Test
    fun cancellationException_naoEhConvertidaEmMensagemDeErro() = runTest {
        var consultou = false
        var consultaJob: Job? = null
        val repositoryFake = object : ProdutoRepository {
            override suspend fun consultarProduto(codigoBarra: String): Response<ProdutoDto> {
                consultou = true
                consultaJob = currentCoroutineContext()[Job]
                throw CancellationException("cancelamento simulado")
            }
        }
        val viewModel = ProdutoViewModel(repositoryFake)

        viewModel.consultarProduto("00123")
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        val estadoAposCancelamento = viewModel.uiState.value
        viewModel.limpar()

        assertTrue(consultou)
        assertTrue(checkNotNull(consultaJob).isCancelled)
        assertFalse(estadoAposCancelamento is ProdutoUiState.Erro)
        assertEquals(ProdutoUiState.Inicial, viewModel.uiState.value)
    }

    private fun verificarFalhaAntigaDescartada(erro: Exception) {
        val repositoryFake = RepositoryPendente()
        val viewModel = ProdutoViewModel(repositoryFake)
        val produtoAtual = criarProduto("00456")

        viewModel.consultarProduto("00123")
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        viewModel.consultarProduto(produtoAtual.codigoBarra)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        repositoryFake.responder(produtoAtual)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        val estadoMaisRecente = viewModel.uiState.value
        repositoryFake.falhar("00123", erro)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(ProdutoUiState.Sucesso(produtoAtual), estadoMaisRecente)
        assertEquals(ProdutoUiState.Sucesso(produtoAtual), viewModel.uiState.value)
    }

    private fun criarProduto(codigoBarra: String) = ProdutoDto(
        codigoBarra = codigoBarra,
        nome = "Produto $codigoBarra",
        descricao = "Produto utilizado no teste",
        preco = "24.90",
        dataAtualizacao = "2026-11-08T10:00:00Z"
    )

    // Simula um callback que entrega resposta mesmo depois do cancelamento.
    private class RepositoryPendente : ProdutoRepository {
        val codigos = mutableListOf<String>()
        private val pendentes = mutableMapOf<String, Continuation<Response<ProdutoDto>>>()

        override suspend fun consultarProduto(codigoBarra: String): Response<ProdutoDto> {
            return suspendCoroutine { continuacao ->
                codigos.add(codigoBarra)
                pendentes[codigoBarra] = continuacao
            }
        }

        fun responder(produto: ProdutoDto) {
            checkNotNull(pendentes.remove(produto.codigoBarra))
                .resume(Response.success(produto))
        }

        fun falhar(codigoBarra: String, erro: Exception) {
            checkNotNull(pendentes.remove(codigoBarra)).resumeWithException(erro)
        }
    }
}