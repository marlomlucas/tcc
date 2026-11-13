package com.tcc.verificadorprecos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tcc.verificadorprecos.connection.EstadoConexao
import com.tcc.verificadorprecos.connection.FaseConexao
import com.tcc.verificadorprecos.connection.ModoConexao
import com.tcc.verificadorprecos.connection.OpcaoServidor
import com.tcc.verificadorprecos.ui.components.AssinaturaATL

@Composable
fun TelaDescobertaServidor(
    estado: EstadoConexao,
    onProcurarNovamente: () -> Unit,
    onUsarAutomatico: () -> Unit,
    onConectarManual: (String, String) -> Unit,
    onSelecionar: (OpcaoServidor) -> Unit,
    onReconectar: () -> Unit,
    modifier: Modifier = Modifier,
    permissaoConcedida: Boolean = true,
    solicitandoPermissao: Boolean = false,
    onVoltar: (() -> Unit)? = null
) {
    var mostrarManual by rememberSaveable { mutableStateOf(estado.modo == ModoConexao.MANUAL) }
    var entradaManual by rememberSaveable { mutableStateOf(estado.manualSalvo.orEmpty()) }
    var portaManual by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(estado.manualSalvo) {
        if (entradaManual.isBlank()) entradaManual = estado.manualSalvo.orEmpty()
    }

    if (onVoltar != null) BackHandler(onBack = onVoltar)

    val ocupado = estado.fase == FaseConexao.PROCURANDO || estado.fase == FaseConexao.VERIFICANDO
    val fase = when (estado.fase) {
        FaseConexao.AGUARDANDO -> "Preparando conexão"
        FaseConexao.PROCURANDO -> "Procurando servidor na rede local"
        FaseConexao.ESCOLHER_SERVIDOR -> "Escolha um servidor"
        FaseConexao.VERIFICANDO -> "Verificando conexão com o servidor"
        FaseConexao.CONECTADO -> "Servidor conectado"
        FaseConexao.INDISPONIVEL -> "Servidor indisponível"
        FaseConexao.ERRO -> "Não foi possível conectar"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Conexão com o servidor", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Conecte ao servidor de preços para liberar a câmera e a consulta manual.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(fase, style = MaterialTheme.typography.titleMedium)
        if (ocupado) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (solicitandoPermissao) Text("Aguardando autorização de rede local...")
        if (!permissaoConcedida && !solicitandoPermissao) {
            Text("Autorize o acesso à rede local para conectar ao servidor.")
        }
        estado.mensagem?.let { mensagem ->
            Text(
                mensagem,
                color = if (estado.fase == FaseConexao.ERRO ||
                    estado.fase == FaseConexao.INDISPONIVEL
                ) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            if (estado.modo == ModoConexao.MANUAL) "Modo: servidor manual" else "Modo: automático",
            style = MaterialTheme.typography.bodyMedium
        )
        estado.sessao?.let { sessao ->
            Text("Servidor ativo: ${sessao.endereco.urlBase}")
        }
        estado.manualSalvo?.let { salvo ->
            Text("Servidor manual salvo: $salvo", style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                if (estado.modo == ModoConexao.MANUAL && estado.manualSalvo == null) {
                    mostrarManual = true
                    if (!permissaoConcedida) onReconectar()
                } else if (estado.modo == ModoConexao.MANUAL) {
                    onReconectar()
                } else {
                    onProcurarNovamente()
                }
            },
            enabled = !ocupado && !solicitandoPermissao,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    !permissaoConcedida -> "Autorizar acesso à rede local"
                    estado.modo == ModoConexao.MANUAL && estado.manualSalvo == null -> "Configurar servidor"
                    estado.modo == ModoConexao.MANUAL -> "Reconectar ao servidor salvo"
                    else -> "Procurar novamente"
                }
            )
        }

        if (estado.candidatos.isNotEmpty()) {
            Text("Servidores encontrados", style = MaterialTheme.typography.titleMedium)
            if (estado.candidatos.size > 1) {
                Text("Selecione o servidor que deseja utilizar.")
            }
            estado.candidatos.forEach { candidato ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(candidato.nome, style = MaterialTheme.typography.titleMedium)
                        Text(candidato.endereco.urlBase)
                        Button(
                            onClick = { onSelecionar(candidato) },
                            enabled = !ocupado && !solicitandoPermissao,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Conectar a este servidor")
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onUsarAutomatico,
            enabled = !solicitandoPermissao,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Usar modo automático")
        }
        if (estado.manualSalvo != null) {
            Text(
                "Ao usar o modo automático, o endereço manual salvo será removido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedButton(
            onClick = { mostrarManual = !mostrarManual },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mostrarManual) "Ocultar configuração manual" else "Configurar servidor manualmente")
        }
        if (mostrarManual) {
            OutlinedTextField(
                value = entradaManual,
                onValueChange = { entradaManual = it },
                label = { Text("IPv4 ou URL do servidor") },
                placeholder = { Text("192.168.1.10 ou http://192.168.1.10:8000/") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = portaManual,
                onValueChange = { portaManual = it },
                label = { Text("Porta (opcional)") },
                placeholder = { Text("8000") },
                supportingText = { Text("Deixe em branco para usar a porta da URL ou a porta padrão.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onConectarManual(entradaManual, portaManual) },
                enabled = !solicitandoPermissao,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Testar e conectar")
            }
        }

        if (onVoltar != null) {
            OutlinedButton(onClick = onVoltar, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar à leitura")
            }
        }

        AssinaturaATL()
    }
}
