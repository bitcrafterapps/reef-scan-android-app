package com.example.reefscan.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.reefscan.data.local.ScanRepository
import com.example.reefscan.ui.screens.CameraScreen
import com.example.reefscan.ui.screens.DateGalleryScreen
import com.example.reefscan.ui.screens.HomeScreen
import com.example.reefscan.ui.screens.LoadingScreen
import com.example.reefscan.ui.screens.ResultsScreen
import com.example.reefscan.ui.screens.SavedScansScreen
import com.example.reefscan.ui.screens.SplashScreen
import com.example.reefscan.ui.screens.TankGalleryScreen
import com.example.reefscan.ui.screens.TanksScreen
import kotlinx.coroutines.launch
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
                    // Navigate to Tanks screen instead of Home directly
                    navController.navigate(Screen.Tanks.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Tanks Screen (New Entry Point)
        composable(route = Screen.Tanks.route) {
            TanksScreen(
                onTankSelected = { tankId ->
                    navController.navigate(Screen.Home.createRoute(tankId))
                }
            )
        }

        // Home Screen (Tank Dashboard)
        composable(
            route = Screen.Home.route,
            arguments = listOf(
                navArgument("tankId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val tankId = backStackEntry.arguments?.getLong("tankId") ?: -1L
            
            HomeScreen(
                tankId = tankId,
                onNavigateToCamera = { mode ->
                    navController.navigate(Screen.Camera.createRoute(mode, tankId))
                },
                onNavigateToSavedScans = {
                    navController.navigate(Screen.SavedScans.createRoute(tankId))
                },
                onNavigateToGallery = {
                    navController.navigate(Screen.TankGallery.createRoute(tankId))
                },
                onNavigateToLoading = { imageUri, mode ->
                    navController.navigate(Screen.Loading.createRoute(imageUri, mode, tankId))
                },
                onNavigateBack = {
                    navController.popBackStack()
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
                },
                navArgument("tankId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode") ?: "COMPREHENSIVE"
            val tankId = backStackEntry.arguments?.getLong("tankId") ?: -1L
            
            val context = LocalContext.current
            val scope = rememberCoroutineScope()
            // Instantiate repository here to handle saving
            val repository = remember { ScanRepository(context) }
            
            CameraScreen(
                mode = mode,
                tankId = tankId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToLoading = { imageUri ->
                    navController.navigate(Screen.Loading.createRoute(imageUri, mode, tankId))
                },
                onGalleryImageCaptured = { uri ->
                    scope.launch {
                        repository.saveGalleryImage(tankId, uri)
                    }
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
                },
                navArgument("tankId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val encodedUri = backStackEntry.arguments?.getString("imageUri") ?: ""
            val imageUri = URLDecoder.decode(encodedUri, "UTF-8")
            val mode = backStackEntry.arguments?.getString("mode") ?: "COMPREHENSIVE"
            val tankId = backStackEntry.arguments?.getLong("tankId") ?: -1L
            
            LoadingScreen(
                imageUri = imageUri,
                mode = mode,
                tankId = tankId,
                onNavigateToResults = { scanId ->
                    navController.navigate(Screen.Results.createRoute(scanId)) {
                        // Pop back to Home so back button from Results goes to Home
                        // We keep Home in the backstack
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
                    navController.popBackStack(Screen.Home.route, false)
                }
            )
        }

        // Saved Scans Screen
        composable(
            route = Screen.SavedScans.route,
            arguments = listOf(
                navArgument("tankId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val tankId = backStackEntry.arguments?.getLong("tankId") ?: -1L

            SavedScansScreen(
                tankId = tankId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToResults = { scanId ->
                    navController.navigate(Screen.Results.createRoute(scanId))
                },
                onNavigateToCamera = {
                    navController.navigate(Screen.Camera.createRoute("COMPREHENSIVE", tankId))
                }
            )
        }
        
        // Tank Gallery Screen
        composable(
            route = Screen.TankGallery.route,
            arguments = listOf(
                navArgument("tankId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val tankId = backStackEntry.arguments?.getLong("tankId") ?: -1L
            
            TankGalleryScreen(
                tankId = tankId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDateGallery = { dateString ->
                    navController.navigate(Screen.DateGallery.createRoute(tankId, dateString))
                }
            )
        }
        
        // Date Gallery Screen
        composable(
            route = Screen.DateGallery.route,
            arguments = listOf(
                navArgument("tankId") { type = NavType.LongType },
                navArgument("dateString") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val tankId = backStackEntry.arguments?.getLong("tankId") ?: -1L
            val dateString = backStackEntry.arguments?.getString("dateString") ?: ""
            
            DateGalleryScreen(
                tankId = tankId,
                dateString = dateString,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
