package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight, recomposition-safe Glassmorphism surface with specular edge highlight
 * and subtle multi-stop translucent sheen. Designed to stay smooth at 60/120fps without heavy blurs.
 */
@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = 20.dp,
  borderWidth: Dp = 1.dp,
  borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f),
  containerColor: Color? = null,
  gradientColors: List<Color>? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  val isDark = isSystemInDarkTheme()
  val resolvedColors =
    gradientColors
      ?: if (containerColor != null) {
        listOf(containerColor, containerColor.copy(alpha = (containerColor.alpha * 0.82f).coerceIn(0.45f, 1f)))
      } else if (isDark) {
        listOf(
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
          MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
        )
      } else {
        listOf(
          MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        )
      }

  val highlightColor =
    if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.75f)

  Box(
    modifier =
      modifier
        .clip(RoundedCornerShape(cornerRadius))
        .background(Brush.linearGradient(resolvedColors))
        .border(
          width = borderWidth,
          brush = Brush.linearGradient(listOf(highlightColor, borderColor, borderColor.copy(alpha = 0.08f))),
          shape = RoundedCornerShape(cornerRadius),
        ),
    content = content,
  )
}

/**
 * Cohesive subtle atmospheric gradient backdrop for top-level and secondary screens.
 */
@Composable
fun FriendsMeetBackdrop(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val bgTop = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
  val bgMid = MaterialTheme.colorScheme.background
  val bgBottom = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
  Box(
    modifier = modifier.background(Brush.verticalGradient(listOf(bgTop, bgMid, bgBottom))),
    content = content,
  )
}
