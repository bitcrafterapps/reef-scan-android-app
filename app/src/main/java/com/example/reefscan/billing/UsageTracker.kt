package com.example.reefscan.billing

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Tracks daily scan usage and enforces tier limits
 * Uses SharedPreferences for persistent storage
 */
class UsageTracker(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    private val _usageState = MutableStateFlow(loadUsageData())
    val usageState: StateFlow<UsageData> = _usageState.asStateFlow()
    
    /**
     * Current usage data
     */
    val currentUsage: UsageData
        get() = _usageState.value
    
    init {
        // Check if we need to reset for a new day
        checkAndResetIfNewDay()
    }
    
    /**
     * Check if the user can perform a scan based on their tier limits
     */
    fun canScan(): Boolean {
        checkAndResetIfNewDay()
        return currentUsage.canScan
    }
    
    /**
     * Get the number of remaining scans for today
     */
    fun getRemainingScans(): Int {
        checkAndResetIfNewDay()
        return currentUsage.remainingScans
    }
    
    /**
     * Increment the scan count after a successful scan
     * Returns true if the scan was counted, false if limit was already reached
     */
    fun incrementScanCount(): Boolean {
        checkAndResetIfNewDay()
        
        val current = currentUsage
        if (!current.canScan) {
            return false
        }
        
        val newCount = current.scansToday + 1
        prefs.edit().putInt(KEY_SCANS_TODAY, newCount).apply()
        
        _usageState.value = current.copy(scansToday = newCount)
        return true
    }
    
    /**
     * Update the user's subscription tier
     * This affects the daily limit
     */
    fun updateTier(tier: SubscriptionTier) {
        prefs.edit().putString(KEY_TIER, tier.name).apply()
        _usageState.value = currentUsage.copy(tier = tier)
    }
    
    /**
     * Get the current subscription tier
     */
    fun getCurrentTier(): SubscriptionTier {
        return currentUsage.tier
    }
    
    /**
     * Check if it's a new day and reset the counter if needed
     */
    private fun checkAndResetIfNewDay() {
        val today = LocalDate.now()
        val lastReset = getLastResetDate()
        
        if (today.isAfter(lastReset)) {
            resetDailyCounter(today)
        }
    }
    
    /**
     * Reset the daily scan counter
     */
    private fun resetDailyCounter(date: LocalDate) {
        prefs.edit()
            .putInt(KEY_SCANS_TODAY, 0)
            .putString(KEY_LAST_RESET_DATE, date.format(DATE_FORMATTER))
            .apply()
        
        _usageState.value = currentUsage.copy(
            scansToday = 0,
            lastResetDate = date
        )
    }
    
    /**
     * Load usage data from SharedPreferences
     */
    private fun loadUsageData(): UsageData {
        val scansToday = prefs.getInt(KEY_SCANS_TODAY, 0)
        val lastResetDateStr = prefs.getString(KEY_LAST_RESET_DATE, null)
        val tierName = prefs.getString(KEY_TIER, SubscriptionTier.FREE.name)
        
        val lastResetDate = if (lastResetDateStr != null) {
            try {
                LocalDate.parse(lastResetDateStr, DATE_FORMATTER)
            } catch (e: Exception) {
                LocalDate.now()
            }
        } else {
            LocalDate.now()
        }
        
        val tier = try {
            SubscriptionTier.valueOf(tierName ?: SubscriptionTier.FREE.name)
        } catch (e: Exception) {
            SubscriptionTier.FREE
        }
        
        return UsageData(
            scansToday = scansToday,
            lastResetDate = lastResetDate,
            tier = tier
        )
    }
    
    /**
     * Get the last reset date from preferences
     */
    private fun getLastResetDate(): LocalDate {
        val dateStr = prefs.getString(KEY_LAST_RESET_DATE, null)
        return if (dateStr != null) {
            try {
                LocalDate.parse(dateStr, DATE_FORMATTER)
            } catch (e: Exception) {
                LocalDate.MIN // Force reset if date is invalid
            }
        } else {
            LocalDate.MIN // Force reset if no date stored
        }
    }
    
    /**
     * Force a refresh of the usage state
     */
    fun refresh() {
        checkAndResetIfNewDay()
        _usageState.value = loadUsageData()
    }
    
    companion object {
        private const val PREFS_NAME = "reefscan_usage"
        private const val KEY_SCANS_TODAY = "scans_today"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_TIER = "subscription_tier"
        
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE
        
        @Volatile
        private var INSTANCE: UsageTracker? = null
        
        fun getInstance(context: Context): UsageTracker {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UsageTracker(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}

/**
 * Data class representing current usage state
 */
data class UsageData(
    val scansToday: Int,
    val lastResetDate: LocalDate,
    val tier: SubscriptionTier
) {
    /**
     * Number of remaining scans for today
     */
    val remainingScans: Int
        get() = (tier.dailyLimit - scansToday).coerceAtLeast(0)
    
    /**
     * Whether the user can perform a scan
     */
    val canScan: Boolean
        get() = scansToday < tier.dailyLimit
    
    /**
     * Usage as a percentage (0.0 to 1.0)
     */
    val usagePercentage: Float
        get() = if (tier.dailyLimit > 0) {
            (scansToday.toFloat() / tier.dailyLimit).coerceIn(0f, 1f)
        } else 0f
    
    /**
     * Whether the user is approaching their limit (80%+)
     */
    val isApproachingLimit: Boolean
        get() = usagePercentage >= 0.8f && canScan
    
    /**
     * Whether the user has reached their limit
     */
    val hasReachedLimit: Boolean
        get() = !canScan
}

