package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = PureWhite,
    primaryContainer = NavyContainer,
    onPrimaryContainer = OnNavyContainer,
    secondary = EmeraldSecondary,
    onSecondary = PureWhite,
    secondaryContainer = EmeraldContainer,
    onSecondaryContainer = OnEmeraldContainer,
    tertiary = SkyAccent,
    onTertiary = PureWhite,
    tertiaryContainer = SkyContainer,
    onTertiaryContainer = OnSkyContainer,
    background = SlateBackground,
    onBackground = InkPrimary,
    surface = PureWhite,
    onSurface = InkPrimary,
    surfaceVariant = SlateSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = SlateOutline,
    outlineVariant = SlateOutlineVariant,
    error = StatusOverdueFg,
    onError = PureWhite,
    errorContainer = StatusOverdueBg,
    onErrorContainer = StatusOverdueFg
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF061A33),
    primaryContainer = NavyPrimaryLight,
    onPrimaryContainer = Color(0xFFDCE8F8),
    secondary = EmeraldVibrant,
    onSecondary = Color(0xFF022C22),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFF38BDF8),
    onTertiary = Color(0xFF082F49),
    background = DarkNavyBackground,
    onBackground = DarkTextPrimary,
    surface = DarkNavySurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkNavySurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF223554),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

val SmartTrackerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun SmartTrackerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = SmartTrackerTypography,
        shapes = SmartTrackerShapes,
        content = content
    )
}
