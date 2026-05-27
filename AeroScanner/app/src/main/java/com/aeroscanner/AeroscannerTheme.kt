package com.aeroscanner

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Airbnb color tokens — from DESIGN.md
object AeroscannerColors {
    val Primary = Color(0xFFFF385C)          // Rausch
    val PrimaryActive = Color(0xFFE00B41)
    val PrimaryDisabled = Color(0xFFFFD1DA)
    val Ink = Color(0xFF222222)              // primary text
    val Body = Color(0xFF3F3F3F)             // secondary text
    val Muted = Color(0xFF6A6A6A)            // sub-titles, inactive tabs
    val MutedSoft = Color(0xFF929292)        // disabled text
    val Hairline = Color(0xFFDDDDDD)         // default borders
    val HairlineSoft = Color(0xFFEBEBEB)     // light dividers
    val BorderStrong = Color(0xFFC1C1C1)     // focused inputs
    val Canvas = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFF7F7F7)
    val SurfaceStrong = Color(0xFFF2F2F2)
    val ErrorText = Color(0xFFC13515)
    val LegalLink = Color(0xFF428BFF)

    // Semantic cost level colors
    val CostLow = Color(0xFF34A853)    // green
    val CostMid = Color(0xFFFBBC04)    // amber
    val CostHigh = Primary             // rausch/coral
}

// Custom font family — OPPO Sans 4.0
val OppoSansFont = FontFamily(
    Font(R.font.oppo_sans, FontWeight.Normal),
    Font(R.font.oppo_sans, FontWeight.Medium),
    Font(R.font.oppo_sans, FontWeight.SemiBold),
    Font(R.font.oppo_sans, FontWeight.Bold),
)

// Typography using OPPO Sans
val AeroscannerTypography = Typography(
    displayLarge = TextStyle(     // 28sp/700 — homepage h1
        fontFamily = OppoSansFont,
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 40.sp,
    ),
    displayMedium = TextStyle(    // 22sp/500 — listing detail h1
        fontFamily = OppoSansFont,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 26.sp,
    ),
    headlineMedium = TextStyle(   // 21sp/700 — section heads
        fontFamily = OppoSansFont,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
    ),
    titleMedium = TextStyle(      // 16sp/600 — card titles
        fontFamily = OppoSansFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(       // 16sp/500 — footer column heads
        fontFamily = OppoSansFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(        // 16sp/400 — running text
        fontFamily = OppoSansFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(       // 14sp/400 — card meta, dates, prices
        fontFamily = OppoSansFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(        // 13sp/400 — legal
        fontFamily = OppoSansFont,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(       // 16sp/500 — button labels
        fontFamily = OppoSansFont,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(      // 14sp/500 — caption labels
        fontFamily = OppoSansFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(       // 11sp/600 — badges
        fontFamily = OppoSansFont,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 13.sp,
    ),
)

// Airbnb spacing tokens (in dp)
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val base = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val section = 64.dp
}

// Airbnb border radius tokens
object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 8.dp
    val lg = 12.dp
    val xl = 20.dp
    val full = 9999.dp
}

private val LightColorScheme = lightColorScheme(
    primary = AeroscannerColors.Primary,
    onPrimary = Color.White,
    primaryContainer = AeroscannerColors.Primary,
    onPrimaryContainer = Color.White,
    secondary = AeroscannerColors.Ink,
    onSecondary = Color.White,
    background = AeroscannerColors.Canvas,
    onBackground = AeroscannerColors.Ink,
    surface = AeroscannerColors.Canvas,
    onSurface = AeroscannerColors.Ink,
    surfaceVariant = AeroscannerColors.SurfaceSoft,
    onSurfaceVariant = AeroscannerColors.Body,
    outline = AeroscannerColors.Hairline,
    outlineVariant = AeroscannerColors.HairlineSoft,
    error = AeroscannerColors.ErrorText,
    onError = Color.White,
)

@Composable
fun AeroscannerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = AeroscannerTypography,
        content = content,
    )
}
