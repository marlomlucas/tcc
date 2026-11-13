package com.tcc.verificadorprecos.connection

import com.tcc.verificadorprecos.discovery.ipv4Valido
import java.net.URI

@ConsistentCopyVisibility
data class EnderecoServidor private constructor(val ip: String, val porta: Int) {
    init {
        require(ipv4Valido(ip)) { "Informe um IPv4 válido da rede." }
        require(porta in 1..65535) { "Informe uma porta entre 1 e 65535." }
    }

    val urlBase: String get() = "http://$ip:$porta/"

    companion object {
        fun criar(ip: String, porta: Int): EnderecoServidor = EnderecoServidor(ip.trim(), porta)

        fun interpretar(entrada: String, porta: String = "8000"): EnderecoServidor {
            val valor = entrada.trim()
            if (ipv4Valido(valor)) {
                val numeroPorta = porta.trim().ifEmpty { "8000" }.takeIf { it.matches(Regex("[0-9]{1,5}")) }
                    ?.toIntOrNull()
                requireNotNull(numeroPorta) { "Informe uma porta entre 1 e 65535." }
                return criar(valor, numeroPorta)
            }

            val uri = try {
                URI(valor)
            } catch (_: Exception) {
                throw IllegalArgumentException("Informe um IPv4 ou uma URL HTTP válida.")
            }
            require(uri.scheme.equals("http", ignoreCase = true) && !uri.isOpaque &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            ) { "Use HTTP com um IPv4, sem credenciais, caminho, consulta ou fragmento." }
            val autoridade = requireNotNull(uri.rawAuthority) { "Informe um IPv4 válido da rede." }
            require(autoridade.matches(Regex("[0-9.]+(?::[0-9]{1,5})?"))) {
                "Informe um IPv4 e uma porta válidos."
            }
            val host = requireNotNull(uri.host) { "Informe um IPv4 válido da rede." }
            return criar(host, if (uri.port == -1) 80 else uri.port)
        }
    }
}
