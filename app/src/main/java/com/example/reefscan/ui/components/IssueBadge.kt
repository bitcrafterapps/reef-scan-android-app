package com.example.reefscan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.reefscan.data.model.IssueStatus

/**
 * A badge component displaying the health/issue status
 */
@Composable
fun IssueBadge(
    status: IssueStatus,
    modifier: Modifier = Modifier
) {
    val backgroundColor = status.getBackgroundColor()
    val contentColor = status.getColor()
    val icon = when (status) {
        IssueStatus.HEALTHY -> Icons.Default.Check
        IssueStatus.WARNING -> Icons.Default.Warning
        IssueStatus.PROBLEM -> Icons.Default.Warning
    }
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        
        Spacer(modifier = Modifier.width(6.dp))
        
        Text(
            text = status.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * A severity badge component
 */
@Composable
fun SeverityBadge(
    severity: String?,
    modifier: Modifier = Modifier
) {
    if (severity == null) return
    
    val status = when (severity.lowercase()) {
        "high" -> IssueStatus.PROBLEM
        "medium" -> IssueStatus.WARNING
        "low" -> IssueStatus.WARNING
        else -> return
    }
    
    val backgroundColor = status.getBackgroundColor()
    val contentColor = status.getColor()
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$severity Severity",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

