package com.antoninofaro.welcometool

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.List
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antoninofaro.welcometool.ui.screens.ChangesetDetailScreen
import com.antoninofaro.welcometool.ui.screens.ChangesetListScreen
import com.antoninofaro.welcometool.ui.screens.ChangesetsViewModel
import com.antoninofaro.welcometool.ui.screens.LicensesScreen
import com.antoninofaro.welcometool.ui.screens.MainViewModel
import com.antoninofaro.welcometool.ui.screens.OnboardingScreen
import com.antoninofaro.welcometool.ui.screens.SettingsScreen
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel
import com.antoninofaro.welcometool.ui.screens.UserDetailScreen
import com.antoninofaro.welcometool.ui.screens.UserListScreen
import com.antoninofaro.welcometool.ui.theme.WelcomeToolTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val changesetsViewModel: ChangesetsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val deepLinkChangesetId = intent?.getStringExtra("DEEP_LINK_CHANGESET_ID")
        val deepLinkChangesetsTab = intent?.getBooleanExtra("DEEP_LINK_CHANGESETS_TAB", false) ?: false
        val deepLinkUserId = intent?.getStringExtra("DEEP_LINK_USER_ID")

        setContent {
            val context = LocalContext.current
            val settings by settingsViewModel.settings.collectAsState()
            val darkTheme = when (settings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            WelcomeToolTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor
            ) {

                LaunchedEffect(settings.autoRefresh, settings.autoRefreshInterval) {
                    com.antoninofaro.welcometool.utils.WorkerUtils.scheduleOsmSyncWorker(
                        context,
                        settings.autoRefreshInterval,
                        settings.autoRefresh
                    )
                }

                LaunchedEffect(settings.defaultBBox) {
                    viewModel.updateBBox(settings.defaultBBox)
                }

                val permissionLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var showRationale by remember { mutableStateOf(false) }
                    var permissionRequestAttempted by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(settings.isOnboardingCompleted, settings.showNotifications) {
                        if (!permissionRequestAttempted && settings.isOnboardingCompleted && settings.showNotifications) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                showRationale = true
                            }
                            permissionRequestAttempted = true
                        }
                    }
                    if (showRationale) {
                        AlertDialog(
                            onDismissRequest = { showRationale = false },
                            title = { Text(stringResource(R.string.notification_rationale_title)) },
                            text = { Text(stringResource(R.string.notification_rationale_desc)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showRationale = false
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }) { Text(stringResource(R.string.ok_btn)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRationale = false }) {
                                    Text(
                                        stringResource(R.string.not_now_btn)
                                    )
                                }
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (settings.isLoaded) {
                    AppNavigation(
                        viewModel,
                        settingsViewModel,
                        changesetsViewModel,
                        settings.isOnboardingCompleted,
                        deepLinkChangesetId,
                        deepLinkChangesetsTab,
                        deepLinkUserId
                    )
                }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    changesetsViewModel: ChangesetsViewModel,
    isOnboardingCompleted: Boolean,
    deepLinkChangesetId: String?,
    deepLinkChangesetsTab: Boolean,
    deepLinkUserId: String?
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    val startDestination = if (isOnboardingCompleted) "user_list" else "onboarding"

    LaunchedEffect(Unit) {
        if (isOnboardingCompleted) {
            if (deepLinkChangesetId != null) {
                navController.navigate("changeset_detail/$deepLinkChangesetId") {
                    launchSingleTop = true
                }
            } else if (deepLinkUserId != null) {
                navController.navigate("user_detail/$deepLinkUserId") {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (isOnboardingCompleted) {
            if (deepLinkChangesetId != null) {
                navController.navigate("changeset_detail/$deepLinkChangesetId") {
                    launchSingleTop = true
                }
            } else if (deepLinkUserId != null) {
                navController.navigate("user_detail/$deepLinkUserId") {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("onboarding") {
            OnboardingScreen(
                viewModel = settingsViewModel,
                onFinish = {
                    navController.navigate("user_list") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }

        composable(
            "user_list?userFilter={userFilter}",
            arguments = listOf(androidx.navigation.navArgument("userFilter") {
                type = androidx.navigation.NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val userFilter = backStackEntry.arguments?.getString("userFilter")?.takeIf { it.isNotBlank() }
            MainHome(
                viewModel = viewModel,
                changesetsViewModel = changesetsViewModel,
                deepLinkChangesetsTab = deepLinkChangesetsTab || userFilter != null,
                initialUserFilter = userFilter,
                onUserClick = { user, _ ->
                    navController.navigate("user_detail/${user.id}")
                },
                onChangesetClick = { changeset ->
                    navController.navigate("changeset_detail/${changeset.entity.id}")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("user_detail/{userId}") { backStackEntry ->
            val userIdStr = backStackEntry.arguments?.getString("userId")
            val userId = userIdStr?.toLongOrNull()

            val selectedUserUiModel = uiState.users.find { it.user.id == userId }
                ?: uiState.users.find { it.user.id == userId }

            if (selectedUserUiModel != null && userId != null) {
                LaunchedEffect(userId) {
                    viewModel.loadOsmchaForUser(userId)
                    viewModel.refreshUser(userId, force = false)
                }
                
                val recentChangesets = remember { androidx.compose.runtime.mutableStateOf<List<com.antoninofaro.welcometool.data.model.ChangesetUiModel>>(emptyList()) }
                LaunchedEffect(userId) {
                    recentChangesets.value = viewModel.getRecentChangesetsForUser(userId)
                }
                
                UserDetailScreen(
                    user = selectedUserUiModel.user,
                    analysis = selectedUserUiModel.analysis,
                    osmchaState = uiState.osmchaState,
                    osmchaLikes = uiState.osmchaLikes,
                    osmchaDislikes = uiState.osmchaDislikes,
                    osmchaLastChecked = selectedUserUiModel.osmchaLastChecked,
                    lastUpdated = selectedUserUiModel.lastUpdated,
                    isOnline = uiState.isOnline,
                    recentChangesets = recentChangesets.value,
                    onOsmchaRefresh = { viewModel.refreshOsmchaForUser(userId) },
                    onNavigateUp = { navController.navigateUp() },
                    onToggleWelcome = {
                        viewModel.toggleWelcomed(
                            selectedUserUiModel.user.id,
                            selectedUserUiModel.analysis.isWelcomed
                        )
                    },
                    onChangesetClick = { changeset ->
                        navController.navigate("changeset_detail/${changeset.entity.id}")
                    },
                    onViewMoreChangesets = {
                        navController.navigate("user_list?userFilter=${selectedUserUiModel.user.displayName}") {
                            popUpTo("user_list?userFilter={userFilter}") { inclusive = true }
                        }
                    },
                    onLoadMoreChangesets = {
                        changesetsViewModel.fetchMoreUserChangesets(userId)
                    }
                )
            } else {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    navController.navigateUp()
                }
            }
        }

        composable("changeset_detail/{changesetId}") { backStackEntry ->
            val changesetIdStr = backStackEntry.arguments?.getString("changesetId")
            val changesetId = changesetIdStr?.toLongOrNull()
            
            val uiState by changesetsViewModel.uiState.collectAsState()
            val changeset = uiState.changesets.find { it.entity.id == changesetId }
            
            if (changeset != null && changesetId != null) {
                LaunchedEffect(changesetId) {
                    changesetsViewModel.loadChangesetElements(changesetId)
                    changesetsViewModel.refreshOsmchaDetails(changesetId)
                }
                
                ChangesetDetailScreen(
                    uiModel = changeset,
                    elements = uiState.selectedChangesetElements,
                    isElementsLoading = uiState.isElementsLoading,
                    onNavigateUp = { navController.navigateUp() },
                    onUserClick = { uid ->
                        navController.navigate("user_detail/$uid")
                    },
                    onExpandElements = {
                        changesetsViewModel.loadChangesetElements(changesetId)
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.navigateUp()
                }
            }
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToLicenses = { navController.navigate("licenses") }
            )
        }

        composable("licenses") {
            LicensesScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}

@Composable
fun MainHome(
    viewModel: MainViewModel,
    changesetsViewModel: ChangesetsViewModel,
    deepLinkChangesetsTab: Boolean = false,
    initialUserFilter: String? = null,
    onUserClick: (com.antoninofaro.welcometool.data.model.OsmUser, com.antoninofaro.welcometool.domain.UserAnalysis) -> Unit,
    onChangesetClick: (com.antoninofaro.welcometool.data.model.ChangesetUiModel) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "mappers"
    
    LaunchedEffect(deepLinkChangesetsTab, initialUserFilter) {
        if (deepLinkChangesetsTab) {
            val route = if (initialUserFilter != null) "changesets?userFilter=$initialUserFilter" else "changesets"
            nestedNavController.navigate(route) {
                popUpTo(nestedNavController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    androidx.compose.material3.Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Mappers") },
                    label = { Text("Mappers") },
                    selected = currentRoute == "mappers",
                    onClick = {
                        if (currentRoute != "mappers") {
                            nestedNavController.navigate("mappers") {
                                popUpTo(nestedNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Changesets") },
                    label = { Text("Changesets") },
                    selected = currentRoute.startsWith("changesets"),
                    onClick = {
                        if (!currentRoute.startsWith("changesets")) {
                            nestedNavController.navigate("changesets") {
                                popUpTo(nestedNavController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(navController = nestedNavController, startDestination = "mappers") {
                composable("mappers") {
                    UserListScreen(
                        viewModel = viewModel,
                        onUserClick = onUserClick,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                composable(
                    "changesets?userFilter={userFilter}",
                    arguments = listOf(androidx.navigation.navArgument("userFilter") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    val userFilter = backStackEntry.arguments?.getString("userFilter")?.takeIf { it.isNotBlank() }
                    ChangesetListScreen(
                        viewModel = changesetsViewModel,
                        initialUserFilter = userFilter,
                        onChangesetClick = onChangesetClick,
                        onClearUserFilter = {
                            backStackEntry.arguments?.remove("userFilter")
                        }
                    )
                }
            }
        }
    }
}
