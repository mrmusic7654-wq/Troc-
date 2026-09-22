package com.example.troc.ui.theme

import androidx.compose.ui.graphics.Color

// Brand palette (from the design spec)
val PurplePrimary = Color(0xFF8B5CF6)
val CyanSecondary = Color(0xFF06B6D4)
val EmeraldTertiary = Color(0xFF10B981)
val RedError = Color(0xFFEF4444)

// Dark scheme surfaces
val DarkBackground = Color(0xFF0F172A)
val DarkSurface = Color(0xFF1E293B)
val DarkSurfaceVariant = Color(0xFF334155)
val DarkOnSurface = Color(0xFFE2E8F0)
val DarkOnSurfaceVariant = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF475569)

// Light scheme surfaces
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFE2E8F0)
val LightOnSurface = Color(0xFF0F172A)
val LightOnSurfaceVariant = Color(0xFF475569)
val LightOutline = Color(0xFFCBD5E1)

// Code blocks
val CodeBackgroundDark = Color(0xFF0B1120)
val CodeBackgroundLight = Color(0xFFF1F5F9)
val CodeTextDark = Color(0xFFE2E8F0)
val CodeTextLight = Color(0xFF1E293B)

// Syntax highlighting (dark)
val HlKeyword = Color(0xFFC084FC)
val HlString = Color(0xFF6EE7B7)
val HlNumber = Color(0xFFFCA5A5)
val HlComment = Color(0xFF64748B)
val HlAnnotation = Color(0xFFFBBF24)

fun syntaxColors(isDark: Boolean): Map<SyntaxRole, Color> = if (isDark) {
    mapOf(
        SyntaxRole.KEYWORD to HlKeyword,
        SyntaxRole.STRING to HlString,
        SyntaxRole.NUMBER to HlNumber,
        SyntaxRole.COMMENT to HlComment,
        SyntaxRole.ANNOTATION to HlAnnotation,
        SyntaxRole.DEFAULT to CodeTextDark
    )
} else {
    mapOf(
        SyntaxRole.KEYWORD to Color(0xFF7C3AED),
        SyntaxRole.STRING to Color(0xFF047857),
        SyntaxRole.NUMBER to Color(0xFFB91C1C),
        SyntaxRole.COMMENT to Color(0xFF94A3B8),
        SyntaxRole.ANNOTATION to Color(0xFFB45309),
        SyntaxRole.DEFAULT to CodeTextLight
    )
}

enum class SyntaxRole { KEYWORD, STRING, NUMBER, COMMENT, ANNOTATION, DEFAULT }
