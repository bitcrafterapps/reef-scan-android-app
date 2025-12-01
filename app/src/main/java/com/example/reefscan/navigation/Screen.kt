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
     * Tanks screen - select or add tank
     */
    data object Tanks : Screen("tanks")

    /**
     * Home screen - main dashboard for a specific tank
     * @param tankId ID of the selected tank
     */
    data object Home : Screen("home/{tankId}") {
        fun createRoute(tankId: Long): String {
            return "home/$tankId"
        }
    }

    /**
     * Camera screen - capture photos for scanning
     * Optional mode parameter: "FISH_ID", "CORAL_ID", etc.
     * Optional tankId parameter to associate scan with tank
     */
    data object Camera : Screen("camera?mode={mode}&tankId={tankId}") {
        fun createRoute(mode: String = "COMPREHENSIVE", tankId: Long = -1): String {
            return "camera?mode=$mode&tankId=$tankId"
        }
    }

    /**
     * Loading screen - shows while analyzing image
     * @param imageUri URI of the captured/selected image (URL encoded)
     * Optional mode parameter for analysis type
     * Optional tankId parameter
     */
    data object Loading : Screen("loading/{imageUri}?mode={mode}&tankId={tankId}") {
        fun createRoute(imageUri: String, mode: String = "COMPREHENSIVE", tankId: Long = -1): String {
            val encodedUri = java.net.URLEncoder.encode(imageUri, "UTF-8")
            return "loading/$encodedUri?mode=$mode&tankId=$tankId"
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
     * Saved scans screen - list of previously saved scan results for a tank
     * @param tankId ID of the tank to show scans for
     */
    data object SavedScans : Screen("saved_scans/{tankId}") {
        fun createRoute(tankId: Long): String {
            return "saved_scans/$tankId"
        }
    }

    /**
     * Tank Gallery screen - shows folders of images by date for a tank
     * @param tankId ID of the tank
     */
    data object TankGallery : Screen("tank_gallery/{tankId}") {
        fun createRoute(tankId: Long): String {
            return "tank_gallery/$tankId"
        }
    }

    /**
     * Date Gallery screen - shows images for a specific date
     * @param tankId ID of the tank
     * @param dateString Date folder name (e.g. "2023-10-27")
     */
    data object DateGallery : Screen("date_gallery/{tankId}/{dateString}") {
        fun createRoute(tankId: Long, dateString: String): String {
            return "date_gallery/$tankId/$dateString"
        }
    }
}
