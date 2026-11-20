package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.FriendTag
import com.example.core.model.UserIdDirectoryEntry
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseColorHex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewFriendProfileScreen(
  peer: UserIdDirectoryEntry,
  activeTag: FriendTag?,
  isFriend: Boolean,
  onAddOrOpenChat: () -> Unit,
  onAudioCall: () -> Unit,
  onVideoCall: () -> Unit,
  onBack: () -> Unit,
) {
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = { Text("Profile") },
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      UserAvatar(
        name = peer.displayName,
        photoUrl = peer.photoUrl,
        size = 96.dp,
        showOnlineIndicator = true,
        online = peer.online,
      )
      Text(peer.displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
      Text("Friends Meet UID: ${peer.friendsMeetUid}", color = MaterialTheme.colorScheme.primary)

      if (activeTag != null) {
        val tagColor = parseColorHex(activeTag.colorHex, MaterialTheme.colorScheme.primary)
        Surface(color = tagColor.copy(alpha = 0.16f), shape = RoundedCornerShape(10.dp)) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.Label, contentDescription = null, tint = tagColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text("${activeTag.category}: ${activeTag.label}", color = tagColor, fontWeight = FontWeight.SemiBold)
          }
        }
      }

      Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Account Status: ${peer.accountStatus}", style = MaterialTheme.typography.bodyMedium)
          Text(
            "E2EE Identity Key: ${if (!peer.identityPublicKey.isNullOrBlank()) "Verified" else "Pending sync"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))
      Button(
        onClick = onAddOrOpenChat,
        modifier = Modifier.fillMaxWidth().height(50.dp).minimumInteractiveComponentSize(),
      ) {
        Icon(Icons.Default.ChatBubble, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(if (isFriend) "Open 1-to-1 Chat" else "Add Friend & Open Chat")
      }
      if (isFriend) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = onAudioCall,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Audio Call")
          }
          OutlinedButton(
            onClick = onVideoCall,
            modifier = Modifier.weight(1f).minimumInteractiveComponentSize(),
          ) {
            Icon(Icons.Default.Videocam, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Video Call")
          }
        }
      }
    }
  }
}
