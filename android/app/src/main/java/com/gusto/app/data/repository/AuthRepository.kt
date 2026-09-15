package com.gusto.app.data.repository

import android.content.Context
import com.gusto.app.data.api.AuthInterceptor
import com.gusto.app.data.api.GustoApiService
import com.gusto.app.data.api.NetworkModule
import com.gusto.app.data.model.*

class AuthRepository(context: Context) {
    private val api: GustoApiService = NetworkModule.provideApiService(context)
    private val interceptor: AuthInterceptor = NetworkModule.getAuthInterceptor(context)

    fun isLoggedIn(): Boolean = !interceptor.getAccessToken().isNullOrEmpty()

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                interceptor.saveTokens(body.accessToken, body.refreshToken)
                Result.success(body.user ?: User(id = "", name = "Chef", email = email))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Falha ao realizar login"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(name: String, email: String, password: String): Result<User> {
        return try {
            val response = api.register(RegisterRequest(name, email, password))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                interceptor.saveTokens(body.accessToken, body.refreshToken)
                Result.success(body.user ?: User(id = "", name = name, email = email))
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Falha ao cadastrar"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun forgotPassword(email: String): Result<String> {
        return try {
            val response = api.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "Código enviado com sucesso")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Erro ao solicitar redefinição"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(email: String, otp: String, newPassword: String): Result<String> {
        return try {
            val response = api.resetPassword(ResetPasswordRequest(email, otp, newPassword))
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "Senha redefinida com sucesso")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Código inválido ou erro"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logout() {
        interceptor.clearTokens()
    }

    fun getSavedTheme(): ThemeMode {
        return when (interceptor.getThemePreference()) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            "OLED_BLACK" -> ThemeMode.OLED_BLACK
            else -> ThemeMode.SYSTEM
        }
    }

    fun saveTheme(theme: ThemeMode) {
        interceptor.saveThemePreference(theme.name)
    }
}
