package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read app name and onesignal app id from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals("Friends Meet", context.getString(R.string.app_name))
    assertEquals(
      "a9f87693-c95c-425a-8c23-04025f72d613",
      context.getString(R.string.onesignal_app_id),
    )
  }
}
