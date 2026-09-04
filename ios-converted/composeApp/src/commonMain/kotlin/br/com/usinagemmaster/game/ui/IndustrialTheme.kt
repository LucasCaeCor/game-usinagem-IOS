package br.com.usinagemmaster.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta espelhada do Theme.kt Android V24.
val Steel980 = Color(0xFF060A0D)
val Steel950 = Color(0xFF090D11)
val Steel900 = Color(0xFF10171C)
val Steel850 = Color(0xFF162027)
val Steel800 = Color(0xFF1D2931)
val Steel700 = Color(0xFF30414B)
val Steel500 = Color(0xFF657984)
val Steel400 = Color(0xFF91A2AB)
val Steel200 = Color(0xFFBCC8CE)
val Steel100 = Color(0xFFE9F0F3)

val IndustrialAmber = Color(0xFFFFB21A)
val IndustrialYellow = Color(0xFFFFD54F)
val IndustrialOrange = Color(0xFFFF7A1A)
val SuccessGreen = Color(0xFF55D998)
val DangerRed = Color(0xFFFF6868)
val ElectricBlue = Color(0xFF5CC8FF)
val PlasmaCyan = Color(0xFF76E4FF)
val RoyalPurple = Color(0xFF8F70D6)

// Aliases mantidos para não quebrar as telas V7–V9.
val SafetyAmber = IndustrialAmber
val SafetyAmberSoft = Color(0xFFFFD27A)
val ProductionGreen = SuccessGreen

private val IndustrialScheme = darkColorScheme(
    primary = IndustrialAmber,
    onPrimary = Color(0xFF251A00),
    primaryContainer = Color(0xFF4A3400),
    onPrimaryContainer = IndustrialYellow,
    secondary = ElectricBlue,
    onSecondary = Color(0xFF001E2D),
    secondaryContainer = Color(0xFF133647),
    onSecondaryContainer = Color(0xFFC8E9F8),
    tertiary = SuccessGreen,
    onTertiary = Color(0xFF002112),
    tertiaryContainer = Color(0xFF0E4D31),
    onTertiaryContainer = Color(0xFFB9F5D2),
    error = DangerRed,
    background = Steel980,
    onBackground = Steel100,
    surface = Steel900,
    onSurface = Steel100,
    surfaceVariant = Steel800,
    onSurfaceVariant = Steel400,
    outline = Steel500,
)

private val IndustrialTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 37.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Black),
    headlineMedium = TextStyle(fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
)

private val IndustrialShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp),
)

@Composable
fun UsinagemMasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IndustrialScheme,
        typography = IndustrialTypography,
        shapes = IndustrialShapes,
        content = content,
    )
}
