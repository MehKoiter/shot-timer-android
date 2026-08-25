package com.shottimer.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SIG Sauer-inspired palette: a fixed black + gold/amber "tactical" identity (not a light/dark
 * pair - see Theme.kt), built from SIG's brand research (Selective Yellow #EEAF00, Sandy Brown
 * #F0AD4E, Porsche #EBB773, Mine Shaft #313131) rather than exact hex matches, tuned for outdoor
 * legibility (WCAG-reasonable contrast) on a shot-timer used in bright sunlight at a range.
 */

// --- Gold / amber accents (brand color, used as primary/secondary/tertiary) ---
val SigGoldBright = Color(0xFFF4C430) // primary - pops on near-black
val SigGoldDeep = Color(0xFF8A6400) // deep bronze-gold, inverse-primary
val SigGoldContainerDark = Color(0xFF4A3600) // dark bronze container
val SigGoldOnContainerDark = Color(0xFFFFDE9E) // pale gold text on dark bronze containers

val SigTan = Color(0xFFEBB773) // "Porsche" muted tan-gold, secondary accent
val SigTanContainerDark = Color(0xFF4A3A1F)
val SigTanOnContainerDark = Color(0xFFF5DDBB)

val SigSandyBrown = Color(0xFFF0AD4E) // warm tertiary accent, distinct enough from gold for chips/tags
val SigSandyContainerDark = Color(0xFF4F3300)
val SigSandyOnContainerDark = Color(0xFFFFDCB0)

// --- Near-black / charcoal neutrals (Mine Shaft inspired) ---
val SigCharcoal = Color(0xFF141414) // near-black background
val SigCharcoalElevated = Color(0xFF201F1D) // elevated surface
val SigCharcoalHigh = Color(0xFF2B2926) // high-elevation surface (cards, sheets)
val SigMineShaft = Color(0xFF313131) // mid dark gray, outlines
val SigGraphite = Color(0xFF57534C) // outline-variant / disabled
val SigSteel = Color(0xFFB8B2A6) // muted warm gray, secondary text/icons on dark

// --- Surface container tiers (Material3 1.2+ tonal elevation roles) ---
// Explicitly set so Cards, NavigationBar, TopAppBar, etc. use the SIG warm neutrals instead of
// falling back to Material3's default purple baseline.
val SigSurfaceDimDark = Color(0xFF100F0D)
val SigSurfaceBrightDark = Color(0xFF3A3733)
val SigSurfaceContainerLowestDark = Color(0xFF0B0A09)
val SigSurfaceContainerLowDark = Color(0xFF1C1B18)
val SigSurfaceContainerDark = Color(0xFF201F1C)
val SigSurfaceContainerHighDark = Color(0xFF2B2926)
val SigSurfaceContainerHighestDark = Color(0xFF363330)

// --- Status colors ---
val SigRedDark = Color(0xFFFFB4A9) // stop / error - lighter for contrast on near-black
val SigRedContainerDark = Color(0xFF930909)
val SigOnRedContainerDark = Color(0xFFFFDAD4)
