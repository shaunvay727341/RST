package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.model.FriendConnection
import com.example.core.model.PrivacySettings
import com.example.core.model.VisibilityScope
import com.example.ui.components.UserAvatar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
  myUid: String,
  currentSettings: PrivacySettings,
  blockedFriends: List<FriendConnection>,
  onSavePrivacy: suspend (PrivacySettings) -> Unit,
  onUnblockFriend: (String) -> Unit,
  onBack: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  fun update(transform: (PrivacySettings) -> PrivacySettings) {
    val updated = transform(currentSettings)
    scope.launch {
      try {
        onSavePrivacy(updated)
      } catch (e: Exception) {
        snackbarHostState.showSnackbar(e.localizedMessage ?: "Update failed")
      }
    }
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("privacy_back_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = { Text("Privacy & Blocking") },
      )
    },
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        PrivacyScopeCard(
          label = "Profile Visibility",
          current = currentSettings.profileVisibility,
          options = listOf(VisibilityScope.EVERYONE, VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(profileVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Profile Photo Visibility",
          current = currentSettings.photoVisibility,
          options = listOf(VisibilityScope.EVERYONE, VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(photoVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Date of Birth Visibility",
          current = currentSettings.dobVisibility,
          options = listOf(VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(dobVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Online Status Visibility",
          current = currentSettings.onlineStatusVisibility,
          options = listOf(VisibilityScope.EVERYONE, VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(onlineStatusVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Last Seen Visibility",
          current = currentSettings.lastSeenVisibility,
          options = listOf(VisibilityScope.EVERYONE, VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(lastSeenVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Bio Visibility",
          current = currentSettings.bioVisibility,
          options = listOf(VisibilityScope.EVERYONE, VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(bioVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Friend List Visibility",
          current = currentSettings.friendListVisibility,
          options = listOf(VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(friendListVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Phone Number Visibility",
          current = currentSettings.phoneVisibility,
          options = listOf(VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(phoneVisibility = scopeVal.name) } },
        )
      }
      item {
        PrivacyScopeCard(
          label = "Email Visibility",
          current = currentSettings.emailVisibility,
          options = listOf(VisibilityScope.FRIENDS, VisibilityScope.NOBODY),
          onSelect = { scopeVal -> update { it.copy(emailVisibility = scopeVal.name) } },
        )
      }

      item {
        Text(
          text = "Blocked Users (${blockedFriends.size})",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(top = 8.dp),
        )
      }

      if (blockedFriends.isEmpty()) {
        item {
          Text(
            text = "No blocked users.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        items(blockedFriends, key = { it.friendUid }) { blocked ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              UserAvatar(name = blocked.friendName, photoUrl = blocked.friendPhotoUrl, size = 40.dp)
              Spacer(modifier = Modifier.width(10.dp))
              Text(blocked.friendName, modifier = Modifier.weight(1f))
              OutlinedButton(onClick = { onUnblockFriend(blocked.friendUid) }) { Text("Unblock") }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PrivacyScopeCard(
  label: String,
  current: String,
  options: List<VisibilityScope>,
  onSelect: (VisibilityScope) -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { scope ->
          FilterChip(
            selected = current == scope.name,
            onClick = { onSelect(scope) },
            label = { Text(scope.name.lowercase().replaceFirstChar { it.uppercase() }) },
            modifier = Modifier.minimumInteractiveComponentSize(),
          )
        }
      }
    }
  }
}
