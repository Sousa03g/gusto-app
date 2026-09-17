package com.gusto.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gusto.app.data.model.Ingredient
import com.gusto.app.data.model.Recipe
import com.gusto.app.data.repository.FridgeRepository
import com.gusto.app.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class RecipeMatch(
    val recipe: Recipe,
    val matchedIngredients: List<String>,
    val missingIngredients: List<Ingredient>,
    val matchPercentage: Int,
    val canMakeNow: Boolean
)

enum class FridgeFilter(val label: String) {
    ALL("Todas as sugestões"),
    READY_NOW("Prontas para fazer (100%)"),
    MISSING_FEW("Faltam poucos (1-2)")
}

data class FridgeUiState(
    val ingredients: List<String> = emptyList(),
    val inputIngredient: String = "",
    val suggestedRecipes: List<RecipeMatch> = emptyList(),
    val filteredRecipes: List<RecipeMatch> = emptyList(),
    val selectedFilter: FridgeFilter = FridgeFilter.ALL,
    val isLoading: Boolean = false,
    val popularSuggestions: List<String> = listOf(
        "Ovo", "Frango", "Cebola", "Alho", "Tomate", "Queijo",
        "Arroz", "Macarrão", "Batata", "Leite", "Manteiga",
        "Azeite", "Carne Moída", "Cenoura", "Farinha de Trigo"
    ),
    val showClearDialog: Boolean = false
)

class FridgeViewModel(
    private val fridgeRepository: FridgeRepository,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FridgeUiState())
    val uiState: StateFlow<FridgeUiState> = _uiState.asStateFlow()

    private var allAvailableRecipes: List<Recipe> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Carrega ingredientes do banco SQLite local de forma instantânea
            val localItems = fridgeRepository.getLocalIngredients()
            _uiState.update { it.copy(ingredients = localItems, isLoading = true) }

            // Carrega receitas disponíveis (cache local / API)
            fetchRecipes()

            // Sincroniza em segundo plano com a nuvem se autenticado
            syncBackend()
        }
    }

    private suspend fun fetchRecipes() = withContext(Dispatchers.IO) {
        val result = recipeRepository.getRecipes(page = 1)
        result.onSuccess { list ->
            allAvailableRecipes = list
            calculateMatches()
        }.onFailure {
            // Fallback se necessário
            calculateMatches()
        }
    }

    private suspend fun syncBackend() = withContext(Dispatchers.IO) {
        val synced = fridgeRepository.syncWithBackend()
        if (synced != _uiState.value.ingredients) {
            _uiState.update { it.copy(ingredients = synced) }
            calculateMatches()
        }
    }

    fun onInputChange(newText: String) {
        _uiState.update { it.copy(inputIngredient = newText) }
    }

    fun addCurrentIngredient() {
        val text = _uiState.value.inputIngredient.trim()
        if (text.isNotBlank()) {
            addIngredient(text)
            _uiState.update { it.copy(inputIngredient = "") }
        }
    }

    fun addIngredient(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val current = _uiState.value.ingredients
        val alreadyExists = current.any { it.equals(trimmed, ignoreCase = true) }
        if (alreadyExists) return

        viewModelScope.launch {
            val updated = fridgeRepository.addIngredient(trimmed)
            _uiState.update { it.copy(ingredients = updated) }
            calculateMatches()
        }
    }

    fun removeIngredient(name: String) {
        viewModelScope.launch {
            val updated = fridgeRepository.removeIngredient(name)
            _uiState.update { it.copy(ingredients = updated) }
            calculateMatches()
        }
    }

    fun setShowClearDialog(show: Boolean) {
        _uiState.update { it.copy(showClearDialog = show) }
    }

    fun clearAllIngredients() {
        viewModelScope.launch {
            val updated = fridgeRepository.clearFridge()
            _uiState.update { it.copy(ingredients = updated, showClearDialog = false) }
            calculateMatches()
        }
    }

    fun selectFilter(filter: FridgeFilter) {
        _uiState.update { state ->
            val filtered = applyFilter(state.suggestedRecipes, filter)
            state.copy(selectedFilter = filter, filteredRecipes = filtered)
        }
    }

    private fun calculateMatches() {
        val userIngredients = _uiState.value.ingredients
        if (userIngredients.isEmpty() || allAvailableRecipes.isEmpty()) {
            _uiState.update {
                it.copy(
                    suggestedRecipes = emptyList(),
                    filteredRecipes = emptyList(),
                    isLoading = false
                )
            }
            return
        }

        val normalizedUser = userIngredients.map { normalize(it) }

        val matches = allAvailableRecipes.mapNotNull { recipe ->
            if (recipe.ingredients.isEmpty()) return@mapNotNull null

            val matchedNames = mutableListOf<String>()
            val missing = mutableListOf<Ingredient>()

            for (ing in recipe.ingredients) {
                val normRecipeIng = normalize(ing.name)
                val isMatched = normalizedUser.any { userIng ->
                    isIngredientMatch(userIng, normRecipeIng)
                }
                if (isMatched) {
                    matchedNames.add(ing.name)
                } else {
                    missing.add(ing)
                }
            }

            // Exibir apenas receitas que tenham ao menos 1 ingrediente correspondente
            if (matchedNames.isNotEmpty()) {
                val totalCount = recipe.ingredients.size
                val percentage = if (totalCount > 0) {
                    ((matchedNames.size.toFloat() / totalCount) * 100).toInt()
                } else 0

                RecipeMatch(
                    recipe = recipe,
                    matchedIngredients = matchedNames,
                    missingIngredients = missing,
                    matchPercentage = percentage,
                    canMakeNow = missing.isEmpty()
                )
            } else {
                null
            }
        }.sortedWith(
            compareByDescending<RecipeMatch> { it.canMakeNow }
                .thenBy { it.missingIngredients.size }
                .thenByDescending { it.matchPercentage }
        )

        _uiState.update { state ->
            val filtered = applyFilter(matches, state.selectedFilter)
            state.copy(
                suggestedRecipes = matches,
                filteredRecipes = filtered,
                isLoading = false
            )
        }
    }

    private fun applyFilter(list: List<RecipeMatch>, filter: FridgeFilter): List<RecipeMatch> {
        return when (filter) {
            FridgeFilter.ALL -> list
            FridgeFilter.READY_NOW -> list.filter { it.canMakeNow }
            FridgeFilter.MISSING_FEW -> list.filter { it.missingIngredients.size in 1..2 }
        }
    }

    private fun normalize(str: String): String {
        val unaccented = Normalizer.normalize(str, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .trim()
            .lowercase()
        // Remove 's' final simples para aproximar singular de plural (ex: ovos -> ovo, tomates -> tomate)
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

        // Casos comuns de equivalência culinária em PT-BR
        val equivalences = listOf(
            setOf("ovo", "ovos", "clara", "gema"),
            setOf("frango", "peito de frango", "sobrecoxa", "filé de frango"),
            setOf("carne", "carne bovina", "carne moida", "patinho", "alcatra"),
            setOf("queijo", "mussarela", "parmesao", "prato", "queijo ralado"),
            setOf("leite", "leite integral", "leite desnatado"),
            setOf("azeite", "oleo", "azeite de oliva"),
            setOf("tomate", "molho de tomate", "extrato de tomate"),
            setOf("massa", "macarrao", "espaguete", "penne")
        )

        for (group in equivalences) {
            val userMatchesGroup = group.any { normalize(it) == userIng || userIng.contains(normalize(it)) }
            val recipeMatchesGroup = group.any { normalize(it) == recipeIng || recipeIng.contains(normalize(it)) }
            if (userMatchesGroup && recipeMatchesGroup) return true
        }

        return false
    }
}
