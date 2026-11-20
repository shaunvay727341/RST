package com.example.feature.friends

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.AddedVia
import com.example.core.model.FriendConnection
import com.example.core.model.FriendTag
import com.example.core.model.UserIdDirectoryEntry
import com.example.core.qr.QrPayload
import com.example.ui.components.QrScannerView
import com.example.ui.components.UserAvatar
import com.example.ui.components.parseColorHex
import java.io.File
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
  viewModel: FriendsViewModel,
  myName: String,
  myFriendsMeetUid: String,
  quickMessages: List<String>,
  customTagCategories: List<String>,
  onOpenChat: (chatId: String, friendUid: String) -> Unit,
  onStartAudioCall: (friendUid: String) -> Unit,
  onOpenProfileModal: (UserIdDirectoryEntry) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  var showAddFriendDialog by remember { mutableStateOf(false) }
  var showQrScanner by remember { mutableStateOf(false) }
  var tagTargetFriend by remember { mutableStateOf<FriendConnection?>(null) }
  var quickMessageFriend by remember { mutableStateOf<FriendConnection?>(null) }
  var voiceMessageFriend by remember { mutableStateOf<FriendConnection?>(null) }

  LaunchedEffect(state.statusMessage) {
    state.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  state.lookupResult?.let { peer ->
    val alreadyFriend = state.friends.any { it.friendUid == peer.uid && !it.blocked }
    ProfilePreviewDialog(
      peer = peer,
      isAlreadyFriend = alreadyFriend,
      isBusy = state.isProcessingAction,
      onAddFriendAndChat = {
        viewModel.addFriendAndOpenChat(peer, state.lookupSource) { chatId, friendUid ->
          showAddFriendDialog = false
          onOpenChat(chatId, friendUid)
        }
      },
      onViewProfile = {
        viewModel.dismissLookupPreview()
        onOpenProfileModal(peer)
      },
      onDismiss = viewModel::dismissLookupPreview,
    )
  }

  if (showAddFriendDialog) {
    AddFriendByUidDialog(
      myFriendsMeetUid = myFriendsMeetUid,
      myName = myName,
      error = state.lookupError,
      isSearching = state.isSearchingUid,
      onSearchUid = { uid -> viewModel.lookupByUidOrQr(uid, AddedVia.UID_LOOKUP) },
      onOpenScanner = {
        showAddFriendDialog = false
        showQrScanner = true
      },
      onDismiss = { showAddFriendDialog = false },
    )
  }

  tagTargetFriend?.let { friend ->
    FriendTagEditorDialog(
      friendName = friend.friendName,
      existingTag = state.friendTags[friend.friendUid],
      customCategories = customTagCategories,
      onSaveTag = { cat, label, hex ->
        viewModel.saveFriendTag(friend.friendUid, cat, label, hex, customTagCategories)
        tagTargetFriend = null
      },
      onClearTag = {
        viewModel.clearFriendTag(friend.friendUid)
        tagTargetFriend = null
      },
      onDismiss = { tagTargetFriend = null },
    )
  }

  quickMessageFriend?.let { friend ->
    QuickMessagesPickerDialog(
      friendName = friend.friendName,
      quickMessages = quickMessages,
      onSendMessage = { msg ->
        viewModel.sendQuickMessage(friend, msg, myName)
        quickMessageFriend = null
      },
      onSaveQuickMessages = viewModel::updateQuickMessages,
      onDismiss = { quickMessageFriend = null },
    )
  }

  voiceMessageFriend?.let { friend ->
    QuickVoiceRecordDialog(
      friendName = friend.friendName,
      onSendVoice = { bytes, durationMs ->
        viewModel.sendQuickVoiceMessage(friend, bytes, durationMs, myName)
        voiceMessageFriend = null
      },
      onDismiss = { voiceMessageFriend = null },
    )
  }

  if (showQrScanner) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Scan Friend QR") },
          navigationIcon = {
            IconButton(
              onClick = { showQrScanner = false },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close scanner")
            }
          },
        )
      }
    ) { pad ->
      Box(modifier = Modifier.fillMaxSize().padding(pad)) {
        QrScannerView(
          onQrDecoded = { raw ->
            showQrScanner = false
            val payload = QrPayload.parse(raw)
            if (payload is QrPayload.FriendProfile) {
              viewModel.lookupByUidOrQr(payload.friendsMeetUid, AddedVia.QR_CODE)
            }
          }
        )
      }
    }
    return
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Friends", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
              text = "UID: $myFriendsMeetUid",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
          }
        },
        actions = {
          IconButton(
            onClick = { showQrScanner = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("friends_qr_scan_button"),
          ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Friend QR")
          }
          IconButton(
            onClick = { showAddFriendDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("friends_add_button"),
          ) {
            Icon(Icons.Default.PersonAdd, contentDescription = "Add friend by UID")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      OutlinedTextField(
        value = state.searchQuery,
        onValueChange = viewModel::updateSearchQuery,
        placeholder = { Text("Search friends, UID, or tag…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("friends_search_input"),
      )

      val friends = state.displayedFriends
      if (friends.isEmpty()) {
        Column(
          modifier = Modifier.fillMaxSize().padding(32.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Default.PersonAdd,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "No friends connected yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Add a friend using their 8-digit UID or scan their QR code to start chatting immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = { showAddFriendDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("empty_add_friend_btn"),
          ) {
            Text("Add by UID or QR")
          }
        }
      } else {
        // Single continuous Friend List (Online friends first, then Offline)
        LazyColumn(
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxSize().testTag("friends_continuous_list"),
        ) {
          items(friends, key = { it.friendUid }) { friend ->
            FriendListRow(
              friend = friend,
              tag = state.friendTags[friend.friendUid],
              onOpenChat = { onOpenChat(friend.chatId, friend.friendUid) },
              onSendReaction = { emoji -> viewModel.sendDynamicReaction(friend, emoji, myName) },
              onAudioCall = { onStartAudioCall(friend.friendUid) },
              onVoiceMessage = { voiceMessageFriend = friend },
              onQuickMessage = { quickMessageFriend = friend },
              onViewProfile = {
                onOpenProfileModal(
                  UserIdDirectoryEntry(
                    uid = friend.friendUid,
                    friendsMeetUid = friend.friendMeetUid,
                    displayName = friend.friendName,
                    photoUrl = friend.friendPhotoUrl,
                    online = friend.friendOnline,
                  )
                )
              },
              onEditTag = { tagTargetFriend = friend },
              onClearTag = { viewModel.clearFriendTag(friend.friendUid) },
              onBlock = { viewModel.blockFriend(friend.friendUid) },
              onRemove = { viewModel.removeFriend(friend.friendUid) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun FriendListRow(
  friend: FriendConnection,
  tag: FriendTag?,
  onOpenChat: () -> Unit,
  onSendReaction: (String) -> Unit,
  onAudioCall: () -> Unit,
  onVoiceMessage: () -> Unit,
  onQuickMessage: () -> Unit,
  onViewProfile: () -> Unit,
  onEditTag: () -> Unit,
  onClearTag: () -> Unit,
  onBlock: () -> Unit,
  onRemove: () -> Unit,
) {
  var showDynamicMenu by remember { mutableStateOf(false) }
  var showReactionRow by remember { mutableStateOf(false) }
  var showThreeDotMenu by remember { mutableStateOf(false) }

  Card(
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpenChat)
        .testTag("friend_row_${friend.friendUid}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      UserAvatar(
        name = friend.friendName,
        photoUrl = friend.friendPhotoUrl,
        size = 50.dp,
        showOnlineIndicator = true,
        online = friend.friendOnline,
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = friend.friendName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = if (friend.friendOnline) "Online" else "Offline",
          style = MaterialTheme.typography.labelSmall,
          color =
            if (friend.friendOnline) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (tag != null) {
          Spacer(modifier = Modifier.height(4.dp))
          val tagColor = parseColorHex(tag.colorHex, MaterialTheme.colorScheme.primary)
          Surface(
            color = tagColor.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
          ) {
            Text(
              text = "${tag.category}: ${tag.label}",
              color = tagColor,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
          }
        }
      }

      // Dynamic Action Icon + Popup directly below it (STRICTLY 4 items, NO Video Call)
      Box {
        IconButton(
          onClick = {
            showReactionRow = false
            showDynamicMenu = true
          },
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("dynamic_action_icon_${friend.friendUid}"),
        ) {
          Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = "Dynamic actions for ${friend.friendName}",
            tint = MaterialTheme.colorScheme.primary,
          )
        }

        DropdownMenu(
          expanded = showDynamicMenu,
          onDismissRequest = { showDynamicMenu = false },
          modifier = Modifier.testTag("dynamic_action_menu_${friend.friendUid}"),
        ) {
          DropdownMenuItem(
            text = { Text("Reaction") },
            leadingIcon = { Text("❤️", style = MaterialTheme.typography.titleMedium) },
            onClick = { showReactionRow = !showReactionRow },
            modifier = Modifier.testTag("dynamic_menu_reaction"),
          )
          if (showReactionRow) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              listOf("❤️", "😂", "👍", "😮").forEach { emoji ->
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = MaterialTheme.colorScheme.secondaryContainer,
                  modifier =
                    Modifier.clickable {
                        showDynamicMenu = false
                        onSendReaction(emoji)
                      }
                      .testTag("reaction_option_$emoji"),
                ) {
                  Text(
                    text = emoji,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                  )
                }
              }
            }
            HorizontalDivider()
          }
          DropdownMenuItem(
            text = { Text("Audio Call") },
            leadingIcon = { Icon(Icons.Default.Call, contentDescription = null) },
            onClick = {
              showDynamicMenu = false
              onAudioCall()
            },
            modifier = Modifier.testTag("dynamic_menu_audio_call"),
          )
          DropdownMenuItem(
            text = { Text("Voice Message") },
            leadingIcon = { Icon(Icons.Default.Mic, contentDescription = null) },
            onClick = {
              showDynamicMenu = false
              onVoiceMessage()
            },
            modifier = Modifier.testTag("dynamic_menu_voice_message"),
          )
          DropdownMenuItem(
            text = { Text("Quick Message") },
            leadingIcon = { Icon(Icons.Default.ChatBubbleOutline, contentDescription = null) },
            onClick = {
              showDynamicMenu = false
              onQuickMessage()
            },
            modifier = Modifier.testTag("dynamic_menu_quick_message"),
          )
        }
      }

      // Three-Dot Menu
      Box {
        IconButton(
          onClick = { showThreeDotMenu = true },
          modifier =
            Modifier.minimumInteractiveComponentSize()
              .testTag("three_dot_menu_${friend.friendUid}"),
        ) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options for ${friend.friendName}",
          )
        }
        DropdownMenu(
          expanded = showThreeDotMenu,
          onDismissRequest = { showThreeDotMenu = false },
        ) {
          DropdownMenuItem(
            text = { Text("View Profile") },
            onClick = {
              showThreeDotMenu = false
              onViewProfile()
            },
          )
          DropdownMenuItem(
            text = { Text(if (tag == null) "Add Friend Tag" else "Change Tag") },
            onClick = {
              showThreeDotMenu = false
              onEditTag()
            },
          )
          if (tag != null) {
            DropdownMenuItem(
              text = { Text("Clear Tag") },
              onClick = {
                showThreeDotMenu = false
                onClearTag()
              },
            )
          }
          DropdownMenuItem(
            text = { Text("Block Friend") },
            onClick = {
              showThreeDotMenu = false
              onBlock()
            },
          )
          DropdownMenuItem(
            text = { Text("Remove Friend", color = MaterialTheme.colorScheme.error) },
            onClick = {
              showThreeDotMenu = false
              onRemove()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun QuickVoiceRecordDialog(
  friendName: String,
  onSendVoice: (ByteArray, Long) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  var hasMic by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val micLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasMic = granted
    }
  var isRecording by remember { mutableStateOf(false) }
  var elapsedMs by remember { mutableLongStateOf(0L) }
  var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
  val outputFile = remember { File(context.cacheDir, "quick_voice_${System.currentTimeMillis()}.m4a") }

  LaunchedEffect(isRecording) {
    val start = System.currentTimeMillis()
    while (isRecording) {
      elapsedMs = System.currentTimeMillis() - start
      delay(200)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        recorder?.release()
      } catch (_: Exception) {}
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Voice Message to $friendName") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        if (!hasMic) {
          Text("Microphone permission is needed to record voice messages.")
          Button(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
            Text("Grant Microphone Access")
          }
        } else {
          Text(
            text = if (isRecording) "Recording… ${elapsedMs / 1000}s" else "Tap Record to start",
            style = MaterialTheme.typography.titleMedium,
          )
          Button(
            onClick = {
              if (!isRecording) {
                val r =
                  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                  else @Suppress("DEPRECATION") MediaRecorder()
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setOutputFile(outputFile.absolutePath)
                r.prepare()
                r.start()
                recorder = r
                isRecording = true
              } else {
                try {
                  recorder?.stop()
                  recorder?.release()
                } catch (_: Exception) {}
                recorder = null
                isRecording = false
                if (outputFile.exists() && outputFile.length() > 0) {
                  onSendVoice(outputFile.readBytes(), elapsedMs.coerceAtLeast(500L))
                }
              }
            },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("quick_voice_toggle"),
          ) {
            Icon(
              imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
              contentDescription = null,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isRecording) "Stop & Send" else "Start Recording")
          }
        }
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
