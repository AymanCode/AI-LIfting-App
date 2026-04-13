package com.ayman.ecolift.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 22.sp,
        letterSpacing = (-0.6).sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 19.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 15.sp,
        letterSpacing = (-0.3).sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 13.sp,
        color = TextPrimary
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 12.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 13.sp,
        color = TextSecondary
    ),
    bodySmall = TextStyle(
        fontSize = 10.sp,
        color = TextSecondary
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.W700,
        fontSize = 9.sp,
        letterSpacing = 0.08.sp,
        color = TextMuted
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.W800,
        fontSize = 8.sp,
        letterSpacing = 0.06.sp
    )
)
