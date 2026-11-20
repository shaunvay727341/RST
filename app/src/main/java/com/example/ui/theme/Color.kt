package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Friends Meet Brand Palette
val TealPrimary = Color(0xFF00897B)
val TealOnPrimary = Color(0xFFFFFFFF)
val TealPrimaryContainer = Color(0xFFD1FAF5)
val TealOnPrimaryContainer = Color(0xFF003731)

val CoralSecondary = Color(0xFFE05252)
val CoralOnSecondary = Color(0xFFFFFFFF)
val CoralSecondaryContainer = Color(0xFFFFDAD6)
val CoralOnSecondaryContainer = Color(0xFF410002)

val AmberTertiary = Color(0xFFD97706)
val AmberOnTertiary = Color(0xFFFFFFFF)
val AmberTertiaryContainer = Color(0xFFFEF3C7)
val AmberOnTertiaryContainer = Color(0xFF451A03)

val LightBackground = Color(0xFFF2F7F6)
val LightOnBackground = Color(0xFF0D1F1E)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0D1F1E)
val LightSurfaceVariant = Color(0xFFE2ECEA)
val LightOnSurfaceVariant = Color(0xFF3E524F)
val LightOutline = Color(0xFF6B827E)

// Dark Theme Tokens (WhatsApp-inspired deep obsidian-teal slate)
val TealPrimaryDark = Color(0xFF25D3B0)
val TealOnPrimaryDark = Color(0xFF00372E)
val TealPrimaryContainerDark = Color(0xFF005147)
val TealOnPrimaryContainerDark = Color(0xFF9DF7E5)

val CoralSecondaryDark = Color(0xFFFFB4AB)
val CoralOnSecondaryDark = Color(0xFF690005)
val CoralSecondaryContainerDark = Color(0xFF93000A)
val CoralOnSecondaryContainerDark = Color(0xFFFFDAD6)

val AmberTertiaryDark = Color(0xFFFBBF24)
val AmberOnTertiaryDark = Color(0xFF451A03)
val AmberTertiaryContainerDark = Color(0xFF78350F)
val AmberOnTertiaryContainerDark = Color(0xFFFEF3C7)

val DarkBackground = Color(0xFF0B141A)
val DarkOnBackground = Color(0xFFE6F0EE)
val DarkSurface = Color(0xFF111B21)
val DarkOnSurface = Color(0xFFE6F0EE)
val DarkSurfaceVariant = Color(0xFF1D2B33)
val DarkOnSurfaceVariant = Color(0xFFAEBFC4)
val DarkOutline = Color(0xFF4B626A)

// Status & Predefined Friends Meet Friend Tag Palette
val OnlineGreen = Color(0xFF16A34A)
val OfflineGray = Color(0xFF94A3B8)

data class FriendTagColorOption(val name: String, val hex: String, val color: Color)

val FriendsMeetTagPalette =
  listOf(
    FriendTagColorOption("Ocean Teal", "#0A8D87", Color(0xFF0A8D87)),
    FriendTagColorOption("Warm Coral", "#E05252", Color(0xFFE05252)),
    FriendTagColorOption("Sunlit Amber", "#D97706", Color(0xFFD97706)),
    FriendTagColorOption("Royal Indigo", "#4F46E5", Color(0xFF4F46E5)),
    FriendTagColorOption("Forest Emerald", "#059669", Color(0xFF059669)),
    FriendTagColorOption("Berry Plum", "#9333EA", Color(0xFF9333EA)),
    FriendTagColorOption("Sky Azure", "#0284C7", Color(0xFF0284C7)),
    FriendTagColorOption("Rose Magenta", "#DB2777", Color(0xFFDB2777)),
  )
