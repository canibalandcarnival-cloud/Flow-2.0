package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class GlassStyle(
    val isDark: Boolean,
    val isFrostedGlassEnabled: Boolean = true,
    val glassBaseColor: Color,
    val barTintAlpha: Float,
    val cardTintAlpha: Float,
    val overlayTintAlpha: Float,
    val solidCardSurface: Color,
    val solidBarSurface: Color,
    val solidOverlaySurface: Color,
    val solidBorderColor: Color,
    val activeSegmentFill: Color,
    val solidActionFill: Color,
    val solidActionContent: Color,
    val contentPrimary: Color,
    val contentSecondary: Color,
    val contentMuted: Color,
    val rimTopLeft: Color,
    val rimBottomRight: Color,
    val topSheen: Color
)

val DarkGlassStyle = GlassStyle(
    isDark = true,
    isFrostedGlassEnabled = true,
    glassBaseColor = PureBlack,
    barTintAlpha = 0.44f,
    cardTintAlpha = 0.40f,
    overlayTintAlpha = 0.62f,
    solidCardSurface = DarkSurfaceContainer,
    solidBarSurface = DarkSurfaceElevated,
    solidOverlaySurface = PureBlack,
    solidBorderColor = PureWhite.copy(alpha = 0.18f),
    activeSegmentFill = PureWhite.copy(alpha = 0.20f),
    solidActionFill = PureWhite,
    solidActionContent = PureBlack,
    contentPrimary = PureWhite,
    contentSecondary = PureWhite.copy(alpha = 0.72f),
    contentMuted = PureWhite.copy(alpha = 0.48f),
    rimTopLeft = PureWhite.copy(alpha = 0.38f),
    rimBottomRight = PureWhite.copy(alpha = 0.10f),
    topSheen = PureWhite.copy(alpha = 0.10f)
)

val LightGlassStyle = GlassStyle(
    isDark = false,
    isFrostedGlassEnabled = true,
    glassBaseColor = PureWhite,
    barTintAlpha = 0.56f,
    cardTintAlpha = 0.54f,
    overlayTintAlpha = 0.66f,
    solidCardSurface = PureWhite,
    solidBarSurface = PureWhite,
    solidOverlaySurface = LightSurfaceElevated,
    solidBorderColor = PureBlack.copy(alpha = 0.14f),
    activeSegmentFill = PureBlack.copy(alpha = 0.12f),
    solidActionFill = PureBlack,
    solidActionContent = PureWhite,
    contentPrimary = PureBlack,
    contentSecondary = PureBlack.copy(alpha = 0.68f),
    contentMuted = PureBlack.copy(alpha = 0.44f),
    rimTopLeft = PureWhite.copy(alpha = 0.85f),
    rimBottomRight = PureBlack.copy(alpha = 0.12f),
    topSheen = PureWhite.copy(alpha = 0.34f)
)

val LocalGlassStyle = staticCompositionLocalOf { DarkGlassStyle }

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = DarkSurfaceContainer,
    onPrimaryContainer = PureWhite,
    secondary = PureWhite,
    onSecondary = PureBlack,
    secondaryContainer = DarkSurfaceContainer,
    onSecondaryContainer = PureWhite,
    tertiary = PureWhite,
    onTertiary = PureBlack,
    background = DarkSurfaceBase,
    onBackground = PureWhite,
    surface = DarkSurfaceBase,
    onSurface = PureWhite,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkOutline,
    outlineVariant = PureWhite.copy(alpha = 0.14f)
)

private val MonochromeLightColorScheme = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = LightSurfaceContainer,
    onPrimaryContainer = PureBlack,
    secondary = PureBlack,
    onSecondary = PureWhite,
    secondaryContainer = LightSurfaceContainer,
    onSecondaryContainer = PureBlack,
    tertiary = PureBlack,
    onTertiary = PureWhite,
    background = LightSurfaceBase,
    onBackground = PureBlack,
    surface = LightSurfaceBase,
    onSurface = PureBlack,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightSecondaryText,
    outline = LightOutline,
    outlineVariant = PureBlack.copy(alpha = 0.10f)
)

val BrowserShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GlassBrowserTheme(
    darkTheme: Boolean,
    frostedGlassEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MonochromeDarkColorScheme else MonochromeLightColorScheme
    val baseGlassStyle = if (darkTheme) DarkGlassStyle else LightGlassStyle
    val glassStyle = remember(baseGlassStyle, frostedGlassEnabled) {
        baseGlassStyle.copy(isFrostedGlassEnabled = frostedGlassEnabled)
    }

    CompositionLocalProvider(LocalGlassStyle provides glassStyle) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BrowserTypography,
            shapes = BrowserShapes,
            content = content
        )
    }
}
