package com.shottimer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SIG Sauer-inspired palette: a black + gold/amber "tactical" identity,
 * built from SIG's brand research (Selective Yellow #EEAF00, Sandy Brown
 * #F0AD4E, Porsche #EBB773, Mine Shaft #313131) rather than exact hex
 * matches, tuned for outdoor legibility (WCAG-reasonable contrast) on a
 * shot-timer used in bright sunlight at a range.
 */

// --- Gold / amber accents (brand color, used as primary/secondary/tertiary) ---
val SigGold = Color(0xFFE3A712) // core brand gold, used as light-theme primary
val SigGoldBright = Color(0xFFF4C430) // brighter gold, used as dark-theme primary (pops on near-black)
val SigGoldDeep = Color(0xFF8A6400) // deep bronze-gold, for containers on light surfaces
val SigGoldContainerDark = Color(0xFF4A3600) // dark bronze container, for containers on dark surfaces
val SigGoldOnContainerLight = Color(0xFF2A1D00) // near-black gold-tinted text on light gold containers
val SigGoldOnContainerDark = Color(0xFFFFDE9E) // pale gold text on dark bronze containers

val SigTan = Color(0xFFEBB773) // "Porsche" muted tan-gold, secondary accent
val SigTanDeep = Color(0xFF6B4A1E)
val SigTanContainerLight = Color(0xFFF3DEC0)
val SigTanContainerDark = Color(0xFF4A3A1F)
val SigTanOnContainerLight = Color(0xFF2E2000)
val SigTanOnContainerDark = Color(0xFFF5DDBB)

val SigSandyBrown = Color(0xFFF0AD4E) // warm tertiary accent, distinct enough from gold for chips/tags
val SigSandyBrownDeep = Color(0xFF7A4A00)
val SigSandyContainerLight = Color(0xFFFFDCB0)
val SigSandyContainerDark = Color(0xFF4F3300)
val SigSandyOnContainerLight = Color(0xFF2A1800)
val SigSandyOnContainerDark = Color(0xFFFFDCB0)

// --- Near-black / charcoal neutrals (Mine Shaft inspired) ---
val SigCharcoal = Color(0xFF141414) // near-black background, dark theme
val SigCharcoalElevated = Color(0xFF201F1D) // elevated surface, dark theme
val SigCharcoalHigh = Color(0xFF2B2926) // high-elevation surface (cards, sheets), dark theme
val SigMineShaft = Color(0xFF313131) // mid dark gray, outlines in dark theme
val SigGraphite = Color(0xFF57534C) // outline-variant / disabled, dark theme
val SigSteel = Color(0xFFB8B2A6) // muted warm gray text/icons on dark

// --- Light neutrals (light theme) ---
val SigOffWhite = Color(0xFFFDFBF6) // background, light theme
val SigWarmWhite = Color(0xFFF7F3EA) // elevated surface, light theme
val SigLightGray = Color(0xFFEFEAE0) // high-elevation surface / surface-variant, light theme
val SigBorderGray = Color(0xFFD8D2C4) // outline, light theme
val SigInkBlack = Color(0xFF1C1B18) // primary text on light backgrounds

// --- Surface container tiers (Material3 1.2+ tonal elevation roles) ---
// Explicitly set so Cards, NavigationBar, TopAppBar, etc. use the SIG warm
// neutrals instead of falling back to Material3's default purple baseline.
val SigSurfaceDimDark = Color(0xFF100F0D)
val SigSurfaceBrightDark = Color(0xFF3A3733)
val SigSurfaceContainerLowestDark = Color(0xFF0B0A09)
val SigSurfaceContainerLowDark = Color(0xFF1C1B18)
val SigSurfaceContainerDark = Color(0xFF201F1C)
val SigSurfaceContainerHighDark = Color(0xFF2B2926)
val SigSurfaceContainerHighestDark = Color(0xFF363330)

val SigSurfaceDimLight = Color(0xFFDED8C7)
val SigSurfaceBrightLight = Color(0xFFFDFBF6)
val SigSurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SigSurfaceContainerLowLight = Color(0xFFF9F5EC)
val SigSurfaceContainerLight = Color(0xFFF3EEDF)
val SigSurfaceContainerHighLight = Color(0xFFEDE7D6)
val SigSurfaceContainerHighestLight = Color(0xFFE7E0CE)

// --- Status colors ---
val SigRed = Color(0xFFBA1B1B) // stop / error, light theme
val SigRedDark = Color(0xFFFFB4A9) // stop / error, dark theme (lighter for contrast on near-black)
val SigRedContainerLight = Color(0xFFFFDAD4)
val SigRedContainerDark = Color(0xFF930909)
val SigOnRedContainerLight = Color(0xFF410001)
val SigOnRedContainerDark = Color(0xFFFFDAD4)
