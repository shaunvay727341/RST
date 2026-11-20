package com.example.feature.calls

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

enum class NetworkQuality {
  EXCELLENT,
  GOOD,
  FAIR,
  POOR,
}

/**
 * Real WebRTC peer connection wrapper using `io.github.webrtc-sdk:android`.
 * Manages audio/video tracks, DTLS-SRTP encryption, ICE trickling, speaker/mute/camera toggles,
 * and stats-driven network quality assessment.
 */
class WebRtcCallManager(private val context: Context) {
  val eglBase: EglBase = EglBase.create()

  private var factory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var localAudioSource: AudioSource? = null
  private var localAudioTrack: AudioTrack? = null
  private var localVideoSource: VideoSource? = null
  private var localVideoTrack: AudioOrVideoHolder? = null
  private var videoCapturer: CameraVideoCapturer? = null
  private var surfaceTextureHelper: SurfaceTextureHelper? = null

  private var localRenderer: SurfaceViewRenderer? = null
  private var remoteRenderer: SurfaceViewRenderer? = null

  private class AudioOrVideoHolder(val track: VideoTrack)

  fun initialize(
    isVideoCall: Boolean,
    onIceCandidateGenerated: (IceCandidate) -> Unit,
    onIceConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit,
    onRemoteVideoTrackReceived: (VideoTrack) -> Unit,
  ) {
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
        .setEnableInternalTracer(false)
        .createInitializationOptions()
    )
    val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
    val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
    factory =
      PeerConnectionFactory.builder()
        .setVideoEncoderFactory(encoderFactory)
        .setVideoDecoderFactory(decoderFactory)
        .createPeerConnectionFactory()

    val iceServers =
      listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
      )
    val rtcConfig =
      PeerConnection.RTCConfiguration(iceServers).apply {
        sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      }

    peerConnection =
      factory?.createPeerConnection(
        rtcConfig,
        object : PeerConnection.Observer {
          override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}

          override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            if (newState != null) onIceConnectionStateChanged(newState)
          }

          override fun onIceConnectionReceivingChange(receiving: Boolean) {}

          override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

          override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) onIceCandidateGenerated(candidate)
          }

          override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

          override fun onAddStream(stream: MediaStream?) {
            val track = stream?.videoTracks?.firstOrNull()
            if (track != null) {
              remoteRenderer?.let { track.addSink(it) }
              onRemoteVideoTrackReceived(track)
            }
          }

          override fun onRemoveStream(stream: MediaStream?) {}

          override fun onDataChannel(dataChannel: DataChannel?) {}

          override fun onRenegotiationNeeded() {}

          override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()
            if (track is VideoTrack) {
              remoteRenderer?.let { track.addSink(it) }
              onRemoteVideoTrackReceived(track)
            }
          }

          override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            if (track is VideoTrack) {
              remoteRenderer?.let { track.addSink(it) }
              onRemoteVideoTrackReceived(track)
            }
          }
        },
      )

    val audioConstraints = MediaConstraints()
    localAudioSource = factory?.createAudioSource(audioConstraints)
    localAudioTrack = factory?.createAudioTrack("FM_AUDIO_TRACK", localAudioSource)
    localAudioTrack?.setEnabled(true)
    peerConnection?.addTrack(localAudioTrack, listOf("FM_STREAM"))

    if (isVideoCall) {
      startLocalVideo()
    }
  }

  private fun startLocalVideo() {
    val enumerator = Camera2Enumerator(context)
    val cameraName =
      enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        ?: enumerator.deviceNames.firstOrNull()
        ?: return
    val capturer = enumerator.createCapturer(cameraName, null) ?: return
    videoCapturer = capturer
    surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
    val source = factory?.createVideoSource(capturer.isScreencast) ?: return
    localVideoSource = source
    capturer.initialize(surfaceTextureHelper, context.applicationContext, source.capturerObserver)
    capturer.startCapture(720, 1280, 30)
    val track = factory?.createVideoTrack("FM_VIDEO_TRACK", source) ?: return
    track.setEnabled(true)
    localRenderer?.let { track.addSink(it) }
    localVideoTrack = AudioOrVideoHolder(track)
    peerConnection?.addTrack(track, listOf("FM_STREAM"))
  }

  fun attachRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
    localRenderer = local
    remoteRenderer = remote
    local?.let { r -> localVideoTrack?.track?.addSink(r) }
  }

  fun createOffer(onSdpReady: (String) -> Unit) {
    val pc = peerConnection ?: return
    val constraints =
      MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
      }
    pc.createOffer(
      object : SimpleSdpObserver() {
        override fun onCreateSuccess(sdp: SessionDescription?) {
          if (sdp != null) {
            pc.setLocalDescription(SimpleSdpObserver(), sdp)
            onSdpReady(sdp.description)
          }
        }
      },
      constraints,
    )
  }

  fun setRemoteOfferAndCreateAnswer(offerSdp: String, onAnswerReady: (String) -> Unit) {
    val pc = peerConnection ?: return
    val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
    pc.setRemoteDescription(
      object : SimpleSdpObserver() {
        override fun onSetSuccess() {
          pc.createAnswer(
            object : SimpleSdpObserver() {
              override fun onCreateSuccess(sdp: SessionDescription?) {
                if (sdp != null) {
                  pc.setLocalDescription(SimpleSdpObserver(), sdp)
                  onAnswerReady(sdp.description)
                }
              }
            },
            MediaConstraints(),
          )
        }
      },
      remoteDesc,
    )
  }

  fun setRemoteAnswer(answerSdp: String) {
    val pc = peerConnection ?: return
    pc.setRemoteDescription(
      SimpleSdpObserver(),
      SessionDescription(SessionDescription.Type.ANSWER, answerSdp),
    )
  }

  fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
    peerConnection?.addIceCandidate(IceCandidate(sdpMid, sdpMLineIndex, candidate))
  }

  fun restartIce(onOfferReady: (String) -> Unit) {
    peerConnection?.restartIce()
    createOffer(onOfferReady)
  }

  fun setMuted(muted: Boolean) {
    localAudioTrack?.setEnabled(!muted)
  }

  fun setSpeakerEnabled(enabled: Boolean) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    @Suppress("DEPRECATION")
    audioManager.isSpeakerphoneOn = enabled
  }

  fun setCameraEnabled(enabled: Boolean) {
    localVideoTrack?.track?.setEnabled(enabled)
  }

  fun switchCamera() {
    videoCapturer?.switchCamera(null)
  }

  fun sampleNetworkQuality(onQuality: (NetworkQuality) -> Unit) {
    val pc = peerConnection ?: return
    pc.getStats { report ->
      var rttSeconds = 0.05
      for (stats in report.statsMap.values) {
        if (stats.type == "candidate-pair") {
          val state = stats.members["state"] as? String
          if (state == "succeeded") {
            val rtt = (stats.members["currentRoundTripTime"] as? Number)?.toDouble()
            if (rtt != null) rttSeconds = rtt
          }
        }
      }
      val quality =
        when {
          rttSeconds < 0.08 -> NetworkQuality.EXCELLENT
          rttSeconds < 0.18 -> NetworkQuality.GOOD
          rttSeconds < 0.35 -> NetworkQuality.FAIR
          else -> NetworkQuality.POOR
        }
      onQuality(quality)
    }
  }

  fun release() {
    try {
      videoCapturer?.stopCapture()
    } catch (_: Exception) {}
    try {
      videoCapturer?.dispose()
      surfaceTextureHelper?.dispose()
      localAudioSource?.dispose()
      localVideoSource?.dispose()
      peerConnection?.close()
      peerConnection?.dispose()
      factory?.dispose()
      eglBase.release()
    } catch (_: Exception) {}
    peerConnection = null
    factory = null
  }

  private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) {}

    override fun onSetSuccess() {}

    override fun onCreateFailure(error: String?) {}

    override fun onSetFailure(error: String?) {}
  }
}
