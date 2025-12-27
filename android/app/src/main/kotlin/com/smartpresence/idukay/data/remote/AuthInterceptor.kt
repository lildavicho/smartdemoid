package com.smartpresence.idukay.data.remote

import android.content.Context
import com.smartpresence.idukay.data.local.AuthPreferences
import com.smartpresence.idukay.data.local.authDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    @ApplicationContext private val context: Context
) : Interceptor {
    
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking {
            context.authDataStore.data.map { preferences ->
                preferences[AuthPreferences.TOKEN_KEY]
            }.first()
        }
        
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        
        return chain.proceed(request)
    }
}
