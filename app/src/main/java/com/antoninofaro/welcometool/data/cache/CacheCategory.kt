package com.antoninofaro.welcometool.data.cache

interface CacheCategory {
    val id: String
    val name: String
    val isEnabled: Boolean
    val sizeBytes: Long
    fun clear()
}
