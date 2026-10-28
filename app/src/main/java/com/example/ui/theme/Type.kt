package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Multi-weight SF Pro metric font family (Light 300 through Black 900)
 * bundled locally in `res/font/` for zero-latency native rendering across all devices.
 */
val SfProFontFamily = FontFamily(
    Font(R.font.inter_300, FontWeight.Thin),
    Font(R.font.inter_300, FontWeight.ExtraLight),
    Font(R.font.inter_300, FontWeight.Light),
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold),
    Font(R.font.inter_800, FontWeight.ExtraBold),
    Font(R.font.inter_900, FontWeight.Black)
)

val SfProDisplayFontFamily = SfProFontFamily
val SfProTextFontFamily = SfProFontFamily

// Retain semantic aliases used by theme consumers
val DisplayFontFamily = SfProDisplayFontFamily
val BodyFontFamily = SfProTextFontFamily

private const val SF_PRO_FEATURES = "calt, kern"

private val SfProPlatformTextStyle = PlatformTextStyle(
    includeFontPadding = false
)

/**
 * Complete Material 3 Typography calibrated to Apple's SF Pro Display (>= 20sp)
 * and SF Pro Text (< 20sp) Human Interface Guidelines optical tracking & leading curve.
 */
val BrowserTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        letterSpacing = (-0.45).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    displayMedium = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.40).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    displaySmall = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.38).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    headlineLarge = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.40).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    headlineMedium = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.42).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.36).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    titleLarge = TextStyle(
        fontFamily = SfProDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.34).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    titleMedium = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.32).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    titleSmall = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.16).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.24).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    bodyMedium = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.10).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    bodySmall = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    labelLarge = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.15).sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    labelMedium = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    ),
    labelSmall = TextStyle(
        fontFamily = SfProTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.5.sp,
        letterSpacing = 0.06.sp,
        fontFeatureSettings = SF_PRO_FEATURES,
        platformStyle = SfProPlatformTextStyle
    )
)
