package com.gusto.app.data.api

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    // Production Vercel URL
    private const val BASE_URL = "https://backend-blond-two-95.vercel.app/"

    private var apiServiceInstance: GustoApiService? = null
    private var authInterceptorInstance: AuthInterceptor? = null

    fun getAuthInterceptor(context: Context): AuthInterceptor {
        return authInterceptorInstance ?: synchronized(this) {
            authInterceptorInstance ?: AuthInterceptor(context.applicationContext).also {
                authInterceptorInstance = it
            }
        }
    }

    fun provideApiService(context: Context): GustoApiService {
        return apiServiceInstance ?: synchronized(this) {
            val interceptor = getAuthInterceptor(context)

            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(GustoApiService::class.java)
            apiServiceInstance = service
            service
        }
    }
}
