package com.bitcraftapps.reefscan.ui.screens

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bitcraftapps.reefscan.billing.SubscriptionManager
import com.bitcraftapps.reefscan.billing.SubscriptionState
import com.bitcraftapps.reefscan.billing.SubscriptionTier
import com.bitcraftapps.reefscan.billing.TierFeatures
import com.bitcraftapps.reefscan.billing.TierPricing
import com.bitcraftapps.reefscan.billing.UsageTracker
import com.bitcraftapps.reefscan.ui.theme.AquaBlue
import com.bitcraftapps.reefscan.ui.theme.CoralAccent
import com.bitcraftapps.reefscan.ui.theme.DeepOcean
import com.bitcraftapps.reefscan.ui.theme.Seafoam
import kotlinx.coroutines.launch

/**
 * Main subscription screen showing tier comparison and purchase options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onNavigateBack: () -> Unit,
    viewModel: SubscriptionViewModel = viewModel(
        factory = SubscriptionViewModelFactory(LocalContext.current)
    )
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val subscriptionState by viewModel.subscriptionState.collectAsState()
    val usageData by viewModel.usageData.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var selectedBillingCycle by remember { mutableStateOf(BillingCycle.MONTHLY) }
    var selectedTier by remember { mutableStateOf<SubscriptionTier?>(null) }
    
    // Handle errors
    LaunchedEffect(subscriptionState.error) {
        subscriptionState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Upgrade ReefScan",
                        color = Color.White
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepOcean
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = DeepOcean
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    SubscriptionHeader(currentTier = subscriptionState.tier)
                }
                
                // Billing cycle toggle
                item {
                    BillingCycleToggle(
                        selectedCycle = selectedBillingCycle,
                        onCycleSelected = { selectedBillingCycle = it }
                    )
                }
                
                // Tier cards - Simple 2-tier model
                item {
                    TierCard(
                        tier = SubscriptionTier.FREE,
                        isCurrentTier = subscriptionState.tier == SubscriptionTier.FREE,
                        isSelected = selectedTier == SubscriptionTier.FREE,
                        billingCycle = selectedBillingCycle,
                        onSelect = { selectedTier = SubscriptionTier.FREE }
                    )
                }
                
                item {
                    TierCard(
                        tier = SubscriptionTier.PRO,
                        isCurrentTier = subscriptionState.tier == SubscriptionTier.PRO,
                        isSelected = selectedTier == SubscriptionTier.PRO,
                        billingCycle = selectedBillingCycle,
                        isRecommended = true,
                        onSelect = { selectedTier = SubscriptionTier.PRO }
                    )
                }
                
                // Feature comparison table
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    FeatureComparisonTable()
                }
                
                // Footer with legal text
                item {
                    LegalFooter()
                }
                
                // Add some bottom padding
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
            
            // Purchase button at bottom
            if (selectedTier != null && selectedTier != SubscriptionTier.FREE && selectedTier != subscriptionState.tier) {
                PurchaseButton(
                    tier = selectedTier!!,
                    billingCycle = selectedBillingCycle,
                    isLoading = subscriptionState.isLoading,
                    onPurchase = {
                        activity?.let { act ->
                            viewModel.purchaseSubscription(
                                activity = act,
                                tier = selectedTier!!,
                                isYearly = selectedBillingCycle == BillingCycle.YEARLY,
                                onSuccess = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Welcome to ${selectedTier!!.displayName}!")
                                    }
                                    onNavigateBack()
                                },
                                onError = { error ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(error)
                                    }
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun SubscriptionHeader(currentTier: SubscriptionTier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AquaBlue.copy(alpha = 0.3f), AquaBlue.copy(alpha = 0.1f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = AquaBlue,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Unlock More Scans",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (currentTier == SubscriptionTier.FREE) {
                "Upgrade to scan more reef life every day"
            } else {
                "Currently on ${currentTier.displayName} plan"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

enum class BillingCycle {
    MONTHLY, YEARLY
}

@Composable
fun BillingCycleToggle(
    selectedCycle: BillingCycle,
    onCycleSelected: (BillingCycle) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(4.dp)
        ) {
            BillingCycleButton(
                text = "Monthly",
                isSelected = selectedCycle == BillingCycle.MONTHLY,
                onClick = { onCycleSelected(BillingCycle.MONTHLY) },
                modifier = Modifier.weight(1f)
            )
            
            BillingCycleButton(
                text = "Yearly (Save 33%)",
                isSelected = selectedCycle == BillingCycle.YEARLY,
                onClick = { onCycleSelected(BillingCycle.YEARLY) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BillingCycleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) AquaBlue else Color.Transparent,
        label = "bgColor"
    )
    
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) DeepOcean else Color.White,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp)
        )
    }
}

@Composable
fun TierCard(
    tier: SubscriptionTier,
    isCurrentTier: Boolean,
    isSelected: Boolean,
    billingCycle: BillingCycle,
    isRecommended: Boolean = false,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        isRecommended && isSelected -> Color(0xFFFFD700)
        isSelected -> AquaBlue
        isRecommended -> Color(0xFFFFD700).copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    
    val price = when (tier) {
        SubscriptionTier.FREE -> "Free"
        SubscriptionTier.PRO -> if (billingCycle == BillingCycle.MONTHLY) 
            TierPricing.PRO_MONTHLY_PRICE else TierPricing.PRO_YEARLY_PRICE
    }
    
    val period = when {
        tier == SubscriptionTier.FREE -> ""
        billingCycle == BillingCycle.MONTHLY -> "/month"
        else -> "/year"
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected || isRecommended) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tier == SubscriptionTier.PRO) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = tier.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row {
                    if (isRecommended) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFD700)
                        ) {
                            Text(
                                text = "BEST VALUE",
                                style = MaterialTheme.typography.labelSmall,
                                color = DeepOcean,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    if (isCurrentTier) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Seafoam.copy(alpha = 0.2f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = Seafoam,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "CURRENT",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Seafoam,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Price
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.headlineMedium,
                    color = when (tier) {
                        SubscriptionTier.PRO -> Color(0xFFFFD700)
                        else -> Color.White
                    },
                    fontWeight = FontWeight.Bold
                )
                if (period.isNotEmpty()) {
                    Text(
                        text = period,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Features
            TierFeatures.getFeaturesForTier(tier).take(4).forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Seafoam,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun FeatureComparisonTable(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Compare Plans",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Feature",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Free",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(70.dp)
                )
                Text(
                    text = "Pro",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(70.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ComparisonRow2("Daily Scans", "3", "20")
            ComparisonRow2("Scan History", "10", "∞")
            ComparisonRow2("Tank Profiles", "1", "∞")
            ComparisonRow2Bool("Export PDF", false, true)
            ComparisonRow2Bool("Priority Processing", false, true)
        }
    }
}

@Composable
fun ComparisonRow2(
    feature: String,
    free: String,
    pro: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = free,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            modifier = Modifier.width(70.dp)
        )
        Text(
            text = pro,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFFFD700),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(70.dp)
        )
    }
}

@Composable
fun ComparisonRow2Bool(
    feature: String,
    free: Boolean,
    pro: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = feature,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.weight(1f)
        )
        
        Box(
            modifier = Modifier.width(70.dp),
            contentAlignment = Alignment.Center
        ) {
            if (free) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Yes",
                    tint = Seafoam,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "No",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        
        Box(
            modifier = Modifier.width(70.dp),
            contentAlignment = Alignment.Center
        ) {
            if (pro) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Yes",
                    tint = Seafoam,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "No",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun PurchaseButton(
    tier: SubscriptionTier,
    billingCycle: BillingCycle,
    isLoading: Boolean,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val price = when (tier) {
        SubscriptionTier.PRO -> if (billingCycle == BillingCycle.MONTHLY) 
            TierPricing.PRO_MONTHLY_PRICE else TierPricing.PRO_YEARLY_PRICE
        else -> ""
    }
    
    Button(
        onClick = onPurchase,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (tier == SubscriptionTier.PRO) Color(0xFFFFD700) else AquaBlue
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = DeepOcean
            )
        } else {
            Text(
                text = "Subscribe to ${tier.displayName} • $price",
                style = MaterialTheme.typography.titleMedium,
                color = DeepOcean,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun LegalFooter(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Subscriptions auto-renew unless cancelled at least 24 hours before the end of the current period. " +
                   "Your account will be charged for renewal within 24 hours prior to the end of the current period. " +
                   "Manage subscriptions in your Google Play settings.",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Terms of Service",
                style = MaterialTheme.typography.labelSmall,
                color = AquaBlue,
                textDecoration = TextDecoration.Underline
            )
            Text(
                text = "Privacy Policy",
                style = MaterialTheme.typography.labelSmall,
                color = AquaBlue,
                textDecoration = TextDecoration.Underline
            )
        }
    }
}

