package com.examshield.data.remote

import android.content.Context
import com.examshield.utils.Constants
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val DEFAULT_URL = Constants.BASE_URL

    private var currentUrl = DEFAULT_URL
    private var retrofit: Retrofit? = null
    private var _apiService: ApiService? = null
    private var initialized = false

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .build()

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(
            Constants.PREF_NAME,
            Context.MODE_PRIVATE
        )
        val savedUrl = prefs.getString(Constants.PREF_BACKEND_URL, null)
        currentUrl = if (!savedUrl.isNullOrBlank()) savedUrl else DEFAULT_URL
        retrofit = null
        _apiService = null
        initialized = true
    }

    fun updateBaseUrl(context: Context, newUrl: String) {
        val normalizedUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        val prefs = context.getSharedPreferences(
            Constants.PREF_NAME,
            Context.MODE_PRIVATE
        )
        prefs.edit()
            .putString(Constants.PREF_BACKEND_URL, normalizedUrl)
            .apply()
        currentUrl = normalizedUrl
        retrofit = null
        _apiService = null
    }

    fun updateBaseUrl(newUrl: String) {
        currentUrl = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        retrofit = null
        _apiService = null
    }

    fun getBaseUrl(): String = currentUrl

    private fun getRetrofit(): Retrofit {
        return retrofit ?: Retrofit.Builder()
            .baseUrl(currentUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .also { retrofit = it }
    }

    val apiService: ApiService
        get() = _apiService ?: getRetrofit().create(ApiService::class.java).also { _apiService = it }
}
