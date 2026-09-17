package com.gusto.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gusto.app.data.model.Ingredient
import com.gusto.app.data.model.Recipe
import com.gusto.app.data.model.ShoppingItem
import com.gusto.app.data.repository.FridgeRepository
import com.gusto.app.data.repository.RecipeRepository
import com.gusto.app.data.repository.ShoppingListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class RecipeShoppingSuggestion(
    val recipe: Recipe,
    val reason: String,
    val missingIngredients: List<Ingredient>
)

data class ShoppingListUiState(
    val items: List<ShoppingItem> = emptyList(),
    val suggestions: List<RecipeShoppingSuggestion> = emptyList(),
    val inputItemName: String = "",
    val isLoading: Boolean = false,
    val transferredCount: Int? = null,
    val showClearDialog: Boolean = false
)

class ShoppingListViewModel(
    private val shoppingListRepository: ShoppingListRepository,
    private val fridgeRepository: FridgeRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val localItems = shoppingListRepository.getLocalItems()
            _uiState.update { it.copy(items = localItems) }

            generateSuggestions()
            _uiState.update { it.copy(isLoading = false) }

            // Sincroniza em nuvem em segundo plano
            syncCloud()
        }
    }

    private suspend fun syncCloud() = withContext(Dispatchers.IO) {
        val synced = shoppingListRepository.syncWithBackend()
        _uiState.update { it.copy(items = synced) }
    }

    fun onInputNameChange(text: String) {
        _uiState.update { it.copy(inputItemName = text) }
    }

    fun addCustomItem() {
        val text = _uiState.value.inputItemName.trim()
        if (text.isNotBlank()) {
            viewModelScope.launch {
                val item = ShoppingItem(name = text)
                val updated = shoppingListRepository.addItem(item)
                _uiState.update { it.copy(items = updated, inputItemName = "") }
            }
        }
    }

    fun toggleItemChecked(id: String, isChecked: Boolean) {
        viewModelScope.launch {
            val updated = shoppingListRepository.toggleItem(id, isChecked)
            _uiState.update { it.copy(items = updated) }
        }
    }

    fun removeItem(id: String) {
        viewModelScope.launch {
            val updated = shoppingListRepository.removeItem(id)
            _uiState.update { it.copy(items = updated) }
        }
    }

    fun clearCompleted() {
        viewModelScope.launch {
            val updated = shoppingListRepository.clearCompleted()
            _uiState.update { it.copy(items = updated) }
        }
    }

    fun setShowClearDialog(show: Boolean) {
        _uiState.update { it.copy(showClearDialog = show) }
    }

    fun clearAll() {
        viewModelScope.launch {
            val updated = shoppingListRepository.clearAll()
            _uiState.update { it.copy(items = updated, showClearDialog = false) }
        }
    }

    /**
     * Transfere itens marcados como comprados para a "Minha Geladeira"
     */
    fun transferCheckedToFridge() {
        viewModelScope.launch {
            val transferred = shoppingListRepository.transferCheckedToFridge()
            val remaining = shoppingListRepository.getLocalItems()
            _uiState.update {
                it.copy(
                    items = remaining,
                    transferredCount = transferred.size
                )
            }
            // Recalcula sugestões pois a geladeira agora tem novos itens
            generateSuggestions()
        }
    }

    fun dismissTransferredNotification() {
        _uiState.update { it.copy(transferredCount = null) }
    }

    /**
     * Adiciona à lista de compras os ingredientes que faltam para uma receita sugerida
     */
    fun addSuggestedIngredients(suggestion: RecipeShoppingSuggestion) {
        viewModelScope.launch {
            val currentItemNames = _uiState.value.items.map { normalize(it.name) }.toSet()
            val newItems = suggestion.missingIngredients
                .filter { !currentItemNames.contains(normalize(it.name)) }
                .map { ing ->
                    ShoppingItem(
                        name = ing.name,
                        quantity = ing.quantity,
                        unit = ing.unit,
                        sourceRecipeTitle = suggestion.recipe.title
                    )
                }

            if (newItems.isNotEmpty()) {
                val updated = shoppingListRepository.addItems(newItems)
                _uiState.update { it.copy(items = updated) }
                generateSuggestions()
            }
        }
    }

    /**
     * Adiciona todas as sugestões faltantes de uma vez
     */
    fun addAllSuggestions() {
        viewModelScope.launch {
            val currentItemNames = _uiState.value.items.map { normalize(it.name) }.toSet()
            val allNew = mutableListOf<ShoppingItem>()

            for (sug in _uiState.value.suggestions) {
                for (ing in sug.missingIngredients) {
                    val norm = normalize(ing.name)
                    if (!currentItemNames.contains(norm) && allNew.none { normalize(it.name) == norm }) {
                        allNew.add(
                            ShoppingItem(
                                name = ing.name,
                                quantity = ing.quantity,
                                unit = ing.unit,
                                sourceRecipeTitle = sug.recipe.title
                            )
                        )
                    }
                }
            }

            if (allNew.isNotEmpty()) {
                val updated = shoppingListRepository.addItems(allNew)
                _uiState.update { it.copy(items = updated) }
                generateSuggestions()
            }
        }
    }

    private suspend fun generateSuggestions() = withContext(Dispatchers.IO) {
        val fridgeItems = fridgeRepository.getLocalIngredients().map { normalize(it) }
        val currentShoppingNames = shoppingListRepository.getLocalItems().map { normalize(it.name) }.toSet()

        // 1. Receitas mais cozinhadas
        val mostCooked = shoppingListRepository.getMostCookedRecipes()

        // 2. Receitas que estão há mais tempo nos favoritos
        val oldestFavorites = shoppingListRepository.getFavoriteRecipesByOldest()

        // 3. Fallback: Se não houver histórico, pega os favoritos gerais ou as receitas em destaque
        val fallbackRecipes = if (mostCooked.isEmpty() && oldestFavorites.isEmpty()) {
            recipeRepository.getFavoriteRecipes().ifEmpty {
                recipeRepository.getRecipes(page = 1).getOrDefault(emptyList()).take(5)
            }
        } else {
            emptyList()
        }

        val suggestionsList = mutableListOf<RecipeShoppingSuggestion>()
        val processedRecipeIds = mutableSetOf<String>()

        // Processa receitas mais cozinhadas
        for (recipe in mostCooked) {
            if (processedRecipeIds.add(recipe.id)) {
                val missing = findMissingIngredients(recipe, fridgeItems, currentShoppingNames)
                if (missing.isNotEmpty()) {
                    suggestionsList.add(
                        RecipeShoppingSuggestion(
                            recipe = recipe,
                            reason = "Receita que você mais cozinha",
                            missingIngredients = missing
                        )
                    )
                }
            }
        }

        // Processa favoritos há mais tempo
        for (recipe in oldestFavorites) {
            if (processedRecipeIds.add(recipe.id)) {
                val missing = findMissingIngredients(recipe, fridgeItems, currentShoppingNames)
                if (missing.isNotEmpty()) {
                    suggestionsList.add(
                        RecipeShoppingSuggestion(
                            recipe = recipe,
                            reason = "Favorito guardado há mais tempo",
                            missingIngredients = missing
                        )
                    )
                }
            }
        }

        // Processa fallback
        for (recipe in fallbackRecipes) {
            if (processedRecipeIds.add(recipe.id)) {
                val missing = findMissingIngredients(recipe, fridgeItems, currentShoppingNames)
                if (missing.isNotEmpty()) {
                    suggestionsList.add(
                        RecipeShoppingSuggestion(
                            recipe = recipe,
                            reason = "Receita do seu cardápio",
                            missingIngredients = missing
                        )
                    )
                }
            }
        }

        _uiState.update { it.copy(suggestions = suggestionsList.take(6)) }
    }

    private fun findMissingIngredients(
        recipe: Recipe,
        fridgeItems: List<String>,
        shoppingItems: Set<String>
    ): List<Ingredient> {
        return recipe.ingredients.filter { ing ->
            val normName = normalize(ing.name)
            // Se já tem na geladeira ou já está na lista de compras ativa, não é faltante
            val inFridge = fridgeItems.any { fridgeItem -> isIngredientMatch(fridgeItem, normName) }
            val inShopping = shoppingItems.any { shopItem -> isIngredientMatch(shopItem, normName) }
            !inFridge && !inShopping
        }
    }

    private fun normalize(str: String): String {
        val unaccented = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
        return if (unaccented.endsWith("s") && unaccented.length > 3) {
            unaccented.substring(0, unaccented.length - 1)
        } else {
            unaccented
        }
    }

    private fun isIngredientMatch(userIng: String, recipeIng: String): Boolean {
        if (userIng.isEmpty() || recipeIng.isEmpty()) return false
        if (userIng == recipeIng) return true
        if (recipeIng.contains(userIng) || userIng.contains(recipeIng)) return true
        return false
    }
}
