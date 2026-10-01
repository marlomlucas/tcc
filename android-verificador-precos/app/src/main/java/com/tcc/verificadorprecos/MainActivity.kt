package com.tcc.verificadorprecos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tcc.verificadorprecos.ui.theme.VerificadorPrecosTheme
import com.tcc.verificadorprecos.viewmodel.ProdutoUiState
import com.tcc.verificadorprecos.viewmodel.ProdutoViewModel

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

    var codigoBarra by remember {
        mutableStateOf("")
    }

    val uiState by produtoViewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Text(
            text = "Verificador de Preços",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        OutlinedTextField(
            value = codigoBarra,
            onValueChange = {
                codigoBarra = it
            },
            label = {
                Text("Código de barras")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Button(
            onClick = {
                produtoViewModel.consultarProduto(codigoBarra)
            },
            enabled = uiState !is ProdutoUiState.Carregando,
            modifier = Modifier.fillMaxWidth()
        ) {

            Text("Consultar")
        }

        Spacer(
            modifier = Modifier.height(32.dp)
        )

        HorizontalDivider()

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        when (val estado = uiState) {

            ProdutoUiState.Inicial -> {

                Text(
                    text = "Produto:",
                    fontWeight = FontWeight.Bold
                )

                Text("-")

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "Descrição:",
                    fontWeight = FontWeight.Bold
                )

                Text("-")

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "Preço:",
                    fontWeight = FontWeight.Bold
                )

                Text("-")
            }

            ProdutoUiState.Carregando -> {

                CircularProgressIndicator()

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text("Consultando produto...")
            }

            is ProdutoUiState.Sucesso -> {

                val produto = estado.produto

                Text(
                    text = "Produto:",
                    fontWeight = FontWeight.Bold
                )

                Text(produto.nome)

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "Descrição:",
                    fontWeight = FontWeight.Bold
                )

                Text(produto.descricao)

                Spacer(
                    modifier = Modifier.height(16.dp)
                )

                Text(
                    text = "Preço:",
                    fontWeight = FontWeight.Bold
                )

                Text("R$ ${produto.preco}")
            }

            is ProdutoUiState.Erro -> {

                Text(
                    text = estado.mensagem,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TelaVerificadorPrecosPreview() {

    VerificadorPrecosTheme {
        TelaVerificadorPrecos()
    }
}