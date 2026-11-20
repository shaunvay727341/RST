package com.example.core.notifications

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener
import com.onesignal.notifications.INotificationLifecycleListener
import com.onesignal.notifications.INotificationWillDisplayEvent
import com.onesignal.notifications.IPermissionObserver
import com.onesignal.user.subscriptions.IPushSubscriptionObserver
import com.onesignal.user.subscriptions.PushSubscriptionChangedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class OneSignalStatus(
  val initialized: Boolean = false,
  val permissionGranted: Boolean = false,
  val optedIn: Boolean = false,
  val subscriptionId: String? = null,
  val externalUserId: String? = null,
  val lastNotificationTitle: String? = null,
  val lastError: String? = null,
)

/** Abstraction around OneSignal SDK v5 calls to keep initialization and state logic unit-testable. */
interface OneSignalGateway {
  fun init(context: Context, appId: String, debugLogging: Boolean)

  fun login(externalId: String)

  fun logout()

  fun addTag(key: String, value: String)

  fun removeTag(key: String)

  suspend fun requestPermission(fallbackToSettings: Boolean): Boolean

  fun currentPermissionGranted(): Boolean

  fun currentOptedIn(): Boolean

  fun currentSubscriptionId(): String?

  fun registerObservers(
    onPermissionChanged: (Boolean) -> Unit,
    onSubscriptionChanged: (subscriptionId: String?, optedIn: Boolean) -> Unit,
    onNotificationDisplayed: (title: String?) -> Unit,
    onNotificationClicked: (title: String?, additionalDataJson: String?) -> Unit,
  )
}

class AndroidOneSignalGateway : OneSignalGateway {
  override fun init(context: Context, appId: String, debugLogging: Boolean) {
    OneSignal.Debug.logLevel = if (debugLogging) LogLevel.FATAL else LogLevel.NONE
    OneSignal.initWithContext(context, appId)
  }

  override fun login(externalId: String) {
    OneSignal.login(externalId)
  }

  override fun logout() {
    OneSignal.logout()
  }

  override fun addTag(key: String, value: String) {
    OneSignal.User.addTag(key, value)
  }

  override fun removeTag(key: String) {
    OneSignal.User.removeTag(key)
  }

  override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean =
    OneSignal.Notifications.requestPermission(fallbackToSettings)

  override fun currentPermissionGranted(): Boolean = OneSignal.Notifications.permission

  override fun currentOptedIn(): Boolean = OneSignal.User.pushSubscription.optedIn

  override fun currentSubscriptionId(): String? =
    OneSignal.User.pushSubscription.id.takeIf { it.isNotBlank() }

  override fun registerObservers(
    onPermissionChanged: (Boolean) -> Unit,
    onSubscriptionChanged: (subscriptionId: String?, optedIn: Boolean) -> Unit,
    onNotificationDisplayed: (title: String?) -> Unit,
    onNotificationClicked: (title: String?, additionalDataJson: String?) -> Unit,
  ) {
    OneSignal.Notifications.addPermissionObserver(
      object : IPermissionObserver {
        override fun onNotificationPermissionChange(permission: Boolean) {
          onPermissionChanged(permission)
        }
      }
    )

    OneSignal.User.pushSubscription.addObserver(
      object : IPushSubscriptionObserver {
        override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
          val id = state.current.id.takeIf { it.isNotBlank() }
          onSubscriptionChanged(id, state.current.optedIn)
        }
      }
    )

    OneSignal.Notifications.addForegroundLifecycleListener(
      object : INotificationLifecycleListener {
        override fun onWillDisplay(event: INotificationWillDisplayEvent) {
          onNotificationDisplayed(event.notification.title)
        }
      }
    )

    OneSignal.Notifications.addClickListener(
      object : INotificationClickListener {
        override fun onClick(event: INotificationClickEvent) {
          onNotificationClicked(
            event.notification.title,
            event.notification.additionalData?.toString(),
          )
        }
      }
    )
  }
}

/**
 * Centralized OneSignal manager following Friends Meet's MVVM/DI architecture.
 * Handles SDK initialization, Auth UID binding (`login`/`logout`), permission prompts,
 * and subscription verification state.
 */
class OneSignalManager(private val gateway: OneSignalGateway = AndroidOneSignalGateway()) {
  private val _status = MutableStateFlow(OneSignalStatus())
  val status: StateFlow<OneSignalStatus> = _status.asStateFlow()

  private var initialized = false

  @Synchronized
  fun initialize(context: Context, appId: String) {
    if (initialized || appId.isBlank()) return
    try {
      gateway.init(context.applicationContext, appId, BuildConfig.DEBUG)
      gateway.registerObservers(
        onPermissionChanged = { granted ->
          _status.update { it.copy(permissionGranted = granted, lastError = null) }
        },
        onSubscriptionChanged = { subId, optedIn ->
          _status.update {
            it.copy(subscriptionId = subId, optedIn = optedIn, lastError = null)
          }
        },
        onNotificationDisplayed = { title ->
          _status.update { it.copy(lastNotificationTitle = title) }
        },
        onNotificationClicked = { title, dataJson ->
          Log.d(TAG, "Notification clicked: title=$title data=$dataJson")
        },
      )
      initialized = true
      _status.update {
        it.copy(
          initialized = true,
          permissionGranted = gateway.currentPermissionGranted(),
          optedIn = gateway.currentOptedIn(),
          subscriptionId = gateway.currentSubscriptionId(),
          lastError = null,
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize OneSignal SDK", e)
      _status.update { it.copy(lastError = e.localizedMessage ?: "OneSignal init failed") }
    }
  }

  fun bindUser(uid: String) {
    if (!initialized || uid.isBlank() || _status.value.externalUserId == uid) return
    try {
      gateway.login(uid)
      _status.update {
        it.copy(
          externalUserId = uid,
          subscriptionId = gateway.currentSubscriptionId(),
          optedIn = gateway.currentOptedIn(),
          lastError = null,
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to bind user to OneSignal", e)
      _status.update { it.copy(lastError = e.localizedMessage ?: "OneSignal login failed") }
    }
  }

  fun unbindUser() {
    if (!initialized || _status.value.externalUserId == null) return
    try {
      gateway.logout()
      _status.update { it.copy(externalUserId = null, lastError = null) }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to unbind OneSignal user: ${e.message}")
    }
  }

  suspend fun requestPermission(fallbackToSettings: Boolean = true): Boolean {
    if (!initialized) return false
    return try {
      val granted = withContext(Dispatchers.IO) { gateway.requestPermission(fallbackToSettings) }
      _status.update {
        it.copy(
          permissionGranted = granted,
          optedIn = gateway.currentOptedIn(),
          subscriptionId = gateway.currentSubscriptionId(),
          lastError = null,
        )
      }
      granted
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Error requesting notification permission", e)
      _status.update { it.copy(lastError = e.localizedMessage ?: "Permission request failed") }
      false
    }
  }

  fun addTag(key: String, value: String) {
    if (!initialized || key.isBlank()) return
    gateway.addTag(key, value)
  }

  fun removeTag(key: String) {
    if (!initialized || key.isBlank()) return
    gateway.removeTag(key)
  }

  companion object {
    private const val TAG = "OneSignalManager"

    fun isAndroidEmulator(): Boolean {
      val fingerprint = Build.FINGERPRINT ?: ""
      val hardware = Build.HARDWARE ?: ""
      val model = Build.MODEL ?: ""
      val manufacturer = Build.MANUFACTURER ?: ""
      val product = Build.PRODUCT ?: ""
      val brand = Build.BRAND ?: ""
      val device = Build.DEVICE ?: ""
      return (brand.startsWith("generic") && device.startsWith("generic")) ||
        fingerprint.startsWith("generic") ||
        fingerprint.startsWith("unknown") ||
        hardware.contains("goldfish") ||
        hardware.contains("ranchu") ||
        model.contains("google_sdk") ||
        model.contains("Emulator") ||
        model.contains("Android SDK built for") ||
        manufacturer.contains("Genymotion") ||
        product.contains("sdk_gphone") ||
        product.contains("google_sdk") ||
        product.contains("sdk") ||
        product.contains("emulator") ||
        product.contains("simulator")
    }
  }
}
