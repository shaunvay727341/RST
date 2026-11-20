package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.core.datastore.ThemeMode
import com.example.core.di.AppContainer
import com.example.core.firebase.FirestorePaths
import com.example.core.model.AddedVia
import com.example.core.model.CallStatus
import com.example.core.model.CallType
import com.example.core.model.PrivacySettings
import com.example.core.model.UserIdDirectoryEntry
import com.example.core.model.UserProfile
import com.example.feature.auth.AuthScreen
import com.example.feature.auth.authStateFlow
import com.example.feature.auth.signOut
import com.example.feature.calls.CallScreen
import com.example.feature.calls.CallViewModel
import com.example.feature.calls.IncomingCallDialog
import com.example.feature.chat.ChatScreen
import com.example.feature.chat.ChatViewModel
import com.example.feature.chat.ChatsListScreen
import com.example.feature.devices.LinkedDevicesScreen
import com.example.feature.friends.FriendsScreen
import com.example.feature.friends.FriendsViewModel
import com.example.feature.group.CreateGroupScreen
import com.example.feature.group.GroupChatScreen
import com.example.feature.group.GroupListScreen
import com.example.feature.group.GroupViewModel
import com.example.feature.letterbox.LetterboxScreen
import com.example.feature.oldchat.OldChatDirectoryScreen
import com.example.feature.oldchat.OldChatRepository
import com.example.feature.registration.GuardianVerificationScreen
import com.example.feature.registration.RegistrationRepository
import com.example.feature.registration.RegistrationScreen
import com.example.feature.registration.RegistrationViewModel
import com.example.feature.settings.BackupRestoreScreen
import com.example.feature.settings.PrivacySettingsScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.ViewFriendProfileScreen
import com.example.ui.components.FriendsMeetBackdrop
import com.example.ui.components.LiquidGlassNavigationBar
import com.example.ui.components.LiquidNavItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ProfileGateState {
  data object Loading : ProfileGateState

  data object NeedsRegistration : ProfileGateState

  data class NeedsGuardian(val profile: UserProfile) : ProfileGateState

  data class Ready(val profile: UserProfile) : ProfileGateState
}

@Composable
fun FriendsMeetApp(
  appContainer: AppContainer,
  themeMode: ThemeMode,
  onSelectThemeMode: (ThemeMode) -> Unit,
) {
  val currentUser by
    appContainer.auth
      .authStateFlow()
      .collectAsStateWithLifecycle(initialValue = appContainer.auth.currentUser)

  val user = currentUser
  if (user == null) {
    LaunchedEffect(Unit) { appContainer.oneSignalManager.unbindUser() }
    AuthScreen(
      auth = appContainer.auth,
      credentialManager = appContainer.credentialManager,
      themeMode = themeMode,
      onCycleThemeMode = {
        val next =
          when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
          }
        onSelectThemeMode(next)
      },
      onAuthSuccess = {},
    )
    return
  }

  key(user.uid) {
    val scope = rememberCoroutineScope()
    val profileFlow by
      appContainer.registrationRepository
        .observeUserProfile(user.uid)
        .collectAsStateWithLifecycle(initialValue = null)
    var initialResolved by remember { mutableStateOf(false) }

    LaunchedEffect(profileFlow) {
      if (profileFlow != null) {
        initialResolved = true
      } else {
        delay(1200)
        initialResolved = true
      }
    }

    LaunchedEffect(user.uid) { appContainer.oneSignalManager.bindUser(user.uid) }

    val handleSignOut: () -> Unit = {
      appContainer.oneSignalManager.unbindUser()
      signOut(appContainer.credentialManager, appContainer.auth, scope)
    }

    val gateState =
      when {
        !initialResolved && profileFlow == null -> ProfileGateState.Loading
        profileFlow == null -> ProfileGateState.NeedsRegistration
        profileFlow!!.isUnder13 && !profileFlow!!.guardianVerified ->
          ProfileGateState.NeedsGuardian(profileFlow!!)
        else -> ProfileGateState.Ready(profileFlow!!)
      }

    when (gateState) {
      ProfileGateState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      ProfileGateState.NeedsRegistration -> {
        val regVm =
          viewModel<RegistrationViewModel>(key = "reg_${user.uid}") {
            RegistrationViewModel(
              repository = appContainer.registrationRepository,
              uid = user.uid,
              email = user.email.orEmpty(),
              initialName = user.displayName.orEmpty(),
              googlePhotoUrl = user.photoUrl?.toString(),
            )
          }
        RegistrationScreen(
          viewModel = regVm,
          email = user.email.orEmpty(),
          googlePhotoUrl = user.photoUrl?.toString(),
          onSignOut = handleSignOut,
        )
      }
      is ProfileGateState.NeedsGuardian -> {
        var viewingLetterbox by remember { mutableStateOf(false) }
        if (viewingLetterbox) {
          BackHandler { viewingLetterbox = false }
          LetterboxScreen(
            repository = appContainer.letterboxRepository,
            myUid = user.uid,
            onBack = { viewingLetterbox = false },
          )
        } else {
          val guardianVm =
            viewModel<com.example.feature.registration.GuardianVerificationViewModel>(
              key = "guardian_${user.uid}"
            ) {
              com.example.feature.registration.GuardianVerificationViewModel(
                appContainer.registrationRepository
              )
            }
          GuardianVerificationScreen(
            viewModel = guardianVm,
            friendsMeetUid = gateState.profile.friendsMeetUid,
            onOpenLetterbox = { viewingLetterbox = true },
            onSignOut = handleSignOut,
          )
        }
      }
      is ProfileGateState.Ready -> {
        AuthenticatedShell(
          appContainer = appContainer,
          profile = gateState.profile,
          themeMode = themeMode,
          onSelectThemeMode = onSelectThemeMode,
          onSignOut = handleSignOut,
        )
      }
    }
  }
}

@Composable
private fun AuthenticatedShell(
  appContainer: AppContainer,
  profile: UserProfile,
  themeMode: ThemeMode,
  onSelectThemeMode: (ThemeMode) -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val navController = rememberNavController()
  val lifecycleOwner = LocalLifecycleOwner.current

  // Online presence lifecycle sync
  DisposableEffect(lifecycleOwner, profile.uid) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> {
          scope.launch {
            appContainer.registrationRepository.syncPresence(
              profile.uid,
              profile.friendsMeetUid,
              true,
            )
            appContainer.chatRepository.flushOfflineQueue()
          }
        }
        Lifecycle.Event.ON_STOP -> {
          scope.launch {
            appContainer.registrationRepository.syncPresence(
              profile.uid,
              profile.friendsMeetUid,
              false,
            )
          }
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val friendsVm =
    viewModel<FriendsViewModel>(key = "friends_${profile.uid}") {
      FriendsViewModel(
        friendsRepository = appContainer.friendsRepository,
        chatRepository = appContainer.chatRepository,
        myUid = profile.uid,
        myFriendsMeetUid = profile.friendsMeetUid,
      )
    }

  val friendsState by friendsVm.uiState.collectAsStateWithLifecycle()
  val chatThreads by
    appContainer.chatRepository
      .observeChatThreadsForUser(profile.uid)
      .collectAsStateWithLifecycle(emptyList())
  val userSettings by
    appContainer.registrationRepository
      .observeUserSettings(profile.uid)
      .collectAsStateWithLifecycle(null)
  val privacySettings by
    appContainer.registrationRepository
      .observePrivacySettings(profile.uid)
      .collectAsStateWithLifecycle(PrivacySettings(uid = profile.uid))
  val unreadLetters by
    appContainer.letterboxRepository
      .observeMessages(profile.uid, trashed = false)
      .collectAsStateWithLifecycle(emptyList())
  val unreadLetterCount = unreadLetters.count { !it.read }
  val oneSignalStatus by appContainer.oneSignalManager.status.collectAsStateWithLifecycle()

  val incomingCall by
    appContainer.callRepository
      .observeIncomingRingingCall(profile.uid)
      .collectAsStateWithLifecycle(null)

  val friendsByUid = remember(friendsState.friends) {
    friendsState.friends.associateBy { it.friendUid }
  }
  val userGroups by
    appContainer.groupRepository
      .observeUserGroups(profile.uid)
      .collectAsStateWithLifecycle(emptyList())
  val oldChatRepo = remember(appContainer.firestore) { OldChatRepository(appContainer.firestore) }
  val directoryUsers by
    oldChatRepo.observeAllDirectoryUsers(profile.uid).collectAsStateWithLifecycle(emptyList())
  val directoryUsersByUid = remember(directoryUsers) { directoryUsers.associateBy { it.uid } }

  val initiateCall: (friendUid: String, callType: CallType) -> Unit = { peerUid, callType ->
    scope.launch {
      val peer = friendsByUid[peerUid]
      val myKey = appContainer.e2eeCryptoManager.getOrCreateIdentityPublicKeyBase64(profile.uid)
      val peerEntry =
        peer?.let {
          appContainer.friendsRepository.lookupByFriendsMeetUid(it.friendMeetUid)
        }
      val fp =
        peerEntry?.identityPublicKey?.let {
          appContainer.e2eeCryptoManager.computeSafetyFingerprint(myKey, it)
        }
      val callId =
        appContainer.callRepository.createOutgoingCall(
          callerUid = profile.uid,
          callerName = profile.fullName,
          calleeUid = peerUid,
          callType = callType,
          e2eeFingerprint = fp,
        )
      navController.navigate(
        FriendsMeetRoute.CallRoute(
          callId = callId,
          callType = callType.name,
          peerName = peer?.friendName ?: "Friend",
          offerSdp = null,
        )
      )
    }
  }

  incomingCall?.let { call ->
    val callerName = friendsByUid[call.callerUid]?.friendName ?: "Friends Meet User"
    IncomingCallDialog(
      call = call,
      callerName = callerName,
      onAccept = {
        navController.navigate(
          FriendsMeetRoute.CallRoute(
            callId = call.id,
            callType = call.callType,
            peerName = callerName,
            offerSdp = call.offerSdp,
          )
        )
      },
      onDecline = {
        scope.launch {
          appContainer.callRepository.updateCallStatus(call.id, CallStatus.DECLINED)
        }
      },
    )
  }

  val navBackStack by navController.currentBackStackEntryAsState()
  val currentDest = navBackStack?.destination
  val isTopLevelTab =
    currentDest?.hasRoute<FriendsMeetRoute.FriendsRoute>() == true ||
      currentDest?.hasRoute<FriendsMeetRoute.ChatsTabRoute>() == true ||
      currentDest?.hasRoute<FriendsMeetRoute.LetterboxRoute>() == true ||
      currentDest?.hasRoute<FriendsMeetRoute.SettingsRoute>() == true

  val navigateTopTab: (FriendsMeetRoute) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val selectedNavIndex =
    when {
      currentDest?.hasRoute<FriendsMeetRoute.FriendsRoute>() == true -> 0
      currentDest?.hasRoute<FriendsMeetRoute.ChatsTabRoute>() == true -> 1
      currentDest?.hasRoute<FriendsMeetRoute.LetterboxRoute>() == true -> 2
      currentDest?.hasRoute<FriendsMeetRoute.SettingsRoute>() == true -> 3
      else -> 0
    }

  val liquidNavItems =
    listOf(
      LiquidNavItem(
        label = "Friends",
        icon = Icons.Default.People,
        testTag = "nav_friends_tab",
      ),
      LiquidNavItem(
        label = "Chats",
        icon = Icons.Default.ChatBubble,
        testTag = "nav_chats_tab",
      ),
      LiquidNavItem(
        label = "Letterbox",
        icon = Icons.Default.MarkEmailUnread,
        badgeCount = unreadLetterCount,
        testTag = "nav_letterbox_tab",
      ),
      LiquidNavItem(
        label = "Settings",
        icon = Icons.Default.Settings,
        testTag = "nav_settings_tab",
      ),
    )

  FriendsMeetBackdrop(modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val useNavRail = maxWidth >= 600.dp

      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
          if (isTopLevelTab && !useNavRail) {
            LiquidGlassNavigationBar(
              items = liquidNavItems,
              selectedIndex = selectedNavIndex,
              onSelect = { idx ->
                when (idx) {
                  0 -> navigateTopTab(FriendsMeetRoute.FriendsRoute)
                  1 -> navigateTopTab(FriendsMeetRoute.ChatsTabRoute)
                  2 -> navigateTopTab(FriendsMeetRoute.LetterboxRoute)
                  3 -> navigateTopTab(FriendsMeetRoute.SettingsRoute)
                }
              },
            )
          }
        },
      ) { innerPadding ->
        Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          if (isTopLevelTab && useNavRail) {
            NavigationRail(modifier = Modifier.fillMaxHeight()) {
              NavigationRailItem(
                selected = currentDest?.hasRoute<FriendsMeetRoute.FriendsRoute>() == true,
                onClick = { navigateTopTab(FriendsMeetRoute.FriendsRoute) },
                icon = { Icon(Icons.Default.People, contentDescription = "Friends") },
                label = { Text("Friends") },
              )
              NavigationRailItem(
                selected = currentDest?.hasRoute<FriendsMeetRoute.ChatsTabRoute>() == true,
                onClick = { navigateTopTab(FriendsMeetRoute.ChatsTabRoute) },
                icon = { Icon(Icons.Default.ChatBubble, contentDescription = "Chats") },
                label = { Text("Chats") },
              )
              NavigationRailItem(
                selected = currentDest?.hasRoute<FriendsMeetRoute.LetterboxRoute>() == true,
                onClick = { navigateTopTab(FriendsMeetRoute.LetterboxRoute) },
                icon = { Icon(Icons.Default.MarkEmailUnread, contentDescription = "Letterbox") },
                label = { Text("Letterbox") },
              )
              NavigationRailItem(
                selected = currentDest?.hasRoute<FriendsMeetRoute.SettingsRoute>() == true,
                onClick = { navigateTopTab(FriendsMeetRoute.SettingsRoute) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = { Text("Settings") },
              )
            }
          }

          NavHost(
            navController = navController,
            startDestination = FriendsMeetRoute.FriendsRoute,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            enterTransition = {
              val isTabTarget =
                targetState.destination.hasRoute<FriendsMeetRoute.FriendsRoute>() ||
                  targetState.destination.hasRoute<FriendsMeetRoute.ChatsTabRoute>() ||
                  targetState.destination.hasRoute<FriendsMeetRoute.LetterboxRoute>() ||
                  targetState.destination.hasRoute<FriendsMeetRoute.SettingsRoute>()
              if (isTabTarget) {
                fadeIn(animationSpec = tween(220))
              } else {
                fadeIn(animationSpec = tween(240)) +
                  slideInHorizontally(animationSpec = tween(260)) { it / 6 }
              }
            },
            exitTransition = {
              val isTabTarget =
                targetState.destination.hasRoute<FriendsMeetRoute.FriendsRoute>() ||
                  targetState.destination.hasRoute<FriendsMeetRoute.ChatsTabRoute>() ||
                  targetState.destination.hasRoute<FriendsMeetRoute.LetterboxRoute>() ||
                  targetState.destination.hasRoute<FriendsMeetRoute.SettingsRoute>()
              if (isTabTarget) {
                fadeOut(animationSpec = tween(180))
              } else {
                fadeOut(animationSpec = tween(180)) +
                  slideOutHorizontally(animationSpec = tween(240)) { -it / 8 }
              }
            },
            popEnterTransition = {
              fadeIn(animationSpec = tween(220)) +
                slideInHorizontally(animationSpec = tween(240)) { -it / 8 }
            },
            popExitTransition = {
              fadeOut(animationSpec = tween(180)) +
                slideOutHorizontally(animationSpec = tween(240)) { it / 6 }
            },
          ) {
          composable<FriendsMeetRoute.FriendsRoute> {
            FriendsScreen(
              viewModel = friendsVm,
              myName = profile.fullName,
              myFriendsMeetUid = profile.friendsMeetUid,
              quickMessages =
                userSettings?.quickMessages?.ifEmpty {
                  RegistrationRepository.DEFAULT_QUICK_MESSAGES
                } ?: RegistrationRepository.DEFAULT_QUICK_MESSAGES,
              customTagCategories = userSettings?.customTagCategories ?: emptyList(),
              onOpenChat = { chatId, friendUid ->
                navController.navigate(FriendsMeetRoute.ChatRoute(chatId, friendUid))
              },
              onStartAudioCall = { friendUid -> initiateCall(friendUid, CallType.AUDIO) },
              onOpenProfileModal = { peer ->
                navController.navigate(
                  FriendsMeetRoute.ViewProfileRoute(
                    peerUid = peer.uid,
                    peerMeetUid = peer.friendsMeetUid,
                    peerName = peer.displayName,
                    peerPhotoUrl = peer.photoUrl,
                    peerOnline = peer.online,
                  )
                )
              },
            )
          }

          composable<FriendsMeetRoute.ChatsTabRoute> {
            ChatsListScreen(
              myUid = profile.uid,
              threads = chatThreads,
              friendsByUid = friendsByUid,
              directoryUsersByUid = directoryUsersByUid,
              onOpenChat = { chatId, friendUid ->
                navController.navigate(FriendsMeetRoute.ChatRoute(chatId, friendUid))
              },
              onOpenOldChatDirectory = {
                navController.navigate(FriendsMeetRoute.OldChatDirectoryRoute)
              },
              onOpenGroups = {
                navController.navigate(FriendsMeetRoute.GroupListRoute)
              },
            )
          }

          composable<FriendsMeetRoute.OldChatDirectoryRoute> {
            OldChatDirectoryScreen(
              users = directoryUsers,
              onStartOldChat = { peerUid, peerName, peerPhotoUrl, initialLimit ->
                val chatId = FirestorePaths.canonicalChatId(profile.uid, peerUid)
                navController.navigate(
                  FriendsMeetRoute.OldChatRoute(
                    chatId = chatId,
                    peerUid = peerUid,
                    peerName = peerName,
                    peerPhotoUrl = peerPhotoUrl,
                    initialLimit = initialLimit,
                  )
                )
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.GroupListRoute> {
            val groupVm = viewModel<GroupViewModel>(key = "group_${profile.uid}") {
              GroupViewModel(appContainer.groupRepository, profile.uid, appContainer.chatRepository)
            }
            val groups by groupVm.groups.collectAsStateWithLifecycle()
            val groupError by groupVm.errorMessage.collectAsStateWithLifecycle()
            val groupBusy by groupVm.isBusy.collectAsStateWithLifecycle()
            GroupListScreen(
              groups = groups,
              errorMessage = groupError,
              isBusy = groupBusy,
              onClearError = groupVm::clearError,
              onJoinByQr = { raw ->
                groupVm.joinGroupByQr(raw) { joined ->
                  navController.navigate(FriendsMeetRoute.GroupChatRoute(joined.id, joined.name))
                }
              },
              onSelectGroup = { groupId, groupName ->
                navController.navigate(FriendsMeetRoute.GroupChatRoute(groupId, groupName))
              },
              onCreateGroup = {
                navController.navigate(FriendsMeetRoute.CreateGroupRoute)
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.CreateGroupRoute> {
            val groupVm = viewModel<GroupViewModel>(key = "group_create_${profile.uid}") {
              GroupViewModel(appContainer.groupRepository, profile.uid, appContainer.chatRepository)
            }
            val groupBusy by groupVm.isBusy.collectAsStateWithLifecycle()
            val groupError by groupVm.errorMessage.collectAsStateWithLifecycle()
            CreateGroupScreen(
              friends = friendsState.friends.map { f ->
                UserProfile(
                  uid = f.friendUid,
                  friendsMeetUid = f.friendMeetUid,
                  fullName = f.friendName,
                  photoUrl = f.friendPhotoUrl,
                  online = f.friendOnline,
                )
              },
              directoryUsers = directoryUsers,
              isBusy = groupBusy,
              errorMessage = groupError,
              onClearError = groupVm::clearError,
              onCreate = { name, desc, members ->
                groupVm.createGroup(name, desc, profile.fullName, members) { groupId ->
                  navController.popBackStack()
                  navController.navigate(FriendsMeetRoute.GroupChatRoute(groupId, name))
                }
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.GroupChatRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<FriendsMeetRoute.GroupChatRoute>()
            val groupVm = viewModel<GroupViewModel>(key = "group_chat_${args.groupId}") {
              GroupViewModel(appContainer.groupRepository, profile.uid, appContainer.chatRepository)
            }
            GroupChatScreen(
              groupId = args.groupId,
              groupName = args.groupName,
              currentUser = profile,
              viewModel = groupVm,
              directoryUsersByUid = directoryUsersByUid,
              friends = friendsState.friends.map { f ->
                UserProfile(
                  uid = f.friendUid,
                  friendsMeetUid = f.friendMeetUid,
                  fullName = f.friendName,
                  photoUrl = f.friendPhotoUrl,
                  online = f.friendOnline,
                )
              },
              allGroups = userGroups,
              friendsList = friendsState.friends,
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.LetterboxRoute> {
            LetterboxScreen(repository = appContainer.letterboxRepository, myUid = profile.uid)
          }

          composable<FriendsMeetRoute.SettingsRoute> {
            SettingsScreen(
              profile = profile,
              userSettings = userSettings,
              themeMode = themeMode,
              oneSignalStatus = oneSignalStatus,
              settingsRepository = appContainer.settingsRepository,
              onSelectThemeMode = onSelectThemeMode,
              onEnablePushNotifications = {
                scope.launch {
                  appContainer.oneSignalManager.initialize(
                    appContainer.appContext,
                    appContainer.appContext.getString(com.example.R.string.onesignal_app_id),
                  )
                  appContainer.oneSignalManager.bindUser(profile.uid)
                  appContainer.oneSignalManager.requestPermission(true)
                }
              },
              onOpenPrivacy = { navController.navigate(FriendsMeetRoute.PrivacyRoute) },
              onOpenBackup = { navController.navigate(FriendsMeetRoute.BackupRoute) },
              onOpenLinkedDevices = { navController.navigate(FriendsMeetRoute.LinkedDevicesRoute) },
              onSignOut = onSignOut,
            )
          }

          composable<FriendsMeetRoute.ChatRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<FriendsMeetRoute.ChatRoute>()
            val chatVm =
              viewModel<ChatViewModel>(key = "chat_${args.chatId}") {
                ChatViewModel(
                  chatRepository = appContainer.chatRepository,
                  e2eeCryptoManager = appContainer.e2eeCryptoManager,
                  chatId = args.chatId,
                  myUid = profile.uid,
                  myName = profile.fullName,
                  friendUid = args.friendUid,
                )
              }
            val friend = friendsByUid[args.friendUid]
            LaunchedEffect(friend) { chatVm.attachFriendInfo(friend) }

            ChatScreen(
              viewModel = chatVm,
              myUid = profile.uid,
              friendsList = friendsState.friends,
              quickMessages =
                userSettings?.quickMessages?.ifEmpty {
                  RegistrationRepository.DEFAULT_QUICK_MESSAGES
                } ?: RegistrationRepository.DEFAULT_QUICK_MESSAGES,
              onSaveQuickMessages = friendsVm::updateQuickMessages,
              onStartAudioCall = { initiateCall(args.friendUid, CallType.AUDIO) },
              onStartVideoCall = { initiateCall(args.friendUid, CallType.VIDEO) },
              onBack = { navController.popBackStack() },
              onAddFriend = { chatVm.addPeerAsFriend(appContainer.friendsRepository) },
              groupsList = userGroups,
              onForwardToGroup = { targetGroup ->
                chatVm.forwardSelectedToGroup(targetGroup, appContainer.groupRepository)
              },
            )
          }

          composable<FriendsMeetRoute.OldChatRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<FriendsMeetRoute.OldChatRoute>()
            val chatVm =
              viewModel<ChatViewModel>(key = "oldchat_${args.chatId}") {
                ChatViewModel(
                  chatRepository = appContainer.chatRepository,
                  e2eeCryptoManager = appContainer.e2eeCryptoManager,
                  chatId = args.chatId,
                  myUid = profile.uid,
                  myName = profile.fullName,
                  friendUid = args.peerUid,
                  initialIsOldChat = true,
                  initialOldChatLimit = args.initialLimit,
                  initialPeerName = args.peerName,
                  initialPeerPhotoUrl = args.peerPhotoUrl,
                )
              }
            val friend = friendsByUid[args.peerUid]
            LaunchedEffect(friend) { chatVm.attachFriendInfo(friend) }

            ChatScreen(
              viewModel = chatVm,
              myUid = profile.uid,
              friendsList = friendsState.friends,
              quickMessages =
                userSettings?.quickMessages?.ifEmpty {
                  RegistrationRepository.DEFAULT_QUICK_MESSAGES
                } ?: RegistrationRepository.DEFAULT_QUICK_MESSAGES,
              onSaveQuickMessages = friendsVm::updateQuickMessages,
              onStartAudioCall = { initiateCall(args.peerUid, CallType.AUDIO) },
              onStartVideoCall = { initiateCall(args.peerUid, CallType.VIDEO) },
              onBack = { navController.popBackStack() },
              onAddFriend = { chatVm.addPeerAsFriend(appContainer.friendsRepository) },
              groupsList = userGroups,
              onForwardToGroup = { targetGroup ->
                chatVm.forwardSelectedToGroup(targetGroup, appContainer.groupRepository)
              },
            )
          }

          composable<FriendsMeetRoute.CallRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<FriendsMeetRoute.CallRoute>()
            val callVm =
              viewModel<CallViewModel>(key = "call_${args.callId}") {
                CallViewModel(
                  context = context,
                  callRepository = appContainer.callRepository,
                  callId = args.callId,
                  myUid = profile.uid,
                  peerName = args.peerName,
                  peerPhotoUrl = null,
                )
              }
            CallScreen(
              viewModel = callVm,
              initialOfferSdp = args.offerSdp,
              onCallFinished = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.LinkedDevicesRoute> {
            LinkedDevicesScreen(
              repository = appContainer.linkedDevicesRepository,
              myUid = profile.uid,
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.PrivacyRoute> {
            PrivacySettingsScreen(
              myUid = profile.uid,
              currentSettings = privacySettings ?: PrivacySettings(uid = profile.uid),
              blockedFriends = friendsState.friends.filter { it.blocked },
              onSavePrivacy = { updated ->
                appContainer.settingsRepository.updatePrivacySettings(profile.uid, updated)
              },
              onUnblockFriend = { fUid ->
                scope.launch { appContainer.friendsRepository.setBlocked(profile.uid, fUid, false) }
              },
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.BackupRoute> {
            BackupRestoreScreen(
              repository = appContainer.settingsRepository,
              myUid = profile.uid,
              onBack = { navController.popBackStack() },
            )
          }

          composable<FriendsMeetRoute.ViewProfileRoute> { backStackEntry ->
            val args = backStackEntry.toRoute<FriendsMeetRoute.ViewProfileRoute>()
            DisposableEffect(args.peerUid) {
              friendsVm.clearLookupError()
              onDispose { friendsVm.clearLookupError() }
            }
            val existingFriend = friendsByUid[args.peerUid]
            val peerEntry =
              directoryUsersByUid[args.peerUid]
                ?: UserIdDirectoryEntry(
                  uid = args.peerUid,
                  friendsMeetUid = args.peerMeetUid,
                  displayName = args.peerName,
                  photoUrl = args.peerPhotoUrl,
                  online = args.peerOnline,
                )
            ViewFriendProfileScreen(
              peer = peerEntry,
              activeTag = friendsState.friendTags[args.peerUid],
              isFriend = existingFriend != null && !existingFriend.blocked,
              isBusy = friendsState.isProcessingAction,
              errorMessage = friendsState.lookupError,
              onAddOrOpenChat = {
                if (existingFriend != null) {
                  navController.navigate(
                    FriendsMeetRoute.ChatRoute(existingFriend.chatId, existingFriend.friendUid)
                  )
                } else {
                  friendsVm.addFriendAndOpenChat(peerEntry, AddedVia.UID_LOOKUP) { cId, fUid ->
                    navController.navigate(FriendsMeetRoute.ChatRoute(cId, fUid)) {
                      popUpTo<FriendsMeetRoute.ViewProfileRoute> { inclusive = true }
                    }
                  }
                }
              },
              onAudioCall = { initiateCall(args.peerUid, CallType.AUDIO) },
              onVideoCall = { initiateCall(args.peerUid, CallType.VIDEO) },
              onBack = { navController.popBackStack() },
            )
          }
        }
      }
    }
  }
}
}
