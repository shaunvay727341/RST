package com.example.core.di

import android.content.Context
import androidx.credentials.CredentialManager
import com.example.R
import com.example.core.datastore.UserPreferencesRepository
import com.example.core.firebase.StorageRepository
import com.example.core.local.OfflineQueueDatabase
import com.example.core.notifications.OneSignalManager
import com.example.core.security.E2eeCryptoManager
import com.example.feature.calls.CallRepository
import com.example.feature.chat.ChatRepository
import com.example.feature.devices.LinkedDevicesRepository
import com.example.feature.friends.FriendsRepository
import com.example.feature.letterbox.LetterboxRepository
import com.example.feature.registration.RegistrationRepository
import com.example.feature.settings.SettingsRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage

/**
 * Constructor-based Dependency Injection container anchored at the Application boundary.
 * Always initializes Cloud Firestore using the provisioned named database ID (`R.string.firestore_database_id`).
 */
class AppContainer(context: Context) {
  val appContext: Context = context.applicationContext

  val firestoreDatabaseId: String by lazy {
    appContext.getString(R.string.firestore_database_id)
  }

  val firestore: FirebaseFirestore by lazy {
    FirebaseFirestore.getInstance(firestoreDatabaseId)
  }

  val auth: FirebaseAuth by lazy { Firebase.auth }

  val storage: FirebaseStorage by lazy { Firebase.storage }

  val functions: FirebaseFunctions by lazy { Firebase.functions }

  val credentialManager: CredentialManager by lazy { CredentialManager.create(appContext) }

  val userPreferencesRepository: UserPreferencesRepository by lazy {
    UserPreferencesRepository(appContext)
  }

  val oneSignalManager: OneSignalManager by lazy { OneSignalManager() }

  val e2eeCryptoManager: E2eeCryptoManager by lazy { E2eeCryptoManager(appContext) }

  val storageRepository: StorageRepository by lazy { StorageRepository(appContext, storage) }

  val offlineQueueDatabase: OfflineQueueDatabase by lazy { OfflineQueueDatabase.build(appContext) }

  val registrationRepository: RegistrationRepository by lazy {
    RegistrationRepository(firestore, functions, storageRepository, e2eeCryptoManager)
  }

  val friendsRepository: FriendsRepository by lazy { FriendsRepository(firestore) }

  val chatRepository: ChatRepository by lazy {
    ChatRepository(appContext, firestore, storageRepository, e2eeCryptoManager, offlineQueueDatabase)
  }

  val callRepository: CallRepository by lazy { CallRepository(firestore) }

  val letterboxRepository: LetterboxRepository by lazy { LetterboxRepository(firestore) }

  val linkedDevicesRepository: LinkedDevicesRepository by lazy {
    LinkedDevicesRepository(firestore, functions, e2eeCryptoManager)
  }

  val settingsRepository: SettingsRepository by lazy {
    SettingsRepository(firestore, storageRepository, e2eeCryptoManager)
  }

  val groupRepository: com.example.feature.group.GroupRepository by lazy {
    com.example.feature.group.GroupRepository(firestore)
  }
}
