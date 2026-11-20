package com.example.feature.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.AddedVia
import com.example.core.model.FriendConnection
import com.example.core.model.FriendTag
import com.example.core.model.UserIdDirectoryEntry
import com.example.feature.chat.ChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendsUiState(
  val friends: List<FriendConnection> = emptyList(),
  val friendTags: Map<String, FriendTag> = emptyMap(),
  val searchQuery: String = "",
  val lookupResult: UserIdDirectoryEntry? = null,
  val lookupSource: AddedVia = AddedVia.UID_LOOKUP,
  val lookupError: String? = null,
  val isSearchingUid: Boolean = false,
  val isProcessingAction: Boolean = false,
  val statusMessage: String? = null,
) {
  /** Single continuous list sorted Online first, then Offline, filtered only when search is non-blank. */
  val displayedFriends: List<FriendConnection>
    get() {
      val nonBlocked = friends.filter { !it.blocked }
      val q = searchQuery.trim().lowercase()
      val filtered =
        if (q.isBlank()) nonBlocked
        else
          nonBlocked.filter { conn ->
            val tag = friendTags[conn.friendUid]
            conn.friendName.lowercase().contains(q) ||
              conn.friendMeetUid.contains(q) ||
              (tag?.label?.lowercase()?.contains(q) == true) ||
              (tag?.category?.lowercase()?.contains(q) == true)
          }
      return filtered.sortedWith(
        compareByDescending<FriendConnection> { it.friendOnline }.thenBy { it.friendName.lowercase() }
      )
    }
}

class FriendsViewModel(
  private val friendsRepository: FriendsRepository,
  private val chatRepository: ChatRepository,
  private val myUid: String,
  private val myFriendsMeetUid: String,
) : ViewModel() {
  private val _uiState = MutableStateFlow(FriendsUiState())
  val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

  init {
    viewModelScope.launch {
      combine(
          friendsRepository.observeFriends(myUid),
          friendsRepository.observeFriendTags(myUid),
        ) { friends, tags -> friends to tags }
        .collect { (friends, tags) ->
          _uiState.update { it.copy(friends = friends, friendTags = tags) }
        }
    }
  }

  fun updateSearchQuery(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
  }

  fun clearStatusMessage() {
    _uiState.update { it.copy(statusMessage = null) }
  }

  fun clearLookupError() {
    _uiState.update { it.copy(lookupError = null) }
  }

  fun reportStatusError(message: String) {
    _uiState.update { it.copy(statusMessage = message) }
  }

  fun lookupByUidOrQr(rawUid: String, via: AddedVia) {
    val cleaned = rawUid.trim()
    if (cleaned == myFriendsMeetUid) {
      _uiState.update {
        it.copy(
          lookupError = "That is your own Friends Meet UID.",
          statusMessage = "You cannot add your own UID.",
        )
      }
      return
    }
    _uiState.update {
      it.copy(isSearchingUid = true, lookupError = null, lookupResult = null, lookupSource = via)
    }
    viewModelScope.launch {
      val found = friendsRepository.lookupByFriendsMeetUid(cleaned)
      if (found == null) {
        val msg = "No Friends Meet user found for UID $cleaned."
        _uiState.update {
          it.copy(
            isSearchingUid = false,
            lookupError = msg,
            statusMessage = if (via == AddedVia.QR_CODE) msg else null,
          )
        }
      } else {
        _uiState.update { it.copy(isSearchingUid = false, lookupResult = found, lookupError = null) }
      }
    }
  }

  fun dismissLookupPreview() {
    _uiState.update { it.copy(lookupResult = null, lookupError = null, isProcessingAction = false) }
  }

  fun addFriendAndOpenChat(
    peer: UserIdDirectoryEntry,
    via: AddedVia,
    onChatReady: (chatId: String, friendUid: String) -> Unit,
  ) {
    _uiState.update { it.copy(isProcessingAction = true, lookupError = null) }
    viewModelScope.launch {
      try {
        val chatId = friendsRepository.addFriendAndOpenChat(myUid, peer, via)
        _uiState.update {
          it.copy(
            isProcessingAction = false,
            lookupResult = null,
            lookupError = null,
            statusMessage = "Connected with ${peer.displayName}",
          )
        }
        onChatReady(chatId, peer.uid)
      } catch (e: Exception) {
        val errMsg = e.localizedMessage ?: "Unable to add friend right now."
        _uiState.update {
          it.copy(
            isProcessingAction = false,
            lookupError = errMsg,
            statusMessage = errMsg,
          )
        }
      }
    }
  }

  fun sendDynamicReaction(friend: FriendConnection, emoji: String, myName: String) {
    viewModelScope.launch {
      try {
        friendsRepository.ensureChatThreadExists(myUid, friend.friendUid)
        chatRepository.sendDynamicReaction(
          chatId = friend.chatId,
          senderUid = myUid,
          senderName = myName,
          recipientUid = friend.friendUid,
          recipientMeetUid = friend.friendMeetUid,
          emoji = emoji,
        )
        _uiState.update {
          it.copy(statusMessage = "Sent $emoji reaction to ${friend.friendName}")
        }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = "Failed to send reaction: ${e.message}") }
      }
    }
  }

  fun sendQuickMessage(friend: FriendConnection, text: String, myName: String) {
    viewModelScope.launch {
      try {
        friendsRepository.ensureChatThreadExists(myUid, friend.friendUid)
        chatRepository.sendQuickMessage(
          chatId = friend.chatId,
          senderUid = myUid,
          senderName = myName,
          recipientUid = friend.friendUid,
          recipientMeetUid = friend.friendMeetUid,
          text = text,
        )
        _uiState.update { it.copy(statusMessage = "Quick message sent to ${friend.friendName}") }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = "Failed: ${e.localizedMessage ?: "Retry later"}") }
      }
    }
  }

  fun sendQuickVoiceMessage(
    friend: FriendConnection,
    audioBytes: ByteArray,
    durationMs: Long,
    myName: String,
  ) {
    viewModelScope.launch {
      try {
        friendsRepository.ensureChatThreadExists(myUid, friend.friendUid)
        chatRepository.sendVoiceMessage(
          chatId = friend.chatId,
          senderUid = myUid,
          senderName = myName,
          recipientUid = friend.friendUid,
          recipientMeetUid = friend.friendMeetUid,
          audioBytes = audioBytes,
          durationMs = durationMs,
        )
        _uiState.update { it.copy(statusMessage = "Voice message sent to ${friend.friendName}") }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = "Failed to send voice: ${e.message}") }
      }
    }
  }

  fun saveFriendTag(
    friendUid: String,
    category: String,
    label: String,
    colorHex: String,
    existingCategories: List<String>,
  ) {
    viewModelScope.launch {
      try {
        friendsRepository.setFriendTag(myUid, friendUid, category, label, colorHex)
        val defaults = setOf("Relationship", "Location", "Office", "Work")
        if (category !in defaults && category !in existingCategories) {
          friendsRepository.saveCustomTagCategories(myUid, existingCategories + category)
        }
        _uiState.update { it.copy(statusMessage = "Tag updated") }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = e.localizedMessage ?: "Failed to save tag") }
      }
    }
  }

  fun clearFriendTag(friendUid: String) {
    viewModelScope.launch {
      try {
        friendsRepository.removeFriendTag(myUid, friendUid)
        _uiState.update { it.copy(statusMessage = "Tag cleared") }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = e.localizedMessage ?: "Failed to remove tag") }
      }
    }
  }

  fun blockFriend(friendUid: String) {
    viewModelScope.launch {
      try {
        friendsRepository.setBlocked(myUid, friendUid, true)
        _uiState.update { it.copy(statusMessage = "Friend blocked") }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = e.localizedMessage ?: "Failed to block friend") }
      }
    }
  }

  fun removeFriend(friendUid: String) {
    viewModelScope.launch {
      try {
        friendsRepository.removeFriend(myUid, friendUid)
        _uiState.update { it.copy(statusMessage = "Friend removed") }
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = e.localizedMessage ?: "Failed to remove friend") }
      }
    }
  }

  fun updateQuickMessages(list: List<String>) {
    viewModelScope.launch {
      try {
        friendsRepository.saveQuickMessages(myUid, list)
      } catch (e: Exception) {
        _uiState.update { it.copy(statusMessage = e.localizedMessage ?: "Failed to update quick messages") }
      }
    }
  }
}
