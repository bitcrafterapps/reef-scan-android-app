package com.bitcraftapps.reefscan.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitcraftapps.reefscan.billing.SubscriptionTier
import com.bitcraftapps.reefscan.billing.UsageData
import com.bitcraftapps.reefscan.ui.theme.AquaBlue
import com.bitcraftapps.reefscan.ui.theme.CoralAccent
import com.bitcraftapps.reefscan.ui.theme.DeepOcean
import com.bitcraftapps.reefscan.ui.theme.Seafoam

/**
 * Displays the current scan usage with a progress indicator
 * Shows remaining scans and tier info
 */
@Composable
fun UsageIndicator(
    usageData: UsageData,
    modifier: Modifier = Modifier,
    onUpgradeClick: () -> Unit = {}
) {
    val progressColor by animateColorAsState(
        targetValue = when {
            usageData.hasReachedLimit -> CoralAccent
            usageData.isApproachingLimit -> Color(0xFFFFB74D) // Amber
            else -> Seafoam
        },
        animationSpec = tween(300),
        label = "progressColor"
    )
    
    val animatedProgress by animateFloatAsState(
        targetValue = usageData.usagePercentage,
        animationSpec = tween(500),
        label = "progress"
    )
    
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DeepOcean.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = AquaBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Daily Scans",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White
                    )
                }
                
                TierBadge(tier = usageData.tier)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(progressColor, progressColor.copy(alpha = 0.7f))
                            )
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (usageData.hasReachedLimit) {
                        "Limit reached"
                    } else {
                        "${usageData.remainingScans} of ${usageData.tier.dailyLimit} remaining"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (usageData.hasReachedLimit) CoralAccent else Color.White.copy(alpha = 0.7f)
                )
                
                if (usageData.tier != SubscriptionTier.PRO) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onUpgradeClick() }
                            .background(AquaBlue.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Upgrade,
                            contentDescription = null,
                            tint = AquaBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Upgrade",
                            style = MaterialTheme.typography.labelSmall,
                            color = AquaBlue,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact usage indicator for smaller spaces
 */
@Composable
fun CompactUsageIndicator(
    usageData: UsageData,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val progressColor = when {
        usageData.hasReachedLimit -> CoralAccent
        usageData.isApproachingLimit -> Color(0xFFFFB74D)
        else -> Seafoam
    }
    
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = DeepOcean.copy(alpha = 0.8f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = progressColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${usageData.remainingScans}/${usageData.tier.dailyLimit}",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Badge showing the current subscription tier
 */
@Composable
fun TierBadge(
    tier: SubscriptionTier,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, textColor, icon) = when (tier) {
        SubscriptionTier.FREE -> Triple(
            Color.Gray.copy(alpha = 0.3f),
            Color.White.copy(alpha = 0.7f),
            null
        )
        SubscriptionTier.PRO -> Triple(
            Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500))),
            Color(0xFFFFD700),
            Icons.Default.Star
        )
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (tier == SubscriptionTier.PRO) Color.Transparent else backgroundColor as Color
    ) {
        Box(
            modifier = if (tier == SubscriptionTier.PRO) {
                Modifier
                    .background(backgroundColor as Brush, RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            } else {
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (tier == SubscriptionTier.PRO) DeepOcean else textColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = tier.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (tier == SubscriptionTier.PRO) DeepOcean else textColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Large usage display for dedicated sections
 */
@Composable
fun LargeUsageDisplay(
    usageData: UsageData,
    modifier: Modifier = Modifier
) {
    val progressColor = when {
        usageData.hasReachedLimit -> CoralAccent
        usageData.isApproachingLimit -> Color(0xFFFFB74D)
        else -> Seafoam
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular progress indicator
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(120.dp)
        ) {
            // Background circle
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            
            // Foreground progress (simplified - could be replaced with actual circular progress)
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(DeepOcean)
            )
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${usageData.remainingScans}",
                    style = MaterialTheme.typography.headlineLarge,
                    color = progressColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "of ${usageData.tier.dailyLimit} daily scans",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

