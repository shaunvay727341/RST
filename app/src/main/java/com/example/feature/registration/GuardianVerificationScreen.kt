package com.example.feature.registration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianVerificationScreen(
  viewModel: GuardianVerificationViewModel,
  friendsMeetUid: String,
  onOpenLetterbox: () -> Unit,
  onSignOut: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Guardian Verification") },
        actions = {
          IconButton(
            onClick = onSignOut,
            modifier =
              Modifier.minimumInteractiveComponentSize().testTag("guardian_sign_out_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 500.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Shield,
          contentDescription = "Guardian safety verification",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(56.dp),
        )
        Text(
          text = "Guardian Approval Required",
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
        )
        Text(
          text =
            "Because this profile belongs to a user under 13 (UID: $friendsMeetUid), a guardian must enter the 4-digit verification code delivered to your official in-app Letterbox (expires in 30 minutes, max 5 attempts).",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Card(
          colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "Step 1: Open your official Letterbox to read the 4-digit Guardian code.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = onOpenLetterbox,
              modifier =
                Modifier.fillMaxWidth()
                  .minimumInteractiveComponentSize()
                  .testTag("open_letterbox_from_guardian_button"),
            ) {
              Icon(Icons.Default.MarkEmailUnread, contentDescription = null)
              Spacer(modifier = Modifier.size(8.dp))
              Text("Open Official Letterbox")
            }
          }
        }

        OutlinedTextField(
          value = state.code,
          onValueChange = viewModel::updateCode,
          label = { Text("4-Digit Guardian Code") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("guardian_code_input"),
        )

        state.errorMessage?.let { msg ->
          Text(
            text = msg,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
          )
        }

        Button(
          onClick = viewModel::submitVerificationCode,
          enabled = !state.isVerifying && state.code.length == 4,
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .minimumInteractiveComponentSize()
              .testTag("verify_guardian_code_button"),
        ) {
          if (state.isVerifying) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
          } else {
            Text("Verify & Activate Account")
          }
        }
      }
    }
  }
}
