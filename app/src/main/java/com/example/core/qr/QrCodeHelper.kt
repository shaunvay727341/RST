package com.example.core.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

sealed interface QrPayload {
  data class FriendProfile(val friendsMeetUid: String) : QrPayload

  data class DeviceLinkToken(val tokenId: String, val ownerUid: String) : QrPayload

  data class GroupInvite(val groupId: String, val inviteCode: String? = null) : QrPayload

  companion object {
    private val eightDigitRegex = Regex("^[1-9][0-9]{7}$")
    private val groupIdRegex = Regex("^[a-zA-Z0-9_\\-]{4,128}$")

    fun encodeFriendPayload(friendsMeetUid: String): String = "fm-friend://$friendsMeetUid"

    fun encodeDeviceLinkPayload(tokenId: String, ownerUid: String): String =
      "fm-link://$tokenId/$ownerUid"

    fun encodeGroupPayload(groupId: String, inviteCode: String? = null): String =
      if (!inviteCode.isNullOrBlank()) "fm-group://$groupId?code=${inviteCode.trim()}"
      else "fm-group://$groupId"

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
      if (trimmed.startsWith("fm-group://")) {
        val body = trimmed.removePrefix("fm-group://").trim()
        val parts = body.split("?", limit = 2)
        val gId = parts[0].trim().trimEnd('/')
        if (!groupIdRegex.matches(gId)) return null
        val code =
          parts
            .getOrNull(1)
            ?.split("&")
            ?.firstOrNull { it.startsWith("code=") }
            ?.removePrefix("code=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return GroupInvite(groupId = gId, inviteCode = code)
      }
      if (trimmed.startsWith("grp_") && groupIdRegex.matches(trimmed)) {
        return GroupInvite(groupId = trimmed)
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
    val pixels = IntArray(sizePx * sizePx)
    for (y in 0 until sizePx) {
      val rowOffset = y * sizePx
      for (x in 0 until sizePx) {
        pixels[rowOffset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
      }
    }
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
    return bitmap
  }
}
