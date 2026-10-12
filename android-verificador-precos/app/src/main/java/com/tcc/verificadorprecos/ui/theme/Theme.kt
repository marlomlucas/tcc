package com.tcc.verificadorprecos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VerificadorColorScheme = lightColorScheme(
    primary = VerdePrincipal,
    onPrimary = Branco,

    primaryContainer = VerdeClaro,
    onPrimaryContainer = VerdeEscuro,

    secondary = VerdeEscuro,
    onSecondary = Branco,

    background = FundoClaro,
    onBackground = TextoEscuro,

    surface = Branco,
    onSurface = TextoEscuro,

    surfaceContainerLowest = Branco,
    surfaceContainerLow = Color(0xFFF2F6F2),
    surfaceContainer = Color(0xFFEDF3ED),
    surfaceContainerHigh = Color(0xFFE7EEE7),
    surfaceContainerHighest = Color(0xFFDDE8DE),

    outline = Color(0xFF738477),
    outlineVariant = Color(0xFFD1DDD3),


    surfaceVariant = VerdeClaro,
    onSurfaceVariant = VerdeEscuro,

    error = Color(0xFFB3261E),
    onError = Branco
)

@Composable
fun VerificadorPrecosTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VerificadorColorScheme,
        typography = Typography,
        content = content
    )
}
