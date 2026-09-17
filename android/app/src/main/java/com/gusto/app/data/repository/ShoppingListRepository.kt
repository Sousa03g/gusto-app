package com.gusto.app.data.repository

import android.content.Context
import com.gusto.app.data.api.GustoApiService
import com.gusto.app.data.api.NetworkModule
import com.gusto.app.data.local.GustoLocalDatabase
import com.gusto.app.data.model.Recipe
import com.gusto.app.data.model.ShoppingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ShoppingListRepository(context: Context) {
    private val localDb: GustoLocalDatabase = GustoLocalDatabase.getInstance(context)
    private val api: GustoApiService = NetworkModule.provideApiService(context)

    fun getLocalItems(): List<ShoppingItem> {
        return localDb.getShoppingItems()
    }

    suspend fun addItem(item: ShoppingItem): List<ShoppingItem> = withContext(Dispatchers.IO) {
        localDb.addShoppingItem(item)
        try {
            api.addShoppingItem(
                mapOf(
                    "name" to item.name,
                    "quantity" to item.quantity,
                    "unit" to item.unit,
                    "sourceRecipeTitle" to item.sourceRecipeTitle
                )
            )
        } catch (_: Exception) {}
        localDb.getShoppingItems()
    }

    suspend fun addItems(items: List<ShoppingItem>): List<ShoppingItem> = withContext(Dispatchers.IO) {
        localDb.addShoppingItems(items)
        try {
            val payload = items.map {
                mapOf(
                    "name" to it.name,
                    "quantity" to it.quantity,
                    "unit" to it.unit,
                    "sourceRecipeTitle" to it.sourceRecipeTitle
                )
            }
            api.batchAddShoppingItems(mapOf("items" to payload))
        } catch (_: Exception) {}
        localDb.getShoppingItems()
    }

    suspend fun toggleItem(id: String, isChecked: Boolean): List<ShoppingItem> = withContext(Dispatchers.IO) {
        localDb.toggleShoppingItem(id, isChecked)
        try {
            api.toggleShoppingItem(mapOf("id" to id, "isChecked" to isChecked))
        } catch (_: Exception) {}
        localDb.getShoppingItems()
    }

    suspend fun removeItem(id: String): List<ShoppingItem> = withContext(Dispatchers.IO) {
        localDb.removeShoppingItem(id)
        try {
            api.deleteShoppingItem(id = id)
        } catch (_: Exception) {}
        localDb.getShoppingItems()
    }

    suspend fun clearCompleted(): List<ShoppingItem> = withContext(Dispatchers.IO) {
        localDb.clearCompletedShoppingItems()
        try {
            api.deleteShoppingItem(clearCompleted = true)
        } catch (_: Exception) {}
        localDb.getShoppingItems()
    }

    suspend fun clearAll(): List<ShoppingItem> = withContext(Dispatchers.IO) {
        localDb.clearAllShoppingItems()
        try {
            api.deleteShoppingItem(clearAll = true)
        } catch (_: Exception) {}
        emptyList()
    }

    /**
     * Transfere todos os itens comprados (marcados com check) para a tabela da geladeira no SQLite
     * e os remove da lista de compras. Sincroniza ambos com a nuvem em background.
     */
    suspend fun transferCheckedToFridge(): List<String> = withContext(Dispatchers.IO) {
        val transferred = localDb.transferCheckedItemsToFridge()
        try {
            // Sincroniza a geladeira com os novos itens na nuvem
            for (name in transferred) {
                api.addFridgeItem(mapOf("name" to name))
            }
            // Remove os concluídos da lista na nuvem
            api.deleteShoppingItem(clearCompleted = true)
        } catch (_: Exception) {}
        transferred
    }

    suspend fun syncWithBackend(): List<ShoppingItem> = withContext(Dispatchers.IO) {
        try {
            val response = api.getShoppingItems()
            if (response.isSuccessful && response.body()?.data != null) {
                val backendItems = response.body()!!.data!!
                val localItems = localDb.getShoppingItems()
                val existingIds = localItems.map { it.id }.toSet()

                val newFromBackend = backendItems.filter { !existingIds.contains(it.id) }
                if (newFromBackend.isNotEmpty()) {
                    localDb.addShoppingItems(newFromBackend)
                }
            }
        } catch (_: Exception) {}
        localDb.getShoppingItems()
    }

    fun getFavoriteRecipesByOldest(): List<Recipe> = localDb.getFavoriteRecipesByOldest()

    fun getMostCookedRecipes(): List<Recipe> = localDb.getMostCookedRecipes(10)
}
