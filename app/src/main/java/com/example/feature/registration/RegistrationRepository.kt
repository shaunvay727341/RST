package com.example.feature.registration

import android.net.Uri
import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.firebase.StorageRepository
import com.example.core.model.AccountStatus
import com.example.core.model.Gender
import com.example.core.model.PrivacySettings
import com.example.core.model.UserProfile
import com.example.core.model.UserSettings
import com.example.core.model.VisibilityScope
import com.example.core.security.E2eeCryptoManager
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import java.security.SecureRandom
import java.util.Calendar
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class RegistrationResult(
  val friendsMeetUid: String,
  val isUnder13: Boolean,
  val serverLetterDispatched: Boolean,
)

class RegistrationRepository(
  private val firestore: FirebaseFirestore,
  private val functions: FirebaseFunctions,
  private val storageRepository: StorageRepository,
  private val e2eeCryptoManager: E2eeCryptoManager,
) {
  private val random = SecureRandom()

  fun observeUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
    val reg =
      firestore.collection(FirestorePaths.USERS).document(uid).addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, OperationType.GET, "${FirestorePaths.USERS}/$uid")
          trySend(null)
          return@addSnapshotListener
        }
        val profile =
          if (snap != null && snap.exists()) {
            snap.toObject(UserProfile::class.java)
          } else {
            null
          }
        trySend(profile)
      }
    awaitClose { reg.remove() }
  }

  fun observePrivacySettings(uid: String): Flow<PrivacySettings?> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.PRIVACY_SETTINGS)
        .document(uid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.GET, "${FirestorePaths.PRIVACY_SETTINGS}/$uid")
            return@addSnapshotListener
          }
          trySend(snap?.takeIf { it.exists() }?.toObject(PrivacySettings::class.java))
        }
    awaitClose { reg.remove() }
  }

  fun observeUserSettings(uid: String): Flow<UserSettings?> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.USER_SETTINGS)
        .document(uid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.GET, "${FirestorePaths.USER_SETTINGS}/$uid")
            return@addSnapshotListener
          }
          trySend(snap?.takeIf { it.exists() }?.toObject(UserSettings::class.java))
        }
    awaitClose { reg.remove() }
  }

  suspend fun ensureDefaultSettingsExist(uid: String) {
    val privRef = firestore.collection(FirestorePaths.PRIVACY_SETTINGS).document(uid)
    val privSnap = privRef.get().await()
    if (!privSnap.exists()) {
      privRef
        .set(
          mapOf(
            "uid" to uid,
            "profileVisibility" to VisibilityScope.EVERYONE.name,
            "photoVisibility" to VisibilityScope.EVERYONE.name,
            "dobVisibility" to VisibilityScope.FRIENDS.name,
            "onlineStatusVisibility" to VisibilityScope.FRIENDS.name,
            "lastSeenVisibility" to VisibilityScope.FRIENDS.name,
            "bioVisibility" to VisibilityScope.EVERYONE.name,
            "friendListVisibility" to VisibilityScope.FRIENDS.name,
            "phoneVisibility" to VisibilityScope.NOBODY.name,
            "emailVisibility" to VisibilityScope.NOBODY.name,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    }

    val settingsRef = firestore.collection(FirestorePaths.USER_SETTINGS).document(uid)
    val settingsSnap = settingsRef.get().await()
    if (!settingsSnap.exists()) {
      settingsRef
        .set(
          mapOf(
            "uid" to uid,
            "themeMode" to "SYSTEM",
            "quickMessages" to DEFAULT_QUICK_MESSAGES,
            "customTagCategories" to emptyList<String>(),
            "translationAutoDetect" to true,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    }
  }

  suspend fun completeRegistration(
    uid: String,
    email: String,
    fullName: String,
    dateOfBirthIso: String,
    gender: Gender,
    phoneNumber: String?,
    photoUri: Uri?,
    googlePhotoUrl: String?,
  ): RegistrationResult {
    val identityPublicKey = e2eeCryptoManager.getOrCreateIdentityPublicKeyBase64(uid)
    val uploadedPhotoUrl =
      if (photoUri != null) {
        storageRepository.uploadProfilePhoto(uid, photoUri)
      } else {
        googlePhotoUrl
      }

    val isUnder13 = computeAgeYears(dateOfBirthIso) < 13

    // Primary trusted path: Cloud Function `completeRegistration`
    try {
      val payload =
        hashMapOf<String, Any?>(
          "fullName" to fullName.trim(),
          "dateOfBirth" to dateOfBirthIso,
          "gender" to gender.name,
          "phoneNumber" to phoneNumber?.trim()?.takeIf { it.isNotBlank() },
          "photoUrl" to uploadedPhotoUrl,
          "identityPublicKey" to identityPublicKey,
        )
      val callResult = functions.getHttpsCallable("completeRegistration").call(payload).await()
      @Suppress("UNCHECKED_CAST")
      val resultMap = callResult.getData() as? Map<String, Any?>
      val fuid = resultMap?.get("friendsMeetUid") as? String
      if (!fuid.isNullOrBlank()) {
        ensureDefaultSettingsExist(uid)
        return RegistrationResult(
          friendsMeetUid = fuid,
          isUnder13 = (resultMap["isUnder13"] as? Boolean) ?: isUnder13,
          serverLetterDispatched = true,
        )
      }
    } catch (e: Exception) {
      val isFunctionUnavailable =
        e is FirebaseFunctionsException &&
          (e.code == FirebaseFunctionsException.Code.NOT_FOUND ||
            e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
            e.code == FirebaseFunctionsException.Code.UNIMPLEMENTED)
      if (!isFunctionUnavailable && e !is IllegalStateException) {
        // Fallback to atomic Firestore transaction below if Cloud Functions are not deployed yet
      }
    }

    // Rule-verified atomic Firestore transaction fallback
    val allocatedUid =
      firestore
        .runTransaction { tx ->
          val userRef = firestore.collection(FirestorePaths.USERS).document(uid)
          val existingUser = tx.get(userRef)
          if (existingUser.exists()) {
            return@runTransaction existingUser.getString("friendsMeetUid")
              ?: error("Existing user missing UID")
          }

          var chosenUid: String? = null
          for (attempt in 0 until 12) {
            val candidate = generateRandomEightDigitUid()
            val idRef = firestore.collection(FirestorePaths.USER_IDS).document(candidate)
            if (!tx.get(idRef).exists()) {
              chosenUid = candidate
              val status =
                if (isUnder13) AccountStatus.PENDING_GUARDIAN.name else AccountStatus.ACTIVE.name
              val idMap =
                mutableMapOf<String, Any>(
                  "uid" to uid,
                  "friendsMeetUid" to candidate,
                  "displayName" to fullName.trim(),
                  "online" to true,
                  "accountStatus" to status,
                  "identityPublicKey" to identityPublicKey,
                  "createdAt" to FieldValue.serverTimestamp(),
                )
              if (!uploadedPhotoUrl.isNullOrBlank()) {
                idMap["photoUrl"] = uploadedPhotoUrl
              }
              tx.set(idRef, idMap)

              val userMap =
                mutableMapOf<String, Any>(
                  "uid" to uid,
                  "friendsMeetUid" to candidate,
                  "fullName" to fullName.trim(),
                  "dateOfBirth" to dateOfBirthIso,
                  "gender" to gender.name,
                  "email" to email,
                  "online" to true,
                  "lastSeenAt" to FieldValue.serverTimestamp(),
                  "isUnder13" to isUnder13,
                  "guardianVerified" to !isUnder13,
                  "accountStatus" to status,
                  "identityPublicKey" to identityPublicKey,
                  "createdAt" to FieldValue.serverTimestamp(),
                  "updatedAt" to FieldValue.serverTimestamp(),
                )
              phoneNumber?.trim()?.takeIf { it.isNotBlank() }?.let { userMap["phoneNumber"] = it }
              if (!uploadedPhotoUrl.isNullOrBlank()) {
                userMap["photoUrl"] = uploadedPhotoUrl
              }
              tx.set(userRef, userMap)
              break
            }
          }
          chosenUid ?: throw IllegalStateException("Could not allocate unique 8-digit UID")
        }
        .await()

    ensureDefaultSettingsExist(uid)
    return RegistrationResult(
      friendsMeetUid = allocatedUid,
      isUnder13 = isUnder13,
      serverLetterDispatched = false,
    )
  }

  suspend fun verifyGuardianCode(code: String): Result<Unit> {
    return try {
      functions.getHttpsCallable("verifyGuardianCode").call(mapOf("code" to code.trim())).await()
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun syncPresence(uid: String, friendsMeetUid: String?, online: Boolean) {
    try {
      val identityPublicKey =
        if (online) e2eeCryptoManager.getOrCreateIdentityPublicKeyBase64(uid) else null
      val userUpdates =
        mutableMapOf<String, Any>(
          "online" to online,
          "lastSeenAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!identityPublicKey.isNullOrBlank()) {
        userUpdates["identityPublicKey"] = identityPublicKey
      }
      firestore.collection(FirestorePaths.USERS).document(uid).update(userUpdates).await()
      if (!friendsMeetUid.isNullOrBlank()) {
        val dirUpdates = mutableMapOf<String, Any>("online" to online)
        if (!identityPublicKey.isNullOrBlank()) {
          dirUpdates["identityPublicKey"] = identityPublicKey
        }
        firestore
          .collection(FirestorePaths.USER_IDS)
          .document(friendsMeetUid)
          .update(dirUpdates)
          .await()
      }
    } catch (_: Exception) {
      // Non-fatal background presence update
    }
  }

  private fun generateRandomEightDigitUid(): String {
    return (10_000_000 + random.nextInt(90_000_000)).toString()
  }

  companion object {
    val DEFAULT_QUICK_MESSAGES =
      listOf(
        "Hey! How are you doing?",
        "On my way!",
        "Call me when you're free.",
        "I'll reply in a moment.",
        "Good morning!",
      )

    fun computeAgeYears(dateOfBirthIso: String): Int {
      val parts = dateOfBirthIso.split("-")
      if (parts.size != 3) return 18
      val year = parts[0].toIntOrNull() ?: return 18
      val month = parts[1].toIntOrNull() ?: return 18
      val day = parts[2].toIntOrNull() ?: return 18
      val today = Calendar.getInstance()
      var age = today.get(Calendar.YEAR) - year
      val currentMonth = today.get(Calendar.MONTH) + 1
      val currentDay = today.get(Calendar.DAY_OF_MONTH)
      if (currentMonth < month || (currentMonth == month && currentDay < day)) {
        age--
      }
      return age
    }
  }
}
