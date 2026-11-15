package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SonaWaneColorScheme = darkColorScheme(
    primary = RoyalGold,
    onPrimary = DeepBlack,
    primaryContainer = DarkGold,
    onPrimaryContainer = LightGold,
    secondary = AmberOrange,
    onSecondary = DeepBlack,
    secondaryContainer = SoftOrange,
    onSecondaryContainer = LightGold,
    tertiary = SilverAccent,
    onTertiary = DeepBlack,
    background = DeepBlack,
    onBackground = PureWhite,
    surface = CharcoalBlack,
    onSurface = PureWhite,
    surfaceVariant = ObsidianCard,
    onSurfaceVariant = SecondarySilver,
    surfaceContainerLow = CharcoalBlack,
    surfaceContainer = ObsidianCard,
    surfaceContainerHigh = ElevatedObsidian,
    outline = GoldBorderSubtle,
    outlineVariant = MutedGrey,
    error = ErrorRed,
    onError = PureWhite,
)

private val SonaWaneShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SonaWaneGoldTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = SonaWaneColorScheme,
        typography = SonaWaneTypography,
        shapes = SonaWaneShapes,
        content = content,
    )
}
