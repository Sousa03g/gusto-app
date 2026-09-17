package com.gusto.app.data.repository

import android.content.Context
import com.gusto.app.data.api.GustoApiService
import com.gusto.app.data.api.NetworkModule
import com.gusto.app.data.local.GustoLocalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FridgeRepository(context: Context) {
    private val localDb: GustoLocalDatabase = GustoLocalDatabase.getInstance(context)
    private val api: GustoApiService = NetworkModule.provideApiService(context)

    /**
     * Retorna os ingredientes salvos no banco SQLite local de forma instantânea.
     */
    fun getLocalIngredients(): List<String> {
        return localDb.getFridgeItems()
    }

    /**
     * Salva o ingrediente no banco SQLite imediatamente e sincroniza com a API/Postgres se online.
     */
    suspend fun addIngredient(name: String): List<String> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank()) {
            localDb.addFridgeItem(cleanName)
            try {
                api.addFridgeItem(mapOf("name" to cleanName))
            } catch (_: Exception) {
                // Se offline ou não autenticado, os dados estão 100% seguros no SQLite local
            }
        }
        localDb.getFridgeItems()
    }

    /**
     * Remove o ingrediente do SQLite e sincroniza remoção com a nuvem.
     */
    suspend fun removeIngredient(name: String): List<String> = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isNotBlank()) {
            localDb.removeFridgeItem(cleanName)
            try {
                api.deleteFridgeItem(name = cleanName)
            } catch (_: Exception) {
                // Continua seguro localmente
            }
        }
        localDb.getFridgeItems()
    }

    /**
     * Limpa todos os itens da geladeira localmente e na nuvem.
     */
    suspend fun clearFridge(): List<String> = withContext(Dispatchers.IO) {
        localDb.clearFridgeItems()
        try {
            api.deleteFridgeItem(clear = true)
        } catch (_: Exception) {
            // Continua seguro localmente
        }
        emptyList()
    }

    /**
     * Sincroniza dados com o backend se o usuário estiver autenticado e online.
     * Caso o backend possua itens, mescla e atualiza o banco local.
     */
    suspend fun syncWithBackend(): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.getFridgeItems()
            if (response.isSuccessful && response.body()?.data != null) {
                val backendItems = response.body()!!.data!!
                val localItems = localDb.getFridgeItems()

                // União de itens locais e remotos
                val combined = (localItems + backendItems).distinctBy { it.trim().lowercase() }
                localDb.saveFridgeItems(combined)

                // Se houveram itens locais novos, sincroniza em lote de volta ao backend
                if (combined.size > backendItems.size) {
                    api.syncFridgeItems(mapOf("items" to combined))
                }
                return@withContext combined
            }
        } catch (_: Exception) {
            // Se falhar ou estiver offline, usa a persistência local
        }
        localDb.getFridgeItems()
    }
}
