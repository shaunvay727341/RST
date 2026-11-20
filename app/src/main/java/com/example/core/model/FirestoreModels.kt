package com.example.core.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

enum class Gender {
  MALE,
  FEMALE,
  OTHER;

  companion object {
    fun fromRaw(raw: String?): Gender = entries.firstOrNull { it.name == raw } ?: OTHER
  }
}

enum class AccountStatus {
  PENDING_GUARDIAN,
  ACTIVE,
  SUSPENDED;

  companion object {
    fun fromRaw(raw: String?): AccountStatus =
      entries.firstOrNull { it.name == raw } ?: PENDING_GUARDIAN
  }
}

enum class AddedVia {
  QR_CODE,
  UID_LOOKUP;

  companion object {
    fun fromRaw(raw: String?): AddedVia = entries.firstOrNull { it.name == raw } ?: UID_LOOKUP
  }
}

enum class MessageType {
  TEXT,
  VOICE,
  IMAGE,
  GIF,
  STICKER,
  REACTION,
  QUICK_MESSAGE;

  companion object {
    fun fromRaw(raw: String?): MessageType = entries.firstOrNull { it.name == raw } ?: TEXT
  }
}

enum class CallType {
  AUDIO,
  VIDEO;

  companion object {
    fun fromRaw(raw: String?): CallType = entries.firstOrNull { it.name == raw } ?: AUDIO
  }
}

enum class CallStatus {
  RINGING,
  ACCEPTED,
  DECLINED,
  RECONNECTING,
  ENDED,
  MISSED;

  companion object {
    fun fromRaw(raw: String?): CallStatus = entries.firstOrNull { it.name == raw } ?: ENDED
  }
}

enum class LetterboxCategory {
  GUARDIAN_VERIFICATION,
  SECURITY_ALERT,
  ACCOUNT_INFO,
  SYSTEM_NOTICE;

  companion object {
    fun fromRaw(raw: String?): LetterboxCategory =
      entries.firstOrNull { it.name == raw } ?: SYSTEM_NOTICE
  }
}

enum class VisibilityScope {
  EVERYONE,
  FRIENDS,
  NOBODY;

  companion object {
    fun fromRaw(raw: String?): VisibilityScope =
      entries.firstOrNull { it.name == raw } ?: FRIENDS
  }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val friendsMeetUid: String = "",
  val fullName: String = "",
  val dateOfBirth: String = "",
  val gender: String = Gender.OTHER.name,
  val phoneNumber: String? = null,
  val email: String = "",
  val photoUrl: String? = null,
  val bio: String? = null,
  val online: Boolean = false,
  val lastSeenAt: Timestamp? = null,
  @get:PropertyName("isUnder13") @PropertyName("isUnder13") val isUnder13: Boolean = false,
  val guardianVerified: Boolean = false,
  val accountStatus: String = AccountStatus.PENDING_GUARDIAN.name,
  val identityPublicKey: String? = null,
  val fcmToken: String? = null,
  val oneSignalPlayerId: String? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
) {
  fun toCreateMap(): Map<String, Any> =
    mapOf(
        "uid" to uid,
        "friendsMeetUid" to friendsMeetUid,
        "fullName" to fullName,
        "dateOfBirth" to dateOfBirth,
        "gender" to gender,
        "phoneNumber" to phoneNumber,
        "email" to email,
        "photoUrl" to photoUrl,
        "bio" to bio,
        "online" to online,
        "lastSeenAt" to FieldValue.serverTimestamp(),
        "isUnder13" to isUnder13,
        "guardianVerified" to guardianVerified,
        "accountStatus" to accountStatus,
        "identityPublicKey" to identityPublicKey,
        "fcmToken" to fcmToken,
        "oneSignalPlayerId" to oneSignalPlayerId,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      .filterValues { it != null }
      .mapValues { it.value!! }
}

@Keep
@IgnoreExtraProperties
data class UserIdDirectoryEntry(
  @DocumentId val id: String = "",
  val uid: String = "",
  val friendsMeetUid: String = "",
  val displayName: String = "",
  val photoUrl: String? = null,
  val online: Boolean = false,
  val accountStatus: String = AccountStatus.ACTIVE.name,
  val identityPublicKey: String? = null,
  val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FriendConnection(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val friendUid: String = "",
  val friendMeetUid: String = "",
  val friendName: String = "",
  val friendPhotoUrl: String? = null,
  val friendOnline: Boolean = false,
  val blocked: Boolean = false,
  val addedVia: String = AddedVia.UID_LOOKUP.name,
  val chatId: String = "",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatThread(
  @DocumentId val id: String = "",
  val participantUids: List<String> = emptyList(),
  val lastMessageText: String? = null,
  val lastMessageType: String? = null,
  val lastMessageSenderUid: String? = null,
  val lastMessageAt: Timestamp? = null,
  val disappearingDurationSeconds: Long = 0L,
  val wallpaperId: String? = null,
  val themeId: String? = null,
  val typingUids: List<String> = emptyList(),
  val e2eeSessionVersion: Long = 1L,
  val chatMode: String = "FRIEND",
  val oldChatInitiatorUid: String? = null,
  val oldChatMessageLimit: Long = 3L,
  val oldChatSentCount: Long = 0L,
  val oldChatUnlocked: Boolean = false,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatMessage(
  @DocumentId val id: String = "",
  val chatId: String = "",
  val senderUid: String = "",
  val recipientUid: String = "",
  val participantUids: List<String> = emptyList(),
  val messageType: String = MessageType.TEXT.name,
  val ciphertext: String = "",
  val iv: String = "",
  val mediaUrl: String? = null,
  val mediaDurationMs: Long? = null,
  val replyToMessageId: String? = null,
  val forwardedCount: Long = 0L,
  val reactions: Map<String, String> = emptyMap(),
  val readByUids: List<String> = emptyList(),
  val deleteForMeUids: List<String> = emptyList(),
  val deletedForEveryone: Boolean = false,
  val edited: Boolean = false,
  val editedAt: Timestamp? = null,
  val expiresAt: Timestamp? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CallSession(
  @DocumentId val id: String = "",
  val callerUid: String = "",
  val calleeUid: String = "",
  val participantUids: List<String> = emptyList(),
  val callType: String = CallType.AUDIO.name,
  val status: String = CallStatus.RINGING.name,
  val offerSdp: String? = null,
  val answerSdp: String? = null,
  val e2eeFingerprint: String? = null,
  val durationSeconds: Long = 0L,
  val startedAt: Timestamp? = null,
  val endedAt: Timestamp? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class FriendTag(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val friendUid: String = "",
  val category: String = "",
  val label: String = "",
  val colorHex: String = "#0A8D87",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class LetterboxMessage(
  @DocumentId val id: String = "",
  val recipientUid: String = "",
  val category: String = LetterboxCategory.SYSTEM_NOTICE.name,
  val title: String = "",
  val body: String = "",
  val read: Boolean = false,
  val trashed: Boolean = false,
  val trashedAt: Timestamp? = null,
  val purgeAfterAt: Timestamp? = null,
  val verificationAttempts: Long = 0L,
  val verificationExpiresAt: Timestamp? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class LinkedDevice(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val deviceName: String = "",
  val devicePlatform: String = "",
  val devicePublicKey: String = "",
  val active: Boolean = true,
  val lastSyncedAt: Timestamp? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class PrivacySettings(
  @DocumentId val id: String = "",
  val uid: String = "",
  val profileVisibility: String = VisibilityScope.EVERYONE.name,
  val photoVisibility: String = VisibilityScope.EVERYONE.name,
  val dobVisibility: String = VisibilityScope.FRIENDS.name,
  val onlineStatusVisibility: String = VisibilityScope.FRIENDS.name,
  val lastSeenVisibility: String = VisibilityScope.FRIENDS.name,
  val bioVisibility: String = VisibilityScope.EVERYONE.name,
  val friendListVisibility: String = VisibilityScope.FRIENDS.name,
  val phoneVisibility: String = VisibilityScope.NOBODY.name,
  val emailVisibility: String = VisibilityScope.NOBODY.name,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class NotificationEvent(
  @DocumentId val id: String = "",
  val recipientUid: String = "",
  val senderUid: String = "",
  val type: String = "MESSAGE",
  val title: String = "",
  val body: String = "",
  val targetId: String? = null,
  val read: Boolean = false,
  val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BackupMetadata(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val storagePath: String = "",
  val keyChecksumSha256: String = "",
  val encryptionAlgorithm: String = "AES-256-GCM",
  val messageCount: Long = 0L,
  val sizeBytes: Long = 0L,
  val lastBackupAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class UserSettings(
  @DocumentId val id: String = "",
  val uid: String = "",
  val themeMode: String = "SYSTEM",
  val quickMessages: List<String> = emptyList(),
  val customTagCategories: List<String> = emptyList(),
  val defaultChatWallpaper: String? = null,
  val translationAutoDetect: Boolean = true,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class DeviceLinkToken(
  @DocumentId val id: String = "",
  val ownerUid: String = "",
  val tokenId: String = "",
  val primaryDevicePublicKey: String = "",
  val used: Boolean = false,
  val redeemedByDeviceId: String? = null,
  val expiresAt: Timestamp? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class GroupThread(
  @DocumentId val id: String = "",
  val name: String = "",
  val description: String? = null,
  val creatorUid: String = "",
  val adminUids: List<String> = emptyList(),
  val participantUids: List<String> = emptyList(),
  val photoUrl: String? = null,
  val inviteCode: String? = null,
  val allowQrJoin: Boolean = true,
  val lastMessageText: String? = null,
  val lastMessageSenderUid: String? = null,
  val lastMessageAt: Timestamp? = null,
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class GroupMessage(
  @DocumentId val id: String = "",
  val groupId: String = "",
  val senderUid: String = "",
  val senderName: String = "",
  val text: String = "",
  val mediaUrl: String? = null,
  val replyToMessageId: String? = null,
  val replyToSenderName: String? = null,
  val replyToText: String? = null,
  val forwardedCount: Long = 0L,
  val createdAt: Timestamp? = null,
)

