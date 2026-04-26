package com.antoninofaro.welcometool.di

import com.antoninofaro.welcometool.data.repository.OsmChaRepository
import com.antoninofaro.welcometool.data.repository.OsmChaRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOsmChaRepository(
        osmChaRepositoryImpl: OsmChaRepositoryImpl
    ): OsmChaRepository
}