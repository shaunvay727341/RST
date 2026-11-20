package com.example.feature.friends

import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.model.AddedVia
import com.example.core.model.FriendConnection
import com.example.core.model.FriendTag
import com.example.core.model.UserIdDirectoryEntry
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FriendsRepository(
  private val firestore: FirebaseFirestore,
) {
  fun observeFriends(ownerUid: String): Flow<List<FriendConnection>> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.FRIENDS)
        .whereEqualTo("ownerUid", ownerUid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.FRIENDS)
            return@addSnapshotListener
          }
          val list =
            snap
              ?.documents
              ?.mapNotNull { it.toObject(FriendConnection::class.java) }
              ?.sortedWith(
                compareByDescending<FriendConnection> { it.friendOnline }
                  .thenBy { it.friendName.lowercase() }
              ) ?: emptyList()
          trySend(list)
        }
    awaitClose { reg.remove() }
  }

  fun observeFriendTags(ownerUid: String): Flow<Map<String, FriendTag>> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.FRIEND_TAGS)
        .whereEqualTo("ownerUid", ownerUid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.FRIEND_TAGS)
            return@addSnapshotListener
          }
          val map =
            snap
              ?.documents
              ?.mapNotNull { it.toObject(FriendTag::class.java) }
              ?.associateBy { it.friendUid } ?: emptyMap()
          trySend(map)
        }
    awaitClose { reg.remove() }
  }

  suspend fun lookupByFriendsMeetUid(friendsMeetUid: String): UserIdDirectoryEntry? {
    val cleaned = friendsMeetUid.trim()
    if (!Regex("^[1-9][0-9]{7}$").matches(cleaned)) return null
    return try {
      val snap = firestore.collection(FirestorePaths.USER_IDS).document(cleaned).get().await()
      if (snap.exists()) snap.toObject(UserIdDirectoryEntry::class.java) else null
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "${FirestorePaths.USER_IDS}/$cleaned")
      null
    }
  }

  /**
   * Immediately connects a friend (no pending request) and creates the 1-to-1 chat thread if missing,
   * returning the canonical chatId so the caller opens ChatScreen immediately.
   */
  suspend fun addFriendAndOpenChat(
    ownerUid: String,
    peer: UserIdDirectoryEntry,
    addedVia: AddedVia,
  ): String {
    require(ownerUid != peer.uid) { "You cannot add yourself as a friend." }
    require(peer.uid.isNotBlank()) { "Invalid peer account ID." }
    require(Regex("^[1-9][0-9]{7}$").matches(peer.friendsMeetUid)) {
      "Invalid 8-digit Friends Meet UID."
    }
    val safeName = peer.displayName.trim().ifBlank { "Friend ${peer.friendsMeetUid}" }.take(80)
    val chatId = FirestorePaths.canonicalChatId(ownerUid, peer.uid)
    val connId = FirestorePaths.friendConnectionId(ownerUid, peer.uid)

    try {
      val connRef = firestore.collection(FirestorePaths.FRIENDS).document(connId)
      val existingConn = connRef.get().await()
      if (!existingConn.exists()) {
        val connMap =
          mutableMapOf<String, Any>(
            "ownerUid" to ownerUid,
            "friendUid" to peer.uid,
            "friendMeetUid" to peer.friendsMeetUid,
            "friendName" to safeName,
            "friendOnline" to peer.online,
            "blocked" to false,
            "addedVia" to addedVia.name,
            "chatId" to chatId,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        if (!peer.photoUrl.isNullOrBlank()) {
          connMap["friendPhotoUrl"] = peer.photoUrl.take(512)
        }
        connRef.set(connMap).await()
      } else {
        val updates =
          mutableMapOf<String, Any>(
            "friendName" to safeName,
            "friendOnline" to peer.online,
            "blocked" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        if (!peer.photoUrl.isNullOrBlank()) updates["friendPhotoUrl"] = peer.photoUrl.take(512)
        connRef.update(updates).await()
      }

      ensureChatThreadExists(ownerUid, peer.uid)
      return chatId
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "${FirestorePaths.FRIENDS}/$connId")
      throw e
    }
  }

  suspend fun ensureChatThreadExists(uidA: String, uidB: String): String {
    val chatId = FirestorePaths.canonicalChatId(uidA, uidB)
    val chatRef = firestore.collection(FirestorePaths.CHATS).document(chatId)
    try {
      val chatSnap = chatRef.get().await()
      if (!chatSnap.exists()) {
        val participants = listOf(uidA, uidB).sorted()
        chatRef
          .set(
            mapOf(
              "participantUids" to participants,
              "disappearingDurationSeconds" to 0L,
              "typingUids" to emptyList<String>(),
              "e2eeSessionVersion" to 1L,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
      return chatId
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "${FirestorePaths.CHATS}/$chatId")
      throw e
    }
  }

  /**
   * Refreshes online status and display info for friends from their public `userIds` directory record.
   */
  suspend fun refreshFriendEntry(ownerUid: String, friend: FriendConnection) {
    try {
      val peerSnap =
        firestore.collection(FirestorePaths.USER_IDS).document(friend.friendMeetUid).get().await()
      if (!peerSnap.exists()) return
      val peer = peerSnap.toObject(UserIdDirectoryEntry::class.java) ?: return
      val updates =
        mutableMapOf<String, Any>(
          "friendName" to peer.displayName,
          "friendOnline" to peer.online,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      if (!peer.photoUrl.isNullOrBlank()) updates["friendPhotoUrl"] = peer.photoUrl
      firestore
        .collection(FirestorePaths.FRIENDS)
        .document(FirestorePaths.friendConnectionId(ownerUid, friend.friendUid))
        .update(updates)
        .await()
    } catch (_: Exception) {
      // Non-fatal refresh
    }
  }

  /**
   * Assigns or replaces the single private active tag for this friend.
   */
  suspend fun setFriendTag(
    ownerUid: String,
    friendUid: String,
    category: String,
    label: String,
    colorHex: String,
  ) {
    val tagId = FirestorePaths.friendTagId(ownerUid, friendUid)
    val ref = firestore.collection(FirestorePaths.FRIEND_TAGS).document(tagId)
    val existing = ref.get().await()
    if (existing.exists()) {
      ref
        .update(
          mapOf(
            "category" to category.trim(),
            "label" to label.trim(),
            "colorHex" to colorHex,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } else {
      ref
        .set(
          mapOf(
            "ownerUid" to ownerUid,
            "friendUid" to friendUid,
            "category" to category.trim(),
            "label" to label.trim(),
            "colorHex" to colorHex,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    }
  }

  suspend fun removeFriendTag(ownerUid: String, friendUid: String) {
    val tagId = FirestorePaths.friendTagId(ownerUid, friendUid)
    firestore.collection(FirestorePaths.FRIEND_TAGS).document(tagId).delete().await()
  }

  suspend fun setBlocked(ownerUid: String, friendUid: String, blocked: Boolean) {
    val connId = FirestorePaths.friendConnectionId(ownerUid, friendUid)
    firestore
      .collection(FirestorePaths.FRIENDS)
      .document(connId)
      .update(mapOf("blocked" to blocked, "updatedAt" to FieldValue.serverTimestamp()))
      .await()
  }

  suspend fun removeFriend(ownerUid: String, friendUid: String) {
    val connId = FirestorePaths.friendConnectionId(ownerUid, friendUid)
    firestore.collection(FirestorePaths.FRIENDS).document(connId).delete().await()
  }

  suspend fun saveCustomTagCategories(uid: String, categories: List<String>) {
    firestore
      .collection(FirestorePaths.USER_SETTINGS)
      .document(uid)
      .update(
        mapOf(
          "customTagCategories" to categories.distinct().take(20),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun saveQuickMessages(uid: String, quickMessages: List<String>) {
    firestore
      .collection(FirestorePaths.USER_SETTINGS)
      .document(uid)
      .update(
        mapOf(
          "quickMessages" to quickMessages.filter { it.isNotBlank() }.take(30),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }
}
