package com.example.feature.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.model.FriendConnection
import com.example.core.model.GroupThread
import com.example.core.model.MessageType
import com.example.feature.friends.QuickMessagesPickerDialog
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
  viewModel: ChatViewModel,
  myUid: String,
  friendsList: List<FriendConnection>,
  quickMessages: List<String>,
  onSaveQuickMessages: (List<String>) -> Unit,
  onStartAudioCall: () -> Unit,
  onStartVideoCall: () -> Unit,
  onBack: () -> Unit,
  onAddFriend: (() -> Unit)? = null,
  groupsList: List<GroupThread> = emptyList(),
  onForwardToGroup: (GroupThread) -> Unit = {},
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val isDark = isSystemInDarkTheme()
  val snackbarHostState = remember { SnackbarHostState() }
  val listState = rememberLazyListState()
  val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
  val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

  var showOverflow by remember { mutableStateOf(false) }
  var showDisappearingDialog by remember { mutableStateOf(false) }
  var showStyleDialog by remember { mutableStateOf(false) }
  var showStickerDialog by remember { mutableStateOf(false) }
  var showQuickMsgDialog by remember { mutableStateOf(false) }
  var showForwardDialog by remember { mutableStateOf(false) }
  var showSafetyDialog by remember { mutableStateOf(false) }
  var showOldChatLimitDialog by remember { mutableStateOf(false) }
  var activeActionMsg by remember { mutableStateOf<DecryptedChatMessage?>(null) }

  val isOldChatLocked =
    state.thread?.chatMode == "OLD_CHAT" && state.thread?.oldChatUnlocked == false
  val isOldChatInitiator = state.thread?.oldChatInitiatorUid == myUid
  val oldChatLimit = (state.thread?.oldChatMessageLimit ?: 3L).coerceAtLeast(1L)
  val oldChatSent = state.thread?.oldChatSentCount ?: 0L
  val oldChatLimitReached = isOldChatLocked && isOldChatInitiator && oldChatSent >= oldChatLimit

  // Voice recording & playback
  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val micLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
      hasMicPermission = it
    }
  var isRecordingVoice by remember { mutableStateOf(false) }
  var voiceStartMs by remember { mutableLongStateOf(0L) }
  var voiceElapsedMs by remember { mutableLongStateOf(0L) }
  var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
  var voiceFile by remember { mutableStateOf<File?>(null) }
  var playingMessageId by remember { mutableStateOf<String?>(null) }
  var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) viewModel.sendImage(uri)
    }

  LaunchedEffect(isRecordingVoice) {
    while (isRecordingVoice) {
      voiceElapsedMs = System.currentTimeMillis() - voiceStartMs
      delay(200)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        mediaRecorder?.release()
      } catch (_: Exception) {}
      try {
        mediaPlayer?.release()
      } catch (_: Exception) {}
    }
  }

  LaunchedEffect(state.statusMessage) {
    state.statusMessage?.let { msg ->
      snackbarHostState.showSnackbar(msg)
      viewModel.clearStatusMessage()
    }
  }

  val wallpaper =
    ChatWallpapers.firstOrNull { it.id == state.thread?.wallpaperId } ?: ChatWallpapers.first()
  val accent =
    ChatThemeAccents.firstOrNull { it.id == state.thread?.themeId } ?: ChatThemeAccents.first()
  val bgColors = if (isDark) wallpaper.darkColors else wallpaper.lightColors

  if (showDisappearingDialog) {
    DisappearingTimerDialog(
      currentSeconds = state.thread?.disappearingDurationSeconds ?: 0L,
      onSelectDuration = viewModel::setDisappearingDuration,
      onDismiss = { showDisappearingDialog = false },
    )
  }
  if (showStyleDialog) {
    ChatStyleDialog(
      currentWallpaperId = state.thread?.wallpaperId,
      currentThemeId = state.thread?.themeId,
      onApply = viewModel::setWallpaperAndTheme,
      onDismiss = { showStyleDialog = false },
    )
  }
  if (showStickerDialog) {
    StickerGifPickerDialog(
      onSend = viewModel::sendStickerOrGif,
      onDismiss = { showStickerDialog = false },
    )
  }
  if (showQuickMsgDialog) {
    QuickMessagesPickerDialog(
      friendName = state.peerName,
      quickMessages = quickMessages,
      onSendMessage = { msg ->
        viewModel.sendQuickMessage(msg)
        showQuickMsgDialog = false
      },
      onSaveQuickMessages = onSaveQuickMessages,
      onDismiss = { showQuickMsgDialog = false },
    )
  }
  if (showForwardDialog) {
    ForwardMessagesDialog(
      friends = friendsList,
      groups = groupsList,
      selectedCount = state.selectedMessageIds.size,
      onSelectFriend = viewModel::forwardSelectedToFriend,
      onSelectGroup = onForwardToGroup,
      onDismiss = { showForwardDialog = false },
    )
  }
  if (showSafetyDialog) {
    E2eeSafetyDialog(
      peerName = state.peerName,
      fingerprint = state.safetyFingerprint,
      onDismiss = { showSafetyDialog = false },
    )
  }
  if (showOldChatLimitDialog) {
    var limitInput by remember { mutableStateOf(oldChatLimit.toString()) }
    AlertDialog(
      onDismissRequest = { showOldChatLimitDialog = false },
      title = { Text("Adjust Old Chat Limit") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Choose how many starter messages ${state.peerName} can send before your reply unlocks unlimited chat (1-100).",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = limitInput,
            onValueChange = { ch -> limitInput = ch.filter { it.isDigit() }.take(3) },
            label = { Text("Message Limit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("old_chat_limit_input"),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val parsed = (limitInput.toIntOrNull() ?: 3).coerceIn(1, 100)
            viewModel.updateOldChatLimit(parsed)
            showOldChatLimitDialog = false
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showOldChatLimitDialog = false }) { Text("Cancel") }
      },
    )
  }

  activeActionMsg?.let { target ->
    MessageActionSheetDialog(
      message = target,
      isMine = target.senderUid == myUid,
      onReaction = { emoji ->
        viewModel.toggleReaction(target, emoji)
        activeActionMsg = null
      },
      onReply = {
        viewModel.setReplyTarget(target)
        activeActionMsg = null
      },
      onEdit = {
        viewModel.startEditing(target)
        activeActionMsg = null
      },
      onForwardSelect = {
        viewModel.toggleSelectForForward(target.id)
        activeActionMsg = null
      },
      onTranslate = {
        viewModel.translateMessage(target)
        activeActionMsg = null
      },
      onDeleteForMe = {
        viewModel.deleteForMe(target)
        activeActionMsg = null
      },
      onDeleteForEveryone = {
        viewModel.deleteForEveryone(target)
        activeActionMsg = null
      },
      onDismiss = { activeActionMsg = null },
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      if (state.selectedMessageIds.isNotEmpty()) {
        TopAppBar(
          title = { Text("${state.selectedMessageIds.size} selected (max 10)") },
          navigationIcon = {
            IconButton(onClick = viewModel::clearSelection) {
              Icon(Icons.Default.Close, contentDescription = "Clear selection")
            }
          },
          actions = {
            IconButton(
              onClick = { showForwardDialog = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("forward_selected_btn"),
            ) {
              Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = "Forward selected")
            }
          },
        )
      } else {
        TopAppBar(
          navigationIcon = {
            IconButton(
              onClick = onBack,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
          },
          title = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.clickable { showSafetyDialog = true },
            ) {
              UserAvatar(
                name = state.peerName,
                photoUrl = state.peerPhotoUrl,
                size = 38.dp,
                showOnlineIndicator = true,
                online = state.peerOnline,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = state.peerName,
                  style = MaterialTheme.typography.titleMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text =
                    when {
                      state.peerTyping -> "Typing…"
                      state.peerOnline -> "Online · E2EE Verified"
                      else -> "Offline · E2EE Verified"
                    },
                  style = MaterialTheme.typography.labelSmall,
                  color =
                    if (state.peerTyping || state.peerOnline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          },
          actions = {
            if (!state.isPeerFriend && onAddFriend != null) {
              FilledTonalButton(
                onClick = onAddFriend,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.padding(end = 4.dp).testTag("chat_add_friend_btn"),
              ) {
                Icon(
                  Icons.Default.PersonAdd,
                  contentDescription = "Add friend",
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add", style = MaterialTheme.typography.labelMedium)
              }
            }
            IconButton(
              onClick = onStartAudioCall,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_audio_call_btn"),
            ) {
              Icon(Icons.Default.Call, contentDescription = "Audio call")
            }
            IconButton(
              onClick = onStartVideoCall,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_video_call_btn"),
            ) {
              Icon(Icons.Default.Videocam, contentDescription = "Video call")
            }
            Box {
              IconButton(
                onClick = { showOverflow = true },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_more_btn"),
              ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Chat options")
              }
              DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                DropdownMenuItem(
                  text = { Text("Disappearing Messages") },
                  leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    showDisappearingDialog = true
                  },
                )
                DropdownMenuItem(
                  text = { Text("Wallpaper & Theme") },
                  leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    showStyleDialog = true
                  },
                )
                DropdownMenuItem(
                  text = { Text("Verify E2EE Fingerprint") },
                  leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    showSafetyDialog = true
                  },
                )
                DropdownMenuItem(
                  text = { Text("Retry Offline Queue") },
                  leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                  onClick = {
                    showOverflow = false
                    viewModel.retryOfflineQueue()
                  },
                )
              }
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
        )
      }
    },
    bottomBar = {
      Surface(
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .animateContentSize(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
              )
              .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          state.replyToMessage?.let { reply ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.secondaryContainer)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Replying: ${reply.text}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              IconButton(onClick = { viewModel.setReplyTarget(null) }) {
                Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
              }
            }
          }
          state.editingMessage?.let { edit ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.tertiaryContainer)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Editing message (within 15-min limit)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
              )
              IconButton(onClick = viewModel::cancelEditing) {
                Icon(Icons.Default.Close, contentDescription = "Cancel edit", modifier = Modifier.size(16.dp))
              }
            }
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            IconButton(
              onClick = { showQuickMsgDialog = true },
              enabled = !oldChatLimitReached,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_quick_msg_btn"),
            ) {
              Icon(Icons.Default.Bolt, contentDescription = "Quick messages")
            }
            IconButton(
              onClick = { showStickerDialog = true },
              enabled = !oldChatLimitReached,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_sticker_btn"),
            ) {
              Icon(Icons.Default.EmojiEmotions, contentDescription = "Stickers & GIFs")
            }
            IconButton(
              onClick = {
                imagePicker.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
              enabled = !oldChatLimitReached,
              modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_image_btn"),
            ) {
              Icon(Icons.Default.Image, contentDescription = "Share image")
            }
            OutlinedTextField(
              value = state.composerText,
              onValueChange = viewModel::onComposerChanged,
              enabled = !oldChatLimitReached,
              placeholder = {
                Text(
                  when {
                    oldChatLimitReached -> "Waiting for ${state.peerName} to reply…"
                    isRecordingVoice -> "Recording ${voiceElapsedMs / 1000}s…"
                    else -> "Message…"
                  }
                )
              },
              maxLines = 4,
              shape = RoundedCornerShape(24.dp),
              modifier = Modifier.weight(1f).testTag("chat_message_input"),
            )
            if (state.composerText.isBlank()) {
              IconButton(
                enabled = !oldChatLimitReached,
                onClick = {
                  if (!hasMicPermission) {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    return@IconButton
                  }
                  if (!isRecordingVoice) {
                    val out = File(context.cacheDir, "chat_voice_${System.currentTimeMillis()}.m4a")
                    val r =
                      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
                      else @Suppress("DEPRECATION") MediaRecorder()
                    r.setAudioSource(MediaRecorder.AudioSource.MIC)
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    r.setOutputFile(out.absolutePath)
                    r.prepare()
                    r.start()
                    voiceFile = out
                    voiceStartMs = System.currentTimeMillis()
                    voiceElapsedMs = 0L
                    mediaRecorder = r
                    isRecordingVoice = true
                  } else {
                    try {
                      mediaRecorder?.stop()
                      mediaRecorder?.release()
                    } catch (_: Exception) {}
                    mediaRecorder = null
                    isRecordingVoice = false
                    val f = voiceFile
                    if (f != null && f.exists() && f.length() > 0) {
                      viewModel.sendVoiceNote(f.readBytes(), voiceElapsedMs.coerceAtLeast(500L))
                    }
                  }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_voice_btn"),
              ) {
                Icon(
                  imageVector = if (isRecordingVoice) Icons.Default.Stop else Icons.Default.Mic,
                  contentDescription = if (isRecordingVoice) "Stop and send voice" else "Record voice message",
                  tint =
                    if (isRecordingVoice) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
              }
            } else {
              IconButton(
                enabled = !oldChatLimitReached,
                onClick = viewModel::sendTextOrSaveEdit,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("chat_send_btn"),
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Send message",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .background(Brush.verticalGradient(bgColors))
          .padding(innerPadding)
    ) {
      if (isOldChatLocked) {
        GlassCard(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
          cornerRadius = 16.dp,
          containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text =
                  if (isOldChatInitiator) {
                    if (oldChatLimitReached) "Starter limit reached ($oldChatSent/$oldChatLimit)"
                    else "Old Chat · $oldChatSent / $oldChatLimit starter messages used"
                  } else {
                    "Old Chat Request · $oldChatSent / $oldChatLimit sent"
                  },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
              )
              Text(
                text =
                  if (isOldChatInitiator) "Unlocks unlimited chat once ${state.peerName} replies."
                  else "Reply once to unlock unlimited chat, or customize starter limit.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
              )
            }
            if (!isOldChatInitiator) {
              TextButton(
                onClick = { showOldChatLimitDialog = true },
                modifier = Modifier.testTag("old_chat_set_limit_btn"),
              ) {
                Icon(
                  Icons.Default.Tune,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Limit")
              }
            }
          }
        }
      }

      val msgs = state.messages
      val msgLookup = remember(msgs) { msgs.associateBy { it.id } }

      LaunchedEffect(msgs.size) {
        if (msgs.isNotEmpty()) {
          val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
          if (lastVisible >= msgs.size - 4 || listState.firstVisibleItemIndex == 0) {
            listState.animateScrollToItem(msgs.lastIndex)
          }
        }
      }

      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat_messages_list"),
      ) {
        if (msgs.size >= state.pageLimit) {
          item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
              OutlinedButton(onClick = viewModel::loadMoreMessages) {
                Text("Load Earlier Messages")
              }
            }
          }
        }

        itemsIndexed(msgs, key = { _, m -> m.id }) { idx, msg ->
          val showDateSeparator =
            idx == 0 || !isSameDay(msgs[idx - 1].createdAtMillis, msg.createdAtMillis)
          if (showDateSeparator) {
            DateSeparatorChip(timestampMillis = msg.createdAtMillis, dateFormat = dateFormat)
          }

          val isMine = msg.senderUid == myUid
          val isSelected = msg.id in state.selectedMessageIds
          val replySnippet = msg.replyToMessageId?.let { msgLookup[it]?.text }
          val translated = state.translatedTexts[msg.id]

          MessageBubbleItem(
            msg = msg,
            isMine = isMine,
            accentColor = accent.bubbleColor,
            replySnippet = replySnippet,
            translatedText = translated,
            formattedTime = remember(msg.createdAtMillis) { timeFormat.format(Date(msg.createdAtMillis)) },
            isSelected = isSelected,
            isPlayingVoice = playingMessageId == msg.id,
            onSwipeReply = { viewModel.setReplyTarget(msg) },
            onTogglePlayVoice = {
              val url = msg.mediaUrl
              if (url != null) {
                if (playingMessageId == msg.id) {
                  try {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                  } catch (_: Exception) {}
                  mediaPlayer = null
                  playingMessageId = null
                } else {
                  try {
                    mediaPlayer?.release()
                    val mp = MediaPlayer().apply {
                      setDataSource(url)
                      prepareAsync()
                      setOnPreparedListener { start() }
                      setOnCompletionListener { playingMessageId = null }
                    }
                    mediaPlayer = mp
                    playingMessageId = msg.id
                  } catch (_: Exception) {}
                }
              }
            },
            onTranslate = { viewModel.translateMessage(msg) },
            onClick = {
              if (state.selectedMessageIds.isNotEmpty()) {
                viewModel.toggleSelectForForward(msg.id)
              } else {
                activeActionMsg = msg
              }
            },
            onLongClick = { activeActionMsg = msg },
          )
        }
      }
    }
  }
}

@Composable
private fun DateSeparatorChip(timestampMillis: Long, dateFormat: SimpleDateFormat) {
  Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
    Surface(
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
      tonalElevation = 1.dp,
    ) {
      Text(
        text = formatDateSeparator(timestampMillis, dateFormat),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      )
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubbleItem(
  msg: DecryptedChatMessage,
  isMine: Boolean,
  accentColor: Color,
  replySnippet: String?,
  translatedText: String?,
  formattedTime: String,
  isSelected: Boolean,
  isPlayingVoice: Boolean,
  onSwipeReply: () -> Unit,
  onTogglePlayVoice: () -> Unit,
  onTranslate: () -> Unit,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val bubbleBg =
    if (isMine) accentColor else MaterialTheme.colorScheme.surface
  val textColor =
    if (isMine) Color.White else MaterialTheme.colorScheme.onSurface

  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current
  val maxSwipePx = remember(density) { with(density) { 74.dp.toPx() } }
  val triggerPx = remember(density) { with(density) { 50.dp.toPx() } }
  var rawDragX by remember(msg.id) { mutableFloatStateOf(0f) }
  var isDragging by remember(msg.id) { mutableStateOf(false) }
  var thresholdHapticFired by remember(msg.id) { mutableStateOf(false) }

  val animatedDragX by
    animateFloatAsState(
      targetValue = rawDragX,
      animationSpec =
        if (isDragging) spring(stiffness = Spring.StiffnessHigh)
        else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium),
      label = "swipe_reply_x",
    )

  val draggableState =
    rememberDraggableState { delta ->
      isDragging = true
      val next = (rawDragX + delta * 0.85f).coerceIn(0f, maxSwipePx)
      rawDragX = next
      if (next >= triggerPx && !thresholdHapticFired) {
        thresholdHapticFired = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
      } else if (next < triggerPx) {
        thresholdHapticFired = false
      }
    }

  val replyProgress = (animatedDragX / triggerPx).coerceIn(0f, 1f)

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .background(
          if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
          else Color.Transparent
        )
        .draggable(
          orientation = Orientation.Horizontal,
          state = draggableState,
          onDragStopped = {
            if (rawDragX >= triggerPx) {
              onSwipeReply()
            }
            isDragging = false
            rawDragX = 0f
            thresholdHapticFired = false
          },
        ),
  ) {
    if (replyProgress > 0.04f) {
      Surface(
        shape = CircleShape,
        color =
          if (replyProgress >= 1f) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        tonalElevation = 2.dp,
        modifier =
          Modifier.align(Alignment.CenterStart)
            .padding(start = 6.dp)
            .size(34.dp)
            .graphicsLayer {
              val s = 0.6f + 0.45f * replyProgress
              scaleX = s
              scaleY = s
              alpha = replyProgress
            },
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "Reply",
            tint =
              if (replyProgress >= 1f) MaterialTheme.colorScheme.onPrimary
              else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(18.dp),
          )
        }
      }
    }

    Row(
      modifier =
        Modifier.fillMaxWidth()
          .offset { IntOffset(animatedDragX.roundToInt(), 0) },
      horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
    Column(
      horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
      modifier = Modifier.widthIn(max = 290.dp),
    ) {
      Surface(
        color = bubbleBg,
        shape =
          RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isMine) 18.dp else 4.dp,
            bottomEnd = if (isMine) 4.dp else 18.dp,
          ),
        tonalElevation = 2.dp,
        modifier =
          Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("message_bubble_${msg.id}"),
      ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          if (msg.forwardedCount > 0) {
            Text(
              text = "Forwarded",
              style = MaterialTheme.typography.labelSmall,
              fontStyle = FontStyle.Italic,
              color = textColor.copy(alpha = 0.8f),
            )
          }
          if (!replySnippet.isNullOrBlank()) {
            Surface(
              color = Color.Black.copy(alpha = 0.14f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
              Text(
                text = replySnippet,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp),
              )
            }
          }

          when (msg.messageType) {
            MessageType.IMAGE -> {
              msg.mediaUrl?.let { url ->
                AsyncImage(
                  model = url,
                  contentDescription = "Shared photo",
                  contentScale = ContentScale.Crop,
                  modifier =
                    Modifier.fillMaxWidth()
                      .height(190.dp)
                      .clip(RoundedCornerShape(12.dp)),
                )
              }
            }
            MessageType.VOICE -> {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onTogglePlayVoice),
              ) {
                Icon(
                  imageVector = if (isPlayingVoice) Icons.Default.Stop else Icons.Default.PlayArrow,
                  contentDescription = if (isPlayingVoice) "Stop voice" else "Play voice",
                  tint = textColor,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Voice · ${(msg.mediaDurationMs ?: 1000L) / 1000}s",
                  color = textColor,
                  fontWeight = FontWeight.Medium,
                )
              }
            }
            else -> {
              Text(
                text = msg.text,
                color = textColor,
                style =
                  if (msg.messageType == MessageType.REACTION) MaterialTheme.typography.headlineMedium
                  else MaterialTheme.typography.bodyMedium,
              )
            }
          }

          if (!translatedText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Translated: $translatedText",
              color = textColor.copy(alpha = 0.9f),
              style = MaterialTheme.typography.bodySmall,
              fontStyle = FontStyle.Italic,
            )
          }

          Spacer(modifier = Modifier.height(4.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = formattedTime,
              style = MaterialTheme.typography.labelSmall,
              color = textColor.copy(alpha = 0.78f),
            )
            if (msg.edited) {
              Text(
                text = "Edited",
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.78f),
              )
            }
            if (msg.isPendingOffline) {
              Icon(
                Icons.Default.Schedule,
                contentDescription = "Queued offline",
                tint = textColor,
                modifier = Modifier.size(13.dp),
              )
            } else if (isMine) {
              val readByPeer = msg.readByUids.size >= 2
              Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = if (readByPeer) "Read" else "Sent",
                tint = if (readByPeer) Color(0xFF9BF4EB) else textColor.copy(alpha = 0.75f),
                modifier = Modifier.size(14.dp),
              )
            }
            if (msg.messageType == MessageType.TEXT && !msg.deletedForEveryone) {
              Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "Translate Bengali/English",
                tint = textColor.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp).clickable(onClick = onTranslate),
              )
            }
          }
        }
      }

      if (msg.reactions.isNotEmpty()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          tonalElevation = 2.dp,
          modifier = Modifier.padding(top = 2.dp),
        ) {
          Text(
            text = msg.reactions.values.distinct().joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
          )
        }
      }
    }
    }
  }
}

@Composable
private fun MessageActionSheetDialog(
  message: DecryptedChatMessage,
  isMine: Boolean,
  onReaction: (String) -> Unit,
  onReply: () -> Unit,
  onEdit: () -> Unit,
  onForwardSelect: () -> Unit,
  onTranslate: () -> Unit,
  onDeleteForMe: () -> Unit,
  onDeleteForEveryone: () -> Unit,
  onDismiss: () -> Unit,
) {
  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("❤️", "😂", "👍", "😮", "😢").forEach { emoji ->
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.clickable { onReaction(emoji) },
          ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(8.dp))
          }
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = onReply, modifier = Modifier.fillMaxWidth()) { Text("Reply") }
        if (isMine && message.canEditWithin15Min) {
          TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Edit (Within 15 min)")
          }
        }
        TextButton(onClick = onForwardSelect, modifier = Modifier.fillMaxWidth()) {
          Text("Select to Forward")
        }
        if (message.messageType == MessageType.TEXT || message.messageType == MessageType.QUICK_MESSAGE) {
          TextButton(onClick = onTranslate, modifier = Modifier.fillMaxWidth()) {
            Text("Translate (Bengali ↔ English)")
          }
        }
        TextButton(onClick = onDeleteForMe, modifier = Modifier.fillMaxWidth()) {
          Text("Delete for Me")
        }
        if (isMine && !message.deletedForEveryone) {
          TextButton(onClick = onDeleteForEveryone, modifier = Modifier.fillMaxWidth()) {
            Text("Delete for Everyone", color = MaterialTheme.colorScheme.error)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}

private fun isSameDay(aMs: Long, bMs: Long): Boolean {
  val ca = Calendar.getInstance().apply { timeInMillis = aMs }
  val cb = Calendar.getInstance().apply { timeInMillis = bMs }
  return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
    ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}

private fun formatDateSeparator(millis: Long, dateFormat: SimpleDateFormat): String {
  val today = Calendar.getInstance()
  val target = Calendar.getInstance().apply { timeInMillis = millis }
  return when {
    today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
      today.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR) -> "Today"
    today.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
      today.get(Calendar.DAY_OF_YEAR) - target.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
    else -> dateFormat.format(Date(millis))
  }
}
