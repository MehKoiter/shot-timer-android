package com.shottimer.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
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
    onBackground = Color(0xFFEAE6DD),
    surface = SigCharcoalElevated,
    onSurface = Color(0xFFEAE6DD),
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

private val LightColorScheme = lightColorScheme(
    // Bright, saturated gold with dark text (not a darkened bronze with
    // white text) so the primary color reads as unmistakably "gold" rather
    // than muted brown, while keeping strong contrast for the on-color text.
    primary = SigGold,
    onPrimary = SigInkBlack,
    primaryContainer = Color(0xFFFFDDA1),
    onPrimaryContainer = SigGoldOnContainerLight,
    secondary = SigTanDeep,
    onSecondary = Color.White,
    secondaryContainer = SigTanContainerLight,
    onSecondaryContainer = SigTanOnContainerLight,
    tertiary = SigSandyBrownDeep,
    onTertiary = Color.White,
    tertiaryContainer = SigSandyContainerLight,
    onTertiaryContainer = SigSandyOnContainerLight,
    error = SigRed,
    onError = Color.White,
    errorContainer = SigRedContainerLight,
    onErrorContainer = SigOnRedContainerLight,
    background = SigOffWhite,
    onBackground = SigInkBlack,
    surface = SigWarmWhite,
    onSurface = SigInkBlack,
    surfaceVariant = SigLightGray,
    onSurfaceVariant = Color(0xFF4E4A40),
    outline = SigBorderGray,
    outlineVariant = Color(0xFFE4DFD2),
    inverseSurface = SigInkBlack,
    inverseOnSurface = SigOffWhite,
    inversePrimary = SigGoldBright,
    surfaceTint = SigGold,
    scrim = Color.Black,
    surfaceDim = SigSurfaceDimLight,
    surfaceBright = SigSurfaceBrightLight,
    surfaceContainerLowest = SigSurfaceContainerLowestLight,
    surfaceContainerLow = SigSurfaceContainerLowLight,
    surfaceContainer = SigSurfaceContainerLight,
    surfaceContainerHigh = SigSurfaceContainerHighLight,
    surfaceContainerHighest = SigSurfaceContainerHighestLight
)

@Composable
fun ShotTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Defaults to false: this app has a deliberate SIG Sauer-inspired brand
    // palette (black + gold/amber). Material You dynamic color would derive
    // colors from the device wallpaper on Android 12+ and silently override
    // that brand identity, so dynamic color must be opted into explicitly.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
