package com.antoninofaro.welcometool

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.antoninofaro.welcometool.data.repository.SettingsRepository
import com.antoninofaro.welcometool.di.ApplicationScope
import com.antoninofaro.welcometool.utils.LogCaptureManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class WelcomeToolApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var logCaptureManager: LogCaptureManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Pianta il LogCaptureTree per catturare i log in memoria
        Timber.plant(logCaptureManager.getLogCaptureTree())

        appScope.launch {
            logCaptureManager.setEnabled(settingsRepository.settingsFlow.first().debugLogsEnabled)
        }

        Timber.d("WelcomeToolApplication initialized")
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Nuovi Mappatori"
            val descriptionText = "Avvisa se ci sono stati rilevati nuovi utenti"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("NEW_MAPPER_CHANNEL", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
