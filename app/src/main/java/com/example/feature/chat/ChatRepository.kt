package com.example.feature.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.firebase.StorageRepository
import com.example.core.local.OfflineQueueDatabase
import com.example.core.local.OfflineQueueMonitor
import com.example.core.local.PendingMessageEntity
import com.example.core.model.ChatMessage
import com.example.core.model.ChatThread
import com.example.core.model.MessageType
import com.example.core.model.UserIdDirectoryEntry
import com.example.core.security.E2eeCryptoManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import java.io.IOException
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class DecryptedChatMessage(
  val id: String,
  val chatId: String,
  val senderUid: String,
  val recipientUid: String,
  val messageType: MessageType,
  val text: String,
  val mediaUrl: String?,
  val mediaDurationMs: Long?,
  val replyToMessageId: String?,
  val forwardedCount: Long,
  val reactions: Map<String, String>,
  val readByUids: List<String>,
  val deletedForEveryone: Boolean,
  val edited: Boolean,
  val createdAtMillis: Long,
  val expiresAtMillis: Long?,
  val isPendingOffline: Boolean = false,
  val offlineRetryCount: Int = 0,
  val canEditWithin15Min: Boolean = false,
)

class ChatRepository(
  private val context: Context,
  private val firestore: FirebaseFirestore,
  private val storageRepository: StorageRepository,
  private val e2eeCryptoManager: E2eeCryptoManager,
  private val offlineQueueDatabase: OfflineQueueDatabase,
) {
  private val flushMutex = Mutex()
  private val pendingDao = offlineQueueDatabase.pendingMessageDao()

  val offlineMonitor =
    OfflineQueueMonitor(context = context, onNetworkRestored = { flushOfflineQueue() })

  init {
    offlineMonitor.start()
  }

  fun observeChatThreadsForUser(uid: String): Flow<List<ChatThread>> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.CHATS)
        .whereArrayContains("participantUids", uid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.CHATS)
            return@addSnapshotListener
          }
          val list =
            snap
              ?.documents
              ?.mapNotNull { doc ->
                val obj = doc.toObject(ChatThread::class.java) ?: return@mapNotNull null
                val updated =
                  doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val lastMsgAt =
                  doc.getTimestamp("lastMessageAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                obj.copy(
                  updatedAt = obj.updatedAt ?: updated,
                  lastMessageAt = obj.lastMessageAt ?: lastMsgAt,
                )
              }
              ?.sortedByDescending { it.updatedAt?.toDate()?.time ?: 0L } ?: emptyList()
          trySend(list)
        }
    awaitClose { reg.remove() }
  }

  fun observeChatThread(chatId: String): Flow<ChatThread?> = callbackFlow {
    val reg =
      firestore.collection(FirestorePaths.CHATS).document(chatId).addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, OperationType.GET, "${FirestorePaths.CHATS}/$chatId")
          return@addSnapshotListener
        }
        trySend(snap?.takeIf { it.exists() }?.toObject(ChatThread::class.java))
      }
    awaitClose { reg.remove() }
  }

  fun observeMessages(
    chatId: String,
    myUid: String,
    pageLimit: Long,
  ): Flow<List<ChatMessage>> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.CHATS)
        .document(chatId)
        .collection(FirestorePaths.MESSAGES)
        .whereArrayContains("participantUids", myUid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(
              err,
              OperationType.LIST,
              "${FirestorePaths.CHATS}/$chatId/${FirestorePaths.MESSAGES}",
            )
            return@addSnapshotListener
          }
          val nowMs = System.currentTimeMillis()
          val list =
            snap
              ?.documents
              ?.mapNotNull { doc ->
                val created =
                  doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                val obj = doc.toObject(ChatMessage::class.java) ?: return@mapNotNull null
                if (obj.createdAt == null && created != null) obj.copy(createdAt = created) else obj
              }
              ?.filter { msg ->
                myUid !in msg.deleteForMeUids &&
                  (msg.expiresAt == null || msg.expiresAt.toDate().time > nowMs)
              }
              ?.sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
              ?.takeLast(pageLimit.toInt().coerceAtLeast(30)) ?: emptyList()
          trySend(list)
        }
    awaitClose { reg.remove() }
  }

  fun observePendingMessages(chatId: String): Flow<List<PendingMessageEntity>> =
    pendingDao.observeForChat(chatId)

  suspend fun fetchPeerDirectoryEntry(friendUid: String, friendMeetUid: String?): UserIdDirectoryEntry? {
    try {
      if (!friendMeetUid.isNullOrBlank()) {
        val snap = firestore.collection(FirestorePaths.USER_IDS).document(friendMeetUid).get().await()
        if (snap.exists()) return snap.toObject(UserIdDirectoryEntry::class.java)
      }
      if (friendUid.isNotBlank()) {
        val querySnap =
          firestore
            .collection(FirestorePaths.USER_IDS)
            .whereEqualTo("uid", friendUid)
            .limit(1)
            .get()
            .await()
        return querySnap.documents.firstOrNull()?.toObject(UserIdDirectoryEntry::class.java)
      }
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, "${FirestorePaths.USER_IDS}/$friendUid")
    }
    return null
  }

  suspend fun resolvePeerDirectoryEntry(ownerUid: String, peerUid: String): UserIdDirectoryEntry? {
    var knownMeetUid: String? = null
    try {
      val connSnap =
        firestore
          .collection(FirestorePaths.FRIENDS)
          .document(FirestorePaths.friendConnectionId(ownerUid, peerUid))
          .get()
          .await()
      knownMeetUid = connSnap.getString("friendMeetUid")
    } catch (_: Exception) {
      // Fallback to directory query by uid
    }
    return fetchPeerDirectoryEntry(peerUid, knownMeetUid)
  }

  suspend fun resolvePeerKeyByFriendConnection(ownerUid: String, friendUid: String): String? {
    return resolvePeerDirectoryEntry(ownerUid, friendUid)?.identityPublicKey
  }

  suspend fun deriveKey(myUid: String, peerPublicKeyBase64: String?, chatId: String): SecretKey {
    return e2eeCryptoManager.deriveChatAesKey(myUid, peerPublicKeyBase64, chatId)
  }

  fun decryptMessage(
    msg: ChatMessage,
    aesKey: SecretKey,
    fallbackKey: SecretKey? = null,
  ): DecryptedChatMessage {
    val plainText =
      if (msg.deletedForEveryone) {
        "This message was deleted"
      } else {
        try {
          e2eeCryptoManager.decryptMessage(msg.ciphertext, msg.iv, aesKey)
        } catch (_: Exception) {
          if (fallbackKey != null) {
            try {
              e2eeCryptoManager.decryptMessage(msg.ciphertext, msg.iv, fallbackKey)
            } catch (_: Exception) {
              "[Encrypted message]"
            }
          } else {
            "[Encrypted message]"
          }
        }
      }
    val createdMs = msg.createdAt?.toDate()?.time ?: System.currentTimeMillis()
    val within15Min =
      !msg.deletedForEveryone &&
        (msg.messageType == MessageType.TEXT.name || msg.messageType == MessageType.QUICK_MESSAGE.name) &&
        (System.currentTimeMillis() - createdMs) <= 15L * 60L * 1000L
    return DecryptedChatMessage(
      id = msg.id,
      chatId = msg.chatId,
      senderUid = msg.senderUid,
      recipientUid = msg.recipientUid,
      messageType = MessageType.fromRaw(msg.messageType),
      text = plainText,
      mediaUrl = if (msg.deletedForEveryone) null else msg.mediaUrl,
      mediaDurationMs = msg.mediaDurationMs,
      replyToMessageId = msg.replyToMessageId,
      forwardedCount = msg.forwardedCount,
      reactions = msg.reactions,
      readByUids = msg.readByUids,
      deletedForEveryone = msg.deletedForEveryone,
      edited = msg.edited,
      createdAtMillis = createdMs,
      expiresAtMillis = msg.expiresAt?.toDate()?.time,
      canEditWithin15Min = within15Min,
    )
  }

  suspend fun ensureOldChatThread(
    myUid: String,
    peerUid: String,
    initialLimit: Int = 3,
  ): String {
    val chatId = FirestorePaths.canonicalChatId(myUid, peerUid)
    val chatRef = firestore.collection(FirestorePaths.CHATS).document(chatId)
    val clampedLimit = initialLimit.coerceIn(1, 100).toLong()
    try {
      val snap = chatRef.get().await()
      if (!snap.exists()) {
        val participants = listOf(myUid, peerUid).sorted()
        chatRef
          .set(
            mapOf(
              "participantUids" to participants,
              "disappearingDurationSeconds" to 0L,
              "typingUids" to emptyList<String>(),
              "e2eeSessionVersion" to 1L,
              "chatMode" to "OLD_CHAT",
              "oldChatInitiatorUid" to myUid,
              "oldChatMessageLimit" to clampedLimit,
              "oldChatSentCount" to 0L,
              "oldChatUnlocked" to false,
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

  suspend fun updateOldChatLimit(chatId: String, newLimit: Int) {
    val clamped = newLimit.coerceIn(1, 100).toLong()
    try {
      firestore
        .collection(FirestorePaths.CHATS)
        .document(chatId)
        .update(
          mapOf(
            "oldChatMessageLimit" to clamped,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "${FirestorePaths.CHATS}/$chatId")
      throw e
    }
  }

  suspend fun sendMessage(
    chatId: String,
    senderUid: String,
    senderName: String,
    recipientUid: String,
    peerPublicKeyBase64: String?,
    messageType: MessageType,
    plainText: String,
    replyToMessageId: String? = null,
    forwardedCount: Long = 0L,
    imageUri: Uri? = null,
    audioBytes: ByteArray? = null,
    mediaDurationMs: Long? = null,
    preUploadedMediaUrl: String? = null,
    disappearingDurationSeconds: Long = 0L,
  ) {
    val localId = "msg_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    if (!offlineMonitor.isOnlineNow() && imageUri == null && audioBytes == null) {
      pendingDao.upsert(
        PendingMessageEntity(
          localId = localId,
          chatId = chatId,
          senderUid = senderUid,
          recipientUid = recipientUid,
          peerPublicKeyBase64 = peerPublicKeyBase64,
          messageType = messageType.name,
          plainText = plainText,
          replyToMessageId = replyToMessageId,
          mediaDurationMs = mediaDurationMs,
          disappearingDurationSeconds = disappearingDurationSeconds,
        )
      )
      return
    }

    try {
      deliverMessageToFirestore(
        messageId = localId,
        chatId = chatId,
        senderUid = senderUid,
        senderName = senderName,
        recipientUid = recipientUid,
        peerPublicKeyBase64 = peerPublicKeyBase64,
        messageType = messageType,
        plainText = plainText,
        replyToMessageId = replyToMessageId,
        forwardedCount = forwardedCount,
        imageUri = imageUri,
        audioBytes = audioBytes,
        mediaDurationMs = mediaDurationMs,
        preUploadedMediaUrl = preUploadedMediaUrl,
        disappearingDurationSeconds = disappearingDurationSeconds,
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val rootFirestore =
        generateSequence<Throwable>(e) { it.cause }
          .filterIsInstance<FirebaseFirestoreException>()
          .firstOrNull()
      val isTransientNetwork =
        !offlineMonitor.isOnlineNow() ||
          e is IOException ||
          rootFirestore?.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
          rootFirestore?.code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED

      if (!isTransientNetwork) {
        handleFirestoreError(
          e,
          OperationType.WRITE,
          "${FirestorePaths.CHATS}/$chatId/${FirestorePaths.MESSAGES}/$localId",
        )
        throw e
      }

      Log.w("ChatRepository", "Delivery deferred to offline queue: ${e.message}")
      var savedAudioPath: String? = null
      if (audioBytes != null) {
        val f = File(context.cacheDir, "pending_voice_$localId.m4a")
        f.writeBytes(audioBytes)
        savedAudioPath = f.absolutePath
      } else if (imageUri != null) {
        val f = File(context.cacheDir, "pending_img_$localId.jpg")
        context.contentResolver.openInputStream(imageUri)?.use { input ->
          f.writeBytes(input.readBytes())
        }
        savedAudioPath = f.absolutePath
      }
      pendingDao.upsert(
        PendingMessageEntity(
          localId = localId,
          chatId = chatId,
          senderUid = senderUid,
          recipientUid = recipientUid,
          peerPublicKeyBase64 = peerPublicKeyBase64,
          messageType = messageType.name,
          plainText = plainText,
          replyToMessageId = replyToMessageId,
          mediaLocalPath = savedAudioPath,
          mediaDurationMs = mediaDurationMs,
          disappearingDurationSeconds = disappearingDurationSeconds,
          lastError = e.localizedMessage,
        )
      )
    }
  }

  private suspend fun deliverMessageToFirestore(
    messageId: String,
    chatId: String,
    senderUid: String,
    senderName: String,
    recipientUid: String,
    peerPublicKeyBase64: String?,
    messageType: MessageType,
    plainText: String,
    replyToMessageId: String?,
    forwardedCount: Long,
    imageUri: Uri?,
    audioBytes: ByteArray?,
    mediaDurationMs: Long?,
    preUploadedMediaUrl: String?,
    disappearingDurationSeconds: Long,
  ) {
    val resolvedPeerKey =
      peerPublicKeyBase64?.takeIf { it.isNotBlank() }
        ?: resolvePeerDirectoryEntry(senderUid, recipientUid)?.identityPublicKey
    val aesKey = deriveKey(senderUid, resolvedPeerKey, chatId)
    val encrypted = e2eeCryptoManager.encryptMessage(plainText.ifBlank { " " }, aesKey)

    val mediaUrl =
      when {
        !preUploadedMediaUrl.isNullOrBlank() -> preUploadedMediaUrl
        imageUri != null -> storageRepository.uploadChatImage(chatId, messageId, imageUri)
        audioBytes != null -> storageRepository.uploadVoiceMessage(chatId, messageId, audioBytes)
        else -> null
      }

    val participants = listOf(senderUid, recipientUid).sorted()
    val expiresAt =
      if (disappearingDurationSeconds > 0L) {
        Timestamp(Date(System.currentTimeMillis() + disappearingDurationSeconds * 1000L))
      } else null

    val msgMap =
      mutableMapOf<String, Any>(
        "chatId" to chatId,
        "senderUid" to senderUid,
        "recipientUid" to recipientUid,
        "participantUids" to participants,
        "messageType" to messageType.name,
        "ciphertext" to encrypted.ciphertextBase64,
        "iv" to encrypted.ivBase64,
        "forwardedCount" to forwardedCount.coerceIn(0L, 10L),
        "reactions" to emptyMap<String, String>(),
        "readByUids" to listOf(senderUid),
        "deleteForMeUids" to emptyList<String>(),
        "deletedForEveryone" to false,
        "edited" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    mediaUrl?.let { msgMap["mediaUrl"] = it }
    mediaDurationMs?.let { msgMap["mediaDurationMs"] = it }
    replyToMessageId?.let { msgMap["replyToMessageId"] = it }
    expiresAt?.let { msgMap["expiresAt"] = it }

    val chatRef = firestore.collection(FirestorePaths.CHATS).document(chatId)
    val chatSnap = chatRef.get().await()
    if (!chatSnap.exists()) {
      chatRef
        .set(
          mapOf(
            "participantUids" to participants,
            "disappearingDurationSeconds" to disappearingDurationSeconds,
            "typingUids" to emptyList<String>(),
            "e2eeSessionVersion" to 1L,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    }

    val isOldChat = chatSnap.exists() && chatSnap.getString("chatMode") == "OLD_CHAT"
    val oldChatUnlocked = chatSnap.exists() && chatSnap.getBoolean("oldChatUnlocked") == true
    val oldChatInitiator = if (chatSnap.exists()) chatSnap.getString("oldChatInitiatorUid") else null
    val oldChatLimit = if (chatSnap.exists()) (chatSnap.getLong("oldChatMessageLimit") ?: 3L).toInt() else 3
    val oldChatSent = if (chatSnap.exists()) (chatSnap.getLong("oldChatSentCount") ?: 0L).toInt() else 0

    if (isOldChat && !oldChatUnlocked && senderUid == oldChatInitiator && oldChatSent >= oldChatLimit) {
      throw IllegalStateException(
        "Old Chat limit ($oldChatLimit messages) reached. Waiting for reply to unlock unlimited messaging."
      )
    }

    chatRef.collection(FirestorePaths.MESSAGES).document(messageId).set(msgMap).await()

    val previewText =
      when (messageType) {
        MessageType.IMAGE -> "📷 Photo"
        MessageType.VOICE -> "🎤 Voice message"
        MessageType.GIF -> "GIF"
        MessageType.STICKER -> "Sticker"
        else -> plainText.take(60)
      }
    val chatUpdates =
      mutableMapOf<String, Any>(
        "lastMessageText" to previewText,
        "lastMessageType" to messageType.name,
        "lastMessageSenderUid" to senderUid,
        "lastMessageAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (isOldChat && !oldChatUnlocked) {
      if (senderUid == oldChatInitiator) {
        chatUpdates["oldChatSentCount"] = (oldChatSent + 1).toLong()
      } else {
        chatUpdates["oldChatUnlocked"] = true
      }
    }
    chatRef.update(chatUpdates).await()

    val notifType = if (messageType == MessageType.REACTION) "REACTION" else "MESSAGE"
    val notifBody =
      if (messageType == MessageType.REACTION) "$senderName reacted $plainText"
      else "$senderName sent an encrypted message"
    try {
      firestore
        .collection(FirestorePaths.NOTIFICATIONS)
        .document("notif_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        .set(
          mapOf(
            "recipientUid" to recipientUid,
            "senderUid" to senderUid,
            "type" to notifType,
            "title" to senderName.trim().ifBlank { "Friends Meet" }.take(120),
            "body" to notifBody.take(500),
            "targetId" to chatId,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (_: Exception) {
      // Non-fatal notification write
    }
  }

  suspend fun flushOfflineQueue() =
    flushMutex.withLock {
      val items = pendingDao.getAll()
      for (item in items) {
        try {
          var imageUri: Uri? = null
          var audioBytes: ByteArray? = null
          if (!item.mediaLocalPath.isNullOrBlank()) {
            val file = File(item.mediaLocalPath)
            if (file.exists()) {
              if (item.messageType == MessageType.IMAGE.name) imageUri = Uri.fromFile(file)
              else if (item.messageType == MessageType.VOICE.name) audioBytes = file.readBytes()
            }
          }
          deliverMessageToFirestore(
            messageId = item.localId,
            chatId = item.chatId,
            senderUid = item.senderUid,
            senderName = "Friends Meet User",
            recipientUid = item.recipientUid,
            peerPublicKeyBase64 = item.peerPublicKeyBase64,
            messageType = MessageType.fromRaw(item.messageType),
            plainText = item.plainText,
            replyToMessageId = item.replyToMessageId,
            forwardedCount = 0L,
            imageUri = imageUri,
            audioBytes = audioBytes,
            mediaDurationMs = item.mediaDurationMs,
            preUploadedMediaUrl = null,
            disappearingDurationSeconds = item.disappearingDurationSeconds,
          )
          pendingDao.deleteById(item.localId)
        } catch (e: Exception) {
          pendingDao.markRetryFailed(item.localId, e.localizedMessage ?: "Retry failed")
        }
      }
    }

  suspend fun sendDynamicReaction(
    chatId: String,
    senderUid: String,
    senderName: String,
    recipientUid: String,
    recipientMeetUid: String,
    emoji: String,
  ) {
    val peerEntry = fetchPeerDirectoryEntry(recipientUid, recipientMeetUid)
    sendMessage(
      chatId = chatId,
      senderUid = senderUid,
      senderName = senderName,
      recipientUid = recipientUid,
      peerPublicKeyBase64 = peerEntry?.identityPublicKey,
      messageType = MessageType.REACTION,
      plainText = emoji,
    )
  }

  suspend fun sendQuickMessage(
    chatId: String,
    senderUid: String,
    senderName: String,
    recipientUid: String,
    recipientMeetUid: String,
    text: String,
  ) {
    val peerEntry = fetchPeerDirectoryEntry(recipientUid, recipientMeetUid)
    sendMessage(
      chatId = chatId,
      senderUid = senderUid,
      senderName = senderName,
      recipientUid = recipientUid,
      peerPublicKeyBase64 = peerEntry?.identityPublicKey,
      messageType = MessageType.QUICK_MESSAGE,
      plainText = text,
    )
  }

  suspend fun sendVoiceMessage(
    chatId: String,
    senderUid: String,
    senderName: String,
    recipientUid: String,
    recipientMeetUid: String,
    audioBytes: ByteArray,
    durationMs: Long,
  ) {
    val peerEntry = fetchPeerDirectoryEntry(recipientUid, recipientMeetUid)
    sendMessage(
      chatId = chatId,
      senderUid = senderUid,
      senderName = senderName,
      recipientUid = recipientUid,
      peerPublicKeyBase64 = peerEntry?.identityPublicKey,
      messageType = MessageType.VOICE,
      plainText = "Voice message (${durationMs / 1000}s)",
      audioBytes = audioBytes,
      mediaDurationMs = durationMs,
    )
  }

  suspend fun toggleReactionOnMessage(
    chatId: String,
    messageId: String,
    myUid: String,
    emoji: String,
    currentReactions: Map<String, String>,
  ) {
    val updated = currentReactions.toMutableMap()
    if (updated[myUid] == emoji) updated.remove(myUid) else updated[myUid] = emoji
    firestore
      .collection(FirestorePaths.CHATS)
      .document(chatId)
      .collection(FirestorePaths.MESSAGES)
      .document(messageId)
      .update(mapOf("reactions" to updated, "updatedAt" to FieldValue.serverTimestamp()))
      .await()
  }

  suspend fun editMessage(
    chatId: String,
    messageId: String,
    myUid: String,
    peerPublicKeyBase64: String?,
    newText: String,
  ) {
    val aesKey = deriveKey(myUid, peerPublicKeyBase64, chatId)
    val encrypted = e2eeCryptoManager.encryptMessage(newText.trim(), aesKey)
    firestore
      .collection(FirestorePaths.CHATS)
      .document(chatId)
      .collection(FirestorePaths.MESSAGES)
      .document(messageId)
      .update(
        mapOf(
          "ciphertext" to encrypted.ciphertextBase64,
          "iv" to encrypted.ivBase64,
          "edited" to true,
          "editedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun deleteForMe(chatId: String, messageId: String, myUid: String) {
    firestore
      .collection(FirestorePaths.CHATS)
      .document(chatId)
      .collection(FirestorePaths.MESSAGES)
      .document(messageId)
      .update(
        mapOf(
          "deleteForMeUids" to FieldValue.arrayUnion(myUid),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun deleteForEveryone(
    chatId: String,
    messageId: String,
    myUid: String,
    peerPublicKeyBase64: String?,
  ) {
    val aesKey = deriveKey(myUid, peerPublicKeyBase64, chatId)
    val encrypted = e2eeCryptoManager.encryptMessage("This message was deleted", aesKey)
    firestore
      .collection(FirestorePaths.CHATS)
      .document(chatId)
      .collection(FirestorePaths.MESSAGES)
      .document(messageId)
      .update(
        mapOf(
          "ciphertext" to encrypted.ciphertextBase64,
          "iv" to encrypted.ivBase64,
          "deletedForEveryone" to true,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun markMessagesRead(chatId: String, messageIds: List<String>, myUid: String) {
    for (id in messageIds) {
      try {
        firestore
          .collection(FirestorePaths.CHATS)
          .document(chatId)
          .collection(FirestorePaths.MESSAGES)
          .document(id)
          .update(
            mapOf(
              "readByUids" to FieldValue.arrayUnion(myUid),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      } catch (_: Exception) {}
    }
  }

  suspend fun setTyping(chatId: String, myUid: String, isTyping: Boolean) {
    try {
      firestore
        .collection(FirestorePaths.CHATS)
        .document(chatId)
        .update(
          mapOf(
            "typingUids" to
              if (isTyping) FieldValue.arrayUnion(myUid) else FieldValue.arrayRemove(myUid),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (_: Exception) {}
  }

  suspend fun setDisappearingDuration(chatId: String, durationSeconds: Long) {
    require(durationSeconds in listOf(0L, 86400L, 604800L, 7776000L))
    firestore
      .collection(FirestorePaths.CHATS)
      .document(chatId)
      .update(
        mapOf(
          "disappearingDurationSeconds" to durationSeconds,
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun setWallpaperAndTheme(chatId: String, wallpaperId: String?, themeId: String?) {
    val map = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
    wallpaperId?.let { map["wallpaperId"] = it }
    themeId?.let { map["themeId"] = it }
    firestore.collection(FirestorePaths.CHATS).document(chatId).update(map).await()
  }
}
