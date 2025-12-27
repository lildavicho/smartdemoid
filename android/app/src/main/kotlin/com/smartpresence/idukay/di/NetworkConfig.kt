package com.smartpresence.idukay.di

import com.smartpresence.idukay.BuildConfig

object NetworkConfig {
    val baseUrl: String by lazy { ensureTrailingSlash(BuildConfig.API_BASE_URL.trim()) }

    private fun ensureTrailingSlash(url: String): String {
        return if (url.endsWith("/")) url else "$url/"
    }
}

