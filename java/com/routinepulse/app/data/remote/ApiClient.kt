package com.routinepulse.app.data.remote

import android.util.Log
import com.routinepulse.app.BuildConfig
import com.routinepulse.app.data.local.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the Retrofit [ApiService] pointed at the hosted API (BuildConfig.API_BASE_URL). */
object ApiClient {
    private const val TAG = "ApiClient"

    fun create(settings: SettingsStore): ApiService {
        // Adds the bearer token to every request and logs the user out if the server says 401.
        val authInterceptor = Interceptor { chain ->
            val token = runBlocking { settings.tokenFlow.first() }
            val request = chain.request().newBuilder().header("Accept", "application/json")
            if (!token.isNullOrBlank()) request.header("Authorization", "Bearer $token")
            val response = chain.proceed(request.build())
            val path = chain.request().url.encodedPath
            if (response.code == 401 && !path.contains("/api/auth/")) {
                Log.w(TAG, "Session rejected by server (401) on $path - clearing session")
                runBlocking { settings.clearSession() }
            }
            response
        }

        // BASIC level logs method/URL/status only - never request bodies, so passwords stay out of Logcat.
        val logging = HttpLoggingInterceptor { message -> Log.d(TAG, message) }.apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }

        // Generous timeouts: Render's free tier sleeps and can take ~50s to wake on the first request.
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val baseUrl = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        Log.i(TAG, "API base URL: $baseUrl")

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
