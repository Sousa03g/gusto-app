package com.gusto.app.data.api

import com.gusto.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

interface GustoApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<AuthResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<ApiResponse<Unit>>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<ApiResponse<Unit>>

    @GET("api/recipes")
    suspend fun getRecipes(
        @Query("search") search: String? = null,
        @Query("category") category: String? = null,
        @Query("maxPrepTime") maxPrepTime: Int? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("includeExternal") includeExternal: Boolean = true
    ): Response<RecipeListResponse>

    @GET("api/recipes/{id}")
    suspend fun getRecipeById(@Path("id") id: String): Response<Recipe>

    @POST("api/recipes")
    suspend fun createRecipe(@Body request: CreateRecipeRequest): Response<Recipe>

    @DELETE("api/recipes/{id}")
    suspend fun deleteRecipe(@Path("id") id: String): Response<ApiResponse<Unit>>

    @GET("api/recipes/discover")
    suspend fun discoverExternalRecipes(
        @Query("q") query: String? = null,
        @Query("category") category: String? = null
    ): Response<RecipeListResponse>
}
