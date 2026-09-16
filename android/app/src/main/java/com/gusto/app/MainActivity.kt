package com.gusto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gusto.app.data.model.ThemeMode
import com.gusto.app.data.repository.AuthRepository
import com.gusto.app.data.repository.RecipeRepository
import com.gusto.app.ui.screens.AuthScreen
import com.gusto.app.ui.screens.CreateRecipeScreen
import com.gusto.app.ui.screens.FeedScreen
import com.gusto.app.ui.screens.RecipeDetailScreen
import com.gusto.app.ui.theme.GustoTheme
import com.gusto.app.ui.viewmodel.FeedViewModel
import com.gusto.app.ui.viewmodel.RecipeDetailViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val authRepository = AuthRepository(this)
        val recipeRepository = RecipeRepository(this)

        setContent {
            var currentTheme by remember { mutableStateOf(authRepository.getSavedTheme()) }

            GustoTheme(themeMode = currentTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val startDestination = if (authRepository.isLoggedIn()) "feed" else "auth"

                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        enterTransition = { fadeIn(animationSpec = tween(220)) },
                        exitTransition = { fadeOut(animationSpec = tween(220)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
                        popExitTransition = { fadeOut(animationSpec = tween(220)) }
                    ) {
                        composable("auth") {
                            AuthScreen(
                                authRepository = authRepository,
                                onAuthSuccess = {
                                    navController.navigate("feed") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("feed") {
                            val feedViewModel: FeedViewModel = remember {
                                FeedViewModel(recipeRepository)
                            }

                            FeedScreen(
                                viewModel = feedViewModel,
                                authRepository = authRepository,
                                currentTheme = currentTheme,
                                onThemeChange = { newTheme ->
                                    currentTheme = newTheme
                                    authRepository.saveTheme(newTheme)
                                },
                                onRecipeClick = { recipeId ->
                                    navController.navigate("recipe/$recipeId")
                                },
                                onCreateRecipeClick = {
                                    navController.navigate("create_recipe")
                                },
                                onLogout = {
                                    navController.navigate("auth") {
                                        popUpTo("feed") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(
                            route = "recipe/{recipeId}",
                            arguments = listOf(navArgument("recipeId") { type = NavType.StringType }),
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(260)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(260)
                                )
                            }
                        ) { backStackEntry ->
                            val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
                            val detailViewModel: RecipeDetailViewModel = remember(recipeId) {
                                RecipeDetailViewModel(recipeId, recipeRepository)
                            }

                            RecipeDetailScreen(
                                viewModel = detailViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "create_recipe",
                            enterTransition = {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Up,
                                    animationSpec = tween(280)
                                )
                            },
                            exitTransition = {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Down,
                                    animationSpec = tween(280)
                                )
                            }
                        ) {
                            CreateRecipeScreen(
                                recipeRepository = recipeRepository,
                                onBack = { navController.popBackStack() },
                                onRecipeCreated = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
