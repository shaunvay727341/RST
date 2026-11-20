package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.notifications.OneSignalGateway
import com.example.core.notifications.OneSignalManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeOneSignalGateway : OneSignalGateway {
  var initializedAppId: String? = null
  var loggedInUid: String? = null
  var loggedOutCount: Int = 0
  var permissionGranted: Boolean = false
  var optedIn: Boolean = false
  var subscriptionId: String? = null
  val tags = mutableMapOf<String, String>()

  private var onPermissionChanged: ((Boolean) -> Unit)? = null
  private var onSubscriptionChanged: ((String?, Boolean) -> Unit)? = null

  override fun init(context: Context, appId: String, debugLogging: Boolean) {
    initializedAppId = appId
  }

  override fun login(externalId: String) {
    loggedInUid = externalId
  }

  override fun logout() {
    loggedInUid = null
    loggedOutCount++
  }

  override fun addTag(key: String, value: String) {
    tags[key] = value
  }

  override fun removeTag(key: String) {
    tags.remove(key)
  }

  override suspend fun requestPermission(fallbackToSettings: Boolean): Boolean {
    permissionGranted = true
    optedIn = true
    subscriptionId = "sub_test_999"
    onPermissionChanged?.invoke(true)
    onSubscriptionChanged?.invoke(subscriptionId, true)
    return true
  }

  override fun currentPermissionGranted(): Boolean = permissionGranted

  override fun currentOptedIn(): Boolean = optedIn

  override fun currentSubscriptionId(): String? = subscriptionId

  override fun registerObservers(
    onPermissionChanged: (Boolean) -> Unit,
    onSubscriptionChanged: (subscriptionId: String?, optedIn: Boolean) -> Unit,
    onNotificationDisplayed: (title: String?) -> Unit,
    onNotificationClicked: (title: String?, additionalDataJson: String?) -> Unit,
  ) {
    this.onPermissionChanged = onPermissionChanged
    this.onSubscriptionChanged = onSubscriptionChanged
  }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OneSignalIntegrationTest {

  @Test
  fun `initialize binds app id and registers subscription state`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gateway = FakeOneSignalGateway()
    val manager = OneSignalManager(gateway)

    manager.initialize(context, "a9f87693-c95c-425a-8c23-04025f72d613")

    assertEquals("a9f87693-c95c-425a-8c23-04025f72d613", gateway.initializedAppId)
    assertTrue(manager.status.value.initialized)
    assertFalse(manager.status.value.permissionGranted)
  }

  @Test
  fun `bindUser and unbindUser sync external id and permission updates subscription`() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val gateway = FakeOneSignalGateway()
    val manager = OneSignalManager(gateway)

    manager.initialize(context, "a9f87693-c95c-425a-8c23-04025f72d613")
    manager.bindUser("firebase_uid_123")
    assertEquals("firebase_uid_123", gateway.loggedInUid)
    assertEquals("firebase_uid_123", manager.status.value.externalUserId)

    val granted = manager.requestPermission(fallbackToSettings = true)
    assertTrue(granted)
    assertTrue(manager.status.value.permissionGranted)
    assertTrue(manager.status.value.optedIn)
    assertEquals("sub_test_999", manager.status.value.subscriptionId)

    manager.addTag("account_status", "active")
    assertEquals("active", gateway.tags["account_status"])

    manager.unbindUser()
    assertNull(manager.status.value.externalUserId)
    assertEquals(1, gateway.loggedOutCount)
  }
}
