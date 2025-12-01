package com.example.reefscan.data.model

import androidx.compose.ui.graphics.Color
import com.example.reefscan.ui.theme.CoralAccent
import com.example.reefscan.ui.theme.Seafoam
import com.example.reefscan.ui.theme.WarningAmber

/**
 * Enum representing the health/issue status of an identified marine organism
 */
enum class IssueStatus(
    val displayName: String,
    val emoji: String
) {
    /**
     * The organism is healthy and poses no concern
     */
    HEALTHY(
        displayName = "Healthy",
        emoji = "✓"
    ),
    
    /**
     * The organism shows signs of concern or is a minor nuisance
     */
    WARNING(
        displayName = "Warning",
        emoji = "⚠"
    ),
    
    /**
     * The organism represents a significant problem that needs attention
     */
    PROBLEM(
        displayName = "Problem",
        emoji = "⚠"
    );
    
    /**
     * Returns the appropriate color for displaying this status
     */
    fun getColor(): Color {
        return when (this) {
            HEALTHY -> Seafoam
            WARNING -> WarningAmber
            PROBLEM -> CoralAccent
        }
    }
    
    /**
     * Returns the appropriate background color (lighter variant)
     */
    fun getBackgroundColor(): Color {
        return when (this) {
            HEALTHY -> Seafoam.copy(alpha = 0.2f)
            WARNING -> WarningAmber.copy(alpha = 0.2f)
            PROBLEM -> CoralAccent.copy(alpha = 0.2f)
        }
    }
    
    companion object {
        /**
         * Determines status from severity string
         */
        fun fromSeverity(severity: String?, isProblem: Boolean): IssueStatus {
            return when {
                !isProblem -> HEALTHY
                severity == null -> if (isProblem) PROBLEM else HEALTHY
                severity.equals("High", ignoreCase = true) -> PROBLEM
                severity.equals("Medium", ignoreCase = true) -> WARNING
                severity.equals("Low", ignoreCase = true) -> WARNING
                else -> HEALTHY
            }
        }
    }
}

