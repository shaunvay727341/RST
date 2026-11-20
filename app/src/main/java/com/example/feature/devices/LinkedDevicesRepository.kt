package com.example.feature.devices

import android.os.Build
import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.model.DeviceLinkToken
import com.example.core.model.LinkedDevice
import com.example.core.security.E2eeCryptoManager
import com.example.core.security.SecureKeyStoreHelper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class GeneratedQrLink(val tokenId: String, val ownerUid: String, val expiresAtMillis: Long)

class LinkedDevicesRepository(
  private val firestore: FirebaseFirestore,
  private val functions: FirebaseFunctions,
  private val e2eeCryptoManager: E2eeCryptoManager,
) {
  fun observeLinkedDevices(ownerUid: String): Flow<List<LinkedDevice>> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.LINKED_DEVICES)
        .whereEqualTo("ownerUid", ownerUid)
        .whereEqualTo("active", true)
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.LINKED_DEVICES)
            return@addSnapshotListener
          }
          val list =
            snap?.documents?.mapNotNull { it.toObject(LinkedDevice::class.java) } ?: emptyList()
          trySend(list)
        }
    awaitClose { reg.remove() }
  }

  /**
   * Generates a short-lived (5-minute) single-use pairing token.
   * Never embeds permanent account credentials or private keys in QR codes.
   */
  suspend fun createShortLivedLinkToken(uid: String): GeneratedQrLink {
    val pubKey = e2eeCryptoManager.getOrCreateIdentityPublicKeyBase64(uid)
    try {
      val result =
        functions
          .getHttpsCallable("createDeviceLinkToken")
          .call(mapOf("primaryDevicePublicKey" to pubKey))
          .await()
      @Suppress("UNCHECKED_CAST")
      val map = result.getData() as? Map<String, Any?>
      val tokenId = map?.get("tokenId") as? String
      val expires = (map?.get("expiresAtMillis") as? Number)?.toLong()
      if (!tokenId.isNullOrBlank() && expires != null) {
        return GeneratedQrLink(tokenId = tokenId, ownerUid = uid, expiresAtMillis = expires)
      }
    } catch (_: Exception) {
      // Fallback to rule-verified Firestore token creation
    }

    val active =
      firestore
        .collection(FirestorePaths.LINKED_DEVICES)
        .whereEqualTo("ownerUid", uid)
        .whereEqualTo("active", true)
        .get()
        .await()
    require(active.size() < 2) { "Maximum of 2 linked devices allowed." }

    val tokenId = "tok_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    val expiresMs = System.currentTimeMillis() + 5L * 60L * 1000L
    firestore
      .collection(FirestorePaths.DEVICE_LINK_TOKENS)
      .document(tokenId)
      .set(
        mapOf(
          "ownerUid" to uid,
          "tokenId" to tokenId,
          "primaryDevicePublicKey" to pubKey,
          "used" to false,
          "expiresAt" to Timestamp(Date(expiresMs)),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
    return GeneratedQrLink(tokenId = tokenId, ownerUid = uid, expiresAtMillis = expiresMs)
  }

  /**
   * Redeems a single-use pairing token scanned from the primary device QR and binds
   * this secondary device with a Keystore-protected device identity.
   */
  suspend fun redeemQrLinkToken(tokenId: String, tokenOwnerUid: String, currentUid: String) {
    require(tokenOwnerUid == currentUid) {
      "Sign in with the same Google account on this device before scanning the link QR."
    }
    val deviceId = "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".take(70)
    val devicePlatform = "Android ${Build.VERSION.RELEASE}"
    val devicePubKey = e2eeCryptoManager.getOrCreateIdentityPublicKeyBase64(currentUid)
    // Seal local device session token using hardware-backed Android Keystore
    SecureKeyStoreHelper.encrypt("linked:$deviceId:$tokenId")

    try {
      functions
        .getHttpsCallable("redeemDeviceLinkToken")
        .call(
          mapOf(
            "tokenId" to tokenId,
            "deviceId" to deviceId,
            "deviceName" to deviceName,
            "devicePlatform" to devicePlatform,
            "devicePublicKey" to devicePubKey,
          )
        )
        .await()
      return
    } catch (_: Exception) {
      // Fallback to rule-verified atomic transaction below
    }

    val activeSnap =
      firestore
        .collection(FirestorePaths.LINKED_DEVICES)
        .whereEqualTo("ownerUid", currentUid)
        .whereEqualTo("active", true)
        .get()
        .await()
    require(activeSnap.size() < 2) { "Maximum of 2 linked devices already active." }

    firestore
      .runTransaction { tx ->
        val tokenRef = firestore.collection(FirestorePaths.DEVICE_LINK_TOKENS).document(tokenId)
        val snap = tx.get(tokenRef)
        val tokenObj = snap.toObject(DeviceLinkToken::class.java)
          ?: throw IllegalArgumentException("Pairing token not found.")
        require(!tokenObj.used) { "This QR token has already been used." }
        val exp = tokenObj.expiresAt?.toDate()?.time ?: 0L
        require(exp >= System.currentTimeMillis()) { "QR pairing token expired." }

        tx.update(
          tokenRef,
          mapOf(
            "used" to true,
            "redeemedByDeviceId" to deviceId,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
        tx.set(
          firestore.collection(FirestorePaths.LINKED_DEVICES).document(deviceId),
          mapOf(
            "ownerUid" to currentUid,
            "deviceName" to deviceName,
            "devicePlatform" to devicePlatform,
            "devicePublicKey" to devicePubKey,
            "active" to true,
            "lastSyncedAt" to FieldValue.serverTimestamp(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      .await()
  }

  suspend fun remoteLogoutDevice(deviceId: String) {
    firestore
      .collection(FirestorePaths.LINKED_DEVICES)
      .document(deviceId)
      .update(mapOf("active" to false, "updatedAt" to FieldValue.serverTimestamp()))
      .await()
  }
}
