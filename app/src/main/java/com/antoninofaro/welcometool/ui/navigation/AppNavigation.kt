package com.antoninofaro.welcometool.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.antoninofaro.welcometool.data.model.ChangesetUiModel
import com.antoninofaro.welcometool.data.model.OsmUser
import com.antoninofaro.welcometool.domain.UserAnalysis
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
import com.antoninofaro.welcometool.ui.screens.UserUiModel
import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
data class UserListRoute(val selectTab: String? = null)

@Serializable
data class UserDetailRoute(val userId: Long)

@Serializable
data class ChangesetDetailRoute(val changesetId: Long)

@Serializable
object SettingsRoute

@Serializable
object LicensesRoute

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val startDestination: Any = if (isOnboardingCompleted) UserListRoute() else OnboardingRoute

    LaunchedEffect(Unit) {
        if (isOnboardingCompleted) {
            if (deepLinkChangesetId != null) {
                deepLinkChangesetId.toLongOrNull()?.let { csId ->
                    navController.navigate(ChangesetDetailRoute(csId)) {
                        launchSingleTop = true
                    }
                }
            } else if (deepLinkUserId != null) {
                deepLinkUserId.toLongOrNull()?.let { uId ->
                    navController.navigate(UserDetailRoute(uId)) {
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {

        composable<OnboardingRoute> {
            OnboardingScreen(
                viewModel = settingsViewModel,
                onFinish = {
                    navController.navigate(UserListRoute()) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
            )
        }

        composable<UserListRoute> { backStackEntry ->
            val route: UserListRoute = backStackEntry.toRoute()
            MainHome(
                viewModel = viewModel,
                changesetsViewModel = changesetsViewModel,
                deepLinkChangesetsTab = deepLinkChangesetsTab || route.selectTab == "changesets",
                onUserClick = { user, _ ->
                    navController.navigate(UserDetailRoute(user.id))
                },
                onChangesetClick = { changeset ->
                    navController.navigate(ChangesetDetailRoute(changeset.entity.id))
                },
                onNavigateToSettings = {
                    navController.navigate(SettingsRoute)
                }
            )
        }

        composable<UserDetailRoute> { backStackEntry ->
            val route: UserDetailRoute = backStackEntry.toRoute()
            val userId = route.userId

            var isLoading by remember { mutableStateOf(true) }
            var selectedUserUiModel by remember { mutableStateOf<UserUiModel?>(null) }

            LaunchedEffect(userId, uiState.users) {
                val inMemory = uiState.users.find { it.user.id == userId }
                if (inMemory != null) {
                    selectedUserUiModel = inMemory
                    isLoading = false
                } else {
                    val fromDb = viewModel.getUserById(userId)
                    if (fromDb != null) {
                        selectedUserUiModel = fromDb
                        isLoading = false
                    } else {
                        viewModel.refreshUser(userId, force = true)
                        val refreshedFromDb = viewModel.getUserById(userId)
                        selectedUserUiModel = refreshedFromDb
                        isLoading = false
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                val userUiModel = selectedUserUiModel
                if (userUiModel != null) {
                    LaunchedEffect(userId) {
                        viewModel.loadOsmchaForUser(userId)
                        viewModel.refreshUser(userId, force = false)
                    }

                    val recentChangesets = remember { mutableStateOf<List<ChangesetUiModel>>(emptyList()) }
                    LaunchedEffect(userId) {
                        recentChangesets.value = viewModel.getRecentChangesetsForUser(userId)
                    }

                    UserDetailScreen(
                        user = userUiModel.user,
                        analysis = userUiModel.analysis,
                        osmchaState = uiState.osmchaState,
                        osmchaLikes = uiState.osmchaLikes,
                        osmchaDislikes = uiState.osmchaDislikes,
                        osmchaLastChecked = userUiModel.osmchaLastChecked,
                        lastUpdated = userUiModel.lastUpdated,
                        isOnline = uiState.isOnline,
                        recentChangesets = recentChangesets.value,
                        onOsmchaRefresh = { viewModel.refreshOsmchaForUser(userId) },
                        onNavigateUp = { navController.navigateUp() },
                        onToggleWelcome = {
                            val currentWelcomed = userUiModel.analysis.isWelcomed
                            viewModel.toggleWelcomed(
                                userUiModel.user.id,
                                currentWelcomed
                            )
                            selectedUserUiModel = selectedUserUiModel?.copy(
                                analysis = userUiModel.analysis.copy(isWelcomed = !currentWelcomed)
                            )
                        },
                        onChangesetClick = { changeset ->
                            navController.navigate(ChangesetDetailRoute(changeset.entity.id))
                        },
                        onViewMoreChangesets = {
                            changesetsViewModel.updateUserFilter(userUiModel.user.displayName)
                            navController.navigate(UserListRoute(selectTab = "changesets")) {
                                popUpTo(UserListRoute()) { inclusive = true }
                            }
                        }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        navController.navigateUp()
                    }
                }
            }
        }

        composable<ChangesetDetailRoute> { backStackEntry ->
            val route: ChangesetDetailRoute = backStackEntry.toRoute()
            val changesetId = route.changesetId
            
            val uiState by changesetsViewModel.uiState.collectAsStateWithLifecycle()
            val changeset = uiState.changesets.find { it.entity.id == changesetId }
            
            if (changeset != null) {
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
                        navController.navigate(UserDetailRoute(uid))
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

        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToLicenses = { navController.navigate(LicensesRoute) }
            )
        }

        composable<LicensesRoute> {
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
    onUserClick: (OsmUser, UserAnalysis) -> Unit,
    onChangesetClick: (ChangesetUiModel) -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val nestedNavController = rememberNavController()
    val navBackStackEntry by nestedNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "mappers"
    
    LaunchedEffect(deepLinkChangesetsTab) {
        if (deepLinkChangesetsTab) {
            nestedNavController.navigate("changesets") {
                popUpTo(nestedNavController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
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
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(navController = nestedNavController, startDestination = "mappers") {
                composable("mappers") {
                    UserListScreen(
                        viewModel = viewModel,
                        onUserClick = onUserClick,
                        onNavigateToSettings = onNavigateToSettings
                    )
                }
                composable("changesets") {
                    ChangesetListScreen(
                        viewModel = changesetsViewModel,
                        initialUserFilter = initialUserFilter,
                        onChangesetClick = onChangesetClick
                    )
                }
            }
        }
    }
}
