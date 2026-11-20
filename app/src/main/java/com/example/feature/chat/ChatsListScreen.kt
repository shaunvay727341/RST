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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.ChatThread
import com.example.core.model.FriendConnection
import com.example.core.model.UserIdDirectoryEntry
import com.example.ui.components.FriendsMeetBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
  myUid: String,
  threads: List<ChatThread>,
  friendsByUid: Map<String, FriendConnection>,
  directoryUsersByUid: Map<String, UserIdDirectoryEntry> = emptyMap(),
  onOpenChat: (chatId: String, friendUid: String) -> Unit,
  onOpenOldChatDirectory: () -> Unit,
  onOpenGroups: () -> Unit,
) {
  val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

  Scaffold(
    containerColor = Color.Transparent,
    topBar = {
      TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        title = {
          Text(
            text = "Chats & Groups",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
          )
        },
        actions = {
          IconButton(onClick = onOpenOldChatDirectory) {
            Icon(Icons.Default.Public, contentDescription = "Old Chat Global Directory")
          }
          IconButton(onClick = onOpenGroups) {
            Icon(Icons.Default.Group, contentDescription = "Group Chats")
          }
        },
      )
    },
  ) { innerPadding ->
    FriendsMeetBackdrop(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      if (threads.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          GlassCard(cornerRadius = 24.dp, modifier = Modifier.fillMaxWidth()) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp),
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                "No active chats yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
              )
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                "Message friends, start an Old Chat with any registered user globally, or create a Group Chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.height(20.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = onOpenOldChatDirectory,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Old Chat")
                }
                OutlinedButton(
                  onClick = onOpenGroups,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier.weight(1f),
                ) {
                  Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Groups")
                }
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize().testTag("chats_list"),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          item {
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Button(
                onClick = onOpenOldChatDirectory,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Old Chat (Global)")
              }
              OutlinedButton(
                onClick = onOpenGroups,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
              ) {
                Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Group Chats")
              }
            }
          }

          items(threads, key = { it.id }) { thread ->
            val peerUid = thread.participantUids.firstOrNull { it != myUid } ?: return@items
            val friend = friendsByUid[peerUid]
            if (friend?.blocked == true) return@items
            val dirEntry = directoryUsersByUid[peerUid]
            val name =
              friend?.friendName
                ?: dirEntry?.displayName
                ?: "User ${peerUid.take(6)}"
            val photoUrl = friend?.friendPhotoUrl ?: dirEntry?.photoUrl
            val isOnline = friend?.friendOnline ?: (dirEntry?.online == true)
            val isOldChat = thread.chatMode == "OLD_CHAT"

            GlassCard(
              cornerRadius = 18.dp,
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
                  photoUrl = photoUrl,
                  size = 52.dp,
                  showOnlineIndicator = true,
                  online = isOnline,
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
                    if (isOldChat && !thread.oldChatUnlocked) {
                      Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp),
                      ) {
                        Text(
                          text = "${thread.oldChatSentCount}/${thread.oldChatMessageLimit}",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onTertiaryContainer,
                          modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                      }
                      Spacer(modifier = Modifier.width(6.dp))
                    } else if (isOldChat && thread.oldChatUnlocked) {
                      Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Unlimited Old Chat unlocked",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                    }
                    if (thread.disappearingDurationSeconds > 0L) {
                      Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Disappearing messages active",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                    }
                    thread.lastMessageAt?.toDate()?.let { date ->
                      Text(
                        text = timeFormatter.format(date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                  }
                  Spacer(modifier = Modifier.height(3.dp))
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
}
