package com.example.core.firebase

import android.content.Context
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Handles all binary asset uploads and downloads against Firebase Storage
 * adhering strictly to `storage.rules` paths and contentType/size constraints.
 */
class StorageRepository(
  private val context: Context,
  private val storage: FirebaseStorage,
) {
  suspend fun uploadProfilePhoto(uid: String, imageUri: Uri): String =
    withContext(Dispatchers.IO) {
      val bytes =
        context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
          ?: throw IllegalArgumentException("Unable to read selected image")
      val ref = storage.reference.child("profiles/$uid/avatar_${System.currentTimeMillis()}.jpg")
      val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
      ref.putBytes(bytes, metadata).await()
      ref.downloadUrl.await().toString()
    }

  suspend fun uploadChatImage(chatId: String, messageId: String, imageUri: Uri): String =
    withContext(Dispatchers.IO) {
      val bytes =
        context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
          ?: throw IllegalArgumentException("Unable to read selected image")
      val ref = storage.reference.child("chats/$chatId/images/$messageId/image.jpg")
      val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
      ref.putBytes(bytes, metadata).await()
      ref.downloadUrl.await().toString()
    }

  suspend fun uploadVoiceMessage(chatId: String, messageId: String, audioBytes: ByteArray): String =
    withContext(Dispatchers.IO) {
      val ref = storage.reference.child("chats/$chatId/voice/$messageId/voice.m4a")
      val metadata = StorageMetadata.Builder().setContentType("audio/mp4").build()
      ref.putBytes(audioBytes, metadata).await()
      ref.downloadUrl.await().toString()
    }

  suspend fun uploadEncryptedBackup(uid: String, encryptedBytes: ByteArray): Pair<String, Long> =
    withContext(Dispatchers.IO) {
      val path = "backups/$uid/latest.enc"
      val ref = storage.reference.child(path)
      val metadata = StorageMetadata.Builder().setContentType("application/octet-stream").build()
      ref.putBytes(encryptedBytes, metadata).await()
      path to encryptedBytes.size.toLong()
    }

  suspend fun downloadEncryptedBackup(storagePath: String): ByteArray =
    withContext(Dispatchers.IO) {
      val ref = storage.reference.child(storagePath)
      ref.getBytes(50L * 1024L * 1024L).await()
    }
}
