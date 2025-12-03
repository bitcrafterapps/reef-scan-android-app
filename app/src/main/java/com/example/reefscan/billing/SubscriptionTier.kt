package com.example.reefscan.billing

/**
 * Subscription tier levels for ReefScan
 * Simple 2-tier model: Free + Pro
 */
enum class SubscriptionTier(
    val displayName: String,
    val dailyLimit: Int,
    val historyLimit: Int,
    val tankLimit: Int,
    val monthlyProductId: String,
    val yearlyProductId: String,
    val canExportReports: Boolean,
    val hasAds: Boolean,
    val hasPriorityProcessing: Boolean
) {
    FREE(
        displayName = "Free",
        dailyLimit = 3,
        historyLimit = 10,
        tankLimit = 1,
        monthlyProductId = "",
        yearlyProductId = "",
        canExportReports = false,
        hasAds = true,
        hasPriorityProcessing = false
    ),
    PRO(
        displayName = "Pro",
        dailyLimit = 20,
        historyLimit = Int.MAX_VALUE,
        tankLimit = Int.MAX_VALUE,
        monthlyProductId = "pro_monthly",
        yearlyProductId = "pro_yearly",
        canExportReports = true,
        hasAds = false,
        hasPriorityProcessing = true
    );

    companion object {
        /**
         * Get tier from RevenueCat entitlement ID
         */
        fun fromEntitlementId(entitlementId: String?): SubscriptionTier {
            return when (entitlementId) {
                "pro_access" -> PRO
                else -> FREE
            }
        }

        /**
         * Get tier from product ID
         */
        fun fromProductId(productId: String?): SubscriptionTier {
            return when (productId) {
                "pro_monthly", "pro_yearly" -> PRO
                else -> FREE
            }
        }
    }
}

/**
 * Pricing information for subscription tiers
 */
object TierPricing {
    // Monthly price
    const val PRO_MONTHLY_PRICE = "$6.99"
    
    // Yearly price (with savings - ~40% off)
    const val PRO_YEARLY_PRICE = "$49.99"
    
    // Yearly savings percentage
    const val PRO_YEARLY_SAVINGS = "40%"
}

/**
 * Feature descriptions for each tier
 */
object TierFeatures {
    val FREE_FEATURES = listOf(
        "3 scans per day",
        "Basic scan history (10 scans)",
        "1 tank profile",
        "Species identification",
        "Problem detection"
    )
    
    val PRO_FEATURES = listOf(
        "20 scans per day",
        "Unlimited scan history",
        "Unlimited tank profiles",
        "Priority processing",
        "Export PDF reports",
        "Early feature access"
    )
    
    fun getFeaturesForTier(tier: SubscriptionTier): List<String> {
        return when (tier) {
            SubscriptionTier.FREE -> FREE_FEATURES
            SubscriptionTier.PRO -> PRO_FEATURES
        }
    }
}

