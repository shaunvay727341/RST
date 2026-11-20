package com.example.feature.letterbox

import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.model.LetterboxMessage
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class LetterboxRepository(private val firestore: FirebaseFirestore) {

  fun observeMessages(uid: String, trashed: Boolean): Flow<List<LetterboxMessage>> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.LETTERBOX)
        .whereEqualTo("recipientUid", uid)
        .whereEqualTo("trashed", trashed)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.LETTERBOX)
            return@addSnapshotListener
          }
          val nowMs = System.currentTimeMillis()
          val list =
            snap
              ?.documents
              ?.mapNotNull { it.toObject(LetterboxMessage::class.java) }
              ?.filter { msg ->
                !trashed || msg.purgeAfterAt == null || msg.purgeAfterAt.toDate().time > nowMs
              } ?: emptyList()
          trySend(list)
        }
    awaitClose { reg.remove() }
  }

  suspend fun markRead(letterId: String) {
    firestore
      .collection(FirestorePaths.LETTERBOX)
      .document(letterId)
      .update(mapOf("read" to true, "updatedAt" to FieldValue.serverTimestamp()))
      .await()
  }

  suspend fun moveToTrash(letterId: String) {
    val thirtyDaysMs = 30L * 24L * 60L * 60L * 1000L
    val purgeDate = Timestamp(Date(System.currentTimeMillis() + thirtyDaysMs))
    firestore
      .collection(FirestorePaths.LETTERBOX)
      .document(letterId)
      .update(
        mapOf(
          "trashed" to true,
          "trashedAt" to FieldValue.serverTimestamp(),
          "purgeAfterAt" to purgeDate,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun restoreFromTrash(letterId: String) {
    firestore
      .collection(FirestorePaths.LETTERBOX)
      .document(letterId)
      .update(
        mapOf(
          "trashed" to false,
          "trashedAt" to FieldValue.delete(),
          "purgeAfterAt" to FieldValue.delete(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun deletePermanently(letterId: String) {
    firestore.collection(FirestorePaths.LETTERBOX).document(letterId).delete().await()
  }
}
