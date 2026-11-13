
package com.tcc.verificadorprecos.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tcc.verificadorprecos.camera.CameraPreview
import com.tcc.verificadorprecos.ui.components.AssinaturaATL
import com.tcc.verificadorprecos.viewmodel.ProdutoUiState

@Composable
fun TelaLeitura(
    codigoBarra: String,
    uiState: ProdutoUiState,
    onCodigoBarraChange: (String) -> Unit,
    onCodigoDetectado: (String) -> Unit,
    onConsultar: () -> Unit,
    onNovaLeitura: () -> Unit,
    modifier: Modifier = Modifier,
    leituraHabilitada: Boolean = true
) {

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "Verificador de Preços",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Aponte a câmera para o código de barras",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )


        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {

                if (leituraHabilitada && uiState == ProdutoUiState.Inicial && !LocalInspectionMode.current) {

                    // Câmera ativa somente no estado inicial
                    CameraPreview(
                        onCodigoDetectado = onCodigoDetectado,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Moldura para orientar o posicionamento
                    Box(
                        modifier = Modifier
                            .size(
                                width = 250.dp,
                                height = 110.dp
                            )
                            .border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            )
                    )

                } else {

                    // Mensagem apresentada durante a pausa
                    Text(
                        text = if (uiState == ProdutoUiState.Carregando) {
                            "Consulta em andamento..."
                        } else {
                            "Leitura pausada"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }


        when (uiState) {

            ProdutoUiState.Inicial -> {
                Text(
                    text = "Posicione o código dentro da área indicada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is ProdutoUiState.Erro -> {
                Text(
                    text = "Para utilizar a câmera novamente, toque em Nova leitura.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> Unit
        }


        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = codigoBarra,
            onValueChange = onCodigoBarraChange,
            label = {
                Text("Código de barras")
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            enabled = leituraHabilitada && uiState !is ProdutoUiState.Carregando,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = onConsultar,
            enabled = leituraHabilitada && uiState !is ProdutoUiState.Carregando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Consultar manualmente")
        }

        when (uiState) {

            ProdutoUiState.Inicial -> Unit

            ProdutoUiState.Carregando -> {

                CircularProgressIndicator()

                Text("Consultando produto...")
            }

            is ProdutoUiState.Erro -> {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        Text(
                            text = uiState.mensagem,
                            color = MaterialTheme.colorScheme.error
                        )

                        OutlinedButton(
                            onClick = onNovaLeitura,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Nova leitura")
                        }
                    }
                }
            }

            is ProdutoUiState.Sucesso -> Unit
        }

        AssinaturaATL()
    }
}
