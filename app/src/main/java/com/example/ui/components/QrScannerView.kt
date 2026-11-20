package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * CameraX + ML Kit QR Code Scanner with Compose runtime CAMERA permission handling.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerView(
  onQrDecoded: (String) -> Unit,
  modifier: Modifier = Modifier,
  hintText: String = "Point camera at a Friends Meet QR code",
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
    )
  }
  val launcher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      hasPermission = granted
    }

  if (!hasPermission) {
    Column(
      modifier = modifier.fillMaxSize().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Icon(
        imageVector = Icons.Default.CameraAlt,
        contentDescription = "Camera permission",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(48.dp),
      )
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Camera access is needed to scan QR codes.",
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
      )
      Spacer(modifier = Modifier.height(16.dp))
      Button(
        onClick = { launcher.launch(Manifest.permission.CAMERA) },
        modifier =
          Modifier.minimumInteractiveComponentSize().testTag("grant_camera_permission_button"),
      ) {
        Text("Enable Camera")
      }
    }
    return
  }

  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  val scanner = remember { BarcodeScanning.getClient() }
  var delivered by remember { mutableStateOf(false) }

  DisposableEffect(Unit) {
    onDispose {
      scanner.close()
      cameraExecutor.shutdown()
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).apply {
          layoutParams =
            ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT,
            )
          val providerFuture = ProcessCameraProvider.getInstance(ctx)
          providerFuture.addListener(
            {
              val cameraProvider = providerFuture.get()
              val preview =
                Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
              val analysis =
                ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()
              analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage != null && !delivered) {
                  val input =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                  scanner
                    .process(input)
                    .addOnSuccessListener { barcodes ->
                      val value =
                        barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                      if (!value.isNullOrBlank() && !delivered) {
                        delivered = true
                        onQrDecoded(value)
                      }
                    }
                    .addOnCompleteListener { imageProxy.close() }
                } else {
                  imageProxy.close()
                }
              }
              try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                  lifecycleOwner,
                  CameraSelector.DEFAULT_BACK_CAMERA,
                  preview,
                  analysis,
                )
              } catch (_: Exception) {
                // Camera unavailable on headless test devices
              }
            },
            ContextCompat.getMainExecutor(ctx),
          )
        }
      },
      modifier = Modifier.fillMaxSize(),
    )

    Box(
      modifier =
        Modifier.align(Alignment.Center)
          .size(220.dp)
          .border(2.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
    )

    Surface(
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Icon(
          imageVector = Icons.Default.QrCodeScanner,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = hintText, style = MaterialTheme.typography.bodyMedium)
      }
    }
  }
}
