package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import com.example.R
import com.example.data.PreferencesManager
import com.example.data.model.SystemSettings
import com.example.data.model.ThemeMode
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.ActivityAndNotificationRepository
import com.example.data.repository.TrackingRepository
import com.example.data.repository.UserAndSettingsRepository
import com.example.data.signOut
import com.example.ui.components.EmptyStateCard
import com.example.ui.screens.ActivityTimelineScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreateEditTrackingScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrackingDetailScreen
import com.example.ui.screens.TrackingListScreen
import com.example.ui.screens.UserManagementScreen
import com.example.ui.theme.SmartTrackerTheme
import com.example.ui.viewmodel.SmartTrackerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

object SmartTrackerRoutes {
    const val DASHBOARD = "dashboard"
    const val TRACKING_LIST = "tracking_list"
    const val CREATE_TRACKING = "create_tracking"
    const val EDIT_TRACKING = "edit_tracking/{recordId}"
    const val DETAIL_TRACKING = "detail_tracking/{recordId}"
    const val ACTIVITY_TIMELINE = "activity_timeline"
    const val NOTIFICATIONS = "notifications"
    const val REPORTS = "reports"
    const val USER_MANAGEMENT = "user_management"
    const val SETTINGS = "settings"

    fun detail(recordId: String) = "detail_tracking/$recordId"
    fun edit(recordId: String) = "edit_tracking/$recordId"
}

private data class SidebarMenuItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val adminOnly: Boolean = false,
    val testTag: String
)

@Composable
fun SmartTrackerApp() {
    val context = LocalContext.current
    val prefsManager = remember(context) { PreferencesManager(context.applicationContext) }
    val localTheme by prefsManager.themeModeFlow.collectAsStateWithLifecycle(initialValue = ThemeMode.LIGHT)

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    SmartTrackerTheme(themeMode = localTheme) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                onAuthSuccess = { currentUser = Firebase.auth.currentUser }
            )
        } else {
            key(user.uid) {
                AuthenticatedWorkspace(
                    firebaseUser = user,
                    prefsManager = prefsManager
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuthenticatedWorkspace(
    firebaseUser: FirebaseUser,
    prefsManager: PreferencesManager
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SmartTrackerViewModel = viewModel(
        key = firebaseUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SmartTrackerViewModel(
                    currentUserId = firebaseUser.uid,
                    firebaseUser = firebaseUser,
                    userRepo = UserAndSettingsRepository(db),
                    trackingRepo = TrackingRepository(db),
                    activityRepo = ActivityAndNotificationRepository(db),
                    prefsManager = prefsManager
                )
            }
        }
    )

    val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()
    val bootstrapError by viewModel.bootstrapError.collectAsStateWithLifecycle()
    val userProfileState by viewModel.currentUserProfile.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val recordsState by viewModel.recordsState.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val notificationsState by viewModel.notificationsState.collectAsStateWithLifecycle()
    val activityLogsState by viewModel.activityLogsState.collectAsStateWithLifecycle()
    val allUsersState by viewModel.allUsersState.collectAsStateWithLifecycle()
    val systemSettingsState by viewModel.systemSettingsState.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadNotificationsCount.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val categoryFilterId by viewModel.categoryFilterId.collectAsStateWithLifecycle()
    val dateFilter by viewModel.dateFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val categories = (categoriesState as? UiState.Success)?.data ?: emptyList()
    val allUsers = (allUsersState as? UiState.Success)?.data ?: emptyList()
    val systemSettings = (systemSettingsState as? UiState.Success)?.data ?: SystemSettings()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Automatically run deadline/overdue evaluation once records & settings load
    LaunchedEffect(recordsState is UiState.Success, systemSettings.autoMarkOverdue) {
        if (recordsState is UiState.Success) {
            viewModel.runAutomatedDeadlineCheck()
        }
    }

    if (isBootstrapping) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Connecting SmartTracker workspace...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    if (bootstrapError != null && userProfile == null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyStateCard(
                title = "Workspace Initialization Issue",
                message = bootstrapError ?: "Unable to connect to Firestore."
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { viewModel.bootstrap() }) { Text("Retry") }
                    Button(onClick = { signOut(credentialManager, scope) }) { Text("Logout") }
                }
            }
        }
        return
    }

    if (userProfile != null && !userProfile.active) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            EmptyStateCard(
                title = "Account Deactivated",
                message = "Your SmartTracker account has been deactivated by an administrator.",
                icon = Icons.Outlined.Block
            ) {
                Button(onClick = { signOut(credentialManager, scope) }) {
                    Text("Sign Out")
                }
            }
        }
        return
    }

    val navController = rememberNavController()
    val navBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStack?.destination?.route ?: SmartTrackerRoutes.DASHBOARD
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val menuItems = remember(isAdmin) {
        buildList {
            add(SidebarMenuItem(SmartTrackerRoutes.DASHBOARD, "Dashboard", Icons.Outlined.Dashboard, testTag = "nav_dashboard"))
            add(SidebarMenuItem(SmartTrackerRoutes.TRACKING_LIST, "Tracking Records", Icons.Outlined.ListAlt, testTag = "nav_records"))
            add(SidebarMenuItem(SmartTrackerRoutes.CREATE_TRACKING, "Create New Tracking", Icons.Outlined.AddCircleOutline, testTag = "nav_create"))
            add(SidebarMenuItem(SmartTrackerRoutes.ACTIVITY_TIMELINE, "Activity Timeline", Icons.Outlined.History, testTag = "nav_activity"))
            add(SidebarMenuItem(SmartTrackerRoutes.NOTIFICATIONS, "Notifications", Icons.Outlined.Notifications, testTag = "nav_notifications"))
            add(SidebarMenuItem(SmartTrackerRoutes.REPORTS, "Reports and Analytics", Icons.Outlined.Assessment, testTag = "nav_reports"))
            if (isAdmin) {
                add(SidebarMenuItem(SmartTrackerRoutes.USER_MANAGEMENT, "User Management", Icons.Outlined.ManageAccounts, adminOnly = true, testTag = "nav_users"))
            }
            add(SidebarMenuItem(SmartTrackerRoutes.SETTINGS, "Settings", Icons.Outlined.Settings, testTag = "nav_settings"))
        }
    }

    val navigateSingleTop: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(SmartTrackerRoutes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        scope.launch { drawerState.close() }
    }

    val drawerSheetContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // Brand + User Header
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!userProfile?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = userProfile?.photoUrl,
                            contentDescription = "User avatar",
                            modifier = Modifier.size(40.dp).clip(CircleShape)
                        )
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = (userProfile?.displayName?.firstOrNull() ?: 'S').uppercase(),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SmartTracker",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "${userProfile?.displayName ?: firebaseUser.displayName ?: "User"} • ${if (isAdmin) "Admin" else "User"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            menuItems.forEach { item ->
                val selected = currentRoute == item.route
                NavigationDrawerItem(
                    icon = {
                        if (item.route == SmartTrackerRoutes.NOTIFICATIONS && unreadCount > 0) {
                            BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        } else {
                            Icon(item.icon, contentDescription = item.label)
                        }
                    },
                    label = { Text(item.label) },
                    selected = selected,
                    onClick = { navigateSingleTop(item.route) },
                    modifier = Modifier.padding(vertical = 2.dp).testTag(item.testTag)
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            // 9. Logout
            NavigationDrawerItem(
                icon = {
                    Icon(
                        Icons.AutoMirrored.Outlined.Logout,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                label = { Text("Logout", color = MaterialTheme.colorScheme.error) },
                selected = false,
                onClick = {
                    scope.launch { drawerState.close() }
                    signOut(credentialManager, scope)
                },
                modifier = Modifier.testTag("nav_logout")
            )
        }
    }

    val mainContent: @Composable (showMenuButton: Boolean) -> Unit = { showMenuButton ->
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        val title = when {
                            currentRoute == SmartTrackerRoutes.DASHBOARD -> "Dashboard"
                            currentRoute == SmartTrackerRoutes.TRACKING_LIST -> "Tracking Records"
                            currentRoute == SmartTrackerRoutes.CREATE_TRACKING -> "New Tracking"
                            currentRoute.startsWith("edit_tracking") -> "Edit Tracking"
                            currentRoute.startsWith("detail_tracking") -> "Tracking Details"
                            currentRoute == SmartTrackerRoutes.ACTIVITY_TIMELINE -> "Activity Timeline"
                            currentRoute == SmartTrackerRoutes.NOTIFICATIONS -> "Notifications"
                            currentRoute == SmartTrackerRoutes.REPORTS -> "Reports & Analytics"
                            currentRoute == SmartTrackerRoutes.USER_MANAGEMENT -> "User Management"
                            currentRoute == SmartTrackerRoutes.SETTINGS -> "Settings"
                            else -> "SmartTracker"
                        }
                        Text(title, style = MaterialTheme.typography.titleLarge)
                    },
                    navigationIcon = {
                        if (showMenuButton) {
                            IconButton(
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.testTag("open_drawer_btn")
                            ) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Open Navigation Menu")
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { navigateSingleTop(SmartTrackerRoutes.NOTIFICATIONS) },
                            modifier = Modifier.testTag("topbar_notifications_btn")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadCount > 0) {
                                        Badge { Text(unreadCount.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.Outlined.Notifications, contentDescription = "Notifications")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (showMenuButton) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val primaryTabs = listOf(
                            Triple(SmartTrackerRoutes.DASHBOARD, "Home", Icons.Outlined.Dashboard),
                            Triple(SmartTrackerRoutes.TRACKING_LIST, "Records", Icons.Outlined.ListAlt),
                            Triple(SmartTrackerRoutes.ACTIVITY_TIMELINE, "Timeline", Icons.Outlined.History),
                            Triple(SmartTrackerRoutes.REPORTS, "Reports", Icons.Outlined.Assessment)
                        )
                        primaryTabs.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = { navigateSingleTop(route) },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = SmartTrackerRoutes.DASHBOARD
                ) {
                    composable(SmartTrackerRoutes.DASHBOARD) {
                        DashboardScreen(
                            userProfile = userProfile,
                            isAdmin = isAdmin,
                            stats = stats,
                            recordsState = recordsState,
                            logsState = activityLogsState,
                            systemSettings = systemSettings,
                            onCreateTracking = { navigateSingleTop(SmartTrackerRoutes.CREATE_TRACKING) },
                            onNavigateToRecordsWithStatus = { st ->
                                viewModel.setStatusFilter(st)
                                navigateSingleTop(SmartTrackerRoutes.TRACKING_LIST)
                            },
                            onOpenRecord = { rec ->
                                navController.navigate(SmartTrackerRoutes.detail(rec.id))
                            },
                            onNavigateToReports = { navigateSingleTop(SmartTrackerRoutes.REPORTS) },
                            onRunOverdueScan = { viewModel.runAutomatedDeadlineCheck() }
                        )
                    }

                    composable(SmartTrackerRoutes.TRACKING_LIST) {
                        TrackingListScreen(
                            recordsState = recordsState,
                            filteredRecords = filteredRecords,
                            categories = categories,
                            searchQuery = searchQuery,
                            statusFilter = statusFilter,
                            categoryFilterId = categoryFilterId,
                            dateFilter = dateFilter,
                            sortOrder = sortOrder,
                            onSearchChange = viewModel::setSearchQuery,
                            onStatusChange = viewModel::setStatusFilter,
                            onCategoryChange = viewModel::setCategoryFilter,
                            onDateFilterChange = viewModel::setDateFilter,
                            onSortChange = viewModel::setSortOrder,
                            onClearFilters = viewModel::clearFilters,
                            onCreateNew = { navigateSingleTop(SmartTrackerRoutes.CREATE_TRACKING) },
                            onOpenRecord = { rec ->
                                navController.navigate(SmartTrackerRoutes.detail(rec.id))
                            }
                        )
                    }

                    composable(SmartTrackerRoutes.CREATE_TRACKING) {
                        CreateEditTrackingScreen(
                            existingRecord = null,
                            currentUser = userProfile,
                            isAdmin = isAdmin,
                            allUsers = allUsers,
                            categories = categories,
                            defaultPriority = systemSettings.parsedDefaultPriority,
                            isBusy = isBusy,
                            onCreateCategory = viewModel::createCategory,
                            onSaveCreate = { title, desc, cat, pri, st, prog, assId, assName, start, due, src ->
                                viewModel.createTrackingRecord(
                                    title = title,
                                    description = desc,
                                    category = cat,
                                    priority = pri,
                                    status = st,
                                    progressPercent = prog,
                                    assigneeId = assId,
                                    assigneeName = assName,
                                    startDateMillis = start,
                                    dueDateMillis = due,
                                    sourceType = src
                                ) { newId ->
                                    navController.navigate(SmartTrackerRoutes.detail(newId)) {
                                        popUpTo(SmartTrackerRoutes.CREATE_TRACKING) { inclusive = true }
                                    }
                                }
                            },
                            onSaveEdit = { _, _, _, _, _, _, _, _, _ -> },
                            onCancel = { navController.popBackStack() }
                        )
                    }

                    composable(
                        route = SmartTrackerRoutes.EDIT_TRACKING,
                        arguments = listOf(navArgument("recordId") { type = NavType.StringType })
                    ) { backStack ->
                        val recordId = backStack.arguments?.getString("recordId").orEmpty()
                        val record = (recordsState as? UiState.Success)?.data?.firstOrNull { it.id == recordId }
                        if (record == null) {
                            EmptyStateCard("Record not found", "The selected record could not be loaded.")
                        } else {
                            CreateEditTrackingScreen(
                                existingRecord = record,
                                currentUser = userProfile,
                                isAdmin = isAdmin,
                                allUsers = allUsers,
                                categories = categories,
                                defaultPriority = systemSettings.parsedDefaultPriority,
                                isBusy = isBusy,
                                onCreateCategory = viewModel::createCategory,
                                onSaveCreate = { _, _, _, _, _, _, _, _, _, _, _ -> },
                                onSaveEdit = { rec, title, desc, cat, pri, assId, assName, start, due ->
                                    viewModel.updateTrackingDetails(
                                        record = rec,
                                        title = title,
                                        description = desc,
                                        category = cat,
                                        priority = pri,
                                        assigneeId = assId,
                                        assigneeName = assName,
                                        startDateMillis = start,
                                        dueDateMillis = due
                                    ) {
                                        navController.popBackStack()
                                    }
                                },
                                onCancel = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(
                        route = SmartTrackerRoutes.DETAIL_TRACKING,
                        arguments = listOf(navArgument("recordId") { type = NavType.StringType })
                    ) { backStack ->
                        val recordId = backStack.arguments?.getString("recordId").orEmpty()
                        val record = (recordsState as? UiState.Success)?.data?.firstOrNull { it.id == recordId }
                        if (record == null) {
                            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                EmptyStateCard("Record unavailable", "This record may have been deleted.") {
                                    Button(onClick = { navController.popBackStack() }) { Text("Go Back") }
                                }
                            }
                        } else {
                            val updatesFlow = remember(record.id) { viewModel.observeRecordUpdates(record) }
                            val attachmentsFlow = remember(record.id) { viewModel.observeRecordAttachments(record) }
                            TrackingDetailScreen(
                                record = record,
                                currentUserId = firebaseUser.uid,
                                isAdmin = isAdmin,
                                isBusy = isBusy,
                                updatesFlow = updatesFlow,
                                attachmentsFlow = attachmentsFlow,
                                onBack = { navController.popBackStack() },
                                onEdit = { rec -> navController.navigate(SmartTrackerRoutes.edit(rec.id)) },
                                onDelete = { rec ->
                                    viewModel.deleteRecord(rec) { navController.popBackStack() }
                                },
                                onUpdateStatus = { newSt, prog, note ->
                                    viewModel.updateRecordStatus(record, newSt, prog, note)
                                },
                                onAddAttachment = { name, mime, size, uri ->
                                    viewModel.addAttachment(record, name, mime, size, uri)
                                }
                            )
                        }
                    }

                    composable(SmartTrackerRoutes.ACTIVITY_TIMELINE) {
                        ActivityTimelineScreen(logsState = activityLogsState, isAdmin = isAdmin)
                    }

                    composable(SmartTrackerRoutes.NOTIFICATIONS) {
                        NotificationsScreen(
                            notificationsState = notificationsState,
                            onMarkRead = viewModel::markNotificationRead,
                            onMarkAllRead = viewModel::markAllNotificationsRead,
                            onScanDeadlines = viewModel::runAutomatedDeadlineCheck,
                            onOpenRecordById = { recId ->
                                navController.navigate(SmartTrackerRoutes.detail(recId))
                            }
                        )
                    }

                    composable(SmartTrackerRoutes.REPORTS) {
                        ReportsScreen(
                            recordsState = recordsState,
                            logsState = activityLogsState,
                            onExported = viewModel::logReportExport,
                            onExportError = { err ->
                                scope.launch { snackbarHostState.showSnackbar(err) }
                            }
                        )
                    }

                    composable(SmartTrackerRoutes.USER_MANAGEMENT) {
                        UserManagementScreen(
                            isAdmin = isAdmin,
                            currentUserId = firebaseUser.uid,
                            usersState = allUsersState,
                            onUpdateUser = viewModel::adminUpdateUser
                        )
                    }

                    composable(SmartTrackerRoutes.SETTINGS) {
                        SettingsScreen(
                            userProfile = userProfile,
                            isAdmin = isAdmin,
                            systemSettings = systemSettings,
                            isBusy = isBusy,
                            onSavePreferences = viewModel::saveUserPreferences,
                            onSaveSystemSettings = viewModel::saveSystemSettings
                        )
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 840.dp
        if (isWideScreen) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(
                        modifier = Modifier.width(280.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        drawerSheetContent()
                    }
                }
            ) {
                mainContent(false)
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        modifier = Modifier.width(290.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surface
                    ) {
                        drawerSheetContent()
                    }
                }
            ) {
                mainContent(true)
            }
        }
    }
}
