package com.example.core.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
  @PrimaryKey val localId: String,
  val chatId: String,
  val senderUid: String,
  val recipientUid: String,
  val peerPublicKeyBase64: String?,
  val messageType: String,
  val plainText: String,
  val replyToMessageId: String? = null,
  val mediaLocalPath: String? = null,
  val mediaDurationMs: Long? = null,
  val disappearingDurationSeconds: Long = 0L,
  val createdAtMillis: Long = System.currentTimeMillis(),
  val retryCount: Int = 0,
  val lastError: String? = null,
)

@Dao
interface PendingMessageDao {
  @Query("SELECT * FROM pending_messages WHERE chatId = :chatId ORDER BY createdAtMillis ASC")
  fun observeForChat(chatId: String): Flow<List<PendingMessageEntity>>

  @Query("SELECT * FROM pending_messages ORDER BY createdAtMillis ASC")
  suspend fun getAll(): List<PendingMessageEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: PendingMessageEntity)

  @Query("DELETE FROM pending_messages WHERE localId = :localId")
  suspend fun deleteById(localId: String)

  @Query(
    "UPDATE pending_messages SET retryCount = retryCount + 1, lastError = :error WHERE localId = :localId"
  )
  suspend fun markRetryFailed(localId: String, error: String)
}

@Database(entities = [PendingMessageEntity::class], version = 1, exportSchema = false)
abstract class OfflineQueueDatabase : RoomDatabase() {
  abstract fun pendingMessageDao(): PendingMessageDao

  companion object {
    fun build(context: Context): OfflineQueueDatabase {
      return Room.databaseBuilder(
          context.applicationContext,
          OfflineQueueDatabase::class.java,
          "friends_meet_offline_queue.db",
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    }
  }
}

/**
 * Monitors network availability via ConnectivityManager and automatically flushes
 * queued offline messages once internet connectivity is restored.
 */
class OfflineQueueMonitor(
  context: Context,
  private val onNetworkRestored: suspend () -> Unit,
) {
  private val cm =
    context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var registered = false

  private val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        scope.launch { onNetworkRestored() }
      }
    }

  fun start() {
    if (registered) return
    try {
      val request =
        NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
      cm.registerNetworkCallback(request, callback)
      registered = true
    } catch (_: Exception) {
      // Safe fallback in unit test environments
    }
  }

  fun isOnlineNow(): Boolean {
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}
