package com.example.feature.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.FriendConnection
import com.example.core.model.GroupMessage
import com.example.core.model.GroupThread
import com.example.core.model.MessageType
import com.example.core.qr.QrPayload
import com.example.feature.chat.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupViewModel(
  private val repository: GroupRepository,
  private val userUid: String,
  private val chatRepository: ChatRepository? = null,
) : ViewModel() {

  private val _errorMessage = MutableStateFlow<String?>(null)
  val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  val groups: StateFlow<List<GroupThread>> =
    repository
      .observeUserGroups(userUid)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  fun observeGroup(groupId: String): Flow<GroupThread?> = repository.observeGroup(groupId)

  fun observeMessages(groupId: String): Flow<List<GroupMessage>> =
    repository.observeGroupMessages(groupId)

  fun clearError() {
    _errorMessage.value = null
  }

  fun clearStatus() {
    _statusMessage.value = null
  }

  fun reportError(message: String) {
    _errorMessage.value = message
  }

  fun createGroup(
    name: String,
    description: String?,
    creatorName: String,
    members: List<String>,
    onCreated: (String) -> Unit,
  ) {
    if (_isBusy.value) return
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val groupId = repository.createGroup(name, description, userUid, creatorName, members)
        onCreated(groupId)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to create group"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun joinGroupByQr(rawOrGroupId: String, onJoined: (GroupThread) -> Unit) {
    if (_isBusy.value) return
    val groupId =
      when (val parsed = QrPayload.parse(rawOrGroupId)) {
        is QrPayload.GroupInvite -> parsed.groupId
        else -> {
          val trimmed = rawOrGroupId.trim()
          if (trimmed.startsWith("grp_")) trimmed else null
        }
      }
    if (groupId == null) {
      _errorMessage.value = "Invalid Group QR code. Scan a Friends Meet Group QR."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val group = repository.joinGroupByQr(groupId, userUid)
        onJoined(group)
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Unable to join group"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun sendMessage(
    groupId: String,
    senderName: String,
    text: String,
    replyTo: GroupMessage? = null,
  ) {
    viewModelScope.launch {
      try {
        repository.sendGroupMessage(
          groupId = groupId,
          senderUid = userUid,
          senderName = senderName,
          text = text,
          replyToMessageId = replyTo?.id,
          replyToSenderName = replyTo?.senderName,
          replyToText = replyTo?.text?.take(120),
        )
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to send group message"
      }
    }
  }

  fun forwardMessageToGroup(
    targetGroup: GroupThread,
    senderName: String,
    message: GroupMessage,
  ) {
    viewModelScope.launch {
      try {
        repository.sendGroupMessage(
          groupId = targetGroup.id,
          senderUid = userUid,
          senderName = senderName,
          text = message.text,
          forwardedCount = (message.forwardedCount + 1L).coerceIn(1L, 10L),
        )
        _statusMessage.value = "Forwarded to ${targetGroup.name}"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to forward to group"
      }
    }
  }

  fun forwardMessageToFriend(
    targetFriend: FriendConnection,
    senderName: String,
    message: GroupMessage,
  ) {
    val chatRepo = chatRepository ?: run {
      _errorMessage.value = "Chat service unavailable."
      return
    }
    viewModelScope.launch {
      try {
        val peerKey = chatRepo.resolvePeerKeyByFriendConnection(userUid, targetFriend.friendUid)
        chatRepo.sendMessage(
          chatId = targetFriend.chatId,
          senderUid = userUid,
          senderName = senderName,
          recipientUid = targetFriend.friendUid,
          peerPublicKeyBase64 = peerKey,
          messageType = MessageType.TEXT,
          plainText = message.text,
          forwardedCount = (message.forwardedCount + 1L).coerceIn(1L, 10L),
        )
        _statusMessage.value = "Forwarded to ${targetFriend.friendName}"
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Failed to forward message"
      }
    }
  }

  fun addMembers(
    groupId: String,
    newMemberUids: List<String>,
    onSuccess: () -> Unit = {},
  ) {
    if (newMemberUids.isEmpty() || _isBusy.value) return
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.addMembers(groupId, newMemberUids)
        onSuccess()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Unable to add members"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun leaveGroup(groupId: String, onLeft: () -> Unit) {
    if (_isBusy.value) return
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.leaveGroup(groupId, userUid)
        onLeft()
      } catch (e: Exception) {
        _errorMessage.value = e.localizedMessage ?: "Unable to leave group"
      } finally {
        _isBusy.value = false
      }
    }
  }
}
