package com.example.feature.calls

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.model.CallStatus
import com.example.core.model.CallType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

data class CallUiState(
  val callId: String = "",
  val callType: CallType = CallType.AUDIO,
  val status: CallStatus = CallStatus.RINGING,
  val isCaller: Boolean = true,
  val peerName: String = "Friend",
  val peerPhotoUrl: String? = null,
  val e2eeFingerprint: String? = null,
  val isMuted: Boolean = false,
  val isSpeakerOn: Boolean = false,
  val isCameraOn: Boolean = true,
  val durationSeconds: Long = 0L,
  val reconnectSecondsLeft: Int = 30,
  val networkQuality: NetworkQuality = NetworkQuality.GOOD,
  val callEnded: Boolean = false,
)

class CallViewModel(
  context: Context,
  private val callRepository: CallRepository,
  private val callId: String,
  private val myUid: String,
  peerName: String,
  peerPhotoUrl: String?,
) : ViewModel() {
  val webRtcManager = WebRtcCallManager(context.applicationContext)

  private val _uiState =
    MutableStateFlow(
      CallUiState(
        callId = callId,
        peerName = peerName,
        peerPhotoUrl = peerPhotoUrl,
      )
    )
  val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

  private val processedIceIds = mutableSetOf<String>()
  private var remoteOfferApplied = false
  private var remoteAnswerApplied = false
  private var initializedWebRtc = false
  private var sessionParticipants: List<String> = emptyList()
  private var durationJob: Job? = null
  private var reconnectJob: Job? = null
  private var statsJob: Job? = null

  init {
    observeSessionAndIce()
  }

  fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
    webRtcManager.attachRenderers(local, remote)
  }

  fun startWebRtcIfPermitted(callType: CallType, isCaller: Boolean, offerSdp: String?) {
    if (initializedWebRtc) return
    initializedWebRtc = true

    webRtcManager.initialize(
      isVideoCall = callType == CallType.VIDEO,
      onIceCandidateGenerated = { candidate ->
        viewModelScope.launch {
          val participants = sessionParticipants
          if (participants.size == 2) {
            callRepository.sendIceCandidate(
              callId = callId,
              senderUid = myUid,
              participantUids = participants,
              sdpMid = candidate.sdpMid ?: "0",
              sdpMLineIndex = candidate.sdpMLineIndex,
              candidate = candidate.sdp,
            )
          }
        }
      },
      onIceConnectionStateChanged = { iceState ->
        when (iceState) {
          PeerConnection.IceConnectionState.CONNECTED,
          PeerConnection.IceConnectionState.COMPLETED -> {
            reconnectJob?.cancel()
            _uiState.update { it.copy(status = CallStatus.ACCEPTED, reconnectSecondsLeft = 30) }
            viewModelScope.launch {
              callRepository.updateCallStatus(callId, CallStatus.ACCEPTED, _uiState.value.durationSeconds)
            }
            startDurationAndStatsLoops()
          }
          PeerConnection.IceConnectionState.DISCONNECTED,
          PeerConnection.IceConnectionState.FAILED -> {
            beginReconnectingRecovery()
          }
          else -> {}
        }
      },
      onRemoteVideoTrackReceived = {},
    )

    if (isCaller) {
      webRtcManager.createOffer { sdp ->
        viewModelScope.launch { callRepository.publishOfferSdp(callId, sdp) }
      }
    } else if (!offerSdp.isNullOrBlank() && !remoteOfferApplied) {
      remoteOfferApplied = true
      webRtcManager.setRemoteOfferAndCreateAnswer(offerSdp) { answer ->
        viewModelScope.launch { callRepository.acceptCallWithAnswerSdp(callId, answer) }
      }
    }
  }

  private fun observeSessionAndIce() {
    viewModelScope.launch {
      callRepository.observeCallSession(callId).collect { session ->
        if (session == null) return@collect
        if (session.participantUids.size == 2) {
          sessionParticipants = session.participantUids
        }
        val isCaller = session.callerUid == myUid
        val cType = CallType.fromRaw(session.callType)
        val cStatus = CallStatus.fromRaw(session.status)
        _uiState.update {
          it.copy(
            callType = cType,
            status = cStatus,
            isCaller = isCaller,
            e2eeFingerprint = session.e2eeFingerprint ?: it.e2eeFingerprint,
            isCameraOn = cType == CallType.VIDEO && it.isCameraOn,
          )
        }

        if (isCaller && !session.answerSdp.isNullOrBlank() && !remoteAnswerApplied) {
          remoteAnswerApplied = true
          webRtcManager.setRemoteAnswer(session.answerSdp)
          startDurationAndStatsLoops()
        } else if (!isCaller && !session.offerSdp.isNullOrBlank() && initializedWebRtc && !remoteOfferApplied) {
          remoteOfferApplied = true
          webRtcManager.setRemoteOfferAndCreateAnswer(session.offerSdp) { answer ->
            viewModelScope.launch { callRepository.acceptCallWithAnswerSdp(callId, answer) }
          }
        }

        if (cStatus == CallStatus.ENDED || cStatus == CallStatus.DECLINED || cStatus == CallStatus.MISSED) {
          endLocally()
        }
      }
    }

    viewModelScope.launch {
      callRepository.observeRemoteIceCandidates(callId, myUid).collect { list ->
        for (ice in list) {
          if (processedIceIds.add(ice.id)) {
            webRtcManager.addRemoteIceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.candidate)
          }
        }
      }
    }
  }

  private fun startDurationAndStatsLoops() {
    if (durationJob?.isActive == true) return
    durationJob =
      viewModelScope.launch {
        while (true) {
          delay(1000)
          if (_uiState.value.status == CallStatus.ACCEPTED) {
            _uiState.update { it.copy(durationSeconds = it.durationSeconds + 1L) }
          }
        }
      }
    statsJob =
      viewModelScope.launch {
        while (true) {
          delay(2000)
          webRtcManager.sampleNetworkQuality { quality ->
            _uiState.update { it.copy(networkQuality = quality) }
          }
        }
      }
  }

  private fun beginReconnectingRecovery() {
    if (reconnectJob?.isActive == true || _uiState.value.callEnded) return
    _uiState.update { it.copy(status = CallStatus.RECONNECTING, reconnectSecondsLeft = 30) }
    viewModelScope.launch {
      callRepository.updateCallStatus(callId, CallStatus.RECONNECTING, _uiState.value.durationSeconds)
    }
    if (_uiState.value.isCaller) {
      webRtcManager.restartIce { newOffer ->
        viewModelScope.launch { callRepository.publishOfferSdp(callId, newOffer) }
      }
    }
    reconnectJob =
      viewModelScope.launch {
        for (sec in 30 downTo 1) {
          _uiState.update { it.copy(reconnectSecondsLeft = sec) }
          delay(1000)
          if (_uiState.value.status == CallStatus.ACCEPTED) return@launch
        }
        // Maximum 30-second recovery attempt exhausted -> end call cleanly
        hangUp()
      }
  }

  fun toggleMute() {
    val next = !_uiState.value.isMuted
    webRtcManager.setMuted(next)
    _uiState.update { it.copy(isMuted = next) }
  }

  fun toggleSpeaker() {
    val next = !_uiState.value.isSpeakerOn
    webRtcManager.setSpeakerEnabled(next)
    _uiState.update { it.copy(isSpeakerOn = next) }
  }

  fun toggleCamera() {
    val next = !_uiState.value.isCameraOn
    webRtcManager.setCameraEnabled(next)
    _uiState.update { it.copy(isCameraOn = next) }
  }

  fun switchCamera() {
    webRtcManager.switchCamera()
  }

  fun hangUp() {
    val duration = _uiState.value.durationSeconds
    viewModelScope.launch {
      callRepository.updateCallStatus(callId, CallStatus.ENDED, duration)
      endLocally()
    }
  }

  private fun endLocally() {
    durationJob?.cancel()
    reconnectJob?.cancel()
    statsJob?.cancel()
    webRtcManager.release()
    _uiState.update { it.copy(status = CallStatus.ENDED, callEnded = true) }
  }

  override fun onCleared() {
    super.onCleared()
    endLocally()
  }
}
