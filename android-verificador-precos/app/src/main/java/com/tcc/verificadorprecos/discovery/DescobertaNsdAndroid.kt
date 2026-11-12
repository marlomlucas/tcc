package com.tcc.verificadorprecos.discovery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address

/** Adapta as APIs assíncronas disponíveis no Android 12 ao controlador de diagnóstico. */
class DescobertaNsdAndroid(context: Context) : PlataformaDescoberta {
    private val contexto = context.applicationContext
    private val nsd = contexto.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = contexto.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val handler = Handler(Looper.getMainLooper())
    private val informacoes = mutableMapOf<String, NsdServiceInfo>()
    private var sessao: Sessao? = null
    private var proximaBusca: EventosDescoberta? = null
    private var resolucao: NsdManager.ResolveListener? = null

    override fun iniciar(eventos: EventosDescoberta) = executar {
        val atual = sessao
        if (atual == null) {
            abrir(eventos)
        } else if (atual.falhouAoParar) {
            eventos.erro("O Android não encerrou a busca anterior. Reinicie o aplicativo para tentar novamente.")
        } else {
            proximaBusca = eventos
            desativar(atual)
            solicitarParada(atual)
        }
    }

    override fun parar() = executar {
        proximaBusca = null
        sessao?.let { atual ->
            desativar(atual)
            solicitarParada(atual)
        }
    }

    @Suppress("DEPRECATION") // resolveService e host mantêm compatibilidade com o Android 12.
    override fun resolver(
        servico: AnuncioServico,
        resultado: (Result<ServicoResolvido>) -> Unit
    ) = executar {
        val original = informacoes[servico.chave]
        if (sessao?.cancelada != false || original == null) {
            resultado(Result.failure(IllegalStateException("O anúncio não está mais disponível nesta busca.")))
            return@executar
        }
        if (resolucao != null) {
            resultado(Result.failure(IllegalStateException("Existe uma resolução de serviço em andamento.")))
            return@executar
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, codigo: Int) = executar {
                Log.w(TAG, "Falha ao resolver o serviço (código $codigo).")
                terminarResolucao(
                    this,
                    Result.failure(IllegalStateException("Falha ao resolver o serviço (código $codigo).")),
                    resultado
                )
            }

            override fun onServiceResolved(info: NsdServiceInfo) = executar {
                val dados = runCatching {
                    val enderecos = if (Build.VERSION.SDK_INT >= 34) {
                        info.hostAddresses
                    } else {
                        listOfNotNull(info.host)
                    }
                    ServicoResolvido(
                        AnuncioServico(info.serviceName, info.serviceType),
                        enderecos.filterIsInstance<Inet4Address>().mapNotNull { it.hostAddress },
                        info.port,
                        info.attributes.mapValues { (_, bytes) -> bytes?.copyOf() ?: byteArrayOf() }
                    )
                }
                terminarResolucao(this, dados, resultado)
            }
        }
        resolucao = listener
        try {
            // Usa o objeto recebido do framework, preservando sua interface/rede de origem.
            nsd.resolveService(original, listener)
        } catch (erro: RuntimeException) {
            terminarResolucao(listener, Result.failure(erro), resultado)
        }
    }

    private fun terminarResolucao(
        listener: NsdManager.ResolveListener,
        dados: Result<ServicoResolvido>,
        resultado: (Result<ServicoResolvido>) -> Unit
    ) {
        if (resolucao !== listener) return
        resolucao = null
        // Mesmo após parar, entrega o evento terminal para o controlador liberar sua fila.
        // O Android 12 não oferece cancelamento público da resolução já enviada.
        resultado(dados)
    }

    private fun abrir(eventos: EventosDescoberta) {
        val atual = Sessao(eventos)
        sessao = atual
        try {
            if (Build.VERSION.SDK_INT >= 37 &&
                contexto.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
            ) {
                throw SecurityException("Permita o acesso à rede local para procurar o servidor.")
            }
            atual.multicast = wifi.createMulticastLock("VerificadorPrecos:descoberta").apply {
                setReferenceCounted(false)
            }
            atual.multicast?.acquire()
            nsd.discoverServices(TIPO_SERVICO_NSD, NsdManager.PROTOCOL_DNS_SD, atual)
        } catch (erro: RuntimeException) {
            Log.w(TAG, "Não foi possível iniciar a descoberta.", erro)
            concluir(atual)
            eventos.erro("Não foi possível iniciar a descoberta: ${erro.message ?: "erro do Android"}")
        }
    }

    private fun desativar(atual: Sessao) {
        atual.cancelada = true
        if (sessao === atual) informacoes.clear()
        liberarMulticast(atual)
    }

    private fun solicitarParada(atual: Sessao) {
        // Antes de onDiscoveryStarted, alguns Androids ainda não aceitam a solicitação de parada.
        if (!atual.iniciada || atual.paradaSolicitada || atual.falhouAoParar) return
        atual.paradaSolicitada = true
        try {
            nsd.stopServiceDiscovery(atual)
        } catch (erro: IllegalArgumentException) {
            // O listener já não está registrado no framework.
            Log.w(TAG, "A descoberta já não estava ativa.", erro)
            concluir(atual)
        } catch (erro: RuntimeException) {
            falhaAoParar(atual, "Não foi possível encerrar a descoberta: ${erro.message}")
        }
    }

    private fun liberarMulticast(atual: Sessao) {
        val lock = atual.multicast
        atual.multicast = null
        try {
            if (lock?.isHeld == true) lock.release()
        } catch (erro: RuntimeException) {
            Log.e(TAG, "Falha ao liberar a recepção multicast.", erro)
        }
    }

    private fun concluir(atual: Sessao) {
        desativar(atual)
        if (sessao !== atual) return
        sessao = null
        val proxima = proximaBusca
        proximaBusca = null
        proxima?.let(::abrir)
    }

    private fun falhaAoParar(atual: Sessao, mensagem: String) {
        Log.e(TAG, mensagem)
        if (sessao !== atual) {
            liberarMulticast(atual)
            return
        }
        desativar(atual)
        atual.falhouAoParar = true
        val proxima = proximaBusca
        proximaBusca = null
        // Não inicia uma busca paralela enquanto o framework não confirma a parada da anterior.
        atual.eventos.erro(mensagem)
        proxima?.erro(mensagem)
    }

    private inner class Sessao(val eventos: EventosDescoberta) : NsdManager.DiscoveryListener {
        var iniciada = false
        var cancelada = false
        var paradaSolicitada = false
        var falhouAoParar = false
        var multicast: WifiManager.MulticastLock? = null

        override fun onDiscoveryStarted(tipo: String) = executar {
            iniciada = true
            if (sessao !== this || cancelada) solicitarParada(this) else eventos.iniciada()
        }

        override fun onServiceFound(info: NsdServiceInfo) = executar {
            if (sessao !== this || cancelada || !tipoServicoCompativel(info.serviceType)) return@executar
            val anuncio = AnuncioServico(info.serviceName, info.serviceType)
            informacoes[anuncio.chave] = info
            eventos.encontrado(anuncio)
        }

        override fun onServiceLost(info: NsdServiceInfo) = executar {
            if (sessao !== this || cancelada || !tipoServicoCompativel(info.serviceType)) return@executar
            val anuncio = AnuncioServico(info.serviceName, info.serviceType)
            informacoes.remove(anuncio.chave)
            eventos.perdido(anuncio)
        }

        override fun onDiscoveryStopped(tipo: String) = executar {
            val avisar = sessao === this && !cancelada
            concluir(this)
            if (avisar) eventos.encerrada()
        }

        override fun onStartDiscoveryFailed(tipo: String, codigo: Int) = executar {
            Log.w(TAG, "Falha ao iniciar a descoberta (código $codigo).")
            val avisar = sessao === this && !cancelada
            concluir(this)
            if (avisar) eventos.erro("Falha ao iniciar a descoberta (código $codigo).")
        }

        override fun onStopDiscoveryFailed(tipo: String, codigo: Int) = executar {
            falhaAoParar(this, "Falha ao encerrar a descoberta (código $codigo).")
        }
    }

    private fun executar(acao: () -> Unit) {
        if (Looper.myLooper() == handler.looper) acao() else handler.post { acao() }
    }

    private companion object {
        const val TAG = "DescobertaNsd"
    }
}
