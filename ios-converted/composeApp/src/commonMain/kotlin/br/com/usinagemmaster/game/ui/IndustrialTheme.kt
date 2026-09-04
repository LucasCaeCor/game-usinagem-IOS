package br.com.usinagemmaster.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Steel950 = Color(0xFF0B1115)
val Steel900 = Color(0xFF111A1F)
val Steel850 = Color(0xFF172229)
val Steel800 = Color(0xFF1D2A31)
val Steel700 = Color(0xFF2A3942)
val Steel500 = Color(0xFF61727C)
val Steel200 = Color(0xFFBCC8CE)
val SafetyAmber = Color(0xFFFFB21A)
val SafetyAmberSoft = Color(0xFFFFD27A)
val ProductionGreen = Color(0xFF49C47B)
val ElectricBlue = Color(0xFF58A6E7)
val DangerRed = Color(0xFFE65C5C)
val RoyalPurple = Color(0xFF8F70D6)

private val IndustrialScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = Color(0xFF271900),
    primaryContainer = Color(0xFF4B3500),
    onPrimaryContainer = Color(0xFFFFDEA0),
    secondary = ElectricBlue,
    onSecondary = Color(0xFF001D32),
    secondaryContainer = Color(0xFF153A52),
    onSecondaryContainer = Color(0xFFCDE8FF),
    tertiary = ProductionGreen,
    onTertiary = Color(0xFF00210E),
    tertiaryContainer = Color(0xFF0F4C2B),
    onTertiaryContainer = Color(0xFFB9F4CD),
    error = DangerRed,
    background = Steel950,
    onBackground = Color(0xFFE3EBEF),
    surface = Steel900,
    onSurface = Color(0xFFE3EBEF),
    surfaceVariant = Steel800,
    onSurfaceVariant = Steel200,
    outline = Steel500,
)

@Composable
fun UsinagemMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialScheme,
        content = content,
    )
}
