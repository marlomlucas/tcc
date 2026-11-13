package com.tcc.verificadorprecos.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Assinatura transparente. O container fornece as margens externas.
 * O texto pode quebrar linhas, sem altura fixa ou reducao da fonte acessivel.
 */
@Composable
fun AssinaturaATL(modifier: Modifier = Modifier) {
    val formas = remember { criarSimboloATL() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Decorativo: o texto ao lado ja identifica a autoria para acessibilidade.
        Canvas(modifier = Modifier.size(width = 32.dp, height = 22.dp)) {
            val escala = min(size.width / 164f, size.height / 100f)
            val deslocamentoX = (size.width - 164f * escala) / 2f
            val deslocamentoY = (size.height - 100f * escala) / 2f
            val degrade = Brush.linearGradient(
                0f to Color(0xFF096638),
                0.60f to Color(0xFF57A620),
                1f to Color(0xFF8BC624),
                start = Offset.Zero,
                end = Offset(164f, 0f)
            )

            withTransform({
                translate(deslocamentoX, deslocamentoY)
                scale(escala, escala, pivot = Offset.Zero)
            }) {
                formas.forEach { forma -> drawPath(forma, brush = degrade) }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Desenvolvido por ATL Software",
            modifier = Modifier.weight(1f, fill = false),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.End,
            softWrap = true
        )
    }
}

private fun criarSimboloATL(): List<Path> = listOf(
    Path().apply {
        moveTo(49f, 15f)
        lineTo(49f, 28f)
        lineTo(15f, 49f)
        lineTo(49f, 71f)
        lineTo(49f, 85f)
        lineTo(4f, 55f)
        quadraticTo(0f, 52f, 0f, 49f)
        quadraticTo(0f, 46f, 4f, 43f)
        close()
    },
    Path().apply {
        moveTo(96f, 0f)
        lineTo(106f, 0f)
        lineTo(70f, 100f)
        lineTo(59f, 100f)
        close()
    },
    Path().apply {
        moveTo(115f, 15f)
        lineTo(160f, 44f)
        quadraticTo(164f, 46f, 164f, 49f)
        quadraticTo(164f, 52f, 160f, 55f)
        lineTo(115f, 85f)
        lineTo(115f, 72f)
        lineTo(149f, 50f)
        lineTo(115f, 29f)
        close()
    }
)

@Preview(name = "Claro", showBackground = true, backgroundColor = 0xFFFFFFFF, widthDp = 320)
@Composable
private fun AssinaturaATLPreviewClaro() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        AssinaturaATL(modifier = Modifier.padding(16.dp))
    }
}

@Preview(
    name = "Escuro",
    showBackground = true,
    backgroundColor = 0xFF121212,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    widthDp = 320
)
@Composable
private fun AssinaturaATLPreviewEscuro() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        AssinaturaATL(modifier = Modifier.padding(16.dp))
    }
}

@Preview(
    name = "Fonte ampliada / tela estreita",
    showBackground = true,
    backgroundColor = 0xFFFFFFFF,
    widthDp = 240,
    fontScale = 2f
)
@Composable
private fun AssinaturaATLPreviewFonteAmpliada() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        AssinaturaATL(modifier = Modifier.padding(16.dp))
    }
}
