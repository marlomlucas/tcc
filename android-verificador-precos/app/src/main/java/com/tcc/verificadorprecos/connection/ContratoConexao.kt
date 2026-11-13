package com.tcc.verificadorprecos.connection

enum class ModoConexao { AUTOMATICO, MANUAL }

enum class FaseConexao {
    AGUARDANDO, PROCURANDO, ESCOLHER_SERVIDOR, VERIFICANDO, CONECTADO, INDISPONIVEL, ERRO
}

data class OpcaoServidor(val nome: String, val endereco: EnderecoServidor)

data class SessaoServidor(val endereco: EnderecoServidor, val revisao: Long)

data class EstadoConexao(
    val fase: FaseConexao = FaseConexao.AGUARDANDO,
    val modo: ModoConexao = ModoConexao.AUTOMATICO,
    val sessao: SessaoServidor? = null,
    val candidatos: List<OpcaoServidor> = emptyList(),
    val mensagem: String? = null,
    val manualSalvo: String? = null
)

interface VerificadorApi {
    suspend fun verificar(endereco: EnderecoServidor)
}

interface PreferenciaServidor {
    fun carregar(): String?
    fun salvar(endereco: String?)
}
