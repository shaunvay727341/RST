package com.example.feature.registration

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.Gender
import com.example.ui.components.UserAvatar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
  viewModel: RegistrationViewModel,
  email: String,
  googlePhotoUrl: String?,
  onSignOut: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  var showDatePicker by remember { mutableStateOf(false) }

  val photoPickerLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) viewModel.updatePhotoUri(uri)
    }

  if (showDatePicker) {
    val datePickerState = rememberDatePickerState()
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            datePickerState.selectedDateMillis?.let { millis ->
              val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
              sdf.timeZone = TimeZone.getTimeZone("UTC")
              viewModel.updateDateOfBirth(sdf.format(Date(millis)))
            }
            showDatePicker = false
          },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("confirm_dob_button"),
        ) {
          Text("Confirm")
        }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      },
    ) {
      DatePicker(state = datePickerState)
    }
  }

  Scaffold(
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        title = { Text("Complete Profile") },
        actions = {
          IconButton(
            onClick = onSignOut,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("reg_sign_out_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Text(
          text = "Create your unique 8-digit Friends Meet UID and E2EE identity.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Box(
          contentAlignment = Alignment.BottomEnd,
          modifier =
            Modifier.clickable {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              }
              .testTag("pick_profile_photo_button"),
        ) {
          UserAvatar(
            name = state.fullName.ifBlank { email },
            photoUrl = state.selectedPhotoUri?.toString() ?: googlePhotoUrl,
            size = 96.dp,
          )
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            tonalElevation = 4.dp,
            modifier = Modifier.size(32.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Default.AddAPhoto,
                contentDescription = "Choose profile photo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }

        OutlinedTextField(
          value = state.fullName,
          onValueChange = viewModel::updateFullName,
          label = { Text("Full Name *") },
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("reg_full_name_input"),
        )

        OutlinedTextField(
          value = state.dateOfBirthIso,
          onValueChange = {},
          readOnly = true,
          label = { Text("Date of Birth (YYYY-MM-DD) *") },
          leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
          trailingIcon = {
            TextButton(
              onClick = { showDatePicker = true },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("open_dob_picker"),
            ) {
              Text("Select")
            }
          },
          modifier =
            Modifier.fillMaxWidth()
              .clickable { showDatePicker = true }
              .testTag("reg_dob_field"),
        )

        if (
          state.dateOfBirthIso.isNotBlank() &&
            RegistrationRepository.computeAgeYears(state.dateOfBirthIso) < 13
        ) {
          Card(
            colors =
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
              ),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text =
                "Under-13 account detected: Guardian verification via the official in-app Letterbox will be required.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onTertiaryContainer,
              modifier = Modifier.padding(12.dp),
            )
          }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Gender *",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Gender.entries.forEach { gender ->
              FilterChip(
                selected = state.gender == gender,
                onClick = { viewModel.updateGender(gender) },
                label = { Text(gender.name.lowercase().replaceFirstChar { it.uppercase() }) },
                modifier =
                  Modifier.minimumInteractiveComponentSize()
                    .testTag("gender_chip_${gender.name.lowercase()}"),
              )
            }
          }
        }

        OutlinedTextField(
          value = state.phoneNumber,
          onValueChange = viewModel::updatePhoneNumber,
          label = { Text("Phone Number (Optional)") },
          leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("reg_phone_input"),
        )

        OutlinedTextField(
          value = email,
          onValueChange = {},
          readOnly = true,
          enabled = false,
          label = { Text("Google Email (Verified)") },
          leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
          modifier = Modifier.fillMaxWidth(),
        )

        state.errorMessage?.let { msg ->
          Text(
            text = msg,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
          )
        }

        Button(
          onClick = viewModel::submitRegistration,
          enabled = !state.isSubmitting,
          modifier =
            Modifier.fillMaxWidth()
              .height(52.dp)
              .minimumInteractiveComponentSize()
              .testTag("submit_registration_button"),
        ) {
          if (state.isSubmitting) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.onPrimary,
              strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Creating Account…")
          } else {
            Text("Complete Registration")
          }
        }
      }
    }
  }
}
