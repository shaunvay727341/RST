package com.example.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

sealed interface QrPayload {
  data class FriendProfile(val friendsMeetUid: String) : QrPayload

  data class DeviceLinkToken(val tokenId: String, val ownerUid: String) : QrPayload

  companion object {
    private val eightDigitRegex = Regex("^[1-9][0-9]{7}$")

    fun encodeFriendPayload(friendsMeetUid: String): String = "fm-friend://$friendsMeetUid"

    fun encodeDeviceLinkPayload(tokenId: String, ownerUid: String): String =
      "fm-link://$tokenId/$ownerUid"

    fun parse(raw: String): QrPayload? {
      val trimmed = raw.trim()
      if (trimmed.startsWith("fm-friend://")) {
        val fuid = trimmed.removePrefix("fm-friend://").trim()
        return if (eightDigitRegex.matches(fuid)) FriendProfile(fuid) else null
      }
      if (trimmed.startsWith("fm-link://")) {
        val parts = trimmed.removePrefix("fm-link://").split("/")
        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
          return DeviceLinkToken(tokenId = parts[0], ownerUid = parts[1])
        }
        return null
      }
      if (eightDigitRegex.matches(trimmed)) {
        return FriendProfile(trimmed)
      }
      return null
    }
  }
}

object QrCodeGenerator {
  fun generateBitmap(content: String, sizePx: Int = 512): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
      for (y in 0 until sizePx) {
        bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
      }
    }
    return bitmap
  }
}
