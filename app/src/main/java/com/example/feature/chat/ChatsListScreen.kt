package com.example.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.ChatThread
import com.example.core.model.FriendConnection
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
  myUid: String,
  threads: List<ChatThread>,
  friendsByUid: Map<String, FriendConnection>,
  onOpenChat: (chatId: String, friendUid: String) -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "1-to-1 Chats",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        }
      )
    }
  ) { innerPadding ->
    if (threads.isEmpty()) {
      Column(
        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
      ) {
        Icon(
          imageVector = Icons.Default.ChatBubbleOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(54.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("No active chats yet", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Select any friend in your Friends list or scan their QR code to open an encrypted 1-to-1 conversation.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("chats_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        items(threads, key = { it.id }) { thread ->
          val peerUid = thread.participantUids.firstOrNull { it != myUid } ?: return@items
          val friend = friendsByUid[peerUid]
          if (friend?.blocked == true) return@items
          val name = friend?.friendName ?: "User ${peerUid.take(6)}"
          Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier =
              Modifier.fillMaxWidth()
                .clickable { onOpenChat(thread.id, peerUid) }
                .testTag("chat_thread_card_${thread.id}"),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              UserAvatar(
                name = name,
                photoUrl = friend?.friendPhotoUrl,
                size = 50.dp,
                showOnlineIndicator = true,
                online = friend?.friendOnline == true,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                  )
                  if (thread.disappearingDurationSeconds > 0L) {
                    Icon(
                      imageVector = Icons.Default.Timer,
                      contentDescription = "Disappearing messages active",
                      tint = MaterialTheme.colorScheme.primary,
                      modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                  }
                  thread.lastMessageAt?.toDate()?.let { date ->
                    Text(
                      text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                  text =
                    when {
                      peerUid in thread.typingUids -> "Typing…"
                      !thread.lastMessageText.isNullOrBlank() -> thread.lastMessageText
                      else -> "Start an encrypted conversation"
                    },
                  style = MaterialTheme.typography.bodyMedium,
                  color =
                    if (peerUid in thread.typingUids) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}
