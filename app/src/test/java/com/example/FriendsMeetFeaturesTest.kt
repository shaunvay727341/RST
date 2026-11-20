package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.model.FriendConnection
import com.example.core.qr.QrCodeGenerator
import com.example.core.qr.QrPayload
import com.example.core.security.E2eeCryptoManager
import com.example.feature.friends.FriendsUiState
import com.example.feature.registration.RegistrationRepository
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import android.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FriendsMeetFeaturesTest {

  @Test
  fun `qr payload parser distinguishes friend UID and single-use device link token`() {
    val friend = QrPayload.parse(QrPayload.encodeFriendPayload("48291037"))
    assertEquals(QrPayload.FriendProfile("48291037"), friend)

    val rawUid = QrPayload.parse("91827364")
    assertEquals(QrPayload.FriendProfile("91827364"), rawUid)

    val link = QrPayload.parse(QrPayload.encodeDeviceLinkPayload("tok_abc123", "uid_owner_1"))
    assertEquals(QrPayload.DeviceLinkToken("tok_abc123", "uid_owner_1"), link)

    val bitmap = QrCodeGenerator.generateBitmap("fm-friend://48291037", 128)
    assertEquals(128, bitmap.width)
    assertEquals(128, bitmap.height)
  }

  @Test
  fun `under-13 age calculation classifies child vs adult correctly`() {
    val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    assertTrue(RegistrationRepository.computeAgeYears("${currentYear - 10}-06-15") < 13)
    assertFalse(RegistrationRepository.computeAgeYears("${currentYear - 14}-01-01") < 13)
  }

  @Test
  fun `friends list orders online first then offline in single continuous list`() {
    val friends =
      listOf(
        FriendConnection(id = "1", friendUid = "u1", friendName = "Zara", friendOnline = false),
        FriendConnection(id = "2", friendUid = "u2", friendName = "Bob", friendOnline = true),
        FriendConnection(id = "3", friendUid = "u3", friendName = "Alice", friendOnline = false),
        FriendConnection(id = "4", friendUid = "u4", friendName = "Amir", friendOnline = true),
      )
    val state = FriendsUiState(friends = friends)
    assertEquals(
      listOf("Amir", "Bob", "Alice", "Zara"),
      state.displayedFriends.map { it.friendName },
    )
  }

  @Test
  fun `e2ee crypto manager encrypts, decrypts, and computes backup key & safety fingerprint`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = E2eeCryptoManager(context)

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val peerKp = kpg.generateKeyPair()
    val peerPubBase64 = Base64.encodeToString(peerKp.public.encoded, Base64.NO_WRAP)

    val fp = manager.computeSafetyFingerprint("pub_a", peerPubBase64)
    assertTrue(fp.isNotBlank())
    assertEquals(fp, manager.computeSafetyFingerprint(peerPubBase64, "pub_a"))

    val (backupKey, checksum) = manager.deriveBackupKey("StrongPass123", "uid_alice")
    assertEquals(64, checksum.length)
    val (cipherBytes, iv) = manager.encryptBytes("FriendsMeetSecretBackup".toByteArray(), backupKey)
    val restored = String(manager.decryptBytes(cipherBytes, iv, backupKey))
    assertEquals("FriendsMeetSecretBackup", restored)
  }
}
