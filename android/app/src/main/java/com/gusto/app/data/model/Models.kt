package com.gusto.app.data.model

import com.google.gson.annotations.SerializedName

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    OLED_BLACK
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val themePreference: String? = "SYSTEM",
    val avatarUrl: String? = null
)

data class AuthResponse(
    val message: String? = null,
    val user: User? = null,
    val accessToken: String,
    val refreshToken: String
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String
)

data class ApiResponse<T>(
    val message: String? = null,
    val error: String? = null,
    val data: T? = null
)

data class RecipeListResponse(
    val data: List<Recipe>,
    val meta: MetaInfo? = null
)

data class MetaInfo(
    val page: Int,
    val limit: Int,
    val totalLocal: Int,
    val hasMore: Boolean
)

data class Recipe(
    val id: String,
    val title: String,
    val description: String? = null,
    val prepTimeMin: Int = 30,
    val servings: Int = 2,
    val category: String = "Geral",
    val imageUrl: String? = null,
    val source: String = "GUSTO_USER",
    val author: String? = null,
    val createdAt: String? = null,
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<RecipeStep> = emptyList()
)

data class Ingredient(
    val name: String,
    val quantity: Double,
    val unit: String
)

data class RecipeStep(
    val orderNumber: Int,
    val instruction: String,
    var isCompleted: Boolean = false
)

data class CreateRecipeRequest(
    val title: String,
    val description: String? = null,
    val prepTimeMin: Int,
    val servings: Int,
    val category: String,
    val imageUrl: String? = null,
    val ingredients: List<Ingredient>,
    val steps: List<CreateStepRequest>
)

data class CreateStepRequest(
    val orderNumber: Int,
    val instruction: String
)

data class ShoppingItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    var isChecked: Boolean = false,
    val sourceRecipeTitle: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

