package com.example.feature.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.ChatThread
import com.example.core.model.FriendConnection
import com.example.core.model.MessageType
import com.example.core.security.E2eeCryptoManager
import com.example.core.translation.TranslatorHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
  val thread: ChatThread? = null,
  val messages: List<DecryptedChatMessage> = emptyList(),
  val peerName: String = "Chat",
  val peerPhotoUrl: String? = null,
  val peerOnline: Boolean = false,
  val peerTyping: Boolean = false,
  val peerPublicKeyBase64: String? = null,
  val safetyFingerprint: String? = null,
  val composerText: String = "",
  val replyToMessage: DecryptedChatMessage? = null,
  val editingMessage: DecryptedChatMessage? = null,
  val selectedMessageIds: Set<String> = emptySet(),
  val translatedTexts: Map<String, String> = emptyMap(),
  val translatingMessageIds: Set<String> = emptySet(),
  val pageLimit: Long = 30L,
  val isSending: Boolean = false,
  val statusMessage: String? = null,
)

class ChatViewModel(
  private val chatRepository: ChatRepository,
  private val e2eeCryptoManager: E2eeCryptoManager,
  private val chatId: String,
  private val myUid: String,
  private val myName: String,
  private val friendUid: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow(ChatUiState())
  val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

  private var messagesObserverJob: Job? = null
  private var typingDebounceJob: Job? = null

  init {
    viewModelScope.launch {
      val peerKey = chatRepository.resolvePeerKeyByFriendConnection(myUid, friendUid)
      val myKey = e2eeCryptoManager.getOrCreateIdentityPublicKeyBase64(myUid)
      val fingerprint =
        if (!peerKey.isNullOrBlank()) {
          e2eeCryptoManager.computeSafetyFingerprint(myKey, peerKey)
        } else null
      _uiState.update {
        it.copy(peerPublicKeyBase64 = peerKey, safetyFingerprint = fingerprint)
      }
      startObservingMessages(limit = 30L)
    }

    viewModelScope.launch {
      chatRepository.observeChatThread(chatId).collect { thread ->
        val peerTyping = thread?.typingUids?.contains(friendUid) == true
        _uiState.update { it.copy(thread = thread, peerTyping = peerTyping) }
      }
    }
  }

  private fun startObservingMessages(limit: Long) {
    messagesObserverJob?.cancel()
    messagesObserverJob =
      viewModelScope.launch {
        combine(
            chatRepository.observeMessages(chatId, myUid, limit),
            chatRepository.observePendingMessages(chatId),
          ) { remote, pending -> remote to pending }
          .collect { (remote, pending) ->
            val key =
              chatRepository.deriveKey(myUid, _uiState.value.peerPublicKeyBase64, chatId)
            val decryptedRemote = remote.map { chatRepository.decryptMessage(it, key) }
            val mappedPending =
              pending.map { p ->
                DecryptedChatMessage(
                  id = p.localId,
                  chatId = p.chatId,
                  senderUid = p.senderUid,
                  recipientUid = p.recipientUid,
                  messageType = MessageType.fromRaw(p.messageType),
                  text = p.plainText,
                  mediaUrl = p.mediaLocalPath,
                  mediaDurationMs = p.mediaDurationMs,
                  replyToMessageId = p.replyToMessageId,
                  forwardedCount = 0L,
                  reactions = emptyMap(),
                  readByUids = listOf(myUid),
                  deletedForEveryone = false,
                  edited = false,
                  createdAtMillis = p.createdAtMillis,
                  expiresAtMillis = null,
                  isPendingOffline = true,
                  offlineRetryCount = p.retryCount,
                  canEditWithin15Min = false,
                )
              }
            val merged = decryptedRemote + mappedPending
            _uiState.update { it.copy(messages = merged) }

            val unreadFromPeer =
              remote.filter { it.senderUid == friendUid && myUid !in it.readByUids }.map { it.id }
            if (unreadFromPeer.isNotEmpty()) {
              chatRepository.markMessagesRead(chatId, unreadFromPeer, myUid)
            }
          }
      }
  }

  fun attachFriendInfo(friend: FriendConnection?) {
    if (friend != null) {
      _uiState.update {
        it.copy(
          peerName = friend.friendName,
          peerPhotoUrl = friend.friendPhotoUrl,
          peerOnline = friend.friendOnline,
        )
      }
    }
  }

  fun loadMoreMessages() {
    val nextLimit = _uiState.value.pageLimit + 30L
    _uiState.update { it.copy(pageLimit = nextLimit) }
    startObservingMessages(nextLimit)
  }

  fun onComposerChanged(text: String) {
    _uiState.update { it.copy(composerText = text) }
    typingDebounceJob?.cancel()
    typingDebounceJob =
      viewModelScope.launch {
        chatRepository.setTyping(chatId, myUid, text.isNotBlank())
        if (text.isNotBlank()) {
          delay(3500)
          chatRepository.setTyping(chatId, myUid, false)
        }
      }
  }

  fun setReplyTarget(msg: DecryptedChatMessage?) {
    _uiState.update { it.copy(replyToMessage = msg, editingMessage = null) }
  }

  fun startEditing(msg: DecryptedChatMessage) {
    if (!msg.canEditWithin15Min) {
      _uiState.update { it.copy(statusMessage = "Messages can only be edited within 15 minutes.") }
      return
    }
    _uiState.update {
      it.copy(editingMessage = msg, replyToMessage = null, composerText = msg.text)
    }
  }

  fun cancelEditing() {
    _uiState.update { it.copy(editingMessage = null, composerText = "") }
  }

  fun sendTextOrSaveEdit() {
    val state = _uiState.value
    val text = state.composerText.trim()
    if (text.isEmpty()) return

    val editTarget = state.editingMessage
    if (editTarget != null) {
      viewModelScope.launch {
        try {
          chatRepository.editMessage(chatId, editTarget.id, myUid, state.peerPublicKeyBase64, text)
          _uiState.update { it.copy(editingMessage = null, composerText = "") }
        } catch (e: Exception) {
          _uiState.update {
            it.copy(statusMessage = "Edit rejected (15-min window expired or network error).")
          }
        }
      }
      return
    }

    _uiState.update { it.copy(composerText = "", replyToMessage = null) }
    viewModelScope.launch {
      chatRepository.setTyping(chatId, myUid, false)
      chatRepository.sendMessage(
        chatId = chatId,
        senderUid = myUid,
        senderName = myName,
        recipientUid = friendUid,
        peerPublicKeyBase64 = state.peerPublicKeyBase64,
        messageType = MessageType.TEXT,
        plainText = text,
        replyToMessageId = state.replyToMessage?.id,
        disappearingDurationSeconds = state.thread?.disappearingDurationSeconds ?: 0L,
      )
    }
  }

  fun sendQuickMessage(text: String) {
    val state = _uiState.value
    viewModelScope.launch {
      chatRepository.sendMessage(
        chatId = chatId,
        senderUid = myUid,
        senderName = myName,
        recipientUid = friendUid,
        peerPublicKeyBase64 = state.peerPublicKeyBase64,
        messageType = MessageType.QUICK_MESSAGE,
        plainText = text,
        disappearingDurationSeconds = state.thread?.disappearingDurationSeconds ?: 0L,
      )
    }
  }

  fun sendImage(uri: Uri) {
    val state = _uiState.value
    _uiState.update { it.copy(isSending = true) }
    viewModelScope.launch {
      try {
        chatRepository.sendMessage(
          chatId = chatId,
          senderUid = myUid,
          senderName = myName,
          recipientUid = friendUid,
          peerPublicKeyBase64 = state.peerPublicKeyBase64,
          messageType = MessageType.IMAGE,
          plainText = "Photo",
          imageUri = uri,
          disappearingDurationSeconds = state.thread?.disappearingDurationSeconds ?: 0L,
        )
      } finally {
        _uiState.update { it.copy(isSending = false) }
      }
    }
  }

  fun sendVoiceNote(audioBytes: ByteArray, durationMs: Long) {
    val state = _uiState.value
    _uiState.update { it.copy(isSending = true) }
    viewModelScope.launch {
      try {
        chatRepository.sendMessage(
          chatId = chatId,
          senderUid = myUid,
          senderName = myName,
          recipientUid = friendUid,
          peerPublicKeyBase64 = state.peerPublicKeyBase64,
          messageType = MessageType.VOICE,
          plainText = "Voice message (${durationMs / 1000}s)",
          audioBytes = audioBytes,
          mediaDurationMs = durationMs,
          disappearingDurationSeconds = state.thread?.disappearingDurationSeconds ?: 0L,
        )
      } finally {
        _uiState.update { it.copy(isSending = false) }
      }
    }
  }

  fun sendStickerOrGif(type: MessageType, label: String) {
    val state = _uiState.value
    viewModelScope.launch {
      chatRepository.sendMessage(
        chatId = chatId,
        senderUid = myUid,
        senderName = myName,
        recipientUid = friendUid,
        peerPublicKeyBase64 = state.peerPublicKeyBase64,
        messageType = type,
        plainText = label,
        disappearingDurationSeconds = state.thread?.disappearingDurationSeconds ?: 0L,
      )
    }
  }

  fun toggleReaction(msg: DecryptedChatMessage, emoji: String) {
    viewModelScope.launch {
      chatRepository.toggleReactionOnMessage(chatId, msg.id, myUid, emoji, msg.reactions)
    }
  }

  fun deleteForMe(msg: DecryptedChatMessage) {
    viewModelScope.launch { chatRepository.deleteForMe(chatId, msg.id, myUid) }
  }

  fun deleteForEveryone(msg: DecryptedChatMessage) {
    viewModelScope.launch {
      chatRepository.deleteForEveryone(chatId, msg.id, myUid, _uiState.value.peerPublicKeyBase64)
    }
  }

  fun toggleSelectForForward(messageId: String) {
    _uiState.update { state ->
      val current = state.selectedMessageIds.toMutableSet()
      if (messageId in current) {
        current.remove(messageId)
        state.copy(selectedMessageIds = current)
      } else if (current.size >= 10) {
        state.copy(statusMessage = "Maximum 10 messages can be forwarded at once.")
      } else {
        current.add(messageId)
        state.copy(selectedMessageIds = current)
      }
    }
  }

  fun clearSelection() {
    _uiState.update { it.copy(selectedMessageIds = emptySet()) }
  }

  fun forwardSelectedToFriend(targetFriend: FriendConnection) {
    val selectedIds = _uiState.value.selectedMessageIds.take(10)
    val toForward = _uiState.value.messages.filter { it.id in selectedIds }
    if (toForward.isEmpty()) return
    viewModelScope.launch {
      val peerKey = chatRepository.resolvePeerKeyByFriendConnection(myUid, targetFriend.friendUid)
      val count = toForward.size.toLong().coerceAtMost(10L)
      for (msg in toForward) {
        chatRepository.sendMessage(
          chatId = targetFriend.chatId,
          senderUid = myUid,
          senderName = myName,
          recipientUid = targetFriend.friendUid,
          peerPublicKeyBase64 = peerKey,
          messageType = msg.messageType,
          plainText = msg.text,
          forwardedCount = count,
          preUploadedMediaUrl = msg.mediaUrl,
          mediaDurationMs = msg.mediaDurationMs,
        )
      }
      _uiState.update {
        it.copy(
          selectedMessageIds = emptySet(),
          statusMessage = "Forwarded ${toForward.size} message(s) to ${targetFriend.friendName}",
        )
      }
    }
  }

  fun translateMessage(msg: DecryptedChatMessage) {
    if (_uiState.value.translatedTexts.containsKey(msg.id)) {
      _uiState.update { it.copy(translatedTexts = it.translatedTexts - msg.id) }
      return
    }
    _uiState.update { it.copy(translatingMessageIds = it.translatingMessageIds + msg.id) }
    viewModelScope.launch {
      try {
        val result = TranslatorHelper.translateBengaliEnglish(msg.text)
        _uiState.update {
          it.copy(
            translatingMessageIds = it.translatingMessageIds - msg.id,
            translatedTexts = it.translatedTexts + (msg.id to result.translatedText),
          )
        }
      } catch (e: Exception) {
        _uiState.update {
          it.copy(
            translatingMessageIds = it.translatingMessageIds - msg.id,
            statusMessage = "Translation failed: ${e.localizedMessage ?: "Download language model on Wi-Fi"}",
          )
        }
      }
    }
  }

  fun setDisappearingDuration(seconds: Long) {
    viewModelScope.launch {
      chatRepository.setDisappearingDuration(chatId, seconds)
      val label =
        when (seconds) {
          86400L -> "24 hours"
          604800L -> "7 days"
          7776000L -> "90 days"
          else -> "Off"
        }
      _uiState.update { it.copy(statusMessage = "Disappearing messages: $label") }
    }
  }

  fun setWallpaperAndTheme(wallpaperId: String?, themeId: String?) {
    viewModelScope.launch {
      chatRepository.setWallpaperAndTheme(chatId, wallpaperId, themeId)
      _uiState.update { it.copy(statusMessage = "Chat style updated") }
    }
  }

  fun retryOfflineQueue() {
    viewModelScope.launch { chatRepository.flushOfflineQueue() }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }
}
