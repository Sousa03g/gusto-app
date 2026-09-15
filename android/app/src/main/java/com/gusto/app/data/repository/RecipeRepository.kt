package com.gusto.app.data.repository

import android.content.Context
import com.gusto.app.data.api.GustoApiService
import com.gusto.app.data.api.NetworkModule
import com.gusto.app.data.model.*

class RecipeRepository(context: Context) {
    private val api: GustoApiService = NetworkModule.provideApiService(context)

    suspend fun getRecipes(
        search: String? = null,
        category: String? = null,
        maxPrepTime: Int? = null,
        page: Int = 1
    ): Result<List<Recipe>> {
        return try {
            val response = api.getRecipes(
                search = search?.ifBlank { null },
                category = category?.ifBlank { null },
                maxPrepTime = maxPrepTime,
                page = page
            )
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.data)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Erro ao carregar receitas"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecipeById(id: String): Result<Recipe> {
        return try {
            val response = api.getRecipeById(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Receita não encontrada"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRecipe(request: CreateRecipeRequest): Result<Recipe> {
        return try {
            val response = api.createRecipe(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Falha ao criar receita"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecipe(id: String): Result<Unit> {
        return try {
            val response = api.deleteRecipe(id)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Falha ao deletar receita"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
