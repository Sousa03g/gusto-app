package com.gusto.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gusto.app.data.model.Recipe
import com.gusto.app.data.repository.RecipeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val recipes: List<Recipe> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val searchQuery: String = "",
    val selectedCategory: String = "Todos",
    val isFavoritesOnly: Boolean = false,
    val favoriteIds: Set<String> = emptySet(),
    val errorMessage: String? = null
)

class FeedViewModel(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var searchDebounceJob: Job? = null

    init {
        refreshFavorites()
        loadInitial()
    }

    fun refreshFavorites() {
        val favs = recipeRepository.getFavoriteIds()
        _uiState.update { it.copy(favoriteIds = favs) }
    }

    fun loadInitial(isRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !isRefresh,
                    isRefreshing = isRefresh,
                    currentPage = 1,
                    hasMore = true,
                    errorMessage = null
                )
            }
            refreshFavorites()

            val state = _uiState.value
            val result = recipeRepository.getRecipes(
                search = state.searchQuery.ifBlank { null },
                category = if (state.selectedCategory == "Todos") null else state.selectedCategory,
                page = 1,
                onlyFavorites = state.isFavoritesOnly
            )

            result.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        recipes = list,
                        isLoading = false,
                        isRefreshing = false,
                        hasMore = list.size >= 15
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message ?: "Erro ao carregar receitas"
                    )
                }
            }
        }
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore || state.isFavoritesOnly) {
            return
        }

        viewModelScope.launch {
            val nextPage = state.currentPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }

            val result = recipeRepository.getRecipes(
                search = state.searchQuery.ifBlank { null },
                category = if (state.selectedCategory == "Todos") null else state.selectedCategory,
                page = nextPage,
                onlyFavorites = false
            )

            result.onSuccess { newList ->
                _uiState.update { current ->
                    val existingIds = current.recipes.map { r -> r.id }.toSet()
                    val filteredNew = newList.filter { r -> !existingIds.contains(r.id) }
                    current.copy(
                        recipes = current.recipes + filteredNew,
                        currentPage = nextPage,
                        isLoadingMore = false,
                        hasMore = newList.isNotEmpty()
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(350) // Debounce para não disparar requisições em cada tecla
            loadInitial()
        }
    }

    fun onCategorySelect(category: String) {
        if (_uiState.value.selectedCategory == category && !_uiState.value.isFavoritesOnly) return
        _uiState.update { it.copy(selectedCategory = category, isFavoritesOnly = false) }
        loadInitial()
    }

    fun toggleFavoritesOnly() {
        val newFavoritesOnly = !_uiState.value.isFavoritesOnly
        _uiState.update { it.copy(isFavoritesOnly = newFavoritesOnly) }
        loadInitial()
    }

    fun toggleFavorite(recipe: Recipe) {
        val isFav = recipeRepository.toggleFavorite(recipe)
        refreshFavorites()
        if (_uiState.value.isFavoritesOnly && !isFav) {
            // Remove do feed se estiver na visão de apenas favoritos
            _uiState.update { state ->
                state.copy(recipes = state.recipes.filter { it.id != recipe.id })
            }
        }
    }
}
