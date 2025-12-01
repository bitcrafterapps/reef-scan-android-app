package com.example.reefscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reefscan.data.model.CategoryType
import com.example.reefscan.ui.theme.CategoryAlgae
import com.example.reefscan.ui.theme.CategoryDisease
import com.example.reefscan.ui.theme.CategoryFish
import com.example.reefscan.ui.theme.CategoryInvertebrate
import com.example.reefscan.ui.theme.CategoryLPSCoral
import com.example.reefscan.ui.theme.CategoryPest
import com.example.reefscan.ui.theme.CategorySPSCoral
import com.example.reefscan.ui.theme.CategorySoftCoral
import com.example.reefscan.ui.theme.TextPrimary
import com.example.reefscan.ui.theme.WarningAmber

/**
 * A chip component displaying the category of identified marine life
 */
@Composable
fun CategoryChip(
    category: String,
    modifier: Modifier = Modifier
) {
    val categoryType = CategoryType.fromString(category)
    val chipColor = getCategoryColor(categoryType)
    val icon = getCategoryIcon(categoryType)
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(chipColor.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = chipColor,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        Text(
            text = categoryType.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = chipColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Get the color for a category type
 */
fun getCategoryColor(categoryType: CategoryType): Color {
    return when (categoryType) {
        CategoryType.FISH -> CategoryFish
        CategoryType.SPS_CORAL -> CategorySPSCoral
        CategoryType.LPS_CORAL -> CategoryLPSCoral
        CategoryType.SOFT_CORAL -> CategorySoftCoral
        CategoryType.INVERTEBRATE -> CategoryInvertebrate
        CategoryType.ALGAE -> CategoryAlgae
        CategoryType.PEST -> CategoryPest
        CategoryType.DISEASE -> CategoryDisease
        CategoryType.TANK_ISSUE -> WarningAmber
        CategoryType.EQUIPMENT -> CategoryFish
        CategoryType.UNKNOWN -> TextPrimary.copy(alpha = 0.5f)
    }
}

/**
 * Get the icon for a category type
 */
fun getCategoryIcon(categoryType: CategoryType): ImageVector {
    return when (categoryType) {
        CategoryType.FISH -> Icons.Default.Pets
        CategoryType.SPS_CORAL -> Icons.Default.Spa
        CategoryType.LPS_CORAL -> Icons.Default.Spa
        CategoryType.SOFT_CORAL -> Icons.Default.Spa
        CategoryType.INVERTEBRATE -> Icons.Default.Pets
        CategoryType.ALGAE -> Icons.Default.Grass
        CategoryType.PEST -> Icons.Default.Warning
        CategoryType.DISEASE -> Icons.Default.Warning
        CategoryType.TANK_ISSUE -> Icons.Default.WaterDrop
        CategoryType.EQUIPMENT -> Icons.Default.WaterDrop
        CategoryType.UNKNOWN -> Icons.Default.WaterDrop
    }
}

