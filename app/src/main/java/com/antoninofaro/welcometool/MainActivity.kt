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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.antoninofaro.welcometool.ui.navigation.AppNavigation
import com.antoninofaro.welcometool.ui.screens.ChangesetsViewModel
import com.antoninofaro.welcometool.ui.screens.LicensesScreen
import com.antoninofaro.welcometool.ui.screens.MainViewModel
import com.antoninofaro.welcometool.ui.screens.OnboardingScreen
import com.antoninofaro.welcometool.ui.screens.SettingsScreen
import com.antoninofaro.welcometool.ui.screens.SettingsViewModel
import com.antoninofaro.welcometool.ui.screens.UserDetailScreen
import com.antoninofaro.welcometool.ui.screens.UserListScreen
import com.antoninofaro.welcometool.ui.screens.UserUiModel
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
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }

            WelcomeToolTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor
            ) {

                LaunchedEffect(settings.isOnboardingCompleted, settings.autoRefresh, settings.autoRefreshInterval) {
                    com.antoninofaro.welcometool.utils.WorkerUtils.scheduleOsmSyncWorker(
                        context,
                        settings.autoRefreshInterval,
                        settings.autoRefresh && settings.isOnboardingCompleted
                    )
                }

                LaunchedEffect(settings.isLoaded, settings.defaultBBox) {
                    if (settings.isLoaded) {
                        viewModel.updateBBox(settings.defaultBBox)
                    }
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

