package com.example.feature.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Translate
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.datastore.ThemeMode
import com.example.core.model.UserProfile
import com.example.core.model.UserSettings
import com.example.core.notifications.OneSignalStatus
import com.example.core.qr.QrCodeGenerator
import com.example.core.qr.QrPayload
import com.example.feature.friends.QuickMessagesPickerDialog
import com.example.ui.components.FriendsMeetBackdrop
import com.example.ui.components.GlassCard
import com.example.ui.components.UserAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  profile: UserProfile,
  userSettings: UserSettings?,
  themeMode: ThemeMode,
  oneSignalStatus: OneSignalStatus,
  settingsRepository: SettingsRepository,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onEnablePushNotifications: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenBackup: () -> Unit,
  onOpenLinkedDevices: () -> Unit,
  onSignOut: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var showQrDialog by remember { mutableStateOf(false) }
  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showQuickMsgManager by remember { mutableStateOf(false) }

  if (showQrDialog) {
    val bitmap by
      produceState<Bitmap?>(initialValue = null, key1 = profile.friendsMeetUid) {
        value =
          withContext(Dispatchers.Default) {
            QrCodeGenerator.generateBitmap(
              QrPayload.encodeFriendPayload(profile.friendsMeetUid),
              420,
            )
          }
      }
    AlertDialog(
      onDismissRequest = { showQrDialog = false },
      title = { Text("My Friends Meet QR") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          val bmp = bitmap
          if (bmp != null) {
            Image(
              bitmap = bmp.asImageBitmap(),
              contentDescription = "My Friends Meet QR Code",
              modifier = Modifier.size(200.dp).clip(RoundedCornerShape(14.dp)),
            )
          } else {
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
              CircularProgressIndicator()
            }
          }
          Text(
            text = "UID: ${profile.friendsMeetUid}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
          )
        }
      },
      confirmButton = { TextButton(onClick = { showQrDialog = false }) { Text("Close") } },
    )
  }

  if (showEditProfileDialog) {
    var nameInput by remember { mutableStateOf(profile.fullName) }
    var bioInput by remember { mutableStateOf(profile.bio.orEmpty()) }
    var phoneInput by remember { mutableStateOf(profile.phoneNumber.orEmpty()) }
    var pickedPhoto by remember { mutableStateOf<Uri?>(null) }
    val photoPicker =
      rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedPhoto = uri
      }
    AlertDialog(
      onDismissRequest = { showEditProfileDialog = false },
      title = { Text("Edit Profile") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedButton(
            onClick = {
              photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(if (pickedPhoto == null) "Change Profile Photo" else "New Photo Selected ✓")
          }
          OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it.take(80) },
            label = { Text("Full Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = bioInput,
            onValueChange = { bioInput = it.take(240) },
            label = { Text("Bio") },
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it.take(24) },
            label = { Text("Phone (Optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            scope.launch {
              try {
                settingsRepository.updateProfile(profile, nameInput, bioInput, phoneInput, pickedPhoto)
                showEditProfileDialog = false
                snackbarHostState.showSnackbar("Profile updated")
              } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.localizedMessage ?: "Failed to update profile")
              }
            }
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = { TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") } },
    )
  }

  if (showQuickMsgManager) {
    QuickMessagesPickerDialog(
      friendName = null,
      quickMessages = userSettings?.quickMessages ?: emptyList(),
      onSendMessage = null,
      onSaveQuickMessages = { updated ->
        scope.launch { settingsRepository.updateQuickMessages(profile.uid, updated) }
      },
      onDismiss = { showQuickMsgManager = false },
    )
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text("Settings", fontWeight = FontWeight.Bold) },
        actions = {
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_sign_out_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
          }
        },
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
          ),
      )
    },
  ) { innerPadding ->
    FriendsMeetBackdrop(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          GlassCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 22.dp,
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
            UserAvatar(
              name = profile.fullName,
              photoUrl = profile.photoUrl,
              size = 64.dp,
              showOnlineIndicator = true,
              online = profile.online,
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = profile.fullName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
              )
              Text(
                text = "UID: ${profile.friendsMeetUid}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
              )
              if (!profile.bio.isNullOrBlank()) {
                Text(
                  text = profile.bio,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
              }
            }
            IconButton(
              onClick = { showQrDialog = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_my_qr_btn"),
            ) {
              Icon(Icons.Default.QrCode2, contentDescription = "Show my QR code")
            }
            IconButton(
              onClick = { showEditProfileDialog = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("settings_edit_profile_btn"),
            ) {
              Icon(Icons.Default.Edit, contentDescription = "Edit profile")
            }
          }
        }
      }

      item {
        SettingsRowCard(
          icon = Icons.Default.Lock,
          title = "Privacy & Blocked Users",
          subtitle = "Control who sees your photo, DOB, status, phone & email",
          onClick = onOpenPrivacy,
          testTag = "settings_privacy_row",
        )
      }
      item {
        SettingsRowCard(
          icon = Icons.Default.Backup,
          title = "Encrypted Backup & Restore",
          subtitle = "AES-256-GCM encrypted cloud backup on Firebase Storage",
          onClick = onOpenBackup,
          testTag = "settings_backup_row",
        )
      }
      item {
        SettingsRowCard(
          icon = Icons.Default.Devices,
          title = "Linked Devices",
          subtitle = "Manage up to 2 additional devices & QR pairing",
          onClick = onOpenLinkedDevices,
          testTag = "settings_devices_row",
        )
      }
      item {
        SettingsRowCard(
          icon = Icons.Default.Bolt,
          title = "Quick Messages",
          subtitle = "Create, edit, delete & reorder custom quick messages",
          onClick = { showQuickMsgManager = true },
          testTag = "settings_quick_msg_row",
        )
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Default.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text("Auto-Detect Bengali / English", style = MaterialTheme.typography.titleMedium)
              Text(
                "One-tap translation inside every 1-to-1 chat",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = userSettings?.translationAutoDetect ?: true,
              onCheckedChange = { enabled ->
                scope.launch { settingsRepository.updateTranslationAutoDetect(profile.uid, enabled) }
              },
            )
          }
        }
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = if (themeMode == ThemeMode.DARK) Icons.Default.DarkMode else Icons.Default.LightMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Appearance Theme", style = MaterialTheme.typography.titleMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              ThemeMode.entries.forEach { mode ->
                FilterChip(
                  selected = themeMode == mode,
                  onClick = { onSelectThemeMode(mode) },
                  label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                  modifier = Modifier.minimumInteractiveComponentSize(),
                )
              }
            }
          }
        }
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text =
                  if (oneSignalStatus.permissionGranted) "Push Notifications Active"
                  else "Push Notifications Disabled",
                style = MaterialTheme.typography.titleMedium,
              )
            }
            if (!oneSignalStatus.permissionGranted) {
              Button(onClick = onEnablePushNotifications, modifier = Modifier.fillMaxWidth()) {
                Text("Enable Push Notifications")
              }
            }
          }
        }
      }
      }
    }
  }
}

@Composable
private fun SettingsRowCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  onClick: () -> Unit,
  testTag: String,
) {
  GlassCard(
    cornerRadius = 18.dp,
    modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(testTag),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
  }
}
