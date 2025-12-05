package com.bitcraftapps.reefscan.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.MotionEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bitcraftapps.reefscan.data.model.Identification
import com.bitcraftapps.reefscan.data.model.IssueStatus
import com.bitcraftapps.reefscan.data.model.ScanResult
import com.bitcraftapps.reefscan.ui.components.CategoryChip
import com.bitcraftapps.reefscan.ui.components.GlassmorphicCard
import com.bitcraftapps.reefscan.ui.components.IssueBadge
import com.bitcraftapps.reefscan.ui.components.SeverityBadge
import com.bitcraftapps.reefscan.ui.theme.AquaBlue
import com.bitcraftapps.reefscan.ui.theme.CoralAccent
import com.bitcraftapps.reefscan.ui.theme.DeepOcean
import com.bitcraftapps.reefscan.ui.theme.GlassWhite
import com.bitcraftapps.reefscan.ui.theme.Seafoam
import com.bitcraftapps.reefscan.ui.theme.StatusHealthy
import com.bitcraftapps.reefscan.ui.theme.StatusProblem
import com.bitcraftapps.reefscan.ui.theme.StatusWarning
import com.bitcraftapps.reefscan.util.WikipediaHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    scanId: String,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    viewModel: ResultsViewModel = viewModel(factory = ResultsViewModelFactory(LocalContext.current))
) {
    val state by viewModel.state.collectAsState()
    var isVisible by remember { mutableStateOf(false) }
    
    // Bottom Sheet State for Wiki
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedWikiUrl by remember { mutableStateOf<String?>(null) }
    var isLoadingWiki by remember { mutableStateOf(false) }
    
    // Load scan data
    LaunchedEffect(scanId) {
        viewModel.loadScan(scanId)
        delay(100) // Small delay for entrance animation
        isVisible = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepOcean,
                        DeepOcean,
                        AquaBlue.copy(alpha = 0.45f)
                    )
                )
            )
    ) {
        when (val currentState = state) {
            is ResultsState.Loading -> {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
                    CircularProgressIndicator(color = AquaBlue)
                }
            }
            is ResultsState.Success -> {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(300)) +
                            slideInVertically(
                                animationSpec = tween(400),
                                initialOffsetY = { it / 4 }
                            )
                ) {
                    ResultsContent(
                        scanResult = currentState.scanResult,
                        imageUri = currentState.imageUri,
                        isSaved = currentState.isSaved,
                        onSave = { viewModel.saveScan() },
                        onNavigateBack = onNavigateBack,
                        onNavigateToCamera = onNavigateToCamera,
                        onOpenWiki = { name ->
                            scope.launch {
                                try {
                                    isLoadingWiki = true
                                    showBottomSheet = true
                                    // Use Wikipedia API to find correct article
                                    selectedWikiUrl = WikipediaHelper.getWikipediaUrl(name)
                                    isLoadingWiki = false
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    isLoadingWiki = false
                                }
                            }
                        }
                    )
                }
            }
            is ResultsState.Error -> {
                ErrorState(
                    message = currentState.message,
                    onNavigateBack = onNavigateBack
                )
            }
        }
        
        // Wikipedia Bottom Sheet
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { 
                    showBottomSheet = false
                    selectedWikiUrl = null
                },
                sheetState = sheetState,
                containerColor = DeepOcean,
                contentColor = Color.White,
                modifier = Modifier.fillMaxHeight(0.9f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 32.dp)
                ) {
                    if (isLoadingWiki || selectedWikiUrl == null) {
                        // Show loading indicator while fetching Wikipedia URL
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = AquaBlue)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Finding article...",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    } else {
                        AndroidView(
                            factory = { context ->
                                @SuppressLint("ClickableViewAccessibility")
                                object : WebView(context) {
                                    override fun onTouchEvent(event: MotionEvent?): Boolean {
                                        // Request parent to not intercept touch events
                                        // This allows the WebView to scroll properly
                                        when (event?.action) {
                                            MotionEvent.ACTION_DOWN -> {
                                                parent?.requestDisallowInterceptTouchEvent(true)
                                            }
                                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                parent?.requestDisallowInterceptTouchEvent(false)
                                            }
                                        }
                                        return super.onTouchEvent(event)
                                    }
                                }.apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    
                                    // Enable scrolling
                                    isVerticalScrollBarEnabled = true
                                    isHorizontalScrollBarEnabled = true
                                    scrollBarStyle = WebView.SCROLLBARS_INSIDE_OVERLAY
                                    
                                    // Enable nested scrolling for bottom sheet compatibility
                                    isNestedScrollingEnabled = true
                                    
                                    webViewClient = WebViewClient()
                                    loadUrl(selectedWikiUrl!!)
                                }
                            },
                            update = { webView ->
                                // Update URL if it changes
                                if (webView.url != selectedWikiUrl) {
                                    webView.loadUrl(selectedWikiUrl!!)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsContent(
    scanResult: ScanResult,
    imageUri: String?,
    isSaved: Boolean,
    onSave: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onOpenWiki: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val hasMultipleIdentifications = scanResult.identifications.isNotEmpty()
    val problems = scanResult.getProblems()
    val healthyItems = scanResult.getHealthyItems()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(GlassWhite)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Scan Results",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.width(44.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Image & Overview Card
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Image thumbnail
                if (imageUri != null) {
                    AsyncImage(
                        model = Uri.parse(imageUri),
                        contentDescription = "Scanned image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                // Tank Health Status
                if (hasMultipleIdentifications) {
                    TankHealthHeader(
                        tankHealth = scanResult.tankHealth,
                        problemCount = problems.size,
                        healthyCount = healthyItems.size
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Summary
                    if (scanResult.summary.isNotBlank()) {
                        Text(
                            text = scanResult.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                } else {
                    // Legacy single-item display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = scanResult.name,
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { onOpenWiki(scanResult.name) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "Wiki Info",
                                tint = AquaBlue
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryChip(category = scanResult.category)
                        IssueBadge(status = scanResult.getIssueStatus())
                    }
                    
                    if (scanResult.isProblem && scanResult.severity != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SeverityBadge(severity = scanResult.severity)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ConfidenceIndicator(confidence = scanResult.confidence)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = scanResult.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
        
        // Problems Section (if multiple identifications)
        if (hasMultipleIdentifications && problems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Section header
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Issues Detected (${problems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    problems.forEachIndexed { index, identification ->
                        IdentificationItem(
                            identification = identification,
                            showDivider = index < problems.lastIndex,
                            onInfoClick = { onOpenWiki(identification.name) }
                        )
                    }
                }
            }
        }
        
        // Healthy Items Section (if multiple identifications)
        if (hasMultipleIdentifications && healthyItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Section header
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = StatusHealthy,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Healthy Livestock (${healthyItems.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    healthyItems.forEachIndexed { index, identification ->
                        IdentificationItem(
                            identification = identification,
                            showDivider = index < healthyItems.lastIndex,
                            onInfoClick = { onOpenWiki(identification.name) }
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Recommendations card
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = Seafoam,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                scanResult.recommendations.forEachIndexed { index, recommendation ->
                    RecommendationItem(
                        number = index + 1,
                        text = recommendation
                    )
                    if (index < scanResult.recommendations.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Action buttons
        if (!isSaved) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Seafoam
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(listOf(Seafoam, Seafoam))
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Save Scan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Seafoam.copy(alpha = 0.2f))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Seafoam,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Saved",
                    style = MaterialTheme.typography.titleMedium,
                    color = Seafoam,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = onNavigateToCamera,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AquaBlue
            )
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Scan Again",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TankHealthHeader(
    tankHealth: String,
    problemCount: Int,
    healthyCount: Int
) {
    val healthColor = when (tankHealth.lowercase()) {
        "excellent" -> StatusHealthy
        "good" -> Seafoam
        "fair" -> StatusWarning
        "needs attention" -> CoralAccent
        "critical" -> StatusProblem
        else -> AquaBlue
    }
    
    val healthIcon = when (tankHealth.lowercase()) {
        "excellent", "good" -> Icons.Default.CheckCircle
        "fair", "needs attention" -> Icons.Default.Warning
        "critical" -> Icons.Default.Error
        else -> Icons.Default.CheckCircle
    }
    
    Column {
        // Tank health badge
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(healthColor.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = healthIcon,
                contentDescription = null,
                tint = healthColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tank Health: $tankHealth",
                style = MaterialTheme.typography.titleMedium,
                color = healthColor,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Count summary
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (healthyCount > 0) {
                CountBadge(
                    count = healthyCount,
                    label = "Healthy",
                    color = StatusHealthy
                )
            }
            if (problemCount > 0) {
                CountBadge(
                    count = problemCount,
                    label = "Issues",
                    color = StatusWarning
                )
            }
        }
    }
}

@Composable
private fun CountBadge(
    count: Int,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun IdentificationItem(
    identification: Identification,
    showDivider: Boolean,
    onInfoClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Name Row with Info Icon
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = identification.name,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "Wiki Info",
                    tint = AquaBlue,
                    modifier = Modifier.size(20.dp)
                )
    }
}

        Spacer(modifier = Modifier.height(4.dp))
        
        // Category & Status chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryChip(category = identification.category)
            
            if (identification.isProblem && identification.severity != null) {
                SeverityBadge(severity = identification.severity)
            } else {
                IssueBadge(status = identification.getIssueStatus())
            }
            
            // Confidence
            Text(
                text = "${identification.confidence}%",
                style = MaterialTheme.typography.labelSmall,
                color = getConfidenceColor(identification.confidence),
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Description
        Text(
            text = identification.description,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f)
        )
        
        if (showDivider) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ConfidenceIndicator(confidence: Int) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Confidence",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = "$confidence%",
                style = MaterialTheme.typography.titleMedium,
                color = getConfidenceColor(confidence),
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
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
                    .fillMaxWidth(confidence / 100f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                getConfidenceColor(confidence),
                                getConfidenceColor(confidence).copy(alpha = 0.7f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
private fun RecommendationItem(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AquaBlue.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = AquaBlue,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "😕",
            style = MaterialTheme.typography.displayLarge
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Unable to Load Results",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onNavigateBack,
            colors = ButtonDefaults.buttonColors(containerColor = AquaBlue)
        ) {
            Text("Go Back")
        }
    }
}

private fun getConfidenceColor(confidence: Int): Color {
    return when {
        confidence >= 80 -> Seafoam
        confidence >= 50 -> AquaBlue
        else -> Color(0xFFFFA726) // Amber
    }
}
