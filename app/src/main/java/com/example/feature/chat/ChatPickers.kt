package com.example.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.FriendConnection
import com.example.core.model.MessageType
import com.example.ui.components.UserAvatar

data class ChatWallpaperOption(
  val id: String,
  val name: String,
  val lightColors: List<Color>,
  val darkColors: List<Color>,
)

val ChatWallpapers =
  listOf(
    ChatWallpaperOption(
      "default",
      "Clean Mint",
      listOf(Color(0xFFF6F9F8), Color(0xFFECF7F5)),
      listOf(Color(0xFF0B1414), Color(0xFF10201F)),
    ),
    ChatWallpaperOption(
      "sunrise",
      "Warm Sunrise",
      listOf(Color(0xFFFFF5EB), Color(0xFFFFE4DF)),
      listOf(Color(0xFF1E1310), Color(0xFF281619)),
    ),
    ChatWallpaperOption(
      "lagoon",
      "Ocean Lagoon",
      listOf(Color(0xFFE0F7FA), Color(0xFFE8F5E9)),
      listOf(Color(0xFF081C24), Color(0xFF0B221C)),
    ),
    ChatWallpaperOption(
      "lavender",
      "Soft Twilight",
      listOf(Color(0xFFF3E8FF), Color(0xFFE0E7FF)),
      listOf(Color(0xFF181126), Color(0xFF11162B)),
    ),
  )

data class ChatThemeAccent(val id: String, val name: String, val bubbleColor: Color)

val ChatThemeAccents =
  listOf(
    ChatThemeAccent("teal", "Friends Teal", Color(0xFF0A8D87)),
    ChatThemeAccent("coral", "Coral Rose", Color(0xFFD64550)),
    ChatThemeAccent("indigo", "Deep Indigo", Color(0xFF4F46E5)),
    ChatThemeAccent("emerald", "Forest Green", Color(0xFF059669)),
  )

val BuiltInStickers =
  listOf(
    "🎉 Party Time!" to "🎉",
    "🤗 Warm Hug" to "🤗",
    "🔥 On Fire" to "🔥",
    "☕ Chai Break" to "☕",
    "🙌 High Five" to "🙌",
    "💯 100%" to "💯",
  )

val BuiltInGifs =
  listOf(
    "GIF · Dancing Happy Cat" to "🐱💃",
    "GIF · Thumbs Up Rocket" to "🚀👍",
    "GIF · Laughing Tears" to "😹✨",
    "GIF · Good Night Moon" to "🌙💤",
  )

@Composable
fun DisappearingTimerDialog(
  currentSeconds: Long,
  onSelectDuration: (Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val options =
    listOf(
      0L to "Off",
      86400L to "24 Hours",
      604800L to "7 Days",
      7776000L to "90 Days",
    )
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Disappearing Messages") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          "New messages in this 1-to-1 chat will automatically disappear after the selected duration.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { (sec, label) ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            color =
              if (currentSeconds == sec) MaterialTheme.colorScheme.primaryContainer
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier =
              Modifier.fillMaxWidth()
                .clickable {
                  onSelectDuration(sec)
                  onDismiss()
                }
                .testTag("disappearing_opt_$sec"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
              if (currentSeconds == sec) {
                Icon(
                  Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

@Composable
fun ChatStyleDialog(
  currentWallpaperId: String?,
  currentThemeId: String?,
  onApply: (wallpaperId: String, themeId: String) -> Unit,
  onDismiss: () -> Unit,
) {
  var chosenWallpaper by remember { mutableStateOf(currentWallpaperId ?: "default") }
  var chosenTheme by remember { mutableStateOf(currentThemeId ?: "teal") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Chat Wallpaper & Theme") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Wallpaper", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ChatWallpapers.forEach { wp ->
            val selected = chosenWallpaper == wp.id
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(52.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(Brush.verticalGradient(wp.lightColors))
                  .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color =
                      if (selected) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                  )
                  .clickable { chosenWallpaper = wp.id },
            ) {
              if (selected) {
                Icon(Icons.Default.Check, contentDescription = wp.name, tint = Color.Black)
              }
            }
          }
        }
        Text("Bubble Accent Theme", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ChatThemeAccents.forEach { accent ->
            FilterChip(
              selected = chosenTheme == accent.id,
              onClick = { chosenTheme = accent.id },
              label = { Text(accent.name.split(" ").first()) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onApply(chosenWallpaper, chosenTheme)
          onDismiss()
        }
      ) {
        Text("Apply")
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun StickerGifPickerDialog(
  onSend: (MessageType, String) -> Unit,
  onDismiss: () -> Unit,
) {
  var showGifs by remember { mutableStateOf(false) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !showGifs,
          onClick = { showGifs = false },
          label = { Text("Stickers") },
          modifier = Modifier.minimumInteractiveComponentSize(),
        )
        FilterChip(
          selected = showGifs,
          onClick = { showGifs = true },
          label = { Text("GIFs") },
          modifier = Modifier.minimumInteractiveComponentSize(),
        )
      }
    },
    text = {
      val itemsList = if (showGifs) BuiltInGifs else BuiltInStickers
      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxWidth().height(240.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(itemsList) { (label, emoji) ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 2.dp,
            modifier =
              Modifier.clickable {
                onSend(if (showGifs) MessageType.GIF else MessageType.STICKER, "$emoji $label")
                onDismiss()
              },
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(emoji, style = MaterialTheme.typography.headlineMedium)
              Spacer(modifier = Modifier.height(4.dp))
              Text(label, style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

@Composable
fun ForwardMessagesDialog(
  friends: List<FriendConnection>,
  selectedCount: Int,
  onSelectFriend: (FriendConnection) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Forward $selectedCount Message(s) (Max 10)") },
    text = {
      LazyColumn(
        modifier = Modifier.fillMaxWidth().height(260.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        items(friends.filter { !it.blocked }, key = { it.friendUid }) { f ->
          Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 1.dp,
            modifier =
              Modifier.fillMaxWidth().clickable {
                onSelectFriend(f)
                onDismiss()
              },
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              UserAvatar(name = f.friendName, photoUrl = f.friendPhotoUrl, size = 40.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Text(f.friendName, style = MaterialTheme.typography.titleMedium)
            }
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun E2eeSafetyDialog(
  peerName: String,
  fingerprint: String?,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    title = { Text("End-to-End Encryption") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text =
            "Messages and calls with $peerName are encrypted using ECDH P-256 key agreement and AES-256-GCM.",
          style = MaterialTheme.typography.bodySmall,
        )
        if (!fingerprint.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = fingerprint,
              fontFamily = FontFamily.Monospace,
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(12.dp),
            )
          }
        } else {
          Text(
            "Peer public identity key syncs automatically when $peerName opens Friends Meet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}
