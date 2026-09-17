package com.gusto.app.data.repository

import android.content.Context
import com.gusto.app.data.api.GustoApiService
import com.gusto.app.data.api.NetworkModule
import com.gusto.app.data.local.GustoLocalDatabase
import com.gusto.app.data.model.*

class RecipeRepository(context: Context) {
    private val api: GustoApiService = NetworkModule.provideApiService(context)
    private val localDb: GustoLocalDatabase = GustoLocalDatabase.getInstance(context)

    suspend fun getRecipes(
        search: String? = null,
        category: String? = null,
        maxPrepTime: Int? = null,
        page: Int = 1,
        onlyFavorites: Boolean = false
    ): Result<List<Recipe>> {
        if (onlyFavorites) {
            val favorites = localDb.getFavoriteRecipes()
            return Result.success(favorites)
        }

        return try {
            val response = api.getRecipes(
                search = search?.ifBlank { null },
                category = category?.ifBlank { null },
                maxPrepTime = maxPrepTime,
                page = page
            )
            if (response.isSuccessful && response.body() != null) {
                val recipes = response.body()!!.data
                // Salvar receitas no cache local para navegação offline
                localDb.saveRecipes(recipes)
                Result.success(recipes)
            } else {
                // Fallback offline caso a API responda erro
                val cached = localDb.getAllCachedRecipes()
                if (cached.isNotEmpty()) {
                    Result.success(filterLocalRecipes(cached, search, category))
                } else {
                    Result.failure(Exception(response.errorBody()?.string() ?: "Erro ao carregar receitas"))
                }
            }
        } catch (e: Exception) {
            // Em caso de falha de conexão (offline), carrega do cache local
            val cached = localDb.getAllCachedRecipes()
            if (cached.isNotEmpty()) {
                Result.success(filterLocalRecipes(cached, search, category))
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecipeById(id: String): Result<Recipe> {
        return try {
            val response = api.getRecipeById(id)
            if (response.isSuccessful && response.body() != null) {
                val recipe = response.body()!!
                localDb.saveRecipe(recipe)
                Result.success(recipe)
            } else {
                val localRecipe = localDb.getRecipe(id)
                if (localRecipe != null) {
                    Result.success(localRecipe)
                } else {
                    Result.failure(Exception("Receita não encontrada"))
                }
            }
        } catch (e: Exception) {
            val localRecipe = localDb.getRecipe(id)
            if (localRecipe != null) {
                Result.success(localRecipe)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun createRecipe(request: CreateRecipeRequest): Result<Recipe> {
        return try {
            val response = api.createRecipe(request)
            if (response.isSuccessful && response.body() != null) {
                val created = response.body()!!
                localDb.saveRecipe(created)
                Result.success(created)
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

    // Gerenciamento de Favoritos e Cache Local
    fun isFavorite(id: String): Boolean = localDb.isFavorite(id)

    fun toggleFavorite(recipe: Recipe): Boolean = localDb.toggleFavorite(recipe)

    fun getFavoriteIds(): Set<String> = localDb.getFavoriteIds()

    fun getFavoriteRecipes(): List<Recipe> = localDb.getFavoriteRecipes()

    fun recordRecipeCooked(id: String) {
        localDb.incrementCookCount(id)
    }

    fun getFavoriteRecipesByOldest(): List<Recipe> = localDb.getFavoriteRecipesByOldest()

    fun getMostCookedRecipes(limit: Int = 10): List<Recipe> = localDb.getMostCookedRecipes(limit)

    private fun filterLocalRecipes(
        recipes: List<Recipe>,
        search: String?,
        category: String?
    ): List<Recipe> {
        var result = recipes
        if (!search.isNullOrBlank()) {
            val q = search.trim().lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                it.category.lowercase().contains(q) ||
                it.ingredients.any { ing -> ing.name.lowercase().contains(q) }
            }
        }
        if (!category.isNullOrBlank() && category != "Todos") {
            val cat = category.trim().lowercase()
            result = result.filter { it.category.lowercase().contains(cat) }
        }
        return result
    }
}
