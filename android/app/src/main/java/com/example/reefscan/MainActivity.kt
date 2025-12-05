package com.bitcraftapps.reefscan

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.bitcraftapps.reefscan.billing.SubscriptionManager
import com.bitcraftapps.reefscan.navigation.ReefScanNavGraph
import com.bitcraftapps.reefscan.ui.theme.ReefScanTheme

class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize RevenueCat for subscription management
        initializeSubscriptions()
        
        // Enable edge-to-edge display
        enableEdgeToEdge()
        
        // Make the app truly full screen - hide system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Hide system bars (status bar and navigation bar)
        window.decorView.post {
            hideSystemBars()
        }
        
        setContent {
            ReefScanTheme {
                val navController = rememberNavController()
                
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReefScanNavGraph(navController = navController)
                }
            }
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }
    
    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
        }
    }
    
    /**
     * Initialize RevenueCat subscription management
     * Uses the API key from BuildConfig (set in local.properties)
     */
    private fun initializeSubscriptions() {
        try {
            val apiKey = getRevenueCatApiKey()
            if (apiKey.isNotBlank()) {
                Log.d(TAG, "Initializing RevenueCat with configured API key")
            } else {
                Log.w(TAG, "RevenueCat API key not configured - running in demo mode")
            }
            
            val subscriptionManager = SubscriptionManager.getInstance(this)
            subscriptionManager.initialize(apiKey)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize subscriptions", e)
        }
    }
    
    /**
     * Get the RevenueCat API key from BuildConfig
     */
    private fun getRevenueCatApiKey(): String {
        return try {
            BuildConfig::class.java.getField("REVENUECAT_API_KEY").get(null) as? String ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
