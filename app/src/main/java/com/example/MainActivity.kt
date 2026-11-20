package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.datastore.ThemeMode
import com.example.ui.navigation.FriendsMeetApp
import com.example.ui.theme.FriendsMeetTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as FriendsMeetApplication).appContainer
    setContent {
      val themeMode by
        appContainer.userPreferencesRepository.themeMode.collectAsStateWithLifecycle(
          initialValue = ThemeMode.SYSTEM
        )
      val scope = rememberCoroutineScope()
      val rememberedContainer = remember { appContainer }

      FriendsMeetTheme(themeMode = themeMode) {
        FriendsMeetApp(
          appContainer = rememberedContainer,
          themeMode = themeMode,
          onSelectThemeMode = { mode ->
            scope.launch { rememberedContainer.userPreferencesRepository.setThemeMode(mode) }
          },
        )
      }
    }
  }
}
