package com.example.feature.letterbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.LetterboxMessage
import com.example.ui.components.FriendsMeetBackdrop
import com.example.ui.components.GlassCard
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterboxScreen(
  repository: LetterboxRepository,
  myUid: String,
  onBack: (() -> Unit)? = null,
) {
  var showTrash by remember { mutableStateOf(false) }
  val inbox by repository.observeMessages(myUid, trashed = false).collectAsStateWithLifecycle(emptyList())
  val trash by repository.observeMessages(myUid, trashed = true).collectAsStateWithLifecycle(emptyList())
  val scope = rememberCoroutineScope()
  val dateFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

  val activeList = if (showTrash) trash else inbox

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        navigationIcon = {
          if (onBack != null) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("letterbox_back_btn"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          }
        },
        title = {
          Column {
            Text(
              "Official Letterbox",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
            Text(
              "Verified system notices, security & guardian verification",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
          ),
      )
    }
  ) { innerPadding ->
    FriendsMeetBackdrop(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(modifier = Modifier.fillMaxSize()) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        FilterChip(
          selected = !showTrash,
          onClick = { showTrash = false },
          label = { Text("Inbox (${inbox.size})") },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("letterbox_inbox_tab"),
        )
        FilterChip(
          selected = showTrash,
          onClick = { showTrash = true },
          label = { Text("Trash · 30d (${trash.size})") },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("letterbox_trash_tab"),
        )
      }

      if (activeList.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            Icons.Default.VerifiedUser,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(52.dp),
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = if (showTrash) "Trash is empty" else "No official letters yet",
            style = MaterialTheme.typography.titleMedium,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text =
              if (showTrash) "Items moved to Trash stay recoverable for 30 days before auto-purge."
              else "Guardian verification codes, security alerts, and official Friends Meet notices appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        LazyColumn(
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxSize().testTag("letterbox_list"),
        ) {
          items(activeList, key = { it.id }) { letter ->
            val formattedDate = remember(letter.createdAt) {
              letter.createdAt?.toDate()?.let { dateFormat.format(it) }
            }
            LetterboxCard(
              letter = letter,
              formattedDate = formattedDate,
              inTrash = showTrash,
              onMarkRead = { scope.launch { repository.markRead(letter.id) } },
              onMoveToTrash = { scope.launch { repository.moveToTrash(letter.id) } },
              onRestore = { scope.launch { repository.restoreFromTrash(letter.id) } },
              onDeleteForever = { scope.launch { repository.deletePermanently(letter.id) } },
            )
          }
        }
      }
      }
    }
  }
}

@Composable
private fun LetterboxCard(
  letter: LetterboxMessage,
  formattedDate: String?,
  inTrash: Boolean,
  onMarkRead: () -> Unit,
  onMoveToTrash: () -> Unit,
  onRestore: () -> Unit,
  onDeleteForever: () -> Unit,
) {
  GlassCard(
    containerColor =
      if (!letter.read && !inTrash) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.48f)
      else null,
    cornerRadius = 18.dp,
    modifier =
      Modifier.fillMaxWidth()
        .clickable { if (!letter.read && !inTrash) onMarkRead() }
        .testTag("letterbox_item_${letter.id}"),
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(8.dp),
        ) {
          Text(
            text = letter.category.replace("_", " "),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        formattedDate?.let { dateText ->
          Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      Text(
        text = letter.title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (!letter.read) FontWeight.Bold else FontWeight.SemiBold,
      )
      Text(
        text = letter.body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
      ) {
        if (!inTrash) {
          if (!letter.read) {
            IconButton(onClick = onMarkRead, modifier = Modifier.minimumInteractiveComponentSize()) {
              Icon(Icons.Default.MarkEmailRead, contentDescription = "Mark as read")
            }
          }
          IconButton(
            onClick = onMoveToTrash,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("trash_letter_${letter.id}"),
          ) {
            Icon(Icons.Default.Delete, contentDescription = "Move to trash")
          }
        } else {
          IconButton(
            onClick = onRestore,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("restore_letter_${letter.id}"),
          ) {
            Icon(Icons.Default.RestoreFromTrash, contentDescription = "Restore from trash")
          }
          IconButton(
            onClick = onDeleteForever,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("purge_letter_${letter.id}"),
          ) {
            Icon(
              Icons.Default.DeleteForever,
              contentDescription = "Delete permanently",
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }
    }
  }
}
