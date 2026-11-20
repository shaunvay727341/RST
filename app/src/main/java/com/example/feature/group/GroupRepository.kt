package com.example.feature.group

import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.model.GroupMessage
import com.example.core.model.GroupThread
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class GroupRepository(private val firestore: FirebaseFirestore) {

  fun observeUserGroups(uid: String): Flow<List<GroupThread>> = callbackFlow {
    val listener =
      firestore
        .collection(FirestorePaths.GROUPS)
        .whereArrayContains("participantUids", uid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.GROUPS)
            return@addSnapshotListener
          }
          val groups =
            snap
              ?.documents
              ?.mapNotNull { doc ->
                val obj = doc.toObject(GroupThread::class.java) ?: return@mapNotNull null
                val updated =
                  doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (obj.updatedAt == null && updated != null) obj.copy(updatedAt = updated) else obj
              }
              ?.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L } ?: emptyList()
          trySend(groups)
        }
    awaitClose { listener.remove() }
  }

  fun observeGroup(groupId: String): Flow<GroupThread?> = callbackFlow {
    val listener =
      firestore.collection(FirestorePaths.GROUPS).document(groupId).addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, OperationType.GET, "${FirestorePaths.GROUPS}/$groupId")
          return@addSnapshotListener
        }
        trySend(snap?.takeIf { it.exists() }?.toObject(GroupThread::class.java))
      }
    awaitClose { listener.remove() }
  }

  fun observeGroupMessages(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
    val listener =
      firestore
        .collection(FirestorePaths.GROUPS)
        .document(groupId)
        .collection(FirestorePaths.GROUP_MESSAGES)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(
              err,
              OperationType.LIST,
              "${FirestorePaths.GROUPS}/$groupId/${FirestorePaths.GROUP_MESSAGES}",
            )
            return@addSnapshotListener
          }
          val messages =
            snap
              ?.documents
              ?.mapNotNull { doc ->
                val obj = doc.toObject(GroupMessage::class.java) ?: return@mapNotNull null
                val created =
                  doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (obj.createdAt == null && created != null) obj.copy(createdAt = created) else obj
              }
              ?.sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE } ?: emptyList()
          trySend(messages)
        }
    awaitClose { listener.remove() }
  }

  suspend fun createGroup(
    name: String,
    description: String?,
    creatorUid: String,
    creatorName: String,
    memberUids: List<String>,
  ): String {
    val trimmedName = name.trim()
    require(trimmedName.isNotEmpty() && trimmedName.length <= 80) {
      "Group name must be between 1 and 80 characters."
    }
    val groupId = "grp_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    val inviteCode = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
    val groupRef = firestore.collection(FirestorePaths.GROUPS).document(groupId)
    val allParticipants = (memberUids + creatorUid).distinct().take(100)
    val safeCreatorName = creatorName.trim().ifBlank { "Member" }.take(40)

    val data =
      mutableMapOf<String, Any>(
        "name" to trimmedName,
        "creatorUid" to creatorUid,
        "adminUids" to listOf(creatorUid),
        "participantUids" to allParticipants,
        "inviteCode" to inviteCode,
        "allowQrJoin" to true,
        "lastMessageText" to "$safeCreatorName created group '$trimmedName'".take(512),
        "lastMessageSenderUid" to creatorUid,
        "lastMessageAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    description?.trim()?.takeIf { it.isNotEmpty() }?.let {
      data["description"] = it.take(300)
    }

    try {
      groupRef.set(data).await()
      return groupId
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "${FirestorePaths.GROUPS}/$groupId")
      throw e
    }
  }

  suspend fun joinGroupByQr(groupId: String, myUid: String): GroupThread {
    val cleanId = groupId.trim()
    require(cleanId.isNotEmpty()) { "Invalid group QR code." }
    val groupRef = firestore.collection(FirestorePaths.GROUPS).document(cleanId)
    try {
      val snap = groupRef.get().await()
      val group =
        snap.takeIf { it.exists() }?.toObject(GroupThread::class.java)
          ?: throw IllegalArgumentException("Group not found or no longer exists.")

      if (myUid in group.participantUids) {
        return group
      }
      if (!group.allowQrJoin) {
        throw IllegalStateException("QR joining is disabled for this group.")
      }
      if (group.participantUids.size >= 100) {
        throw IllegalStateException("Group is full (max 100 members).")
      }

      groupRef
        .update(
          mapOf(
            "participantUids" to FieldValue.arrayUnion(myUid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()

      return group.copy(participantUids = group.participantUids + myUid)
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "${FirestorePaths.GROUPS}/$cleanId")
      throw e
    }
  }

  suspend fun sendGroupMessage(
    groupId: String,
    senderUid: String,
    senderName: String,
    text: String,
    replyToMessageId: String? = null,
    replyToSenderName: String? = null,
    replyToText: String? = null,
    forwardedCount: Long = 0L,
  ) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    val msgId = "gmsg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    val groupRef = firestore.collection(FirestorePaths.GROUPS).document(groupId)
    val msgRef = groupRef.collection(FirestorePaths.GROUP_MESSAGES).document(msgId)
    val safeSenderName = senderName.trim().ifBlank { "Member" }.take(80)

    val msgMap =
      mutableMapOf<String, Any>(
        "groupId" to groupId,
        "senderUid" to senderUid,
        "senderName" to safeSenderName,
        "text" to trimmed.take(4000),
        "createdAt" to FieldValue.serverTimestamp(),
      )
    replyToMessageId?.takeIf { it.isNotBlank() }?.let { msgMap["replyToMessageId"] = it.take(128) }
    replyToSenderName?.takeIf { it.isNotBlank() }?.let { msgMap["replyToSenderName"] = it.take(80) }
    replyToText?.takeIf { it.isNotBlank() }?.let { msgMap["replyToText"] = it.take(300) }
    if (forwardedCount > 0L) {
      msgMap["forwardedCount"] = forwardedCount.coerceIn(1L, 10L)
    }

    try {
      msgRef.set(msgMap).await()
      groupRef
        .update(
          mapOf(
            "lastMessageText" to trimmed.take(512),
            "lastMessageSenderUid" to senderUid,
            "lastMessageAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "${FirestorePaths.GROUPS}/$groupId/$msgId")
      throw e
    }
  }

  suspend fun addMembers(groupId: String, newMemberUids: List<String>) {
    if (newMemberUids.isEmpty()) return
    try {
      firestore
        .collection(FirestorePaths.GROUPS)
        .document(groupId)
        .update(
          mapOf(
            "participantUids" to FieldValue.arrayUnion(*newMemberUids.toTypedArray()),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "${FirestorePaths.GROUPS}/$groupId")
      throw e
    }
  }

  suspend fun leaveGroup(groupId: String, myUid: String) {
    try {
      firestore
        .collection(FirestorePaths.GROUPS)
        .document(groupId)
        .update(
          mapOf(
            "participantUids" to FieldValue.arrayRemove(myUid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "${FirestorePaths.GROUPS}/$groupId")
      throw e
    }
  }
}
