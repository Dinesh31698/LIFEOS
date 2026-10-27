package com.lifeos.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ============================================================================
// LIFEOS Design System: Core Palette & Theme Tokens
// Supports Cybernetic Dark, AMOLED Pitch Black, and Frosted Light Glass
// ============================================================================

// Vibrant Cybernetic Accents (Consistent across all themes)
val PrimaryPurple = Color(0xFF6C5CE7)
val PurpleLight = Color(0xFFA29BFE)
val CyanAccent = Color(0xFF00CEC9)
val SuccessGreen = Color(0xFF00B894)
val WarningAmber = Color(0xFFFDCB6E)
val AlertRed = Color(0xFFFF7675)
val GhostPurple = Color(0xFF8C7AE6)

// Contextual Mode Accent Colors (Consistent across all themes)
val ContextCollege = Color(0xFF00E5FF) // Cyan / Blue
val ContextStudy = Color(0xFF00BFA5)   // Teal / Green
val ContextGym = Color(0xFFFF9100)     // Amber-Orange
val ContextSports = Color(0xFFFF5252)   // Coral-Red
val ContextTravel = Color(0xFF7C4DFF)   // Electric Purple
val ContextWork = Color(0xFFFFAB00)     // Amber Gold
val ContextHome = Color(0xFFF06292)     // Soft Rose Pink
val ContextCustom = Color(0xFF26C6DA)   // Turquoise

// Aliases for Accent Colors
val LifeOsAccentViolet = PrimaryPurple
val LifeOsAccentPurple = PurpleLight
val LifeOsAccentCyan = CyanAccent
val LifeOsSuccessGreen = SuccessGreen
val LifeOsAlertRed = AlertRed
val LifeOsWarningAmber = WarningAmber
val LifeOsGhostPurple = GhostPurple

// Legacy Material3 Tokens
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// ============================================================================
// Palette Data Architecture
// ============================================================================

data class LifeOsColorPalette(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val border: Color,
    val borderLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val glassBackground: Color,
    val glassSurface: Color,
    val glassCard: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val primary: Color = PrimaryPurple,
    val primaryLight: Color = PurpleLight,
    val cyanAccent: Color = CyanAccent,
    val success: Color = SuccessGreen,
    val warning: Color = WarningAmber,
    val alert: Color = AlertRed,
    val isDark: Boolean = true
)

// Standard Cybernetic Dark Palette
val DarkColorPalette = LifeOsColorPalette(
    background = Color(0xFF0B0E14),
    surface = Color(0xFF161B26),
    surfaceElevated = Color(0xFF1E2638),
    surfaceHighlight = Color(0xFF263045),
    border = Color(0xFF283248),
    borderLight = Color(0xFF384560),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0AEC0),
    textMuted = Color(0xFF64748B),
    glassBackground = Color(0xCC0B0E14),
    glassSurface = Color(0xB8161B26),
    glassCard = Color(0xCC1E2638),
    glassBorder = Color(0x33FFFFFF),
    glassHighlight = Color(0x26FFFFFF),
    isDark = true
)

// AMOLED Pitch Black Palette (Maximum OLED power savings)
val AmoledColorPalette = LifeOsColorPalette(
    background = Color(0xFF000000), // Pure Pitch Black
    surface = Color(0xFF05080E),
    surfaceElevated = Color(0xFF0C111C),
    surfaceHighlight = Color(0xFF151D2D),
    border = Color(0xFF1F293D),
    borderLight = Color(0xFF2E3D59),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFA0AEC0),
    textMuted = Color(0xFF64748B),
    glassBackground = Color(0xE6000000),
    glassSurface = Color(0xD905080E),
    glassCard = Color(0xE60C111C),
    glassBorder = Color(0x33FFFFFF),
    glassHighlight = Color(0x26FFFFFF),
    isDark = true
)

// Frosted Light Glass Palette
val LightColorPalette = LifeOsColorPalette(
    background = Color(0xFFF6F8FC), // Soft clean light neutral
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F5F9), // Slate 100
    surfaceHighlight = Color(0xFFE2E8F0), // Slate 200
    border = Color(0xFFCBD5E1), // Slate 300
    borderLight = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A), // Slate 900
    textSecondary = Color(0xFF475569), // Slate 600
    textMuted = Color(0xFF94A3B8), // Slate 400
    glassBackground = Color(0xEEF6F8FC),
    glassSurface = Color(0xEEFFFFFF),
    glassCard = Color(0xCCFFFFFF), // Crisp frosted white
    glassBorder = Color(0x20000000), // Dark specular border
    glassHighlight = Color(0x14000000),
    isDark = false
)

// Composition Local for Dynamic LIFEOS Colors
val LocalLifeOsColors = staticCompositionLocalOf { DarkColorPalette }

object LifeOsTheme {
    val colors: LifeOsColorPalette
        @Composable
        get() = LocalLifeOsColors.current
}

// ============================================================================
// Dynamic Composable Color Accessors
// These automatically adapt to Dark, AMOLED, and Light themes across the entire app
// ============================================================================

val BackgroundDark: Color
    @Composable
    get() = LocalLifeOsColors.current.background

val SurfaceDark: Color
    @Composable
    get() = LocalLifeOsColors.current.surface

val CardDark: Color
    @Composable
    get() = LocalLifeOsColors.current.surfaceElevated

val BorderDark: Color
    @Composable
    get() = LocalLifeOsColors.current.border

val BorderDarkLight: Color
    @Composable
    get() = LocalLifeOsColors.current.borderLight

val TextPrimary: Color
    @Composable
    get() = LocalLifeOsColors.current.textPrimary

val TextSecondary: Color
    @Composable
    get() = LocalLifeOsColors.current.textSecondary

val TextMuted: Color
    @Composable
    get() = LocalLifeOsColors.current.textMuted

// Backwards-Compatible Aliases
val LifeOsBackground: Color
    @Composable
    get() = LocalLifeOsColors.current.background

val LifeOsSurface: Color
    @Composable
    get() = LocalLifeOsColors.current.surface

val LifeOsSurfaceElevated: Color
    @Composable
    get() = LocalLifeOsColors.current.surfaceElevated

val LifeOsSurfaceHighlight: Color
    @Composable
    get() = LocalLifeOsColors.current.surfaceHighlight

val LifeOsBorder: Color
    @Composable
    get() = LocalLifeOsColors.current.border

val LifeOsBorderLight: Color
    @Composable
    get() = LocalLifeOsColors.current.borderLight

val LifeOsTextPrimary: Color
    @Composable
    get() = LocalLifeOsColors.current.textPrimary

val LifeOsTextSecondary: Color
    @Composable
    get() = LocalLifeOsColors.current.textSecondary

val LifeOsTextMuted: Color
    @Composable
    get() = LocalLifeOsColors.current.textMuted

// Glass Effect Tokens
val GlassBackground: Color
    @Composable
    get() = LocalLifeOsColors.current.glassBackground

val GlassSurfaceDark: Color
    @Composable
    get() = LocalLifeOsColors.current.glassSurface

val GlassCardDark: Color
    @Composable
    get() = LocalLifeOsColors.current.glassCard

val GlassHighlight: Color
    @Composable
    get() = LocalLifeOsColors.current.glassHighlight

val GlassBorder: Color
    @Composable
    get() = LocalLifeOsColors.current.glassBorder