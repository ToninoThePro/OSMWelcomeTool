package com.antoninofaro.welcometool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antoninofaro.welcometool.ui.screens.DebugLogsScreen
import com.antoninofaro.welcometool.ui.screens.MainViewModel
import com.antoninofaro.welcometool.ui.screens.SettingsScreen
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel
import com.antoninofaro.welcometool.ui.screens.UserDetailScreen
import com.antoninofaro.welcometool.ui.screens.UserListScreen
import com.antoninofaro.welcometool.ui.theme.WelcomeToolTheme
import com.antoninofaro.welcometool.utils.LogCaptureManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    @Inject
    lateinit var logCaptureManager: LogCaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WelcomeToolTheme {
                val context = LocalContext.current
                val settings by settingsViewModel.settings.collectAsState()

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

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel, settingsViewModel, logCaptureManager)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    logCaptureManager: LogCaptureManager
) {
    val navController = rememberNavController()
    val uiState by viewModel.uiState.collectAsState()

    NavHost(navController = navController, startDestination = "user_list") {

        composable("user_list") {
            UserListScreen(
                viewModel = viewModel,
                onUserClick = { user, _ ->
                    navController.navigate("user_detail/${user.id}")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("user_detail/{userId}") { backStackEntry ->
            val userIdStr = backStackEntry.arguments?.getString("userId")
            val userId = userIdStr?.toLongOrNull()

            val selectedUserUiModel = uiState.filteredUsers.find { it.user.id == userId }
                ?: uiState.users.find { it.user.id == userId }

            if (selectedUserUiModel != null && userId != null) {
                LaunchedEffect(userId) {
                    viewModel.loadOsmchaForUser(userId)
                    viewModel.refreshUser(userId)
                }
                UserDetailScreen(
                    user = selectedUserUiModel.user,
                    analysis = selectedUserUiModel.analysis,
                    onNavigateUp = { navController.navigateUp() },
                    onToggleWelcome = {
                        viewModel.toggleWelcomed(
                            selectedUserUiModel.user.id,
                            selectedUserUiModel.analysis.isWelcomed
                        )
                    }
                )
            } else {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    navController.navigateUp()
                }
            }
        }

        composable("settings") {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateUp = { navController.navigateUp() },
                onNavigateToDebugLogs = { navController.navigate("debug_logs") }
            )
        }

        composable("debug_logs") {
            DebugLogsScreen(
                logCaptureManager = logCaptureManager,
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}
