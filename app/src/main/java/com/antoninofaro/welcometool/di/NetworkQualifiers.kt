package com.antoninofaro.welcometool.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OsmClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OsmChaClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NominatimClient
