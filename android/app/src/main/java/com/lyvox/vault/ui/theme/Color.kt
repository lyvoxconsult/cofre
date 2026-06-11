package com.lyvox.vault.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Dark Theme ──────────────────────────────────────────────

val DarkBg = Color(0xFF0A0A0B)
val DarkSurface = Color(0xFF141416)
val DarkSurfaceElevated = Color(0xFF1C1C1F)
val DarkBorder = Color(0xFF27272A)
val DarkTextPrimary = Color(0xFFFAFAFA)
val DarkTextSecondary = Color(0xFFA1A1AA)
val DarkTextTertiary = Color(0xFF71717A)

// ─── Light Theme ─────────────────────────────────────────────

val LightBg = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceElevated = Color(0xFFF4F4F5)
val LightBorder = Color(0xFFE4E4E7)
val LightTextPrimary = Color(0xFF18181B)
val LightTextSecondary = Color(0xFF52525B)
val LightTextTertiary = Color(0xFFA1A1AA)

// ─── Accent / Primary (Indigo) ───────────────────────────────

val Accent = Color(0xFF6366F1)
val AccentMuted = Color(0x206366F1)
val AccentSubtle = Color(0x106366F1)
val AccentLight = Color(0xFFA5B4FC)
val AccentDark = Color(0xFF4F46E5)

// ─── Semantic ────────────────────────────────────────────────

val Danger = Color(0xFFEF4444)
val DangerMuted = Color(0x20EF4444)
val Warning = Color(0xFFEAB308)
val WarningMuted = Color(0x20EAB308)
val Success = Color(0xFF22C55E)
val SuccessMuted = Color(0x2022C55E)
val Info = Color(0xFF3B82F6)

// ─── Strength Colors ─────────────────────────────────────────

val StrengthWeak = Danger
val StrengthFair = Warning
val StrengthGood = Color(0xFF6366F1)
val StrengthStrong = Color(0xFF3B82F6)
val StrengthVeryStrong = Success

// ─── Category Colors ─────────────────────────────────────────

val CategoryPersonal = Accent
val CategoryWork = Warning
val CategoryBank = Success
val CategoryOther = DarkTextTertiary
