package com.gusto.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gusto.app.data.model.Ingredient
import com.gusto.app.data.model.Recipe
import com.gusto.app.data.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val recipe: Recipe? = null,
    val isLoading: Boolean = true,
    val isFavorite: Boolean = false,
    val servings: Int = 1,
    val originalServings: Int = 1,
    val isCookMode: Boolean = false,
    val completedSteps: Set<Int> = emptySet(),
    val completedIngredients: Set<String> = emptySet(),
    val activeTimers: Map<Int, Long> = emptyMap(), // orderNumber -> segundos restantes
    val timerRunning: Map<Int, Boolean> = emptyMap(), // orderNumber -> está rodando?
    val timerAlert: Int? = null, // orderNumber que acabou de finalizar
    val errorMessage: String? = null
)

class RecipeDetailViewModel(
    private val recipeId: String,
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecipeDetailUiState())
    val uiState: StateFlow<RecipeDetailUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        loadRecipe()
    }

    fun loadRecipe() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = recipeRepository.getRecipeById(recipeId)

            result.onSuccess { recipe ->
                val isFav = recipeRepository.isFavorite(recipe.id)
                _uiState.update {
                    it.copy(
                        recipe = recipe,
                        isLoading = false,
                        isFavorite = isFav,
                        servings = recipe.servings.coerceAtLeast(1),
                        originalServings = recipe.servings.coerceAtLeast(1)
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "Erro ao carregar receita")
                }
            }
        }
    }

    fun incrementServings() {
        _uiState.update { it.copy(servings = (it.servings + 1).coerceAtMost(30)) }
    }

    fun decrementServings() {
        _uiState.update { it.copy(servings = (it.servings - 1).coerceAtLeast(1)) }
    }

    fun toggleCookMode() {
        _uiState.update { it.copy(isCookMode = !it.isCookMode) }
    }

    fun toggleStep(orderNumber: Int) {
        _uiState.update { state ->
            val set = state.completedSteps.toMutableSet()
            if (set.contains(orderNumber)) set.remove(orderNumber) else set.add(orderNumber)
            state.copy(completedSteps = set)
        }
    }

    fun toggleIngredient(name: String) {
        _uiState.update { state ->
            val set = state.completedIngredients.toMutableSet()
            if (set.contains(name)) set.remove(name) else set.add(name)
            state.copy(completedIngredients = set)
        }
    }

    fun toggleFavorite() {
        val currentRecipe = _uiState.value.recipe ?: return
        val newFav = recipeRepository.toggleFavorite(currentRecipe)
        _uiState.update { it.copy(isFavorite = newFav) }
    }

    // Gerenciamento de Timers por Passo
    fun startOrResumeTimer(orderNumber: Int, defaultMinutes: Int = 5) {
        val currentSeconds = _uiState.value.activeTimers[orderNumber]
        val secondsToRun = if (currentSeconds != null && currentSeconds > 0) {
            currentSeconds
        } else {
            defaultMinutes.toLong() * 60
        }

        _uiState.update { state ->
            val updatedTimers = state.activeTimers.toMutableMap()
            val updatedRunning = state.timerRunning.toMutableMap()
            updatedTimers[orderNumber] = secondsToRun
            updatedRunning[orderNumber] = true
            state.copy(activeTimers = updatedTimers, timerRunning = updatedRunning, timerAlert = null)
        }

        ensureTimerLoopRunning()
    }

    fun pauseTimer(orderNumber: Int) {
        _uiState.update { state ->
            val updatedRunning = state.timerRunning.toMutableMap()
            updatedRunning[orderNumber] = false
            state.copy(timerRunning = updatedRunning)
        }
    }

    fun resetTimer(orderNumber: Int, defaultMinutes: Int = 5) {
        _uiState.update { state ->
            val updatedTimers = state.activeTimers.toMutableMap()
            val updatedRunning = state.timerRunning.toMutableMap()
            updatedTimers[orderNumber] = defaultMinutes.toLong() * 60
            updatedRunning[orderNumber] = false
            state.copy(activeTimers = updatedTimers, timerRunning = updatedRunning)
        }
    }

    fun dismissTimerAlert() {
        _uiState.update { it.copy(timerAlert = null) }
    }

    private fun ensureTimerLoopRunning() {
        if (timerJob?.isActive == true) return

        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val state = _uiState.value
                val hasRunningTimers = state.timerRunning.values.any { it }
                if (!hasRunningTimers) break

                val updatedTimers = state.activeTimers.toMutableMap()
                val updatedRunning = state.timerRunning.toMutableMap()
                var finishedOrderNumber: Int? = null

                for ((order, isRunning) in state.timerRunning) {
                    if (isRunning) {
                        val current = updatedTimers[order] ?: 0L
                        if (current > 1) {
                            updatedTimers[order] = current - 1
                        } else {
                            updatedTimers[order] = 0
                            updatedRunning[order] = false
                            finishedOrderNumber = order
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        activeTimers = updatedTimers,
                        timerRunning = updatedRunning,
                        timerAlert = finishedOrderNumber ?: it.timerAlert
                    )
                }
            }
        }
    }

    /**
     * Calcula a quantidade de ingrediente com base no multiplicador de porções
     */
    fun calculateQuantity(ingredient: Ingredient): Double {
        val original = _uiState.value.originalServings.toDouble()
        val current = _uiState.value.servings.toDouble()
        if (original <= 0) return ingredient.quantity
        val scaled = (ingredient.quantity / original) * current
        return Math.round(scaled * 100.0) / 100.0
    }
}
