package com.ralenday.memoflash.mobile.tema

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta propia y sobria (índigo suave sobre grises cálidos). No se usa el color dinámico del
 * sistema para que la app se vea igual en todos los teléfonos.
 */
private val Claro = lightColorScheme(
    primary = Color(0xFF4A55C8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1E3FF),
    onPrimaryContainer = Color(0xFF16206E),
    secondary = Color(0xFF5B5E72),
    secondaryContainer = Color(0xFFE2E2F2),
    onSecondaryContainer = Color(0xFF181B2C),
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFAFAFC),
    onSurface = Color(0xFF1B1B21),
    onSurfaceVariant = Color(0xFF5E5E6B),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F4F8),
    surfaceContainer = Color(0xFFEEEEF3),
    surfaceContainerHigh = Color(0xFFE8E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
    outline = Color(0xFF8A8A97),
    outlineVariant = Color(0xFFDCDCE4),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF1E2A8F),
    primaryContainer = Color(0xFF333FAF),
    onPrimaryContainer = Color(0xFFE1E3FF),
    secondary = Color(0xFFC4C5DD),
    secondaryContainer = Color(0xFF2E3042),
    onSecondaryContainer = Color(0xFFE2E2F2),
    background = Color(0xFF121317),
    onBackground = Color(0xFFE4E4EA),
    surface = Color(0xFF121317),
    onSurface = Color(0xFFE4E4EA),
    onSurfaceVariant = Color(0xFFA9A9B6),
    surfaceContainerLowest = Color(0xFF0D0E11),
    surfaceContainerLow = Color(0xFF1A1B20),
    surfaceContainer = Color(0xFF1F2025),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34353B),
    outline = Color(0xFF8E8E9B),
    outlineVariant = Color(0xFF3A3B43),
)

private val Tipografia = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val Formas = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Colores de las respuestas (los mismos tonos que en el reloj, pero más suaves). */
object ColoresRespuesta {
    val dificil = Color(0xFFD9534F)
    val regular = Color(0xFFD08A00)
    val facil = Color(0xFF2E9461)
}

@Composable
fun TemaMemoFlash(contenido: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro,
        typography = Tipografia,
        shapes = Formas,
        content = contenido,
    )
}
