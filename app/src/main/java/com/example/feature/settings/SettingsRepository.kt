package com.example.feature.settings

import android.net.Uri
import android.util.Base64
import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.firebase.StorageRepository
import com.example.core.model.BackupMetadata
import com.example.core.model.FriendTag
import com.example.core.model.PrivacySettings
import com.example.core.model.UserProfile
import com.example.core.model.UserSettings
import com.example.core.security.E2eeCryptoManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class SettingsRepository(
  private val firestore: FirebaseFirestore,
  private val storageRepository: StorageRepository,
  private val e2eeCryptoManager: E2eeCryptoManager,
) {
  fun observeBackupMetadata(uid: String): Flow<BackupMetadata?> = callbackFlow {
    val reg =
      firestore.collection(FirestorePaths.BACKUP_METADATA).document(uid).addSnapshotListener {
        snap,
        err ->
        if (err != null) {
          handleFirestoreError(err, OperationType.GET, "${FirestorePaths.BACKUP_METADATA}/$uid")
          return@addSnapshotListener
        }
        trySend(snap?.takeIf { it.exists() }?.toObject(BackupMetadata::class.java))
      }
    awaitClose { reg.remove() }
  }

  suspend fun updateProfile(
    profile: UserProfile,
    fullName: String,
    bio: String?,
    phoneNumber: String?,
    newPhotoUri: Uri?,
  ) {
    val uploadedUrl =
      if (newPhotoUri != null) {
        storageRepository.uploadProfilePhoto(profile.uid, newPhotoUri)
      } else {
        profile.photoUrl
      }

    val userUpdates =
      mutableMapOf<String, Any>(
        "fullName" to fullName.trim(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (!bio.isNullOrBlank()) userUpdates["bio"] = bio.trim()
    if (!phoneNumber.isNullOrBlank()) userUpdates["phoneNumber"] = phoneNumber.trim()
    if (!uploadedUrl.isNullOrBlank()) userUpdates["photoUrl"] = uploadedUrl

    firestore.collection(FirestorePaths.USERS).document(profile.uid).update(userUpdates).await()

    val dirUpdates = mutableMapOf<String, Any>("displayName" to fullName.trim())
    if (!uploadedUrl.isNullOrBlank()) dirUpdates["photoUrl"] = uploadedUrl
    firestore
      .collection(FirestorePaths.USER_IDS)
      .document(profile.friendsMeetUid)
      .update(dirUpdates)
      .await()
  }

  suspend fun updatePrivacySettings(uid: String, settings: PrivacySettings) {
    firestore
      .collection(FirestorePaths.PRIVACY_SETTINGS)
      .document(uid)
      .set(
        mapOf(
          "uid" to uid,
          "profileVisibility" to settings.profileVisibility,
          "photoVisibility" to settings.photoVisibility,
          "dobVisibility" to settings.dobVisibility,
          "onlineStatusVisibility" to settings.onlineStatusVisibility,
          "lastSeenVisibility" to settings.lastSeenVisibility,
          "bioVisibility" to settings.bioVisibility,
          "friendListVisibility" to settings.friendListVisibility,
          "phoneVisibility" to settings.phoneVisibility,
          "emailVisibility" to settings.emailVisibility,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun updateQuickMessages(uid: String, messages: List<String>) {
    firestore
      .collection(FirestorePaths.USER_SETTINGS)
      .document(uid)
      .update(
        mapOf(
          "quickMessages" to messages.filter { it.isNotBlank() }.take(30),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun updateTranslationAutoDetect(uid: String, enabled: Boolean) {
    firestore
      .collection(FirestorePaths.USER_SETTINGS)
      .document(uid)
      .update(
        mapOf(
          "translationAutoDetect" to enabled,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun createEncryptedBackup(uid: String, passphrase: String): BackupMetadata {
    require(passphrase.length >= 6) { "Use a backup passphrase of at least 6 characters." }

    val friendsSnap =
      firestore.collection(FirestorePaths.FRIENDS).whereEqualTo("ownerUid", uid).get().await()
    val tagsSnap =
      firestore.collection(FirestorePaths.FRIEND_TAGS).whereEqualTo("ownerUid", uid).get().await()
    val chatsSnap =
      firestore
        .collection(FirestorePaths.CHATS)
        .whereArrayContains("participantUids", uid)
        .get()
        .await()
    val settingsSnap = firestore.collection(FirestorePaths.USER_SETTINGS).document(uid).get().await()
    val privSnap = firestore.collection(FirestorePaths.PRIVACY_SETTINGS).document(uid).get().await()

    var totalMessagesCount = 0L
    val chatMessagesArray = JSONArray()
    for (chatDoc in chatsSnap.documents) {
      val msgsSnap =
        chatDoc.reference
          .collection(FirestorePaths.MESSAGES)
          .whereArrayContains("participantUids", uid)
          .limit(200)
          .get()
          .await()
      totalMessagesCount += msgsSnap.size()
      val msgArr = JSONArray()
      for (m in msgsSnap.documents) {
        msgArr.put(
          JSONObject().apply {
            put("id", m.id)
            put("chatId", chatDoc.id)
            put("ciphertext", m.getString("ciphertext").orEmpty())
            put("iv", m.getString("iv").orEmpty())
            put("messageType", m.getString("messageType").orEmpty())
          }
        )
      }
      chatMessagesArray.put(JSONObject().apply {
        put("chatId", chatDoc.id)
        put("messages", msgArr)
      })
    }

    val tagsArr = JSONArray()
    for (t in tagsSnap.documents) {
      t.toObject(FriendTag::class.java)?.let { tag ->
        tagsArr.put(
          JSONObject().apply {
            put("friendUid", tag.friendUid)
            put("category", tag.category)
            put("label", tag.label)
            put("colorHex", tag.colorHex)
          }
        )
      }
    }

    val archiveJson =
      JSONObject().apply {
        put("version", 1)
        put("uid", uid)
        put("friendCount", friendsSnap.size())
        put("tags", tagsArr)
        put("chats", chatMessagesArray)
        put("quickMessages", JSONArray(settingsSnap.toObject(UserSettings::class.java)?.quickMessages ?: emptyList<String>()))
        put("themeMode", settingsSnap.getString("themeMode") ?: "SYSTEM")
        put("profileVisibility", privSnap.getString("profileVisibility") ?: "EVERYONE")
      }

    val (backupKey, checksumHex) = e2eeCryptoManager.deriveBackupKey(passphrase, uid)
    val (cipherBytes, ivBase64) =
      e2eeCryptoManager.encryptBytes(archiveJson.toString().toByteArray(Charsets.UTF_8), backupKey)
    val envelope =
      JSONObject()
        .apply {
          put("iv", ivBase64)
          put("payload", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        }
        .toString()
        .toByteArray(Charsets.UTF_8)

    val (storagePath, sizeBytes) = storageRepository.uploadEncryptedBackup(uid, envelope)

    val metaMap =
      mapOf(
        "ownerUid" to uid,
        "storagePath" to storagePath,
        "keyChecksumSha256" to checksumHex,
        "encryptionAlgorithm" to "AES-256-GCM",
        "messageCount" to totalMessagesCount,
        "sizeBytes" to sizeBytes,
        "lastBackupAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    firestore.collection(FirestorePaths.BACKUP_METADATA).document(uid).set(metaMap).await()
    return BackupMetadata(
      id = uid,
      ownerUid = uid,
      storagePath = storagePath,
      keyChecksumSha256 = checksumHex,
      encryptionAlgorithm = "AES-256-GCM",
      messageCount = totalMessagesCount,
      sizeBytes = sizeBytes,
    )
  }

  suspend fun restoreEncryptedBackup(uid: String, passphrase: String): Int {
    val metaSnap = firestore.collection(FirestorePaths.BACKUP_METADATA).document(uid).get().await()
    val meta = metaSnap.toObject(BackupMetadata::class.java)
      ?: throw IllegalStateException("No cloud backup found for this account.")
    val (backupKey, checksumHex) = e2eeCryptoManager.deriveBackupKey(passphrase, uid)
    require(checksumHex == meta.keyChecksumSha256) {
      "Incorrect backup passphrase (checksum mismatch)."
    }

    val envelopeBytes = storageRepository.downloadEncryptedBackup(meta.storagePath)
    val envelope = JSONObject(String(envelopeBytes, Charsets.UTF_8))
    val iv = envelope.getString("iv")
    val cipherBytes = Base64.decode(envelope.getString("payload"), Base64.NO_WRAP)
    val plainJson =
      JSONObject(String(e2eeCryptoManager.decryptBytes(cipherBytes, iv, backupKey), Charsets.UTF_8))

    val quickList = mutableListOf<String>()
    val qArr = plainJson.optJSONArray("quickMessages")
    if (qArr != null) {
      for (i in 0 until qArr.length()) quickList.add(qArr.getString(i))
    }
    if (quickList.isNotEmpty()) {
      updateQuickMessages(uid, quickList)
    }

    val tagsArr = plainJson.optJSONArray("tags")
    var restoredTags = 0
    if (tagsArr != null) {
      for (i in 0 until tagsArr.length()) {
        val obj = tagsArr.getJSONObject(i)
        val fUid = obj.getString("friendUid")
        val tagId = FirestorePaths.friendTagId(uid, fUid)
        firestore
          .collection(FirestorePaths.FRIEND_TAGS)
          .document(tagId)
          .set(
            mapOf(
              "ownerUid" to uid,
              "friendUid" to fUid,
              "category" to obj.getString("category"),
              "label" to obj.getString("label"),
              "colorHex" to obj.getString("colorHex"),
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
        restoredTags++
      }
    }
    return restoredTags
  }
}
