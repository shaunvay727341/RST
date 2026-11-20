package com.example.core.firebase

/**
 * Single source of truth for Firestore collection names and deterministic document IDs.
 * Every path here mirrors `/firebase-blueprint.json` and `/firestore.rules` 1:1.
 */
object FirestorePaths {
  const val USERS = "users"
  const val USER_IDS = "userIds"
  const val FRIENDS = "friends"
  const val CHATS = "chats"
  const val MESSAGES = "messages"
  const val CALLS = "calls"
  const val ICE_CANDIDATES = "iceCandidates"
  const val FRIEND_TAGS = "friendTags"
  const val LETTERBOX = "letterbox"
  const val LINKED_DEVICES = "linkedDevices"
  const val PRIVACY_SETTINGS = "privacySettings"
  const val NOTIFICATIONS = "notifications"
  const val BACKUP_METADATA = "backupMetadata"
  const val USER_SETTINGS = "userSettings"
  const val DEVICE_LINK_TOKENS = "deviceLinkTokens"

  fun canonicalChatId(uidA: String, uidB: String): String {
    return if (uidA <= uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
  }

  fun friendConnectionId(ownerUid: String, friendUid: String): String {
    return "${ownerUid}_${friendUid}"
  }

  fun friendTagId(ownerUid: String, friendUid: String): String {
    return "${ownerUid}_${friendUid}"
  }
}
