package com.example.core.translation

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

enum class TranslationDirection {
  BENGALI_TO_ENGLISH,
  ENGLISH_TO_BENGALI,
}

data class TranslationResult(
  val translatedText: String,
  val direction: TranslationDirection,
)

/**
 * On-device ML Kit translator supporting instant bidirectional translation between Bengali and English.
 */
object TranslatorHelper {
  fun detectDirection(text: String): TranslationDirection {
    val hasBengaliScript = text.any { ch -> ch in '\u0980'..'\u09FF' }
    return if (hasBengaliScript) {
      TranslationDirection.BENGALI_TO_ENGLISH
    } else {
      TranslationDirection.ENGLISH_TO_BENGALI
    }
  }

  suspend fun translateBengaliEnglish(
    text: String,
    direction: TranslationDirection = detectDirection(text),
  ): TranslationResult {
    val (sourceLang, targetLang) =
      when (direction) {
        TranslationDirection.BENGALI_TO_ENGLISH ->
          TranslateLanguage.BENGALI to TranslateLanguage.ENGLISH
        TranslationDirection.ENGLISH_TO_BENGALI ->
          TranslateLanguage.ENGLISH to TranslateLanguage.BENGALI
      }
    val options =
      TranslatorOptions.Builder()
        .setSourceLanguage(sourceLang)
        .setTargetLanguage(targetLang)
        .build()
    val client = Translation.getClient(options)
    return try {
      val conditions = DownloadConditions.Builder().build()
      client.downloadModelIfNeeded(conditions).await()
      val output = client.translate(text).await()
      TranslationResult(translatedText = output, direction = direction)
    } finally {
      client.close()
    }
  }
}
