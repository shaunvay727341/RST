package com.example

import android.app.Application
import com.example.core.di.AppContainer
import com.example.core.notifications.OneSignalManager

class FriendsMeetApplication : Application() {
  lateinit var appContainer: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    appContainer = AppContainer(this)
    if (!OneSignalManager.isAndroidEmulator()) {
      appContainer.oneSignalManager.initialize(
        context = this,
        appId = getString(R.string.onesignal_app_id),
      )
    }
  }
}
