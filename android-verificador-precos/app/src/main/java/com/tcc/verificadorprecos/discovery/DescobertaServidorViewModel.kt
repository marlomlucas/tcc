package com.tcc.verificadorprecos.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class DescobertaServidorViewModel(
    private val plataforma: PlataformaDescoberta
) : ViewModel() {
    private class Sessao {
        val conhecidos = mutableMapOf<String, Pedido>()
        val servidores = mutableMapOf<String, ServidorDescoberto>()
        val fila = ArrayDeque<Pedido>()
    }

    private class Pedido(val sessao: Sessao, val anuncio: AnuncioServico)

    private val _estado = MutableStateFlow(EstadoDescoberta())
    val estado = _estado.asStateFlow()
    private var sessao: Sessao? = null
    private var timeout: Job? = null

    // No Android 12 não existe cancelamento público da resolução legada.
    // Aguarda seu callback terminal mesmo após parar, antes de resolver outro serviço.
    private var resolucaoAtiva: Pedido? = null

    fun iniciar() {
        if (sessao != null) return
        val atual = Sessao()
        sessao = atual
        _estado.value = EstadoDescoberta(fase = FaseDescoberta.PROCURANDO, buscando = true)
        timeout = viewModelScope.launch {
            delay(10_000)
            if (sessao === atual) finalizar()
        }
        try {
            plataforma.iniciar(eventos(atual))
        } catch (erro: RuntimeException) {
            finalizar("Não foi possível iniciar a descoberta: ${erro.message}")
        }
    }

    fun parar() {
        if (sessao != null) finalizar()
    }

    fun informarErro(mensagem: String) {
        if (sessao != null) {
            finalizar(mensagem)
        } else {
            _estado.value = _estado.value.copy(
                fase = FaseDescoberta.ERRO, buscando = false, mensagem = mensagem
            )
        }
    }

    private fun eventos(atual: Sessao) = object : EventosDescoberta {
        override fun iniciada() = receber(atual) {
            if (resolucaoAtiva == null) {
                _estado.value = _estado.value.copy(fase = FaseDescoberta.PROCURANDO)
            }
        }

        override fun encontrado(servico: AnuncioServico) = receber(atual) {
            if (!tipoServicoCompativel(servico.tipo)) {
                avisar("Serviço ${servico.nome} ignorado: tipo incompatível.")
                return@receber
            }
            if (servico.chave in atual.conhecidos) return@receber
            val pedido = Pedido(atual, servico)
            atual.conhecidos[servico.chave] = pedido
            atual.fila.addLast(pedido)
            resolverProximo()
        }

        override fun perdido(servico: AnuncioServico) = receber(atual) {
            atual.conhecidos.remove(servico.chave)
            atual.fila.removeAll { it.anuncio.chave == servico.chave }
            atual.servidores.remove(servico.chave)
            publicarServidores(atual)
            avisar("Serviço ${servico.nome} deixou de estar disponível durante a busca.")
        }

        override fun erro(mensagem: String) = receber(atual) { finalizar(mensagem) }

        override fun encerrada() = receber(atual) {
            finalizar("A descoberta foi encerrada pelo sistema antes do prazo.")
        }
    }

    private fun receber(atual: Sessao, acao: () -> Unit) {
        viewModelScope.launch {
            if (sessao === atual) acao()
        }
    }

    private fun resolverProximo() {
        val atual = sessao ?: return
        if (resolucaoAtiva != null) {
            _estado.value = _estado.value.copy(
                fase = FaseDescoberta.RESOLVENDO,
                mensagem = "Aguardando a conclusão da resolução em andamento."
            )
            return
        }
        val pedido = atual.fila.pollFirst() ?: return
        resolucaoAtiva = pedido
        _estado.value = _estado.value.copy(
            fase = FaseDescoberta.RESOLVENDO,
            mensagem = "Resolvendo ${pedido.anuncio.nome}..."
        )
        try {
            plataforma.resolver(pedido.anuncio) { resultado ->
                viewModelScope.launch { concluirResolucao(pedido, resultado) }
            }
        } catch (erro: RuntimeException) {
            concluirResolucao(pedido, Result.failure(erro))
        }
    }

    private fun concluirResolucao(pedido: Pedido, resultado: Result<ServicoResolvido>) {
        if (resolucaoAtiva !== pedido) return
        resolucaoAtiva = null
        val atual = sessao
        if (atual != null && atual === pedido.sessao && atual.conhecidos[pedido.anuncio.chave] === pedido) {
            resultado.fold(
                onSuccess = { resolvido ->
                    _estado.value = _estado.value.copy(
                        fase = FaseDescoberta.VALIDANDO,
                        mensagem = "Validando a identidade de ${pedido.anuncio.nome}..."
                    )
                    try {
                        require(resolvido.anuncio.chave == pedido.anuncio.chave) {
                            "A resposta pertence a outro serviço."
                        }
                        atual.servidores[pedido.anuncio.chave] = validarServico(resolvido)
                    } catch (erro: IllegalArgumentException) {
                        avisar("Serviço ${pedido.anuncio.nome} ignorado: ${erro.message}")
                    }
                },
                onFailure = { erro ->
                    avisar("Falha ao resolver ${pedido.anuncio.nome}: ${erro.message}")
                }
            )
            publicarServidores(atual)
        }
        // Um callback antigo apenas libera a resolução; seus dados não entram na nova busca.
        resolverProximo()
    }

    private fun publicarServidores(atual: Sessao) {
        val servidores = atual.servidores.values.sortedBy { it.nome }
        _estado.value = _estado.value.copy(
            servidores = servidores,
            fase = when {
                resolucaoAtiva != null -> FaseDescoberta.RESOLVENDO
                servidores.isEmpty() -> FaseDescoberta.PROCURANDO
                else -> FaseDescoberta.ENCONTRADO
            },
            mensagem = null
        )
    }

    private fun avisar(mensagem: String) {
        _estado.value = _estado.value.copy(avisos = (_estado.value.avisos + mensagem).distinct())
    }

    private fun finalizar(mensagem: String? = null) {
        sessao = null
        timeout?.cancel()
        timeout = null
        var falha = mensagem
        try {
            plataforma.parar()
        } catch (erro: RuntimeException) {
            falha = listOfNotNull(falha, "Falha ao encerrar a descoberta: ${erro.message}")
                .joinToString(" ")
        }
        val fase = when {
            falha != null -> FaseDescoberta.ERRO
            _estado.value.servidores.isNotEmpty() -> FaseDescoberta.ENCONTRADO
            else -> FaseDescoberta.SEM_RESULTADOS
        }
        _estado.value = _estado.value.copy(fase = fase, buscando = false, mensagem = falha)
    }

    override fun onCleared() {
        parar()
    }
}
