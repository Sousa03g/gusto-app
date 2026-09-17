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

    @GET("api/fridge")
    suspend fun getFridgeItems(): Response<ApiResponse<List<String>>>

    @POST("api/fridge")
    suspend fun addFridgeItem(@Body body: Map<String, String>): Response<ApiResponse<List<String>>>

    @POST("api/fridge")
    suspend fun syncFridgeItems(@Body body: Map<String, List<String>>): Response<ApiResponse<List<String>>>

    @DELETE("api/fridge")
    suspend fun deleteFridgeItem(
        @Query("name") name: String? = null,
        @Query("clear") clear: Boolean? = null
    ): Response<ApiResponse<List<String>>>

    @GET("api/shopping-list")
    suspend fun getShoppingItems(): Response<ApiResponse<List<ShoppingItem>>>

    @POST("api/shopping-list")
    suspend fun addShoppingItem(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<ApiResponse<ShoppingItem>>

    @POST("api/shopping-list")
    suspend fun batchAddShoppingItems(@Body body: Map<String, @JvmSuppressWildcards List<Map<String, Any?>>>): Response<ApiResponse<List<ShoppingItem>>>

    @PATCH("api/shopping-list")
    suspend fun toggleShoppingItem(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<ApiResponse<Any>>

    @DELETE("api/shopping-list")
    suspend fun deleteShoppingItem(
        @Query("id") id: String? = null,
        @Query("clearCompleted") clearCompleted: Boolean? = null,
        @Query("clearAll") clearAll: Boolean? = null
    ): Response<ApiResponse<Any>>
}

