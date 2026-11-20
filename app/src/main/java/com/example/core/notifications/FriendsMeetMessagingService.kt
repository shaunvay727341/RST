package com.example.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.FriendsMeetApplication
import com.example.MainActivity
import com.example.R
import com.example.core.firebase.FirestorePaths
import com.google.firebase.firestore.FieldValue
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Handles Firebase Cloud Messaging token refreshes and incoming push data messages
 * (messages, reactions, incoming calls, and Letterbox alerts) alongside OneSignal.
 */
class FriendsMeetMessagingService : FirebaseMessagingService() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    val container = (application as? FriendsMeetApplication)?.appContainer ?: return
    val uid = container.auth.currentUser?.uid ?: return
    serviceScope.launch {
      try {
        container.firestore
          .collection(FirestorePaths.USERS)
          .document(uid)
          .update(mapOf("fcmToken" to token, "updatedAt" to FieldValue.serverTimestamp()))
          .await()
      } catch (e: Exception) {
        Log.w(TAG, "Unable to persist refreshed FCM token: ${e.message}")
      }
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
    val body = message.notification?.body ?: message.data["body"] ?: return
    showLocalNotification(this, title, body)
  }

  companion object {
    private const val TAG = "FriendsMeetFCM"
    private const val CHANNEL_ID = "friends_meet_alerts"

    fun showLocalNotification(context: Context, title: String, body: String) {
      if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
          ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
      ) {
        return
      }
      val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel =
          NotificationChannel(
            CHANNEL_ID,
            "Friends Meet Alerts",
            NotificationManager.IMPORTANCE_HIGH,
          )
        mgr.createNotificationChannel(channel)
      }
      val intent =
        Intent(context, MainActivity::class.java).apply {
          flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
      val pending =
        PendingIntent.getActivity(
          context,
          0,
          intent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      val notif =
        NotificationCompat.Builder(context, CHANNEL_ID)
          .setSmallIcon(R.mipmap.ic_launcher)
          .setContentTitle(title)
          .setContentText(body)
          .setAutoCancel(true)
          .setContentIntent(pending)
          .setPriority(NotificationCompat.PRIORITY_HIGH)
          .build()
      NotificationManagerCompat.from(context)
        .notify((System.currentTimeMillis() and 0xFFFF).toInt(), notif)
    }
  }
}
