package com.antoninofaro.welcometool.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class NotifiedDataStore
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class WelcomedDataStore

private val Context.notifiedDataStore: DataStore<Preferences> by preferencesDataStore(name = "osm_notified_registry")
private val Context.welcomedDataStore: DataStore<Preferences> by preferencesDataStore(name = "osm_welcomed_registry")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @NotifiedDataStore
    @Provides @Singleton
    fun provideNotifiedDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.notifiedDataStore

    @WelcomedDataStore
    @Provides @Singleton
    fun provideWelcomedDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.welcomedDataStore
}
