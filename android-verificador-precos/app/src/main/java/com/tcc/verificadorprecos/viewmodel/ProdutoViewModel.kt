package com.tcc.verificadorprecos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.verificadorprecos.data.ProdutoDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import com.tcc.verificadorprecos.repository.ProdutoRepository
import com.tcc.verificadorprecos.connection.SessaoServidor

sealed interface ProdutoUiState {

    object Inicial : ProdutoUiState

    object Carregando : ProdutoUiState

    data class Sucesso(
        val produto: ProdutoDto
    ) : ProdutoUiState

    data class Erro(
        val mensagem: String
    ) : ProdutoUiState
}

class ProdutoViewModel(
    private val repository: ProdutoRepository,
    private val servidor: StateFlow<SessaoServidor?>? = null
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ProdutoUiState>(ProdutoUiState.Inicial)

    val uiState: StateFlow<ProdutoUiState> =
        _uiState.asStateFlow()

    private var consultaAtiva: Job? = null

    init {
        servidor?.let { origem ->
            var anterior = origem.value
            viewModelScope.launch {
                origem.collect { atual ->
                    if (atual != anterior) {
                        anterior = atual
                        limpar()
                    }
                }
            }
        }
    }

    fun consultarProduto(codigoBarra: String) {

        consultaAtiva?.cancel()
        consultaAtiva = null

        val codigo = codigoBarra.trim()

        if (codigo.isEmpty()) {
            _uiState.value =
                ProdutoUiState.Erro("Informe um código de barras.")
            return
        }

        val sessaoInicial = servidor?.value
        if (servidor != null && sessaoInicial == null) {
            _uiState.value = ProdutoUiState.Erro("Conecte ao servidor antes de consultar produtos.")
            return
        }

        consultaAtiva = viewModelScope.launch {

            _uiState.value = ProdutoUiState.Carregando

            try {

                val response =
                    repository.consultarProduto(codigo)

                // Ignora retornos antigos mesmo se o repository não cooperar com o cancelamento.
                ensureActive()
                if (servidor != null && servidor.value != sessaoInicial) return@launch

                if (response.isSuccessful) {

                    val produto = response.body()

                    if (produto != null) {
                        _uiState.value =
                            ProdutoUiState.Sucesso(produto)
                    } else {
                        _uiState.value =
                            ProdutoUiState.Erro(
                                "A API retornou uma resposta vazia."
                            )
                    }

                } else {

                    val mensagem = when (response.code()) {

                        400 ->
                            "Código de barras inválido."

                        404 ->
                            "Produto não encontrado."

                        else ->
                            "Erro na consulta. HTTP ${response.code()}."
                    }

                    _uiState.value =
                        ProdutoUiState.Erro(mensagem)
                }

            } catch (e: CancellationException) {

                throw e

            } catch (e: IOException) {

                ensureActive()
                if (servidor != null && servidor.value != sessaoInicial) return@launch
                _uiState.value =
                    ProdutoUiState.Erro(
                        "Não foi possível conectar ao servidor."
                    )

            } catch (e: Exception) {

                ensureActive()
                if (servidor != null && servidor.value != sessaoInicial) return@launch
                _uiState.value =
                    ProdutoUiState.Erro(
                        "Ocorreu um erro durante a consulta."
                    )
            }
        }
    }

    fun limpar() {
        consultaAtiva?.cancel()
        consultaAtiva = null
        _uiState.value = ProdutoUiState.Inicial
    }
}
