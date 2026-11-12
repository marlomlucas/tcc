package com.tcc.verificadorprecos.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tcc.verificadorprecos.discovery.DescobertaNsdAndroid
import com.tcc.verificadorprecos.discovery.DescobertaServidorViewModel
import com.tcc.verificadorprecos.discovery.FaseDescoberta

@Composable
fun TelaDescobertaServidor(
    onVoltar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current.applicationContext
    val donoCicloDeVida = LocalLifecycleOwner.current
    val fabrica = remember(contexto) {
        viewModelFactory {
            initializer { DescobertaServidorViewModel(DescobertaNsdAndroid(contexto)) }
        }
    }
    val descoberta: DescobertaServidorViewModel = viewModel(factory = fabrica)
    val estado by descoberta.estado.collectAsStateWithLifecycle()
    var telaAtiva by remember { mutableStateOf(true) }
    var solicitandoPermissao by remember { mutableStateOf(false) }
    var buscarAposPermissao by remember { mutableStateOf(false) }

    fun temPermissao(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

    var tinhaPermissao by remember { mutableStateOf(temPermissao()) }

    fun informarPermissaoNegada() {
        buscarAposPermissao = false
        descoberta.parar()
        descoberta.informarErro(
            "Permissão de rede local não concedida. Autorize o acesso para procurar servidores."
        )
    }

    val solicitarPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        solicitandoPermissao = false
        tinhaPermissao = temPermissao()
        if (telaAtiva && buscarAposPermissao) {
            if (!concedida || !temPermissao()) {
                informarPermissaoNegada()
            } else if (donoCicloDeVida.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                buscarAposPermissao = false
                descoberta.iniciar()
            }
            // Se o resultado anteceder ON_RESUME, o observador conclui o pedido do usuário.
        }
    }

    DisposableEffect(donoCicloDeVida, descoberta) {
        telaAtiva = true
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> {
                    buscarAposPermissao = false
                    descoberta.parar()
                }
                Lifecycle.Event.ON_RESUME -> {
                    val permitida = temPermissao()
                    if (!permitida) {
                        if (tinhaPermissao || descoberta.estado.value.buscando) informarPermissaoNegada()
                    } else if (telaAtiva && buscarAposPermissao && !solicitandoPermissao) {
                        buscarAposPermissao = false
                        descoberta.iniciar()
                    }
                    tinhaPermissao = permitida
                }
                else -> Unit
            }
        }
        donoCicloDeVida.lifecycle.addObserver(observador)
        onDispose {
            telaAtiva = false
            buscarAposPermissao = false
            donoCicloDeVida.lifecycle.removeObserver(observador)
            descoberta.parar()
        }
    }

    val sair = {
        telaAtiva = false
        buscarAposPermissao = false
        descoberta.parar()
        onVoltar()
    }
    BackHandler(onBack = sair)

    val fase = when (estado.fase) {
        FaseDescoberta.AGUARDANDO -> "Aguardando busca"
        FaseDescoberta.PROCURANDO -> "Procurando servidor"
        FaseDescoberta.RESOLVENDO -> "Resolvendo endereço do serviço"
        FaseDescoberta.VALIDANDO -> "Validando a identificação do serviço"
        FaseDescoberta.ENCONTRADO -> "Servidor encontrado"
        FaseDescoberta.SEM_RESULTADOS -> "Nenhum servidor encontrado"
        FaseDescoberta.ERRO -> "Erro na descoberta"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Descoberta de servidor", style = MaterialTheme.typography.headlineSmall)
        Text("A busca apenas apresenta os servidores encontrados na rede local.")
        Text("Estado: $fase", style = MaterialTheme.typography.titleMedium)
        if (estado.buscando) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (solicitandoPermissao) Text("Aguardando autorização de rede local...")
        estado.mensagem?.let { Text(it) }

        Button(
            enabled = !estado.buscando && !solicitandoPermissao,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (telaAtiva &&
                    donoCicloDeVida.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                ) {
                    if (temPermissao()) {
                        tinhaPermissao = true
                        descoberta.iniciar()
                    } else if (Build.VERSION.SDK_INT >= 37) {
                        buscarAposPermissao = true
                        solicitandoPermissao = true
                        solicitarPermissao.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    }
                }
            }
        ) {
            Text(if (estado.fase == FaseDescoberta.AGUARDANDO) "Procurar servidor" else "Procurar novamente")
        }

        if (estado.buscando) {
            OutlinedButton(
                onClick = {
                    buscarAposPermissao = false
                    descoberta.parar()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Parar busca")
            }
        }

        if (estado.servidores.size > 1) {
            Text("Mais de um servidor compatível foi encontrado. Nenhum foi selecionado.")
        }
        if (!estado.buscando && estado.servidores.isNotEmpty()) {
            Text("Resultados da última busca; inicie outra busca para conferir alterações.")
        }
        estado.servidores.forEach { servidor ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(servidor.nome, style = MaterialTheme.typography.titleMedium)
                    Text("IPv4: ${servidor.enderecosIpv4.joinToString()}")
                    Text("Porta: ${servidor.porta}")
                    Text("TXT app: verificador-precos")
                    Text("Versão: ${servidor.versao}")
                    Text("Identificação app e versão validadas.")
                }
            }
        }
        estado.avisos.forEach { aviso ->
            Text(aviso, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(onClick = sair, modifier = Modifier.fillMaxWidth()) {
            Text("Voltar à leitura")
        }
    }
}
