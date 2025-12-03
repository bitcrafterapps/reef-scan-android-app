package com.example.reefscan.ui.screens

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.reefscan.billing.SubscriptionManager
import com.example.reefscan.billing.SubscriptionState
import com.example.reefscan.billing.SubscriptionTier
import com.example.reefscan.billing.UsageData
import com.example.reefscan.billing.UsageTracker
import com.revenuecat.purchases.Package
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the subscription screen
 * Manages subscription state and purchase flows
 */
class SubscriptionViewModel(
    private val subscriptionManager: SubscriptionManager,
    private val usageTracker: UsageTracker
) : ViewModel() {
    
    val subscriptionState: StateFlow<SubscriptionState> = subscriptionManager.subscriptionState
    val usageData: StateFlow<UsageData> = usageTracker.usageState
    
    init {
        // Refresh offerings when screen loads
        subscriptionManager.fetchOfferings()
        subscriptionManager.refreshCustomerInfo()
    }
    
    /**
     * Purchase a subscription
     */
    fun purchaseSubscription(
        activity: Activity,
        tier: SubscriptionTier,
        isYearly: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val productId = if (isYearly) tier.yearlyProductId else tier.monthlyProductId
        
        if (productId.isEmpty()) {
            onError("Invalid product")
            return
        }
        
        val packageToPurchase = subscriptionManager.getPackage(productId)
        
        if (packageToPurchase != null) {
            subscriptionManager.purchase(
                activity = activity,
                packageToPurchase = packageToPurchase,
                onSuccess = { onSuccess() },
                onError = onError
            )
        } else {
            // For demo/testing when RevenueCat isn't configured
            // This allows testing the UI flow
            subscriptionManager.setDemoTier(tier)
            onSuccess()
        }
    }
    
    /**
     * Restore previous purchases
     */
    fun restorePurchases(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        subscriptionManager.restorePurchases(
            onSuccess = { onSuccess() },
            onError = onError
        )
    }
    
    /**
     * Clear any error state
     */
    fun clearError() {
        subscriptionManager.clearError()
    }
    
    /**
     * Get available packages for purchase
     */
    fun getAvailablePackages(): List<Package> {
        return subscriptionManager.getAvailablePackages()
    }
}

/**
 * Factory for creating SubscriptionViewModel
 */
class SubscriptionViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SubscriptionViewModel::class.java)) {
            return SubscriptionViewModel(
                subscriptionManager = SubscriptionManager.getInstance(context),
                usageTracker = UsageTracker.getInstance(context)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

