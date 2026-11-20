package com.example.feature.oldchat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.core.model.UserIdDirectoryEntry
import com.example.ui.components.FriendsMeetBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OldChatDirectoryScreen(
  users: List<UserIdDirectoryEntry>,
  onStartOldChat: (peerUid: String, peerName: String, peerPhotoUrl: String?, initialLimit: Int) -> Unit,
  onBack: () -> Unit
) {
  var searchQuery by remember { mutableStateOf("") }
  var selectedUserForLimit by remember { mutableStateOf<UserIdDirectoryEntry?>(null) }
  var customLimitInput by remember { mutableStateOf("3") }

  val filteredUsers = remember(users, searchQuery) {
    if (searchQuery.isBlank()) users
    else users.filter {
      it.displayName.contains(searchQuery, true) || it.friendsMeetUid.contains(searchQuery, true)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Old Chat (Global Directory)", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
          ),
      )
    }
  ) { padding ->
    FriendsMeetBackdrop(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
      ) {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search by name or Friends Meet UID...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(16.dp),
          singleLine = true
        )

        Spacer(modifier = Modifier.height(14.dp))
        Text("Chat with any registered user. Recipient has a 3-message limit until they reply or limit is unlocked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))

        if (filteredUsers.isEmpty()) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No users found", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(filteredUsers, key = { it.uid }) { user ->
              GlassCard(
                modifier = Modifier
                  .fillMaxWidth()
                  .clickable { selectedUserForLimit = user },
                cornerRadius = 16.dp
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  UserAvatar(
                    name = user.displayName,
                    photoUrl = user.photoUrl,
                    size = 48.dp,
                    showOnlineIndicator = true,
                    online = user.online,
                  )
                  Spacer(modifier = Modifier.width(14.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("UID: ${user.friendsMeetUid}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                  }
                  IconButton(
                    onClick = { selectedUserForLimit = user },
                    modifier = Modifier
                      .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                  ) {
                    Icon(Icons.Default.Chat, contentDescription = "Chat", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  selectedUserForLimit?.let { user ->
    AlertDialog(
      onDismissRequest = { selectedUserForLimit = null },
      title = { Text("Old Chat Settings") },
      text = {
        Column {
          Text("Start Old Chat with ${user.displayName}?")
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            "Default starter limit is 3 messages. Once ${user.displayName} replies (or updates the limit), chat unlocks unlimited.",
            style = MaterialTheme.typography.bodySmall,
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = customLimitInput,
            onValueChange = { ch -> customLimitInput = ch.filter { it.isDigit() }.take(3) },
            label = { Text("Starter Message Limit (1-100)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp)
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val limit = (customLimitInput.toIntOrNull() ?: 3).coerceIn(1, 100)
            val peer = selectedUserForLimit
            selectedUserForLimit = null
            if (peer != null) {
              onStartOldChat(peer.uid, peer.displayName, peer.photoUrl, limit)
            }
          }
        ) {
          Text("Start Old Chat")
        }
      },
      dismissButton = {
        TextButton(onClick = { selectedUserForLimit = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
