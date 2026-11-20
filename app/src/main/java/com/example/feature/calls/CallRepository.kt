package com.example.feature.calls

import com.example.core.common.OperationType
import com.example.core.common.handleFirestoreError
import com.example.core.firebase.FirestorePaths
import com.example.core.model.CallSession
import com.example.core.model.CallStatus
import com.example.core.model.CallType
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class IceCandidateDoc(
  val id: String = "",
  val callId: String = "",
  val senderUid: String = "",
  val sdpMid: String = "",
  val sdpMLineIndex: Int = 0,
  val candidate: String = "",
)

class CallRepository(private val firestore: FirebaseFirestore) {

  fun observeIncomingRingingCall(myUid: String): Flow<CallSession?> = callbackFlow {
    val reg =
      firestore
        .collection(FirestorePaths.CALLS)
        .whereArrayContains("participantUids", myUid)
        .addSnapshotListener { snap, err ->
          if (err != null) {
            handleFirestoreError(err, OperationType.LIST, FirestorePaths.CALLS)
            return@addSnapshotListener
          }
          val ringing =
            snap
              ?.documents
              ?.mapNotNull { it.toObject(CallSession::class.java) }
              ?.filter {
                it.calleeUid == myUid && it.status == CallStatus.RINGING.name
              }
              ?.maxByOrNull { it.createdAt?.toDate()?.time ?: 0L }
          trySend(ringing)
        }
    awaitClose { reg.remove() }
  }

  fun observeCallSession(callId: String): Flow<CallSession?> = callbackFlow {
    val reg =
      firestore.collection(FirestorePaths.CALLS).document(callId).addSnapshotListener { snap, err ->
        if (err != null) {
          handleFirestoreError(err, OperationType.GET, "${FirestorePaths.CALLS}/$callId")
          return@addSnapshotListener
        }
        trySend(snap?.takeIf { it.exists() }?.toObject(CallSession::class.java))
      }
    awaitClose { reg.remove() }
  }

  fun observeRemoteIceCandidates(callId: String, myUid: String): Flow<List<IceCandidateDoc>> =
    callbackFlow {
      val reg =
        firestore
          .collection(FirestorePaths.CALLS)
          .document(callId)
          .collection(FirestorePaths.ICE_CANDIDATES)
          .whereArrayContains("participantUids", myUid)
          .addSnapshotListener { snap, err ->
            if (err != null) return@addSnapshotListener
            val items =
              snap
                ?.documents
                ?.mapNotNull { d ->
                  val sender = d.getString("senderUid") ?: return@mapNotNull null
                  if (sender == myUid) return@mapNotNull null
                  IceCandidateDoc(
                    id = d.id,
                    callId = d.getString("callId").orEmpty(),
                    senderUid = sender,
                    sdpMid = d.getString("sdpMid").orEmpty(),
                    sdpMLineIndex = (d.getLong("sdpMLineIndex") ?: 0L).toInt(),
                    candidate = d.getString("candidate").orEmpty(),
                  )
                } ?: emptyList()
            trySend(items)
          }
      awaitClose { reg.remove() }
    }

  suspend fun createOutgoingCall(
    callerUid: String,
    callerName: String,
    calleeUid: String,
    callType: CallType,
    e2eeFingerprint: String?,
  ): String {
    val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(18)}"
    val participants = listOf(callerUid, calleeUid)
    val doc =
      mutableMapOf<String, Any>(
        "callerUid" to callerUid,
        "calleeUid" to calleeUid,
        "participantUids" to participants,
        "callType" to callType.name,
        "status" to CallStatus.RINGING.name,
        "durationSeconds" to 0L,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (!e2eeFingerprint.isNullOrBlank()) {
      doc["e2eeFingerprint"] = e2eeFingerprint
    }
    firestore.collection(FirestorePaths.CALLS).document(callId).set(doc).await()

    try {
      firestore
        .collection(FirestorePaths.NOTIFICATIONS)
        .document("notif_${UUID.randomUUID().toString().replace("-", "").take(16)}")
        .set(
          mapOf(
            "recipientUid" to calleeUid,
            "senderUid" to callerUid,
            "type" to "CALL",
            "title" to "Incoming ${callType.name.lowercase()} call",
            "body" to "$callerName is calling you on Friends Meet",
            "targetId" to callId,
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp(),
          )
        )
        .await()
    } catch (_: Exception) {
      // Non-fatal notification log
    }

    return callId
  }

  suspend fun publishOfferSdp(callId: String, sdp: String) {
    firestore
      .collection(FirestorePaths.CALLS)
      .document(callId)
      .update(mapOf("offerSdp" to sdp, "updatedAt" to FieldValue.serverTimestamp()))
      .await()
  }

  suspend fun acceptCallWithAnswerSdp(callId: String, answerSdp: String) {
    firestore
      .collection(FirestorePaths.CALLS)
      .document(callId)
      .update(
        mapOf(
          "status" to CallStatus.ACCEPTED.name,
          "answerSdp" to answerSdp,
          "startedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }

  suspend fun updateCallStatus(
    callId: String,
    status: CallStatus,
    durationSeconds: Long = 0L,
  ) {
    val updates =
      mutableMapOf<String, Any>(
        "status" to status.name,
        "durationSeconds" to durationSeconds,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (status == CallStatus.ENDED || status == CallStatus.DECLINED || status == CallStatus.MISSED) {
      updates["endedAt"] = FieldValue.serverTimestamp()
    }
    try {
      firestore.collection(FirestorePaths.CALLS).document(callId).update(updates).await()
    } catch (_: Exception) {
      // Ignore if already in terminal state
    }
  }

  suspend fun sendIceCandidate(
    callId: String,
    senderUid: String,
    participantUids: List<String>,
    sdpMid: String,
    sdpMLineIndex: Int,
    candidate: String,
  ) {
    val id = "ice_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    firestore
      .collection(FirestorePaths.CALLS)
      .document(callId)
      .collection(FirestorePaths.ICE_CANDIDATES)
      .document(id)
      .set(
        mapOf(
          "callId" to callId,
          "senderUid" to senderUid,
          "participantUids" to participantUids,
          "sdpMid" to sdpMid.take(64),
          "sdpMLineIndex" to sdpMLineIndex.coerceIn(0, 16),
          "candidate" to candidate.take(2048),
          "createdAt" to FieldValue.serverTimestamp(),
        )
      )
      .await()
  }
}
