package com.example.feature.devices

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.qr.QrCodeGenerator
import com.example.core.qr.QrPayload
import com.example.ui.components.QrScannerView
import com.example.ui.theme.OnlineGreen
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedDevicesScreen(
  repository: LinkedDevicesRepository,
  myUid: String,
  onBack: () -> Unit,
) {
  val devices by repository.observeLinkedDevices(myUid).collectAsStateWithLifecycle(emptyList())
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  var activeLinkQr by remember { mutableStateOf<GeneratedQrLink?>(null) }
  var showScanner by remember { mutableStateOf(false) }

  activeLinkQr?.let { link ->
    val payload = QrPayload.encodeDeviceLinkPayload(link.tokenId, link.ownerUid)
    val bitmap: Bitmap = remember(payload) { QrCodeGenerator.generateBitmap(payload, 420) }
    AlertDialog(
      onDismissRequest = { activeLinkQr = null },
      title = { Text("Single-Use Link QR") },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Single-use linked device pairing QR",
            modifier = Modifier.size(200.dp).clip(RoundedCornerShape(14.dp)),
          )
          Text(
            text = "Expires in 5 minutes · Valid for 1 device only. No account secrets embedded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      },
      confirmButton = { TextButton(onClick = { activeLinkQr = null }) { Text("Done") } },
    )
  }

  if (showScanner) {
    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text("Scan Primary Device QR") },
          navigationIcon = {
            IconButton(onClick = { showScanner = false }) {
              Icon(Icons.Default.Close, contentDescription = "Close scanner")
            }
          },
        )
      }
    ) { pad ->
      Box(modifier = Modifier.fillMaxSize().padding(pad)) {
        QrScannerView(
          hintText = "Scan single-use pairing QR from primary device",
          onQrDecoded = { raw ->
            showScanner = false
            val parsed = QrPayload.parse(raw)
            if (parsed is QrPayload.DeviceLinkToken) {
              scope.launch {
                try {
                  repository.redeemQrLinkToken(parsed.tokenId, parsed.ownerUid, myUid)
                  snackbarHostState.showSnackbar("Device linked & real-time sync active")
                } catch (e: Exception) {
                  snackbarHostState.showSnackbar(e.localizedMessage ?: "Unable to link device")
                }
              }
            } else {
              scope.launch { snackbarHostState.showSnackbar("Invalid device pairing QR code") }
            }
          },
        )
      }
    }
    return
  }

  Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(
            onClick = onBack,
            modifier = Modifier.minimumInteractiveComponentSize().testTag("devices_back_btn"),
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        title = { Text("Linked Devices (${devices.size}/2)") },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Card(
        colors =
          CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Devices, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              "Link Up to 2 Additional Devices",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.SemiBold,
            )
          }
          Text(
            "Real-time message & call sync across authorized devices. Credentials stay sealed in Android Keystore.",
            style = MaterialTheme.typography.bodySmall,
          )
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
              onClick = {
                scope.launch {
                  try {
                    activeLinkQr = repository.createShortLivedLinkToken(myUid)
                  } catch (e: Exception) {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Limit reached")
                  }
                }
              },
              enabled = devices.size < 2,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("generate_device_qr_btn"),
            ) {
              Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Show Link QR")
            }
            OutlinedButton(
              onClick = { showScanner = true },
              enabled = devices.size < 2,
              modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("scan_device_qr_btn"),
            ) {
              Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Scan QR")
            }
          }
        }
      }

      LazyColumn(
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
      ) {
        items(devices, key = { it.id }) { dev ->
          Card(modifier = Modifier.fillMaxWidth()) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(Icons.Default.Sync, contentDescription = "Synced", tint = OnlineGreen)
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(dev.deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                  text =
                    dev.devicePlatform +
                      (dev.lastSyncedAt?.toDate()?.let {
                        " · Synced ${SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(it)}"
                      } ?: ""),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              IconButton(
                onClick = { scope.launch { repository.remoteLogoutDevice(dev.id) } },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("logout_device_${dev.id}"),
              ) {
                Icon(
                  Icons.AutoMirrored.Filled.Logout,
                  contentDescription = "Remote logout",
                  tint = MaterialTheme.colorScheme.error,
                )
              }
            }
          }
        }
      }
    }
  }
}
