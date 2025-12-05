package com.bitcraftapps.reefscan.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun RatingBar(
    rating: Int,
    onRatingChanged: ((Int) -> Unit)? = null,
    maxRating: Int = 5,
    starSize: Dp = 24.dp,
    activeColor: Color = Color(0xFFFFD700),
    inactiveColor: Color = Color.Gray
) {
    Row {
        for (i in 1..maxRating) {
            Icon(
                imageVector = if (i <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Star $i",
                tint = if (i <= rating) activeColor else inactiveColor,
                modifier = Modifier
                    .size(starSize)
                    .clickable(enabled = onRatingChanged != null) {
                        onRatingChanged?.invoke(i)
                    }
            )
        }
    }
}

