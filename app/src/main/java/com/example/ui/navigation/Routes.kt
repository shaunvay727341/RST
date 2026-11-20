package com.example.ui.navigation

import kotlinx.serialization.Serializable

sealed interface FriendsMeetRoute {
  @Serializable data object FriendsRoute : FriendsMeetRoute

  @Serializable data object ChatsTabRoute : FriendsMeetRoute

  @Serializable data object LetterboxRoute : FriendsMeetRoute

  @Serializable data object SettingsRoute : FriendsMeetRoute

  @Serializable data class ChatRoute(val chatId: String, val friendUid: String) : FriendsMeetRoute

  @Serializable
  data class CallRoute(
    val callId: String,
    val callType: String,
    val peerName: String,
    val offerSdp: String? = null,
  ) : FriendsMeetRoute

  @Serializable data object LinkedDevicesRoute : FriendsMeetRoute

  @Serializable data object PrivacyRoute : FriendsMeetRoute

  @Serializable data object BackupRoute : FriendsMeetRoute

  @Serializable
  data class ViewProfileRoute(
    val peerUid: String,
    val peerMeetUid: String,
    val peerName: String,
    val peerPhotoUrl: String? = null,
    val peerOnline: Boolean = false,
  ) : FriendsMeetRoute
}
