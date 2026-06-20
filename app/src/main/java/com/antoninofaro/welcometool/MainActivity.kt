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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antoninofaro.welcometool.ui.screens.MainViewModel
import com.antoninofaro.welcometool.ui.screens.LicensesScreen
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

                val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                // ponytail: one-shot rationale per process start, no persistent flag needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    var showRationale by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            showRationale = true
                        }
                    }
                    if (showRationale) {
                        AlertDialog(
                            onDismissRequest = { showRationale = false },
                            title = { Text("Notifiche") },
                            text = { Text("Abilita le notifiche per ricevere avvisi quando qualcuno modifica l'area monitorata o quando si rileva un nuovo mappatore.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showRationale = false
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }) { Text("OK") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRationale = false }) { Text("Non ora") }
                            }
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(viewModel, settingsViewModel)
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
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
