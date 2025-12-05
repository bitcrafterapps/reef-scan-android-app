package com.bitcraftapps.reefscan.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages subscription state and purchases using RevenueCat
 */
class SubscriptionManager private constructor(
    private val context: Context,
    private val usageTracker: UsageTracker
) {
    
    private val _subscriptionState = MutableStateFlow(SubscriptionState())
    val subscriptionState: StateFlow<SubscriptionState> = _subscriptionState.asStateFlow()
    
    private val _offerings = MutableStateFlow<Offerings?>(null)
    val offerings: StateFlow<Offerings?> = _offerings.asStateFlow()
    
    /**
     * Current subscription tier
     */
    val currentTier: SubscriptionTier
        get() = _subscriptionState.value.tier
    
    /**
     * Whether the user has an active paid subscription
     */
    val isPremium: Boolean
        get() = _subscriptionState.value.tier != SubscriptionTier.FREE
    
    /**
     * Initialize RevenueCat SDK
     * Call this in Application.onCreate() or MainActivity
     */
    fun initialize(apiKey: String) {
        if (apiKey.isBlank()) {
            Log.w(TAG, "RevenueCat API key not configured, running in demo mode")
            _subscriptionState.value = SubscriptionState(
                isInitialized = true,
                tier = usageTracker.getCurrentTier()
            )
            return
        }
        
        try {
            val config = PurchasesConfiguration.Builder(context, apiKey)
                .build()
            Purchases.configure(config)
            
            // Fetch initial customer info
            refreshCustomerInfo()
            
            // Fetch available offerings
            fetchOfferings()
            
            _subscriptionState.value = _subscriptionState.value.copy(isInitialized = true)
            Log.d(TAG, "RevenueCat initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize RevenueCat", e)
            _subscriptionState.value = SubscriptionState(
                isInitialized = true,
                error = "Failed to initialize billing: ${e.message}"
            )
        }
    }
    
    /**
     * Refresh customer info and update subscription tier
     */
    fun refreshCustomerInfo() {
        if (!Purchases.isConfigured) {
            Log.w(TAG, "Purchases not configured, skipping refresh")
            return
        }
        
        Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateTierFromCustomerInfo(customerInfo)
            }
            
            override fun onError(error: PurchasesError) {
                Log.e(TAG, "Error fetching customer info: ${error.message}")
                _subscriptionState.value = _subscriptionState.value.copy(
                    error = error.message
                )
            }
        })
    }
    
    /**
     * Fetch available subscription offerings
     */
    fun fetchOfferings() {
        if (!Purchases.isConfigured) {
            Log.w(TAG, "Purchases not configured, skipping fetch offerings")
            return
        }
        
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.e(TAG, "Error fetching offerings: ${error.message}")
            },
            onSuccess = { offerings ->
                _offerings.value = offerings
                Log.d(TAG, "Fetched ${offerings.all.size} offerings")
            }
        )
    }
    
    /**
     * Purchase a subscription package
     */
    fun purchase(
        activity: Activity,
        packageToPurchase: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!Purchases.isConfigured) {
            onError("Billing not configured")
            return
        }
        
        _subscriptionState.value = _subscriptionState.value.copy(isPurchasing = true)
        
        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, packageToPurchase).build(),
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    updateTierFromCustomerInfo(customerInfo)
                    _subscriptionState.value = _subscriptionState.value.copy(isPurchasing = false)
                    onSuccess(customerInfo)
                    Log.d(TAG, "Purchase completed successfully")
                }
                
                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _subscriptionState.value = _subscriptionState.value.copy(
                        isPurchasing = false,
                        error = if (userCancelled) null else error.message
                    )
                    if (!userCancelled) {
                        onError(error.message)
                    }
                    Log.e(TAG, "Purchase error: ${error.message}, cancelled: $userCancelled")
                }
            }
        )
    }
    
    /**
     * Restore previous purchases
     */
    fun restorePurchases(
        onSuccess: (CustomerInfo) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!Purchases.isConfigured) {
            onError("Billing not configured")
            return
        }
        
        _subscriptionState.value = _subscriptionState.value.copy(isRestoring = true)
        
        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateTierFromCustomerInfo(customerInfo)
                _subscriptionState.value = _subscriptionState.value.copy(isRestoring = false)
                onSuccess(customerInfo)
                Log.d(TAG, "Purchases restored successfully")
            }
            
            override fun onError(error: PurchasesError) {
                _subscriptionState.value = _subscriptionState.value.copy(
                    isRestoring = false,
                    error = error.message
                )
                onError(error.message)
                Log.e(TAG, "Restore error: ${error.message}")
            }
        })
    }
    
    /**
     * Update subscription tier based on RevenueCat customer info
     */
    private fun updateTierFromCustomerInfo(customerInfo: CustomerInfo) {
        val tier = when {
            customerInfo.entitlements["pro_access"]?.isActive == true -> SubscriptionTier.PRO
            else -> SubscriptionTier.FREE
        }
        
        _subscriptionState.value = _subscriptionState.value.copy(
            tier = tier,
            expirationDate = customerInfo.entitlements.active.values.firstOrNull()?.expirationDate,
            error = null
        )
        
        // Update usage tracker with new tier
        usageTracker.updateTier(tier)
        
        Log.d(TAG, "Updated tier to: ${tier.displayName}")
    }
    
    /**
     * Get packages for the default offering
     */
    fun getAvailablePackages(): List<Package> {
        return _offerings.value?.current?.availablePackages ?: emptyList()
    }
    
    /**
     * Get package by product ID
     */
    fun getPackage(productId: String): Package? {
        return getAvailablePackages().find { it.product.id == productId }
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        _subscriptionState.value = _subscriptionState.value.copy(error = null)
    }
    
    /**
     * For testing/demo: manually set tier (only works when RevenueCat is not configured)
     */
    fun setDemoTier(tier: SubscriptionTier) {
        if (!Purchases.isConfigured) {
            _subscriptionState.value = _subscriptionState.value.copy(tier = tier)
            usageTracker.updateTier(tier)
        }
    }
    
    companion object {
        private const val TAG = "SubscriptionManager"
        
        @Volatile
        private var INSTANCE: SubscriptionManager? = null
        
        fun getInstance(context: Context): SubscriptionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SubscriptionManager(
                    context.applicationContext,
                    UsageTracker.getInstance(context)
                ).also { INSTANCE = it }
            }
        }
    }
}

/**
 * Represents the current subscription state
 */
data class SubscriptionState(
    val isInitialized: Boolean = false,
    val tier: SubscriptionTier = SubscriptionTier.FREE,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val expirationDate: java.util.Date? = null,
    val error: String? = null
) {
    val isPremium: Boolean
        get() = tier != SubscriptionTier.FREE
    
    val isLoading: Boolean
        get() = isPurchasing || isRestoring
}

