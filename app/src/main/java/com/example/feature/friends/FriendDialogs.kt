package com.example.feature.friends

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.model.FriendTag
import com.example.core.model.UserIdDirectoryEntry
import com.example.core.qr.QrCodeGenerator
import com.example.core.qr.QrPayload
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import com.example.ui.theme.FriendsMeetTagPalette
import com.example.ui.theme.OfflineGray
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfilePreviewDialog(
  peer: UserIdDirectoryEntry,
  isAlreadyFriend: Boolean,
  isBusy: Boolean,
  error: String? = null,
  onAddFriendAndChat: () -> Unit,
  onViewProfile: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Profile Preview") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        UserAvatar(
          name = peer.displayName,
          photoUrl = peer.photoUrl,
          size = 76.dp,
          showOnlineIndicator = true,
          online = peer.online,
        )
        Text(
          text = peer.displayName,
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
        )
        Text(
          text = "UID: ${peer.friendsMeetUid}",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier =
              Modifier.size(8.dp)
                .clip(CircleShape)
                .background(if (peer.online) OnlineGreen else OfflineGray)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (peer.online) "Online now" else "Offline",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (!error.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = error,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onAddFriendAndChat,
        enabled = !isBusy,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("preview_add_friend_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text("Connecting…")
        } else {
          Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(if (isAlreadyFriend) "Open Chat" else "Add Friend")
        }
      }
    },
    dismissButton = {
      Row {
        OutlinedButton(
          onClick = onViewProfile,
          enabled = !isBusy,
          modifier =
            Modifier.minimumInteractiveComponentSize().testTag("preview_view_profile_button"),
        ) {
          Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("View Profile")
        }
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Close") }
      }
    },
  )
}

@Composable
fun AddFriendByUidDialog(
  myFriendsMeetUid: String,
  myName: String,
  error: String?,
  isSearching: Boolean,
  onSearchUid: (String) -> Unit,
  onOpenScanner: () -> Unit,
  onDismiss: () -> Unit,
) {
  var uidInput by remember { mutableStateOf("") }
  val qrBitmap by produceState<Bitmap?>(initialValue = null, myFriendsMeetUid) {
    value = withContext(Dispatchers.Default) {
      QrCodeGenerator.generateBitmap(QrPayload.encodeFriendPayload(myFriendsMeetUid), 360)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Connect Friend") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        OutlinedTextField(
          value = uidInput,
          onValueChange = { uidInput = it.filter { ch -> ch.isDigit() }.take(8) },
          label = { Text("8-Digit Friends Meet UID") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("uid_lookup_input"),
        )
        if (!error.isNullOrBlank()) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = error,
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(10.dp),
            )
          }
        }
        GlassCard(
          cornerRadius = 16.dp,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = "My QR Card · $myName",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            val bmp = qrBitmap
            if (bmp != null) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "My Friends Meet QR Code",
                modifier = Modifier.size(140.dp).clip(RoundedCornerShape(12.dp)),
              )
            } else {
              Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "UID: $myFriendsMeetUid",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSearchUid(uidInput) },
        enabled = !isSearching && uidInput.length == 8,
        modifier = Modifier.minimumInteractiveComponentSize().testTag("lookup_uid_submit"),
      ) {
        Text(if (isSearching) "Searching…" else "Find UID")
      }
    },
    dismissButton = {
      Row {
        OutlinedButton(
          onClick = onOpenScanner,
          modifier = Modifier.minimumInteractiveComponentSize().testTag("open_qr_scanner_button"),
        ) {
          Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Scan QR")
        }
        TextButton(onClick = onDismiss) { Text("Close") }
      }
    },
  )
}

@Composable
fun FriendTagEditorDialog(
  friendName: String,
  existingTag: FriendTag?,
  customCategories: List<String>,
  onSaveTag: (category: String, label: String, colorHex: String) -> Unit,
  onClearTag: (() -> Unit)?,
  onDismiss: () -> Unit,
) {
  val baseCategories = listOf("Relationship", "Location", "Office", "Work")
  val allCategories = (baseCategories + customCategories).distinct()
  var selectedCategory by remember { mutableStateOf(existingTag?.category ?: allCategories.first()) }
  var customCategoryInput by remember { mutableStateOf("") }
  var labelInput by remember { mutableStateOf(existingTag?.label.orEmpty()) }
  var selectedHex by remember {
    mutableStateOf(existingTag?.colorHex ?: FriendsMeetTagPalette.first().hex)
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Tag for $friendName") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "Private to you. Only one active tag per friend.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          allCategories.forEach { cat ->
            FilterChip(
              selected = selectedCategory == cat,
              onClick = { selectedCategory = cat },
              label = { Text(cat) },
              modifier = Modifier.minimumInteractiveComponentSize(),
            )
          }
        }
        OutlinedTextField(
          value = customCategoryInput,
          onValueChange = {
            customCategoryInput = it.take(40)
            if (it.isNotBlank()) selectedCategory = it.trim()
          },
          label = { Text("Or Custom Category") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = labelInput,
          onValueChange = { labelInput = it.take(40) },
          label = { Text("Tag Label (e.g., Best Friend, Dhaka, Designer)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("friend_tag_label_input"),
        )
        Text("Tag Palette Color", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          FriendsMeetTagPalette.forEach { option ->
            val selected = selectedHex.equals(option.hex, ignoreCase = true)
            Box(
              contentAlignment = Alignment.Center,
              modifier =
                Modifier.size(38.dp)
                  .clip(CircleShape)
                  .background(option.color)
                  .border(
                    width = if (selected) 3.dp else 1.dp,
                    color =
                      if (selected) MaterialTheme.colorScheme.onSurface
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    shape = CircleShape,
                  )
                  .clickable { selectedHex = option.hex },
            ) {
              if (selected) {
                Icon(
                  Icons.Default.Check,
                  contentDescription = option.name,
                  tint = MaterialTheme.colorScheme.surface,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val finalCat = customCategoryInput.trim().ifBlank { selectedCategory }
          if (labelInput.isNotBlank() && finalCat.isNotBlank()) {
            onSaveTag(finalCat, labelInput.trim(), selectedHex)
          }
        },
        enabled = labelInput.isNotBlank(),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("save_friend_tag_button"),
      ) {
        Text("Save Tag")
      }
    },
    dismissButton = {
      Row {
        if (onClearTag != null && existingTag != null) {
          TextButton(onClick = onClearTag) { Text("Remove") }
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
      }
    },
  )
}

@Composable
fun QuickMessagesPickerDialog(
  friendName: String?,
  quickMessages: List<String>,
  onSendMessage: ((String) -> Unit)?,
  onSaveQuickMessages: (List<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var items by remember(quickMessages) { mutableStateOf(quickMessages) }
  var newText by remember { mutableStateOf("") }
  var editingIndex by remember { mutableStateOf<Int?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(if (friendName != null) "Quick Message to $friendName" else "Manage Quick Messages")
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          OutlinedTextField(
            value = newText,
            onValueChange = { newText = it.take(140) },
            label = { Text(if (editingIndex == null) "New Quick Message" else "Edit Message") },
            singleLine = true,
            modifier = Modifier.weight(1f).testTag("quick_message_input"),
          )
          IconButton(
            onClick = {
              val trimmed = newText.trim()
              if (trimmed.isNotEmpty()) {
                val updated =
                  if (editingIndex != null && editingIndex!! in items.indices) {
                    items.toMutableList().apply { set(editingIndex!!, trimmed) }
                  } else {
                    items + trimmed
                  }
                items = updated
                onSaveQuickMessages(updated)
                newText = ""
                editingIndex = null
              }
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_quick_message_button"),
          ) {
            Icon(
              imageVector = if (editingIndex == null) Icons.Default.Add else Icons.Default.Check,
              contentDescription = "Save quick message",
            )
          }
        }

        LazyColumn(
          modifier = Modifier.fillMaxWidth().height(260.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          itemsIndexed(items) { idx, msg ->
            Surface(
              shape = RoundedCornerShape(12.dp),
              tonalElevation = 1.dp,
              modifier =
                Modifier.fillMaxWidth().clickable(enabled = onSendMessage != null) {
                  onSendMessage?.invoke(msg)
                },
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = msg,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
                )
                IconButton(
                  onClick = {
                    if (idx > 0) {
                      val swapped = items.toMutableList().apply {
                        val tmp = this[idx - 1]
                        this[idx - 1] = this[idx]
                        this[idx] = tmp
                      }
                      items = swapped
                      onSaveQuickMessages(swapped)
                    }
                  },
                  enabled = idx > 0,
                ) {
                  Icon(Icons.Default.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                }
                IconButton(
                  onClick = {
                    if (idx < items.lastIndex) {
                      val swapped = items.toMutableList().apply {
                        val tmp = this[idx + 1]
                        this[idx + 1] = this[idx]
                        this[idx] = tmp
                      }
                      items = swapped
                      onSaveQuickMessages(swapped)
                    }
                  },
                  enabled = idx < items.lastIndex,
                ) {
                  Icon(Icons.Default.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                }
                IconButton(
                  onClick = {
                    newText = msg
                    editingIndex = idx
                  }
                ) {
                  Icon(Icons.Default.Edit, contentDescription = "Edit quick message", modifier = Modifier.size(16.dp))
                }
                IconButton(
                  onClick = {
                    val updated = items.toMutableList().apply { removeAt(idx) }
                    items = updated
                    onSaveQuickMessages(updated)
                  }
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "Delete quick message", modifier = Modifier.size(16.dp))
                }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
  )
}
