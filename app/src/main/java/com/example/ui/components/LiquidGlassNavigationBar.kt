package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

data class LiquidNavItem(
  val label: String,
  val icon: ImageVector,
  val badgeCount: Int = 0,
  val testTag: String,
)

/**
 * Floating frosted-glass bottom navigation bar with a liquid water-drop indicator
 * that smoothly glides, stretches, and emits a soft water-ripple ring on tap.
 */
@Composable
fun LiquidGlassNavigationBar(
  items: List<LiquidNavItem>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  if (items.isEmpty()) return
  val isDark = isSystemInDarkTheme()
  val safeIndex = selectedIndex.coerceIn(0, items.lastIndex)

  val animatedPosition by
    animateFloatAsState(
      targetValue = safeIndex.toFloat(),
      animationSpec =
        spring(
          dampingRatio = 0.62f,
          stiffness = Spring.StiffnessMediumLow,
        ),
      label = "liquid_nav_pos",
    )

  val rippleProgress = remember { Animatable(1f) }
  LaunchedEffect(safeIndex) {
    rippleProgress.snapTo(0f)
    rippleProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
    )
  }

  val dockColors =
    if (isDark) {
      listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
      )
    } else {
      listOf(
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
      )
    }

  val borderBrush =
    Brush.verticalGradient(
      listOf(
        if (isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.85f),
        MaterialTheme.colorScheme.outline.copy(alpha = 0.16f),
      )
    )

  val primaryColor = MaterialTheme.colorScheme.primary
  val secondaryContainer = MaterialTheme.colorScheme.primaryContainer

  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(horizontal = 14.dp, vertical = 8.dp),
    contentAlignment = Alignment.Center,
  ) {
    BoxWithConstraints(
      modifier =
        Modifier.fillMaxWidth()
          .height(68.dp)
          .shadow(
            elevation = if (isDark) 10.dp else 14.dp,
            shape = RoundedCornerShape(30.dp),
            ambientColor = primaryColor.copy(alpha = 0.25f),
            spotColor = primaryColor.copy(alpha = 0.25f),
          )
          .clip(RoundedCornerShape(30.dp))
          .background(Brush.verticalGradient(dockColors))
          .border(width = 1.dp, brush = borderBrush, shape = RoundedCornerShape(30.dp))
          .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
      val itemCount = items.size
      val slotWidth = maxWidth / itemCount
      val travelDelta = abs(animatedPosition - safeIndex)
      val liquidStretch = (travelDelta * 0.34f).coerceIn(0f, 0.34f)

      // Liquid droplet background + water-ripple ring canvas
      Canvas(modifier = Modifier.fillMaxSize()) {
        val slotPx = size.width / itemCount
        val centerX = (animatedPosition + 0.5f) * slotPx
        val centerY = size.height / 2f

        val basePillWidth = slotPx * 0.86f
        val stretchedWidth = (basePillWidth * (1f + liquidStretch)).coerceAtMost(size.width * 0.52f)
        val pillHeight = size.height * (1f - liquidStretch * 0.14f)
        val topLeftX = (centerX - stretchedWidth / 2f).coerceIn(0f, size.width - stretchedWidth)
        val topLeftY = centerY - pillHeight / 2f

        // Water-ripple ring expanding on selection change
        val rProg = rippleProgress.value
        if (rProg < 0.99f) {
          val targetCenterX = (safeIndex + 0.5f) * slotPx
          val radius = (slotPx * 0.22f) + (slotPx * 0.42f * rProg)
          drawCircle(
            color = primaryColor.copy(alpha = (1f - rProg) * 0.36f),
            radius = radius,
            center = Offset(targetCenterX, centerY),
            style = Stroke(width = 2.5.dp.toPx() * (1f - rProg * 0.5f)),
          )
        }

        // Translucent liquid droplet capsule
        drawRoundRect(
          brush =
            Brush.verticalGradient(
              listOf(
                primaryColor.copy(alpha = if (isDark) 0.34f else 0.20f),
                secondaryContainer.copy(alpha = if (isDark) 0.22f else 0.52f),
              )
            ),
          topLeft = Offset(topLeftX, topLeftY),
          size = Size(stretchedWidth, pillHeight),
          cornerRadius = CornerRadius(pillHeight / 2f, pillHeight / 2f),
        )

        // Subtle specular droplet sheen along top edge
        drawRoundRect(
          color = Color.White.copy(alpha = if (isDark) 0.16f else 0.48f),
          topLeft = Offset(topLeftX + 6.dp.toPx(), topLeftY + 1.5.dp.toPx()),
          size = Size((stretchedWidth - 12.dp.toPx()).coerceAtLeast(8f), 2.dp.toPx()),
          cornerRadius = CornerRadius(4f, 4f),
        )
      }

      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        items.forEachIndexed { index, item ->
          val selected = index == safeIndex
          val iconScale by
            animateFloatAsState(
              targetValue = if (selected) 1.14f else 0.96f,
              animationSpec =
                spring(
                  dampingRatio = 0.55f,
                  stiffness = Spring.StiffnessMedium,
                ),
              label = "nav_icon_scale_$index",
            )
          val itemColor by
            animateColorAsState(
              targetValue =
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
              animationSpec = tween(durationMillis = 200),
              label = "nav_item_color_$index",
            )
          val interactionSource = remember { MutableInteractionSource() }

          Column(
            modifier =
              Modifier.width(slotWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(24.dp))
                .minimumInteractiveComponentSize()
                .clickable(
                  interactionSource = interactionSource,
                  indication = null,
                  role = Role.Tab,
                  onClick = { onSelect(index) },
                )
                .testTag(item.testTag),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Box(
              modifier =
                Modifier.graphicsLayer {
                  scaleX = iconScale
                  scaleY = iconScale
                }
            ) {
              if (item.badgeCount > 0) {
                BadgedBox(
                  badge = {
                    Badge {
                      Text(
                        text = if (item.badgeCount > 99) "99+" else "${item.badgeCount}",
                        fontSize = 10.sp,
                      )
                    }
                  }
                ) {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = itemColor,
                    modifier = Modifier.size(22.dp),
                  )
                }
              } else {
                Icon(
                  imageVector = item.icon,
                  contentDescription = item.label,
                  tint = itemColor,
                  modifier = Modifier.size(22.dp),
                )
              }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = item.label,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = itemColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }
  }
}
