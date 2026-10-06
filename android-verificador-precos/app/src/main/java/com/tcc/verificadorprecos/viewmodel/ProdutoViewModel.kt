package com.tcc.verificadorprecos.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.verificadorprecos.data.ProdutoDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import com.tcc.verificadorprecos.repository.ProdutoRepository
import com.tcc.verificadorprecos.repository.ProdutoRepositoryImpl

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
    private val repository: ProdutoRepository
) : ViewModel() {

    constructor() : this(ProdutoRepositoryImpl())

    private val _uiState =
        MutableStateFlow<ProdutoUiState>(ProdutoUiState.Inicial)

    val uiState: StateFlow<ProdutoUiState> =
        _uiState.asStateFlow()

    fun consultarProduto(codigoBarra: String) {

        val codigo = codigoBarra.trim()

        if (codigo.isEmpty()) {
            _uiState.value =
                ProdutoUiState.Erro("Informe um código de barras.")
            return
        }

        viewModelScope.launch {

            _uiState.value = ProdutoUiState.Carregando

            try {

                val response =
                    repository.consultarProduto(codigo)

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

            } catch (e: IOException) {

                _uiState.value =
                    ProdutoUiState.Erro(
                        "Não foi possível conectar ao servidor."
                    )

            } catch (e: Exception) {

                _uiState.value =
                    ProdutoUiState.Erro(
                        "Ocorreu um erro durante a consulta."
                    )
            }
        }
    }

    fun limpar() {
        _uiState.value = ProdutoUiState.Inicial
    }
}