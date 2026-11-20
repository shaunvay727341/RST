package com.example.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
  repository: SettingsRepository,
  myUid: String,
  onBack: () -> Unit,
) {
  val meta by repository.observeBackupMetadata(myUid).collectAsStateWithLifecycle(null)
  var passphrase by remember { mutableStateOf("") }
  var isBusy by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("backup_back_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = { Text("Encrypted Backup & Restore") },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = if (meta == null) "No Cloud Backup Yet" else "Latest Encrypted Backup",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
          )
          if (meta != null) {
            Text("Algorithm: ${meta!!.encryptionAlgorithm}")
            Text("Messages covered: ${meta!!.messageCount}")
            Text("Archive size: ${meta!!.sizeBytes} bytes")
            meta!!.lastBackupAt?.toDate()?.let { date ->
              Text(
                "Updated: ${SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(date)}"
              )
            }
          } else {
            Text(
              "Your chats, friend tags, quick messages & privacy settings are encrypted locally with AES-256-GCM before upload.",
              style = MaterialTheme.typography.bodySmall,
            )
          }
        }
      }

      OutlinedTextField(
        value = passphrase,
        onValueChange = { passphrase = it },
        label = { Text("Backup Passphrase (min 6 characters)") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("backup_passphrase_input"),
      )

      Button(
        onClick = {
          isBusy = true
          scope.launch {
            try {
              val res = repository.createEncryptedBackup(myUid, passphrase)
              snackbarHostState.showSnackbar("Encrypted backup saved (${res.sizeBytes} bytes)")
            } catch (e: Exception) {
              snackbarHostState.showSnackbar(e.localizedMessage ?: "Backup failed")
            } finally {
              isBusy = false
            }
          }
        },
        enabled = !isBusy && passphrase.length >= 6,
        modifier =
          Modifier.fillMaxWidth().height(52.dp).minimumInteractiveComponentSize().testTag("create_backup_btn"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
          Icon(Icons.Default.CloudUpload, contentDescription = null)
          Spacer(modifier = Modifier.size(8.dp))
          Text("Create Encrypted Backup")
        }
      }

      OutlinedButton(
        onClick = {
          isBusy = true
          scope.launch {
            try {
              val count = repository.restoreEncryptedBackup(myUid, passphrase)
              snackbarHostState.showSnackbar("Restored $count friend tag(s) & settings")
            } catch (e: Exception) {
              snackbarHostState.showSnackbar(e.localizedMessage ?: "Restore failed")
            } finally {
              isBusy = false
            }
          }
        },
        enabled = !isBusy && meta != null && passphrase.length >= 6,
        modifier =
          Modifier.fillMaxWidth().height(52.dp).minimumInteractiveComponentSize().testTag("restore_backup_btn"),
      ) {
        Icon(Icons.Default.Restore, contentDescription = null)
        Spacer(modifier = Modifier.size(8.dp))
        Text("Restore Encrypted Backup")
      }
    }
  }
}
