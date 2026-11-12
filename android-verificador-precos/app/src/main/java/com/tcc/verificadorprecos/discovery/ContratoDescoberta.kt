package com.tcc.verificadorprecos.discovery

import java.util.Locale

const val TIPO_SERVICO_NSD = "_vprecos._tcp."
const val TIPO_SERVICO_COMPLETO = "_vprecos._tcp.local."

data class AnuncioServico(val nome: String, val tipo: String) {
    val chave: String get() = "$nome|${normalizarTipoServico(tipo)}"
}

data class ServicoResolvido(
    val anuncio: AnuncioServico,
    val enderecos: List<String>,
    val porta: Int,
    val atributos: Map<String, ByteArray>
)

data class ServidorDescoberto(
    val nome: String,
    val enderecosIpv4: List<String>,
    val porta: Int,
    val versao: String
)

enum class FaseDescoberta {
    AGUARDANDO, PROCURANDO, RESOLVENDO, VALIDANDO, ENCONTRADO, SEM_RESULTADOS, ERRO
}

data class EstadoDescoberta(
    val fase: FaseDescoberta = FaseDescoberta.AGUARDANDO,
    val buscando: Boolean = false,
    val servidores: List<ServidorDescoberto> = emptyList(),
    val mensagem: String? = null,
    val avisos: List<String> = emptyList()
)

interface EventosDescoberta {
    fun iniciada()
    fun encontrado(servico: AnuncioServico)
    fun perdido(servico: AnuncioServico)
    fun erro(mensagem: String)
    fun encerrada()
}

interface PlataformaDescoberta {
    fun iniciar(eventos: EventosDescoberta)
    fun resolver(servico: AnuncioServico, resultado: (Result<ServicoResolvido>) -> Unit)
    fun parar()
}

fun normalizarTipoServico(tipo: String): String =
    tipo.lowercase(Locale.ROOT).trim('.').removeSuffix(".local").trim('.')

fun tipoServicoCompativel(tipo: String): Boolean =
    normalizarTipoServico(tipo) == "_vprecos._tcp"

fun ipv4Valido(endereco: String): Boolean {
    val partes = endereco.split('.')
    if (partes.size != 4) return false
    val octetos = partes.map { parte ->
        if (parte.isEmpty() || parte.any { it !in '0'..'9' } ||
            (parte.length > 1 && parte.startsWith('0'))
        ) return false
        parte.toIntOrNull()?.takeIf { it in 0..255 } ?: return false
    }
    return octetos[0] in 1..223 && octetos[0] != 127 &&
        !(octetos[0] == 169 && octetos[1] == 254)
}

fun validarServico(servico: ServicoResolvido): ServidorDescoberto {
    require(tipoServicoCompativel(servico.anuncio.tipo)) { "Tipo de serviço incompatível." }
    require(servico.atributos["app"]?.toString(Charsets.UTF_8) == "verificador-precos") {
        "Identificação app incompatível."
    }
    require(servico.atributos["version"]?.toString(Charsets.UTF_8) == "1.0") {
        "Versão do serviço incompatível; esperada 1.0."
    }
    require(servico.porta in 1..65535) { "Porta inválida." }
    val enderecos = servico.enderecos.filter(::ipv4Valido).distinct().sorted()
    require(enderecos.isNotEmpty()) { "O serviço não possui um IPv4 válido." }
    return ServidorDescoberto(servico.anuncio.nome, enderecos, servico.porta, "1.0")
}
