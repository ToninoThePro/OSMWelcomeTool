package com.antoninofaro.welcometool

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.antoninofaro.welcometool.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import coil3.ImageLoader
import coil3.SingletonImageLoader
import javax.inject.Inject

@HiltAndroidApp
class WelcomeToolApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var imageLoader: ImageLoader

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return imageLoader
    }

    override fun onCreate() {
        super.onCreate()

        notificationHelper.createNotificationChannel()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("WelcomeToolApplication initialized")
    }
}
