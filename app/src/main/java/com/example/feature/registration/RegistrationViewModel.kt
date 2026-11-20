package com.example.feature.registration

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.Gender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegistrationUiState(
  val fullName: String = "",
  val dateOfBirthIso: String = "",
  val gender: Gender = Gender.OTHER,
  val phoneNumber: String = "",
  val selectedPhotoUri: Uri? = null,
  val isSubmitting: Boolean = false,
  val errorMessage: String? = null,
  val registeredUid: String? = null,
  val serverLetterMissingWarning: Boolean = false,
)

class RegistrationViewModel(
  private val repository: RegistrationRepository,
  private val uid: String,
  private val email: String,
  initialName: String,
  private val googlePhotoUrl: String?,
) : ViewModel() {
  private val _uiState = MutableStateFlow(RegistrationUiState(fullName = initialName))
  val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

  fun updateFullName(value: String) {
    _uiState.update { it.copy(fullName = value, errorMessage = null) }
  }

  fun updateDateOfBirth(isoDate: String) {
    _uiState.update { it.copy(dateOfBirthIso = isoDate, errorMessage = null) }
  }

  fun updateGender(gender: Gender) {
    _uiState.update { it.copy(gender = gender, errorMessage = null) }
  }

  fun updatePhoneNumber(value: String) {
    _uiState.update { it.copy(phoneNumber = value, errorMessage = null) }
  }

  fun updatePhotoUri(uri: Uri?) {
    _uiState.update { it.copy(selectedPhotoUri = uri, errorMessage = null) }
  }

  fun submitRegistration() {
    val current = _uiState.value
    if (current.fullName.trim().length < 2) {
      _uiState.update { it.copy(errorMessage = "Please enter your full name (at least 2 characters).") }
      return
    }
    if (!Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$").matches(current.dateOfBirthIso)) {
      _uiState.update { it.copy(errorMessage = "Please select a valid date of birth.") }
      return
    }

    _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
    viewModelScope.launch {
      try {
        val res =
          repository.completeRegistration(
            uid = uid,
            email = email,
            fullName = current.fullName,
            dateOfBirthIso = current.dateOfBirthIso,
            gender = current.gender,
            phoneNumber = current.phoneNumber,
            photoUri = current.selectedPhotoUri,
            googlePhotoUrl = googlePhotoUrl,
          )
        _uiState.update {
          it.copy(
            isSubmitting = false,
            registeredUid = res.friendsMeetUid,
            serverLetterMissingWarning = res.isUnder13 && !res.serverLetterDispatched,
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            isSubmitting = false,
            errorMessage = e.localizedMessage ?: "Registration failed. Please try again.",
          )
        }
      }
    }
  }
}

data class GuardianVerificationUiState(
  val code: String = "",
  val isVerifying: Boolean = false,
  val errorMessage: String? = null,
  val verified: Boolean = false,
)

class GuardianVerificationViewModel(
  private val repository: RegistrationRepository,
) : ViewModel() {
  private val _uiState = MutableStateFlow(GuardianVerificationUiState())
  val uiState: StateFlow<GuardianVerificationUiState> = _uiState.asStateFlow()

  fun updateCode(value: String) {
    val digitsOnly = value.filter { it.isDigit() }.take(4)
    _uiState.update { it.copy(code = digitsOnly, errorMessage = null) }
  }

  fun submitVerificationCode() {
    val code = _uiState.value.code
    if (code.length != 4) {
      _uiState.update { it.copy(errorMessage = "Enter the 4-digit code from your Letterbox.") }
      return
    }
    _uiState.update { it.copy(isVerifying = true, errorMessage = null) }
    viewModelScope.launch {
      val res = repository.verifyGuardianCode(code)
      res.fold(
        onSuccess = { _uiState.update { it.copy(isVerifying = false, verified = true) } },
        onFailure = { e ->
          _uiState.update {
            it.copy(
              isVerifying = false,
              errorMessage =
                e.localizedMessage
                  ?: "Verification failed. Check attempts (max 5) or 30-min expiration.",
            )
          }
        },
      )
    }
  }
}
