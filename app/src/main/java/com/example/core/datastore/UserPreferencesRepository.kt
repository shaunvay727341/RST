package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.friendsMeetDataStore: DataStore<Preferences> by
  preferencesDataStore(name = "friends_meet_prefs")

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK;

  companion object {
    fun fromRaw(raw: String?): ThemeMode {
      return entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
  }
}

class UserPreferencesRepository(private val context: Context) {
  private val themeModeKey = stringPreferencesKey("theme_mode")

  val themeMode: Flow<ThemeMode> =
    context.friendsMeetDataStore.data.map { prefs -> ThemeMode.fromRaw(prefs[themeModeKey]) }

  suspend fun setThemeMode(mode: ThemeMode) {
    context.friendsMeetDataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
  }
}
