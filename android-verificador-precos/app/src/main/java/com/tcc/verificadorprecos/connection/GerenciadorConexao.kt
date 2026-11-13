package com.tcc.verificadorprecos.connection

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Coordena a escolha e a validação do servidor, sem controlar a descoberta de rede. */
class GerenciadorConexao(
    private val verificador: VerificadorApi,
    private val preferencia: PreferenciaServidor,
    private val escopo: CoroutineScope
) {
    private val _estado = MutableStateFlow(EstadoConexao())
    val estado: StateFlow<EstadoConexao> = _estado.asStateFlow()

    private val _sessao = MutableStateFlow<SessaoServidor?>(null)
    val sessao: StateFlow<SessaoServidor?> = _sessao.asStateFlow()

    private var verificacao: Job? = null
    private var geracao = 0L
    private var revisao = 0L

    /**
     * Retorna false somente quando não há preferência manual. Uma falha de leitura
     * também é tratada aqui para impedir uma troca automática silenciosa.
     */
    fun restaurarManual(): Boolean {
        val salvo = try {
            preferencia.carregar()
        } catch (cancelamento: CancellationException) {
            throw cancelamento
        } catch (erro: Exception) {
            _estado.value = _estado.value.copy(modo = ModoConexao.MANUAL)
            informarErro("Não foi possível ler o servidor salvo: ${descricao(erro)}")
            return true
        } ?: return false

        cancelarVerificacao()
        invalidarSessao()
        _estado.value = _estado.value.copy(
            modo = ModoConexao.MANUAL,
            candidatos = emptyList(),
            manualSalvo = salvo,
            mensagem = null
        )
        val endereco = interpretar(salvo, "8000") ?: return true
        verificar(endereco, persistirManual = false)
        return true
    }

    fun procurar() {
        if (_estado.value.modo != ModoConexao.AUTOMATICO) return
        cancelarVerificacao()
        invalidarSessao()
        _estado.value = _estado.value.copy(
            fase = FaseConexao.PROCURANDO,
            candidatos = emptyList(),
            mensagem = null
        )
    }

    fun apresentarCandidatos(candidatos: List<OpcaoServidor>) {
        if (_estado.value.modo != ModoConexao.AUTOMATICO ||
            _estado.value.fase != FaseConexao.PROCURANDO
        ) return

        val opcoes = candidatos.toList()
        when (opcoes.size) {
            0 -> _estado.value = _estado.value.copy(
                fase = FaseConexao.INDISPONIVEL,
                candidatos = emptyList(),
                mensagem = "Nenhum servidor compatível foi encontrado."
            )
            1 -> verificar(opcoes.single().endereco, persistirManual = false)
            else -> _estado.value = _estado.value.copy(
                fase = FaseConexao.ESCOLHER_SERVIDOR,
                candidatos = opcoes,
                mensagem = null
            )
        }
    }

    fun selecionar(opcao: OpcaoServidor) {
        val atual = _estado.value
        if (atual.modo != ModoConexao.AUTOMATICO ||
            atual.fase != FaseConexao.ESCOLHER_SERVIDOR ||
            opcao !in atual.candidatos
        ) return
        verificar(opcao.endereco, persistirManual = false)
    }

    fun conectarManual(entrada: String, porta: String = "8000") {
        cancelarVerificacao()
        invalidarSessao()
        _estado.value = _estado.value.copy(
            modo = ModoConexao.MANUAL,
            candidatos = emptyList(),
            mensagem = null
        )
        val endereco = interpretar(entrada, porta) ?: return
        verificar(endereco, persistirManual = true)
    }

    fun usarAutomatico() {
        cancelarVerificacao()
        invalidarSessao()
        try {
            preferencia.salvar(null)
        } catch (cancelamento: CancellationException) {
            throw cancelamento
        } catch (erro: Exception) {
            informarErro("Não foi possível remover o servidor salvo: ${descricao(erro)}")
            return
        }
        _estado.value = _estado.value.copy(
            modo = ModoConexao.AUTOMATICO,
            fase = FaseConexao.PROCURANDO,
            candidatos = emptyList(),
            mensagem = null,
            manualSalvo = null
        )
    }

    /** A saída da tela cancela trabalho pendente, mas conserva uma conexão validada. */
    fun interromperOperacao() {
        cancelarVerificacao()
        if (_sessao.value != null) return
        _estado.value = _estado.value.copy(
            fase = FaseConexao.INDISPONIVEL,
            candidatos = emptyList(),
            mensagem = null
        )
    }

    fun registrarFalha(sessao: SessaoServidor, mensagem: String) {
        if (_sessao.value != sessao) return
        informarErro(mensagem)
    }

    fun informarErro(mensagem: String) {
        cancelarVerificacao()
        invalidarSessao()
        _estado.value = _estado.value.copy(
            fase = FaseConexao.ERRO,
            candidatos = emptyList(),
            mensagem = mensagem
        )
    }

    private fun interpretar(entrada: String, porta: String): EnderecoServidor? = try {
        EnderecoServidor.interpretar(entrada, porta)
    } catch (erro: IllegalArgumentException) {
        _estado.value = _estado.value.copy(
            fase = FaseConexao.ERRO,
            mensagem = "Endereço do servidor inválido: ${descricao(erro)}"
        )
        null
    }

    private fun verificar(endereco: EnderecoServidor, persistirManual: Boolean) {
        cancelarVerificacao()
        invalidarSessao()
        val operacao = geracao
        _estado.value = _estado.value.copy(
            fase = FaseConexao.VERIFICANDO,
            candidatos = emptyList(),
            mensagem = null
        )
        verificacao = escopo.launch {
            var salvandoManual = false
            try {
                verificador.verificar(endereco)
                currentCoroutineContext().ensureActive()
                if (operacao != geracao) return@launch

                if (persistirManual) {
                    salvandoManual = true
                    preferencia.salvar(endereco.urlBase)
                    currentCoroutineContext().ensureActive()
                    if (operacao != geracao) return@launch
                }

                val nova = SessaoServidor(endereco, ++revisao)
                _sessao.value = nova
                _estado.value = _estado.value.copy(
                    fase = FaseConexao.CONECTADO,
                    sessao = nova,
                    manualSalvo = if (persistirManual) endereco.urlBase else _estado.value.manualSalvo,
                    mensagem = null
                )
            } catch (cancelamento: CancellationException) {
                throw cancelamento
            } catch (erro: Exception) {
                // Também protege contra verificadores que ignoram o cancelamento e
                // terminam lançando outro erro depois que uma nova operação começou.
                currentCoroutineContext().ensureActive()
                if (operacao != geracao) return@launch
                _estado.value = _estado.value.copy(
                    fase = FaseConexao.ERRO,
                    mensagem = if (salvandoManual) {
                        "Não foi possível salvar o servidor manual: ${descricao(erro)}"
                    } else {
                        "Não foi possível conectar ao servidor: ${descricao(erro)}"
                    }
                )
            } finally {
                if (operacao == geracao) verificacao = null
            }
        }
    }

    private fun cancelarVerificacao() {
        geracao++
        verificacao?.cancel()
        verificacao = null
    }

    private fun invalidarSessao() {
        _sessao.value = null
        _estado.value = _estado.value.copy(sessao = null)
    }

    private fun descricao(erro: Exception): String = erro.message ?: "erro sem detalhes"
}
