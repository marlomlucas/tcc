package com.tcc.verificadorprecos.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.verificadorprecos.discovery.DescobertaServidorViewModel
import com.tcc.verificadorprecos.discovery.FaseDescoberta
import kotlinx.coroutines.launch

/** Reutiliza uma única descoberta e entrega somente servidores verificados à rede. */
class ConexaoServidorViewModel(
    private val descoberta: DescobertaServidorViewModel,
    verificador: VerificadorApi,
    preferencia: PreferenciaServidor
) : ViewModel() {
    val configuracao = GerenciadorConexao(verificador, preferencia, viewModelScope)
    val estado = configuracao.estado
    val sessao = configuracao.sessao
    private var inicializado = false
    private var aguardandoDescoberta = false

    init {
        viewModelScope.launch {
            descoberta.estado.collect { resultado ->
                if (!aguardandoDescoberta || resultado.buscando ||
                    resultado.fase == FaseDescoberta.AGUARDANDO
                ) return@collect
                aguardandoDescoberta = false
                if (resultado.fase == FaseDescoberta.ERRO) {
                    configuracao.informarErro(resultado.mensagem ?: "Falha na descoberta do servidor.")
                } else {
                    val candidatos = resultado.servidores.flatMap { servidor ->
                        servidor.enderecosIpv4.mapNotNull { ip ->
                            runCatching {
                                OpcaoServidor(servidor.nome, EnderecoServidor.criar(ip, servidor.porta))
                            }.getOrNull()
                        }
                    }.distinct()
                    configuracao.apresentarCandidatos(candidatos)
                }
            }
        }
    }

    fun iniciar() {
        if (inicializado || estado.value.fase == FaseConexao.CONECTADO) return
        inicializado = true
        if (!configuracao.restaurarManual()) iniciarDescoberta()
    }

    fun procurarNovamente() {
        if (estado.value.modo == ModoConexao.MANUAL) reconectar()
        else iniciarDescoberta()
    }

    fun usarAutomatico() {
        inicializado = true
        pararDescoberta()
        configuracao.usarAutomatico()
        iniciarNsdSeNecessario()
    }

    fun conectarManual(entrada: String, porta: String) {
        inicializado = true
        pararDescoberta()
        configuracao.conectarManual(entrada, porta)
    }

    fun selecionar(opcao: OpcaoServidor) {
        pararDescoberta()
        configuracao.selecionar(opcao)
    }

    fun reconectar() {
        inicializado = true
        pararDescoberta()
        if (estado.value.modo == ModoConexao.MANUAL) configuracao.restaurarManual()
        else iniciarDescoberta()
    }

    fun pausar() {
        val pendente = aguardandoDescoberta || estado.value.fase in setOf(
            FaseConexao.PROCURANDO, FaseConexao.VERIFICANDO
        )
        pararDescoberta()
        if (pendente) {
            configuracao.interromperOperacao()
            inicializado = false
        }
    }

    fun permissaoNegada() {
        pararDescoberta()
        configuracao.informarErro("Permita o acesso à rede local para conectar ao servidor.")
        inicializado = false
    }

    private fun iniciarDescoberta() {
        inicializado = true
        pararDescoberta()
        configuracao.procurar()
        iniciarNsdSeNecessario()
    }

    private fun iniciarNsdSeNecessario() {
        if (estado.value.modo != ModoConexao.AUTOMATICO ||
            estado.value.fase != FaseConexao.PROCURANDO
        ) return
        aguardandoDescoberta = true
        descoberta.iniciar()
    }

    private fun pararDescoberta() {
        aguardandoDescoberta = false
        descoberta.parar()
    }

    override fun onCleared() {
        pararDescoberta()
        configuracao.interromperOperacao()
    }
}
