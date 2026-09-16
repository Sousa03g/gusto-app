package com.gusto.app.data.api

import android.content.Context
import com.google.gson.Gson
import com.gusto.app.data.model.AuthResponse
import com.gusto.app.data.model.RefreshRequest
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class TokenAuthenticator(
    private val context: Context,
    private val authInterceptor: AuthInterceptor,
    private val baseUrl: String
) : Authenticator {

    private val lock = Any()
    private val gson = Gson()

    override fun authenticate(route: Route?, response: Response): Request? {
        // Prevenir loops infinitos se falhar repetidamente
        if (responseCount(response) >= 3) {
            return null
        }

        synchronized(lock) {
            val currentAccessToken = authInterceptor.getAccessToken()
            val requestToken = response.request.header("Authorization")?.replace("Bearer ", "")

            // Se outro thread já atualizou o token, apenas reenvia com o novo token
            if (currentAccessToken != null && currentAccessToken != requestToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentAccessToken")
                    .build()
            }

            val refreshToken = authInterceptor.getRefreshToken() ?: return null

            // Fazer chamada direta de refresh sem passar pelo interceptor para evitar deadlock
            val client = OkHttpClient()
            val jsonMedia = "application/json; charset=utf-8".toMediaType()
            val requestBody = gson.toJson(RefreshRequest(refreshToken)).toRequestBody(jsonMedia)

            val refreshRequest = Request.Builder()
                .url("${baseUrl}api/auth/refresh")
                .post(requestBody)
                .build()

            try {
                val refreshResponse = client.newCall(refreshRequest).execute()
                if (refreshResponse.isSuccessful) {
                    val responseBody = refreshResponse.body?.string()
                    val authData = gson.fromJson(responseBody, AuthResponse::class.java)

                    if (authData != null && !authData.accessToken.isNullOrEmpty()) {
                        authInterceptor.saveTokens(
                            authData.accessToken,
                            authData.refreshToken ?: refreshToken
                        )

                        return response.request.newBuilder()
                            .header("Authorization", "Bearer ${authData.accessToken}")
                            .build()
                    }
                } else {
                    // Refresh token inválido ou revogado
                    authInterceptor.clearTokens()
                }
            } catch (e: Exception) {
                // Erro de rede durante refresh
            }

            return null
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
