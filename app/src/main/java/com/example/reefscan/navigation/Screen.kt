package com.example.reefscan.navigation

/**
 * Sealed class defining all navigation routes in the ReefScan app.
 */
sealed class Screen(val route: String) {
    /**
     * Splash screen - app entry point with animated logo
     */
    data object Splash : Screen("splash")

    /**
     * Home screen - main landing page with scan button
     */
    data object Home : Screen("home")

    /**
     * Camera screen - capture photos for scanning
     * Optional mode parameter: "FISH_ID", "CORAL_ID", etc.
     */
    data object Camera : Screen("camera?mode={mode}") {
        fun createRoute(mode: String = "COMPREHENSIVE"): String {
            return "camera?mode=$mode"
        }
    }

    /**
     * Loading screen - shows while analyzing image
     * @param imageUri URI of the captured/selected image (URL encoded)
     * Optional mode parameter for analysis type
     */
    data object Loading : Screen("loading/{imageUri}?mode={mode}") {
        fun createRoute(imageUri: String, mode: String = "COMPREHENSIVE"): String {
            val encodedUri = java.net.URLEncoder.encode(imageUri, "UTF-8")
            return "loading/$encodedUri?mode=$mode"
        }
    }

    /**
     * Results screen - displays scan analysis results
     * @param scanId ID of the scan result (can be "temp" for immediate results or database ID)
     */
    data object Results : Screen("results/{scanId}") {
        fun createRoute(scanId: String): String {
            return "results/$scanId"
        }
    }

    /**
     * Saved scans screen - list of previously saved scan results
     */
    data object SavedScans : Screen("saved_scans")
}
