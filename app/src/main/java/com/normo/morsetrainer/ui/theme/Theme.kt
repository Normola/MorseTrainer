package com.normo.morsetrainer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette lifted from the card itself: black anodised plate, brass pads, cream edge. */
object CardColors {
    val Black = Color(0xFF141210)
    val PlateTop = Color(0xFF1E1A17)
    val PlateBottom = Color(0xFF0E0C0B)
    val Brass = Color(0xFFC6A664)
    val BrassBright = Color(0xFFF0D89A)
    val BrassDim = Color(0xFF6B5A35)
    val Cream = Color(0xFFEDE4CC)
    val CreamDim = Color(0xFF8A836F)
    val Trace = Color(0xFF9A9384)
    val Live = Color(0xFF7FE0A8)
    val Wrong = Color(0xFFE07F7F)
}

private val DarkScheme = darkColorScheme(
    primary = CardColors.Brass,
    onPrimary = CardColors.Black,
    primaryContainer = CardColors.BrassDim,
    onPrimaryContainer = CardColors.Cream,
    secondary = CardColors.Cream,
    onSecondary = CardColors.Black,
    background = CardColors.Black,
    onBackground = CardColors.Cream,
    surface = CardColors.PlateTop,
    onSurface = CardColors.Cream,
    surfaceVariant = Color(0xFF2A2521),
    onSurfaceVariant = CardColors.CreamDim,
    outline = CardColors.BrassDim,
    error = CardColors.Wrong,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF7A6120),
    onPrimary = Color.White,
    secondary = Color(0xFF4E4636),
    background = Color(0xFFF7F1E4),
    onBackground = Color(0xFF1D1B16),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF1D1B16),
    outline = Color(0xFF7F7667),
)

/**
 * The chart is drawn in card colours regardless of scheme, so the app is dark by
 * default — a bright surround makes the black plate look wrong.
 */
@Composable
fun MorseTrainerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
