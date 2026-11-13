package com.tcc.verificadorprecos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tcc.verificadorprecos.connection.ConexaoServidorViewModel
import com.tcc.verificadorprecos.connection.FaseConexao
import com.tcc.verificadorprecos.connection.PreferenciaServidorAndroid
import com.tcc.verificadorprecos.connection.SessaoServidor
import com.tcc.verificadorprecos.connection.VerificadorApiHttp
import com.tcc.verificadorprecos.discovery.DescobertaNsdAndroid
import com.tcc.verificadorprecos.discovery.DescobertaServidorViewModel
import com.tcc.verificadorprecos.repository.ProdutoRepositoryImpl
import com.tcc.verificadorprecos.ui.TelaDescobertaServidor
import com.tcc.verificadorprecos.ui.TelaLeitura
import com.tcc.verificadorprecos.ui.TelaResultado
import com.tcc.verificadorprecos.ui.theme.VerificadorPrecosTheme
import com.tcc.verificadorprecos.viewmodel.ProdutoUiState
import com.tcc.verificadorprecos.viewmodel.ProdutoViewModel
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val descoberta = ViewModelProvider(this, viewModelFactory {
            initializer { DescobertaServidorViewModel(DescobertaNsdAndroid(applicationContext)) }
        })[DescobertaServidorViewModel::class.java]
        val conexao = ViewModelProvider(this, viewModelFactory {
            initializer {
                ConexaoServidorViewModel(
                    descoberta = descoberta,
                    verificador = VerificadorApiHttp(),
                    preferencia = PreferenciaServidorAndroid(applicationContext)
                )
            }
        })[ConexaoServidorViewModel::class.java]
        val produto = ViewModelProvider(this, viewModelFactory {
            initializer {
                ProdutoViewModel(
                    repository = ProdutoRepositoryImpl(conexao.configuracao),
                    servidor = conexao.sessao
                )
            }
        })[ProdutoViewModel::class.java]

        setContent {
            VerificadorPrecosTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TelaVerificadorPrecos(produto, conexao)
                }
            }
        }
    }
}

@Composable
fun TelaVerificadorPrecos(
    produtoViewModel: ProdutoViewModel? = null,
    conexaoViewModel: ConexaoServidorViewModel? = null
) {
    if (LocalInspectionMode.current) {
        TelaLeitura(
            codigoBarra = "",
            uiState = ProdutoUiState.Inicial,
            onCodigoBarraChange = {},
            onCodigoDetectado = {},
            onConsultar = {},
            onNovaLeitura = {},
            leituraHabilitada = false
        )
        return
    }

    val produto = requireNotNull(produtoViewModel)
    val conexao = requireNotNull(conexaoViewModel)
    val contexto = LocalContext.current.applicationContext
    val donoCicloDeVida = LocalLifecycleOwner.current
    val teclado = LocalSoftwareKeyboardController.current
    val gerenciadorFoco = LocalFocusManager.current
    val estado by conexao.estado.collectAsStateWithLifecycle()
    val sessao by conexao.sessao.collectAsStateWithLifecycle()
    val uiState by produto.uiState.collectAsStateWithLifecycle()

    var codigoBarra by rememberSaveable { mutableStateOf("") }
    var leituraAtiva by remember { mutableStateOf(true) }
    var configuracaoAberta by rememberSaveable { mutableStateOf(false) }
    var sessaoPreparada by remember { mutableStateOf<SessaoServidor?>(null) }
    var chaveUltimaSessao by rememberSaveable { mutableStateOf<String?>(null) }
    var telaAtiva by remember { mutableStateOf(true) }
    var resumida by remember { mutableStateOf(false) }
    var solicitandoPermissao by remember { mutableStateOf(false) }
    var permissaoInicialSolicitada by rememberSaveable { mutableStateOf(false) }
    var acaoAposPermissao by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun temPermissao(): Boolean = Build.VERSION.SDK_INT < 37 ||
        ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_LOCAL_NETWORK) ==
        PackageManager.PERMISSION_GRANTED

    var permissaoConcedida by remember { mutableStateOf(temPermissao()) }

    val solicitarPermissao = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedida ->
        solicitandoPermissao = false
        if (telaAtiva) {
            permissaoConcedida = concedida && temPermissao()
            if (!permissaoConcedida) {
                acaoAposPermissao = null
                conexao.permissaoNegada()
            } else if (donoCicloDeVida.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val acao = acaoAposPermissao
                acaoAposPermissao = null
                acao?.invoke()
            }
            // Um resultado anterior ao ON_RESUME aguarda a retomada do host.
        }
    }

    fun executarComPermissao(acao: () -> Unit) {
        if (!telaAtiva ||
            !donoCicloDeVida.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) return
        permissaoConcedida = temPermissao()
        if (permissaoConcedida) {
            acao()
        } else {
            conexao.permissaoNegada()
            if (Build.VERSION.SDK_INT >= 37 && !solicitandoPermissao) {
                permissaoInicialSolicitada = true
                acaoAposPermissao = acao
                solicitandoPermissao = true
                solicitarPermissao.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }
    }

    DisposableEffect(donoCicloDeVida, conexao) {
        telaAtiva = true
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> permissaoConcedida = temPermissao()
                Lifecycle.Event.ON_RESUME -> {
                    resumida = true
                    permissaoConcedida = temPermissao()
                    if (permissaoConcedida) {
                        if (!solicitandoPermissao) {
                            val acao = acaoAposPermissao
                            acaoAposPermissao = null
                            if (acao != null) acao() else conexao.iniciar()
                        }
                    } else {
                        conexao.permissaoNegada()
                        if (!permissaoInicialSolicitada) {
                            executarComPermissao { conexao.iniciar() }
                        }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> resumida = false
                Lifecycle.Event.ON_STOP -> {
                    resumida = false
                    acaoAposPermissao = null
                    conexao.pausar()
                }
                else -> Unit
            }
        }
        donoCicloDeVida.lifecycle.addObserver(observador)
        onDispose {
            telaAtiva = false
            resumida = false
            acaoAposPermissao = null
            donoCicloDeVida.lifecycle.removeObserver(observador)
            conexao.pausar()
        }
    }

    LaunchedEffect(sessao) {
        val chaveAtual = sessao?.let { "${it.endereco.urlBase}|${it.revisao}" }
        if (chaveUltimaSessao != chaveAtual) {
            produto.limpar()
            codigoBarra = ""
            leituraAtiva = true
            chaveUltimaSessao = chaveAtual
            if (sessao != null) configuracaoAberta = false
        }
        sessaoPreparada = sessao
    }

    fun podeConsultar(): Boolean {
        if (!temPermissao()) {
            permissaoConcedida = false
            conexao.permissaoNegada()
            return false
        }
        return telaAtiva && resumida && !configuracaoAberta && sessao != null &&
            sessaoPreparada == sessao && conexao.sessao.value == sessao &&
            conexao.estado.value.fase == FaseConexao.CONECTADO
    }

    val sessaoAtiva = sessao != null && sessaoPreparada == sessao &&
        estado.sessao == sessao && estado.fase == FaseConexao.CONECTADO &&
        permissaoConcedida && temPermissao()

    if (!sessaoAtiva || configuracaoAberta) {
        TelaDescobertaServidor(
            estado = estado,
            permissaoConcedida = permissaoConcedida,
            solicitandoPermissao = solicitandoPermissao,
            onProcurarNovamente = { executarComPermissao { conexao.procurarNovamente() } },
            onUsarAutomatico = { executarComPermissao { conexao.usarAutomatico() } },
            onConectarManual = { entrada, porta ->
                executarComPermissao { conexao.conectarManual(entrada, porta) }
            },
            onSelecionar = { opcao -> executarComPermissao { conexao.selecionar(opcao) } },
            onReconectar = { executarComPermissao { conexao.reconectar() } },
            onVoltar = if (sessaoAtiva) ({ configuracaoAberta = false }) else null,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val novaLeitura = {
        codigoBarra = ""
        produto.limpar()
        leituraAtiva = true
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Servidor: ${sessao?.endereco?.urlBase.orEmpty()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                gerenciadorFoco.clearFocus()
                teclado?.hide()
                novaLeitura()
                configuracaoAberta = true
            }) {
                Text("Alterar")
            }
        }

        if (uiState is ProdutoUiState.Sucesso) {
            TelaResultado(
                produto = (uiState as ProdutoUiState.Sucesso).produto,
                onNovaLeitura = novaLeitura,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        } else {
            TelaLeitura(
                codigoBarra = codigoBarra,
                uiState = uiState,
                onCodigoBarraChange = { codigoBarra = it },
                onCodigoDetectado = { codigo ->
                    if (leituraAtiva && podeConsultar()) {
                        leituraAtiva = false
                        codigoBarra = codigo
                        gerenciadorFoco.clearFocus()
                        teclado?.hide()
                        produto.consultarProduto(codigo)
                    }
                },
                onConsultar = {
                    if (podeConsultar()) {
                        gerenciadorFoco.clearFocus()
                        teclado?.hide()
                        leituraAtiva = false
                        produto.consultarProduto(codigoBarra)
                    }
                },
                onNovaLeitura = novaLeitura,
                leituraHabilitada = sessaoAtiva,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

fun formatarPreco(preco: String): String {
    val valor = preco.toBigDecimalOrNull()
    return if (valor != null) {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")).format(valor)
    } else {
        preco
    }
}

@Preview(showBackground = true)
@Composable
fun TelaVerificadorPrecosPreview() {
    VerificadorPrecosTheme {
        TelaVerificadorPrecos()
    }
}
