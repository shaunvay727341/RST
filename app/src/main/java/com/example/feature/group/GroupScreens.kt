package com.example.feature.group

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.FriendConnection
import com.example.core.model.GroupMessage
import com.example.core.model.GroupThread
import com.example.core.model.UserIdDirectoryEntry
import com.example.core.model.UserProfile
import com.example.core.qr.QrCodeGenerator
import com.example.core.qr.QrPayload
import com.example.feature.chat.ForwardMessagesDialog
import com.example.ui.components.FriendsMeetBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.QrScannerView
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class GroupSelectableUser(
  val uid: String,
  val friendsMeetUid: String,
  val displayName: String,
  val photoUrl: String?,
  val online: Boolean,
  val isFriend: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
  groups: List<GroupThread>,
  errorMessage: String? = null,
  isBusy: Boolean = false,
  onClearError: () -> Unit = {},
  onJoinByQr: (String) -> Unit = {},
  onSelectGroup: (String, String) -> Unit,
  onCreateGroup: () -> Unit,
  onBack: () -> Unit,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  var showQrScanner by remember { mutableStateOf(false) }
  var showEnterCodeDialog by remember { mutableStateOf(false) }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      snackbarHostState.showSnackbar(errorMessage)
      onClearError()
    }
  }

  if (showEnterCodeDialog) {
    var codeInput by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showEnterCodeDialog = false },
      title = { Text("Join Group by Link or ID") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Paste a Friends Meet group invite link (fm-group://…) or Group ID:",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = codeInput,
            onValueChange = { codeInput = it },
            label = { Text("Group Invite / ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("group_join_code_input"),
          )
        }
      },
      confirmButton = {
        Button(
          enabled = codeInput.isNotBlank() && !isBusy,
          onClick = {
            val raw = codeInput.trim()
            showEnterCodeDialog = false
            onJoinByQr(raw)
          },
        ) {
          Text("Join Group")
        }
      },
      dismissButton = {
        TextButton(onClick = { showEnterCodeDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showQrScanner) {
    BackHandler { showQrScanner = false }
    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      topBar = {
        TopAppBar(
          title = { Text("Scan Group QR") },
          navigationIcon = {
            IconButton(
              onClick = { showQrScanner = false },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(Icons.Default.Close, contentDescription = "Close scanner")
            }
          },
          actions = {
            TextButton(
              onClick = {
                showQrScanner = false
                showEnterCodeDialog = true
              }
            ) {
              Text("Enter Link")
            }
          },
        )
      },
    ) { pad ->
      Box(modifier = Modifier.fillMaxSize().padding(pad)) {
        QrScannerView(
          hintText = "Point camera at a Friends Meet Group QR code",
          onQrDecoded = { raw ->
            showQrScanner = false
            onJoinByQr(raw)
          },
        )
      }
    }
    return
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text("Group Chats", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("group_list_back_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = { showQrScanner = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("group_list_scan_qr_btn"),
          ) {
            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Group QR")
          }
          IconButton(
            onClick = onCreateGroup,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("group_list_add_btn"),
          ) {
            Icon(Icons.Default.Add, contentDescription = "Create Group")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
          ),
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = onCreateGroup,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.testTag("group_list_fab"),
      ) {
        Icon(Icons.Default.Group, contentDescription = "New Group")
      }
    },
  ) { padding ->
    FriendsMeetBackdrop(modifier = Modifier.fillMaxSize().padding(padding)) {
      Column(modifier = Modifier.fillMaxSize()) {
        if (isBusy) {
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (groups.isEmpty()) {
          Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            GlassCard(cornerRadius = 24.dp) {
              Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Icon(
                  Icons.Default.Group,
                  contentDescription = null,
                  modifier = Modifier.size(56.dp),
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                  "No group chats yet",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  "Create a group or scan a Group QR code to join instantly.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                  OutlinedButton(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan QR")
                  }
                  Button(
                    onClick = onCreateGroup,
                    modifier = Modifier.weight(1f),
                  ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create")
                  }
                }
              }
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("group_list_items"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            items(groups, key = { it.id }) { group ->
              GlassCard(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onSelectGroup(group.id, group.name) }
                    .testTag("group_item_${group.id}"),
                cornerRadius = 18.dp,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Box(
                    modifier =
                      Modifier.size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                  ) {
                    Icon(
                      Icons.Default.Group,
                      contentDescription = null,
                      tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                  Spacer(modifier = Modifier.width(14.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                      )
                      Spacer(modifier = Modifier.width(8.dp))
                      Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(10.dp),
                      ) {
                        Text(
                          text = "${group.participantUids.size} members",
                          style = MaterialTheme.typography.labelSmall,
                          color = MaterialTheme.colorScheme.onSecondaryContainer,
                          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                      }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                      text = group.lastMessageText ?: "Group created",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GroupChatScreen(
  groupId: String,
  groupName: String,
  currentUser: UserProfile,
  viewModel: GroupViewModel,
  directoryUsersByUid: Map<String, UserIdDirectoryEntry> = emptyMap(),
  friends: List<UserProfile> = emptyList(),
  allGroups: List<GroupThread> = emptyList(),
  friendsList: List<FriendConnection> = emptyList(),
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val groupThread by viewModel.observeGroup(groupId).collectAsStateWithLifecycle(initialValue = null)
  val messages by viewModel.observeMessages(groupId).collectAsStateWithLifecycle(initialValue = emptyList())
  val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
  val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }
  val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
  var textInput by remember { mutableStateOf("") }
  var replyTarget by remember { mutableStateOf<GroupMessage?>(null) }
  var actionTargetMsg by remember { mutableStateOf<GroupMessage?>(null) }
  var forwardTargetMsg by remember { mutableStateOf<GroupMessage?>(null) }
  var showInfoDialog by remember { mutableStateOf(false) }
  var showQrShareDialog by remember { mutableStateOf(false) }
  var showAddMembersDialog by remember { mutableStateOf(false) }
  var showLeaveConfirm by remember { mutableStateOf(false) }
  val listState = rememberLazyListState()

  val resolvedGroupName = groupThread?.name?.takeIf { it.isNotBlank() } ?: groupName
  val memberCount = groupThread?.participantUids?.size ?: 1
  val friendsByUid = remember(friends) { friends.associateBy { it.uid } }
  val groupSharePayload =
    remember(groupId, groupThread?.inviteCode) {
      QrPayload.encodeGroupPayload(groupId, groupThread?.inviteCode)
    }

  val shareGroupInvite = {
    val shareBody =
      "Join my Friends Meet group \"$resolvedGroupName\"!\nScan the Group QR or enter invite link:\n$groupSharePayload"
    val sendIntent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Join $resolvedGroupName on Friends Meet")
        putExtra(Intent.EXTRA_TEXT, shareBody)
      }
    context.startActivity(Intent.createChooser(sendIntent, "Share Group Invite"))
  }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      snackbarHostState.showSnackbar(errorMessage!!)
      viewModel.clearError()
    }
  }
  LaunchedEffect(statusMessage) {
    if (statusMessage != null) {
      snackbarHostState.showSnackbar(statusMessage!!)
      viewModel.clearStatus()
    }
  }

  LaunchedEffect(messages.size) {
    if (messages.isNotEmpty()) {
      listState.animateScrollToItem(messages.size - 1)
    }
  }

  actionTargetMsg?.let { target ->
    AlertDialog(
      onDismissRequest = { actionTargetMsg = null },
      title = { Text(target.senderName, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(
            onClick = {
              replyTarget = target
              actionTargetMsg = null
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reply")
          }
          TextButton(
            onClick = {
              forwardTargetMsg = target
              actionTargetMsg = null
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Forward Message")
          }
          TextButton(
            onClick = {
              clipboardManager.setText(AnnotatedString(target.text))
              actionTargetMsg = null
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy Text")
          }
        }
      },
      confirmButton = { TextButton(onClick = { actionTargetMsg = null }) { Text("Close") } },
    )
  }

  forwardTargetMsg?.let { msg ->
    ForwardMessagesDialog(
      friends = friendsList,
      groups = allGroups.filter { it.id != groupId },
      selectedCount = 1,
      onSelectFriend = { friend ->
        viewModel.forwardMessageToFriend(friend, currentUser.fullName.ifBlank { "Member" }, msg)
      },
      onSelectGroup = { grp ->
        viewModel.forwardMessageToGroup(grp, currentUser.fullName.ifBlank { "Member" }, msg)
      },
      onDismiss = { forwardTargetMsg = null },
    )
  }

  if (showQrShareDialog) {
    val qrBitmap by
      produceState<Bitmap?>(initialValue = null, key1 = groupSharePayload) {
        value =
          withContext(Dispatchers.Default) {
            QrCodeGenerator.generateBitmap(groupSharePayload, 440)
          }
      }
    AlertDialog(
      onDismissRequest = { showQrShareDialog = false },
      title = { Text("Group QR & Share", fontWeight = FontWeight.Bold) },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            text = resolvedGroupName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          val bmp = qrBitmap
          if (bmp != null) {
            GlassCard(cornerRadius = 18.dp) {
              Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Group invite QR code",
                modifier = Modifier.padding(12.dp).size(196.dp).clip(RoundedCornerShape(12.dp)),
              )
            }
          } else {
            Box(modifier = Modifier.size(196.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
          Text(
            text = "Scan inside Group Chats to join instantly",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = {
                clipboardManager.setText(AnnotatedString(groupSharePayload))
              },
              modifier = Modifier.weight(1f),
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Copy Link")
            }
            Button(
              onClick = shareGroupInvite,
              modifier = Modifier.weight(1f).testTag("group_share_intent_btn"),
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Share")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showQrShareDialog = false }) { Text("Close") }
      },
    )
  }

  if (showInfoDialog) {
    val participants = groupThread?.participantUids ?: listOf(currentUser.uid)
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = { Text(resolvedGroupName, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          val desc = groupThread?.description
          if (!desc.isNullOrBlank()) {
            Text(
              text = desc,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = "Members (${participants.size})",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
          )
          LazyColumn(
            modifier = Modifier.heightIn(max = 200.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(participants, key = { it }) { uid ->
              val friend = friendsByUid[uid]
              val dir = directoryUsersByUid[uid]
              val name =
                when {
                  uid == currentUser.uid -> "${currentUser.fullName} (You)"
                  friend != null -> friend.fullName
                  dir != null -> dir.displayName
                  else -> "Member (${uid.take(6)})"
                }
              val photo =
                when {
                  uid == currentUser.uid -> currentUser.photoUrl
                  friend != null -> friend.photoUrl
                  else -> dir?.photoUrl
                }
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
              ) {
                UserAvatar(name = name, photoUrl = photo, size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = name,
                  style = MaterialTheme.typography.bodyMedium,
                  modifier = Modifier.weight(1f),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
          FilledTonalButton(
            onClick = {
              showInfoDialog = false
              showQrShareDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Group QR & Share Invite")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            FilledTonalButton(
              onClick = {
                showInfoDialog = false
                showAddMembersDialog = true
              },
              modifier = Modifier.weight(1f).testTag("group_add_members_btn"),
            ) {
              Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Add")
            }
            OutlinedButton(
              onClick = {
                showInfoDialog = false
                showLeaveConfirm = true
              },
              colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.weight(1f).testTag("group_leave_btn"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Leave")
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
      },
    )
  }

  if (showAddMembersDialog) {
    val existing = (groupThread?.participantUids ?: emptyList()).toSet()
    val candidates =
      remember(friends, directoryUsersByUid, existing, currentUser.uid) {
        val merged = linkedMapOf<String, GroupSelectableUser>()
        friends.forEach { f ->
          if (f.uid !in existing && f.uid != currentUser.uid) {
            merged[f.uid] =
              GroupSelectableUser(
                uid = f.uid,
                friendsMeetUid = f.friendsMeetUid,
                displayName = f.fullName,
                photoUrl = f.photoUrl,
                online = f.online,
                isFriend = true,
              )
          }
        }
        directoryUsersByUid.values.forEach { d ->
          if (d.uid !in existing && d.uid != currentUser.uid && !merged.containsKey(d.uid)) {
            merged[d.uid] =
              GroupSelectableUser(
                uid = d.uid,
                friendsMeetUid = d.friendsMeetUid,
                displayName = d.displayName,
                photoUrl = d.photoUrl,
                online = d.online,
                isFriend = false,
              )
          }
        }
        merged.values.sortedWith(
          compareByDescending<GroupSelectableUser> { it.isFriend }
            .thenBy { it.displayName.lowercase() }
        )
      }
    val picked = remember { mutableStateListOf<String>() }
    AlertDialog(
      onDismissRequest = { showAddMembersDialog = false },
      title = { Text("Add Members") },
      text = {
        if (candidates.isEmpty()) {
          Text("All available users are already in this group.")
        } else {
          LazyColumn(
            modifier = Modifier.heightIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            items(candidates, key = { it.uid }) { candidate ->
              val isSelected = candidate.uid in picked
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      if (isSelected) picked.remove(candidate.uid) else picked.add(candidate.uid)
                    }
                    .padding(vertical = 4.dp),
              ) {
                UserAvatar(
                  name = candidate.displayName,
                  photoUrl = candidate.photoUrl,
                  size = 36.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(candidate.displayName, fontWeight = FontWeight.Medium)
                  Text(
                    "UID: ${candidate.friendsMeetUid}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Checkbox(
                  checked = isSelected,
                  onCheckedChange = {
                    if (isSelected) picked.remove(candidate.uid) else picked.add(candidate.uid)
                  },
                )
              }
            }
          }
        }
      },
      confirmButton = {
        Button(
          enabled = picked.isNotEmpty() && !isBusy,
          onClick = {
            viewModel.addMembers(groupId, picked.toList()) { showAddMembersDialog = false }
          },
        ) {
          Text("Add (${picked.size})")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddMembersDialog = false }) { Text("Cancel") }
      },
    )
  }

  if (showLeaveConfirm) {
    AlertDialog(
      onDismissRequest = { showLeaveConfirm = false },
      title = { Text("Leave Group?") },
      text = { Text("You will stop receiving messages from $resolvedGroupName.") },
      confirmButton = {
        Button(
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          enabled = !isBusy,
          onClick = {
            viewModel.leaveGroup(groupId) {
              showLeaveConfirm = false
              onBack()
            }
          },
        ) {
          Text("Leave")
        }
      },
      dismissButton = {
        TextButton(onClick = { showLeaveConfirm = false }) { Text("Cancel") }
      },
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showInfoDialog = true },
          ) {
            Box(
              modifier =
                Modifier.size(38.dp)
                  .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                Icons.Default.Group,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                resolvedGroupName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                "$memberCount members · Tap for info",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("group_chat_back_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(
            onClick = { showQrShareDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("group_qr_share_btn"),
          ) {
            Icon(Icons.Default.QrCode2, contentDescription = "Group QR & Share")
          }
          IconButton(
            onClick = { showInfoDialog = true },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("group_info_btn"),
          ) {
            Icon(Icons.Default.Info, contentDescription = "Group info")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
          ),
      )
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 4.dp,
      ) {
        Column(
          modifier =
            Modifier.fillMaxWidth()
              .animateContentSize(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
              )
              .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
          replyTarget?.let { reply ->
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .padding(bottom = 6.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(MaterialTheme.colorScheme.secondaryContainer)
                  .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Replying to ${reply.senderName}: ${reply.text}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
              )
              IconButton(
                onClick = { replyTarget = null },
                modifier = Modifier.size(28.dp),
              ) {
                Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(16.dp))
              }
            }
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            OutlinedTextField(
              value = textInput,
              onValueChange = { textInput = it },
              placeholder = { Text("Type group message...") },
              modifier = Modifier.weight(1f).testTag("group_message_input"),
              shape = RoundedCornerShape(24.dp),
              colors =
                OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = MaterialTheme.colorScheme.primary,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
              maxLines = 4,
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = {
                if (textInput.isNotBlank()) {
                  viewModel.sendMessage(
                    groupId = groupId,
                    senderName = currentUser.fullName.ifBlank { "Member" },
                    text = textInput.trim(),
                    replyTo = replyTarget,
                  )
                  textInput = ""
                  replyTarget = null
                }
              },
              modifier =
                Modifier.size(48.dp)
                  .background(MaterialTheme.colorScheme.primary, CircleShape)
                  .testTag("group_send_btn"),
            ) {
              Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = MaterialTheme.colorScheme.onPrimary,
              )
            }
          }
        }
      }
    },
  ) { padding ->
    FriendsMeetBackdrop(modifier = Modifier.fillMaxSize().padding(padding)) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("group_messages_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
      ) {
        items(messages, key = { it.id }) { msg ->
          val isMe = msg.senderUid == currentUser.uid
          val timeStr = remember(msg.createdAt) {
            msg.createdAt?.toDate()?.let { timeFormat.format(it) } ?: ""
          }
          GroupSwipeableMessageBubble(
            msg = msg,
            isMe = isMe,
            timeStr = timeStr,
            onSwipeReply = { replyTarget = msg },
            onClick = { actionTargetMsg = msg },
            onLongClick = { actionTargetMsg = msg },
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupSwipeableMessageBubble(
  msg: GroupMessage,
  isMe: Boolean,
  timeStr: String,
  onSwipeReply: () -> Unit,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
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
      label = "group_swipe_reply_x",
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

    Box(
      modifier =
        Modifier.fillMaxWidth()
          .offset { IntOffset(animatedDragX.roundToInt(), 0) },
      contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
      GlassCard(
        modifier =
          Modifier.widthIn(max = 285.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        cornerRadius = 16.dp,
        containerColor =
          if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
          else MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
          if (msg.forwardedCount > 0L) {
            Text(
              text = "Forwarded",
              style = MaterialTheme.typography.labelSmall,
              fontStyle = FontStyle.Italic,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (!isMe) {
            Text(
              text = msg.senderName,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
          }
          if (!msg.replyToText.isNullOrBlank()) {
            Surface(
              color = Color.Black.copy(alpha = 0.08f),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            ) {
              Column(modifier = Modifier.padding(6.dp)) {
                msg.replyToSenderName?.takeIf { it.isNotBlank() }?.let { rName ->
                  Text(
                    text = rName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                Text(
                  text = msg.replyToText,
                  style = MaterialTheme.typography.labelSmall,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
          Text(text = msg.text, style = MaterialTheme.typography.bodyMedium)
          if (timeStr.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = timeStr,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.align(Alignment.End),
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
  friends: List<UserProfile>,
  directoryUsers: List<UserIdDirectoryEntry> = emptyList(),
  isBusy: Boolean = false,
  errorMessage: String? = null,
  onClearError: () -> Unit = {},
  onCreate: (String, String?, List<String>) -> Unit,
  onBack: () -> Unit,
) {
  var groupName by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var searchQuery by remember { mutableStateOf("") }
  val selectedMembers = remember { mutableStateListOf<String>() }
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(errorMessage) {
    if (errorMessage != null) {
      snackbarHostState.showSnackbar(errorMessage)
      onClearError()
    }
  }

  val allUsers =
    remember(friends, directoryUsers) {
      val map = linkedMapOf<String, GroupSelectableUser>()
      friends.forEach { f ->
        map[f.uid] =
          GroupSelectableUser(
            uid = f.uid,
            friendsMeetUid = f.friendsMeetUid,
            displayName = f.fullName,
            photoUrl = f.photoUrl,
            online = f.online,
            isFriend = true,
          )
      }
      directoryUsers.forEach { d ->
        if (!map.containsKey(d.uid)) {
          map[d.uid] =
            GroupSelectableUser(
              uid = d.uid,
              friendsMeetUid = d.friendsMeetUid,
              displayName = d.displayName,
              photoUrl = d.photoUrl,
              online = d.online,
              isFriend = false,
            )
        }
      }
      map.values.sortedWith(
        compareByDescending<GroupSelectableUser> { it.isFriend }
          .thenByDescending { it.online }
          .thenBy { it.displayName.lowercase() }
      )
    }

  val filteredUsers =
    remember(allUsers, searchQuery) {
      if (searchQuery.isBlank()) allUsers
      else {
        val q = searchQuery.trim()
        allUsers.filter {
          it.displayName.contains(q, ignoreCase = true) ||
            it.friendsMeetUid.contains(q, ignoreCase = true)
        }
      }
    }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text("New Group Chat", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("create_group_back_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
          ),
      )
    },
    bottomBar = {
      Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
      ) {
        Button(
          onClick = {
            if (groupName.isNotBlank() && !isBusy) {
              onCreate(
                groupName.trim(),
                description.takeIf { it.isNotBlank() },
                selectedMembers.toList(),
              )
            }
          },
          modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("create_group_submit_btn"),
          shape = RoundedCornerShape(16.dp),
          enabled = groupName.isNotBlank() && selectedMembers.isNotEmpty() && !isBusy,
        ) {
          if (isBusy) {
            CircularProgressIndicator(
              modifier = Modifier.size(18.dp),
              strokeWidth = 2.dp,
              color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.width(8.dp))
          }
          Text("Create Group (${selectedMembers.size} selected)")
        }
      }
    },
  ) { padding ->
    FriendsMeetBackdrop(modifier = Modifier.fillMaxSize().padding(padding)) {
      Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
          value = groupName,
          onValueChange = { groupName = it.take(80) },
          label = { Text("Group Name") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("create_group_name_input"),
          shape = RoundedCornerShape(14.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = description,
          onValueChange = { description = it.take(300) },
          label = { Text("Description (Optional)") },
          modifier = Modifier.fillMaxWidth().testTag("create_group_desc_input"),
          shape = RoundedCornerShape(14.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            "Select Participants",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          Text(
            "${filteredUsers.size} available",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search friends or directory UID…") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (filteredUsers.isEmpty()) {
          Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
              "No matching users found",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(filteredUsers, key = { it.uid }) { candidate ->
              val isSelected = selectedMembers.contains(candidate.uid)
              GlassCard(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable {
                      if (isSelected) selectedMembers.remove(candidate.uid)
                      else selectedMembers.add(candidate.uid)
                    },
                cornerRadius = 14.dp,
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  UserAvatar(
                    name = candidate.displayName,
                    photoUrl = candidate.photoUrl,
                    size = 40.dp,
                    showOnlineIndicator = true,
                    online = candidate.online,
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                      Text(
                        candidate.displayName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                      )
                      if (!candidate.isFriend) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                          color = MaterialTheme.colorScheme.secondaryContainer,
                          shape = RoundedCornerShape(6.dp),
                        ) {
                          Text(
                            text = "Directory",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                          )
                        }
                      }
                    }
                    Text(
                      "UID: ${candidate.friendsMeetUid}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                  }
                  Checkbox(
                    checked = isSelected,
                    onCheckedChange = {
                      if (isSelected) selectedMembers.remove(candidate.uid)
                      else selectedMembers.add(candidate.uid)
                    },
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
