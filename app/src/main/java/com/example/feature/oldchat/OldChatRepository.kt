package com.example.feature.oldchat

import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.model.UserIdDirectoryEntry
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class OldChatRepository(private val firestore: FirebaseFirestore) {

  fun observeAllDirectoryUsers(currentUid: String): Flow<List<UserIdDirectoryEntry>> = callbackFlow {
    val listener =
      firestore.collection(FirestorePaths.USER_IDS).addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, OperationType.LIST, FirestorePaths.USER_IDS)
          return@addSnapshotListener
        }
        val users =
          snap
            ?.documents
            ?.mapNotNull { it.toObject(UserIdDirectoryEntry::class.java) }
            ?.filter { it.uid != currentUid && it.uid.isNotBlank() }
            ?.sortedWith(
              compareByDescending<UserIdDirectoryEntry> { it.online }
                .thenBy { it.displayName.lowercase() }
            ) ?: emptyList()
        trySend(users)
      }
    awaitClose { listener.remove() }
  }
}
