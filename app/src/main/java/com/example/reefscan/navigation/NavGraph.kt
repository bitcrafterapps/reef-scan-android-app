package com.example.reefscan.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.reefscan.ui.screens.CameraScreen
import com.example.reefscan.ui.screens.HomeScreen
import com.example.reefscan.ui.screens.LoadingScreen
import com.example.reefscan.ui.screens.ResultsScreen
import com.example.reefscan.ui.screens.SavedScansScreen
import com.example.reefscan.ui.screens.SplashScreen
import java.net.URLDecoder

private const val ANIMATION_DURATION = 300

@Composable
fun ReefScanNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(ANIMATION_DURATION)
                )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(ANIMATION_DURATION)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(ANIMATION_DURATION)
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(ANIMATION_DURATION)) +
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(ANIMATION_DURATION)
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(ANIMATION_DURATION)) +
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(ANIMATION_DURATION)
                )
        }
    ) {
        // Splash Screen
        composable(
            route = Screen.Splash.route,
            enterTransition = { fadeIn(animationSpec = tween(ANIMATION_DURATION)) },
            exitTransition = { fadeOut(animationSpec = tween(ANIMATION_DURATION)) }
        ) {
            SplashScreen(
                onNavigateToHome = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Home Screen
        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigateToCamera = { mode ->
                    navController.navigate(Screen.Camera.createRoute(mode))
                },
                onNavigateToSavedScans = {
                    navController.navigate(Screen.SavedScans.route)
                },
                onNavigateToLoading = { imageUri, mode ->
                    navController.navigate(Screen.Loading.createRoute(imageUri, mode))
                }
            )
        }

        // Camera Screen
        composable(
            route = Screen.Camera.route,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "COMPREHENSIVE"
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "COMPREHENSIVE"
            
            CameraScreen(
                mode = mode,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLoading = { imageUri ->
                    navController.navigate(Screen.Loading.createRoute(imageUri, mode))
                }
            )
        }

        // Loading Screen
        composable(
            route = Screen.Loading.route,
            arguments = listOf(
                navArgument("imageUri") {
                    type = NavType.StringType
                },
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = "COMPREHENSIVE"
                }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = URLDecoder.decode(encodedUri, "UTF-8")
            val mode = backStackEntry.arguments?.getString("mode") ?: "COMPREHENSIVE"
            
            LoadingScreen(
                imageUri = imageUri,
                mode = mode,
                onNavigateToResults = { scanId ->
                    navController.navigate(Screen.Results.createRoute(scanId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Results Screen
        composable(
            route = Screen.Results.route,
            arguments = listOf(
                navArgument("scanId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val scanId = backStackEntry.arguments?.getString("scanId") ?: ""
            
            ResultsScreen(
                scanId = scanId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.createRoute("COMPREHENSIVE")) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                }
            )
        }

        // Saved Scans Screen
        composable(route = Screen.SavedScans.route) {
            SavedScansScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToResults = { scanId ->
                    navController.navigate(Screen.Results.createRoute(scanId))
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.createRoute("COMPREHENSIVE"))
                }
            )
        }
    }
}
