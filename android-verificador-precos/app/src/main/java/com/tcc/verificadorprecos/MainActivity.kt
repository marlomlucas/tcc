package com.tcc.verificadorprecos


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
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

        setContent {
            VerificadorPrecosTheme {

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    TelaVerificadorPrecos()
                }
            }
        }
    }
}


@Composable
fun TelaVerificadorPrecos(
    produtoViewModel: ProdutoViewModel = viewModel()
) {

    var codigoBarra by rememberSaveable {
        mutableStateOf("")
    }

    var leituraAtiva by remember {
        mutableStateOf(true)
    }

    var mostrarDiagnostico by remember {
        mutableStateOf(false)
    }

    val uiState by produtoViewModel.uiState.collectAsState()

    val teclado = LocalSoftwareKeyboardController.current
    val gerenciadorFoco = LocalFocusManager.current

    if (mostrarDiagnostico) {
        TelaDescobertaServidor(
            onVoltar = { mostrarDiagnostico = false },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    // Quando encontrar um produto, mostra a tela de resultado.
    if (uiState is ProdutoUiState.Sucesso) {

        val produto = (uiState as ProdutoUiState.Sucesso).produto

        TelaResultado(
            produto = produto,
            onNovaLeitura = {
                codigoBarra = ""
                produtoViewModel.limpar()
                leituraAtiva = true
            },
            modifier = Modifier.fillMaxSize()
        )

        return
    }

    // Nos demais estados, mostra a tela de leitura.
    TelaLeitura(
        codigoBarra = codigoBarra,
        uiState = uiState,

        onCodigoBarraChange = { novoCodigo ->
            codigoBarra = novoCodigo
        },

        onCodigoDetectado = { codigo ->

            if (leituraAtiva && !mostrarDiagnostico) {

                leituraAtiva = false
                codigoBarra = codigo

                gerenciadorFoco.clearFocus()
                teclado?.hide()

                produtoViewModel.consultarProduto(codigo)
            }
        },

        onConsultar = {

            gerenciadorFoco.clearFocus()
            teclado?.hide()

            leituraAtiva = false

            produtoViewModel.consultarProduto(codigoBarra)
        },

        onNovaLeitura = {
            codigoBarra = ""
            produtoViewModel.limpar()
            leituraAtiva = true
        },

        onProcurarServidor = {
            gerenciadorFoco.clearFocus()
            teclado?.hide()
            mostrarDiagnostico = true
        },

        modifier = Modifier.fillMaxSize()
    )
}


fun formatarPreco(preco: String): String {

    val valor = preco.toBigDecimalOrNull()

    return if (valor != null) {

        NumberFormat
            .getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
            .format(valor)

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