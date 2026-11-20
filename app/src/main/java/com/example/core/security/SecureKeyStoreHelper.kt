package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed Android Keystore AES-256-GCM cipher helper for sensitive local device data
 * (linked device session tokens, backup key material, and identity key wrappers).
 */
object SecureKeyStoreHelper {
  private const val ANDROID_KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "friends_meet_device_master_key"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"
  private const val GCM_TAG_LENGTH_BITS = 128

  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    if (existing != null) return existing

    val keyGenerator =
      KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec =
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  fun encrypt(plainText: String): Pair<String, String> {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
    val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipherBytes, Base64.NO_WRAP) to
      Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
  }

  fun decrypt(cipherTextBase64: String, ivBase64: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
    val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
    cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)
    val plainBytes = cipher.doFinal(Base64.decode(cipherTextBase64, Base64.NO_WRAP))
    return String(plainBytes, Charsets.UTF_8)
  }
}
