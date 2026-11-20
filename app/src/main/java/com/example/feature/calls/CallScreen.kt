package com.example.feature.calls

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.model.CallSession
import com.example.core.model.CallStatus
import com.example.core.model.CallType
import com.example.ui.components.UserAvatar
import com.example.ui.theme.OnlineGreen
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
  viewModel: CallViewModel,
  initialOfferSdp: String?,
  onCallFinished: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  val neededPermissions =
    remember(state.callType) {
      if (state.callType == CallType.VIDEO) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
      } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
      }
    }

  var permissionsGranted by remember {
    mutableStateOf(
      neededPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
      }
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { map ->
      permissionsGranted = map.values.all { it }
    }

  LaunchedEffect(permissionsGranted, state.callType) {
    if (permissionsGranted) {
      viewModel.startWebRtcIfPermitted(state.callType, state.isCaller, initialOfferSdp)
    } else {
      permissionLauncher.launch(neededPermissions)
    }
  }

  LaunchedEffect(state.callEnded) {
    if (state.callEnded) {
      onCallFinished()
    }
  }

  Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { innerPadding ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(
            Brush.verticalGradient(listOf(Color(0xFF0A2423), Color(0xFF071313), Color(0xFF040A0A)))
          )
          .padding(innerPadding)
    ) {
      if (state.callType == CallType.VIDEO && permissionsGranted) {
        AndroidView(
          factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
              init(viewModel.webRtcManager.eglBase.eglBaseContext, null)
              setMirror(false)
              viewModel.attachRenderers(local = null, remote = this)
            }
          },
          modifier = Modifier.fillMaxSize(),
        )
        AndroidView(
          factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
              init(viewModel.webRtcManager.eglBase.eglBaseContext, null)
              setMirror(true)
              setZOrderMediaOverlay(true)
              viewModel.attachRenderers(local = this, remote = null)
            }
          },
          modifier =
            Modifier.align(Alignment.TopEnd)
              .padding(top = 72.dp, end = 16.dp)
              .size(width = 110.dp, height = 160.dp)
              .clip(RoundedCornerShape(16.dp)),
        )
      }

      // Top status & network quality bar
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.45f),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Default.Lock,
              contentDescription = "End-to-End Encrypted",
              tint = OnlineGreen,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("E2EE DTLS-SRTP", color = Color.White, style = MaterialTheme.typography.labelSmall)
          }
        }

        Surface(
          color = Color.Black.copy(alpha = 0.45f),
          shape = RoundedCornerShape(16.dp),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              Icons.Default.SignalCellularAlt,
              contentDescription = "Network quality",
              tint =
                when (state.networkQuality) {
                  NetworkQuality.EXCELLENT, NetworkQuality.GOOD -> OnlineGreen
                  NetworkQuality.FAIR -> Color(0xFFFBBF24)
                  NetworkQuality.POOR -> Color(0xFFEF4444)
                },
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = state.networkQuality.name.lowercase().replaceFirstChar { it.uppercase() },
              color = Color.White,
              style = MaterialTheme.typography.labelSmall,
            )
          }
        }
      }

      // Center identity & duration/reconnecting state
      Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (state.callType == CallType.AUDIO || state.status != CallStatus.ACCEPTED) {
          UserAvatar(name = state.peerName, photoUrl = state.peerPhotoUrl, size = 104.dp)
          Spacer(modifier = Modifier.height(16.dp))
        }
        Text(
          text = state.peerName,
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Spacer(modifier = Modifier.height(6.dp))
        val statusLabel =
          when (state.status) {
            CallStatus.RINGING -> if (state.isCaller) "Calling…" else "Incoming Call…"
            CallStatus.ACCEPTED -> formatCallDuration(state.durationSeconds)
            CallStatus.RECONNECTING ->
              "Reconnecting… (${state.reconnectSecondsLeft}s recovery)"
            CallStatus.DECLINED -> "Declined"
            CallStatus.MISSED -> "Missed"
            CallStatus.ENDED -> "Call Ended"
          }
        Text(
          text = statusLabel,
          style = MaterialTheme.typography.titleMedium,
          color =
            if (state.status == CallStatus.RECONNECTING) Color(0xFFFBBF24)
            else Color.White.copy(alpha = 0.85f),
          modifier = Modifier.testTag("call_status_text"),
        )
        state.e2eeFingerprint?.let { fp ->
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Key Fingerprint: ${fp.replace("\n", " ").take(23)}…",
            color = Color.White.copy(alpha = 0.65f),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
          )
        }
      }

      // Bottom Call Controls
      Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledIconToggleButton(
            checked = state.isMuted,
            onCheckedChange = { viewModel.toggleMute() },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("call_mute_btn"),
          ) {
            Icon(
              if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = "Mute microphone",
            )
          }

          FilledIconToggleButton(
            checked = state.isSpeakerOn,
            onCheckedChange = { viewModel.toggleSpeaker() },
            modifier = Modifier.minimumInteractiveComponentSize().testTag("call_speaker_btn"),
          ) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Toggle speaker")
          }

          if (state.callType == CallType.VIDEO) {
            FilledIconToggleButton(
              checked = !state.isCameraOn,
              onCheckedChange = { viewModel.toggleCamera() },
              modifier = Modifier.minimumInteractiveComponentSize().testTag("call_camera_btn"),
            ) {
              Icon(
                if (state.isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                contentDescription = "Camera On/Off",
              )
            }
            IconButton(
              onClick = viewModel::switchCamera,
              modifier =
                Modifier.clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.16f))
                  .minimumInteractiveComponentSize()
                  .testTag("call_switch_camera_btn"),
            ) {
              Icon(
                Icons.Default.Cameraswitch,
                contentDescription = "Switch camera",
                tint = Color.White,
              )
            }
          }

          IconButton(
            onClick = viewModel::hangUp,
            modifier =
              Modifier.size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
                .minimumInteractiveComponentSize()
                .testTag("call_end_btn"),
          ) {
            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
          }
        }
      }
    }
  }
}

@Composable
fun IncomingCallDialog(
  call: CallSession,
  callerName: String,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = {},
    title = {
      Text(
        text =
          if (call.callType == CallType.VIDEO.name) "Incoming Video Call"
          else "Incoming Audio Call"
      )
    },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        UserAvatar(name = callerName, photoUrl = null, size = 64.dp)
        Text(callerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
          "End-to-End Encrypted WebRTC Call",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onAccept,
        colors = ButtonDefaults.buttonColors(containerColor = OnlineGreen),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("accept_incoming_call_btn"),
      ) {
        Icon(Icons.Default.Call, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Accept")
      }
    },
    dismissButton = {
      Button(
        onClick = onDecline,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.minimumInteractiveComponentSize().testTag("decline_incoming_call_btn"),
      ) {
        Icon(Icons.Default.CallEnd, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Decline")
      }
    },
  )
}

private fun formatCallDuration(seconds: Long): String {
  val m = seconds / 60L
  val s = seconds % 60L
  return "%02d:%02d".format(m, s)
}
