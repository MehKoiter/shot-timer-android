package com.shottimer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fixed brand identity, not a light/dark pair - this app always looks like this, regardless of
// the device's system theme or wallpaper (see ShotTimerTheme below). Black background, gold
// buttons/accents, white primary text/numbers, per explicit direction - a shot timer used
// outdoors at a range benefits from one deliberate high-contrast look more than from following
// whatever the phone's system theme happens to be set to.
private val SigColorScheme = darkColorScheme(
    primary = SigGoldBright,
    onPrimary = SigCharcoal,
    primaryContainer = SigGoldContainerDark,
    onPrimaryContainer = SigGoldOnContainerDark,
    secondary = SigTan,
    onSecondary = SigCharcoal,
    secondaryContainer = SigTanContainerDark,
    onSecondaryContainer = SigTanOnContainerDark,
    tertiary = SigSandyBrown,
    onTertiary = SigCharcoal,
    tertiaryContainer = SigSandyContainerDark,
    onTertiaryContainer = SigSandyOnContainerDark,
    error = SigRedDark,
    onError = Color(0xFF680003),
    errorContainer = SigRedContainerDark,
    onErrorContainer = SigOnRedContainerDark,
    background = SigCharcoal,
    onBackground = Color.White,
    surface = SigCharcoalElevated,
    onSurface = Color.White,
    surfaceVariant = SigCharcoalHigh,
    onSurfaceVariant = SigSteel,
    outline = SigGraphite,
    outlineVariant = SigMineShaft,
    inverseSurface = Color(0xFFEAE6DD),
    inverseOnSurface = SigCharcoal,
    inversePrimary = SigGoldDeep,
    surfaceTint = SigGoldBright,
    scrim = Color.Black,
    surfaceDim = SigSurfaceDimDark,
    surfaceBright = SigSurfaceBrightDark,
    surfaceContainerLowest = SigSurfaceContainerLowestDark,
    surfaceContainerLow = SigSurfaceContainerLowDark,
    surfaceContainer = SigSurfaceContainerDark,
    surfaceContainerHigh = SigSurfaceContainerHighDark,
    surfaceContainerHighest = SigSurfaceContainerHighestDark
)

@Composable
fun ShotTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SigColorScheme,
        typography = Typography,
        content = content
    )
}
