package com.shottimer.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

val Typography = Typography(
    // The hero timer clock. tnum (tabular numerals) makes every digit the same width so the
    // running time doesn't shift horizontally as digits change - proportional "1"s are narrower,
    // which made the clock jiggle at 100 Hz.
    displayLarge = Default.displayLarge.copy(
        fontFeatureSettings = "tnum",
        fontWeight = FontWeight.Medium
    ),
    // Also used for digits that line up in columns (run list elapsed times, shooter stats).
    titleMedium = Default.titleMedium.copy(fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
