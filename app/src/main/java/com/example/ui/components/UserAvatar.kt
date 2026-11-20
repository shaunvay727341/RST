package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.FriendsMeetTagPalette
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import kotlin.math.abs

/**
 * Displays a profile picture via Coil or deterministically generates a letter avatar
 * from the first character of the user's name when no photo exists, with optional online dot.
 */
@Composable
fun UserAvatar(
  name: String,
  photoUrl: String?,
  modifier: Modifier = Modifier,
  size: Dp = 48.dp,
  showOnlineIndicator: Boolean = false,
  online: Boolean = false,
) {
  val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
  val paletteColor =
    FriendsMeetTagPalette[abs(name.hashCode()) % FriendsMeetTagPalette.size].color

  Box(modifier = modifier.size(size)) {
    if (!photoUrl.isNullOrBlank()) {
      AsyncImage(
        model = photoUrl,
        contentDescription = "$name avatar",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().clip(CircleShape),
      )
    } else {
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.fillMaxSize()
            .clip(CircleShape)
            .background(paletteColor.copy(alpha = 0.18f))
            .border(1.5.dp, paletteColor.copy(alpha = 0.45f), CircleShape),
      ) {
        Text(
          text = initial,
          color = paletteColor,
          fontWeight = FontWeight.Bold,
          fontSize = (size.value * 0.42f).sp,
        )
      }
    }

    if (showOnlineIndicator) {
      val dotSize = (size.value * 0.28f).dp.coerceIn(10.dp, 16.dp)
      Box(
        modifier =
          Modifier.align(Alignment.BottomEnd)
            .size(dotSize)
            .clip(CircleShape)
            .background(if (online) OnlineGreen else OfflineGray)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
      )
    }
  }
}

fun parseColorHex(hex: String, fallback: Color): Color {
  return try {
    Color(android.graphics.Color.parseColor(hex))
  } catch (_: Exception) {
    fallback
  }
}
