package com.bitcraftapps.reefscan.ui.screens

import android.content.Context
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.bitcraftapps.reefscan.data.local.ScanEntity
import com.bitcraftapps.reefscan.data.local.ScanRepository
import com.bitcraftapps.reefscan.ui.components.CategoryChip
import com.bitcraftapps.reefscan.ui.components.GlassmorphicCard
import com.bitcraftapps.reefscan.ui.components.IssueBadge
import com.bitcraftapps.reefscan.ui.theme.AquaBlue
import com.bitcraftapps.reefscan.ui.theme.AquaBlueDark
import com.bitcraftapps.reefscan.ui.theme.CoralAccent
import com.bitcraftapps.reefscan.ui.theme.DeepOcean
import com.bitcraftapps.reefscan.ui.theme.DeepOceanDark
import com.bitcraftapps.reefscan.ui.theme.GlassWhite
import com.bitcraftapps.reefscan.ui.theme.GlassWhiteBorder
import com.bitcraftapps.reefscan.ui.theme.StatusHealthy
import com.bitcraftapps.reefscan.ui.theme.StatusWarning
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScansScreen(
    tankId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToResults: (String) -> Unit,
    onNavigateToCamera: () -> Unit,
    viewModel: SavedScansViewModel = viewModel(factory = SavedScansViewModelFactory(LocalContext.current, tankId))
) {
    val allScans by viewModel.scans.collectAsState()
    var isVisible by remember { mutableStateOf(false) }
    
    // Date filter state - default to today
    var selectedDate by remember { mutableStateOf(getTodayStart()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Filter scans by selected date
    val filteredScans by remember(allScans, selectedDate) {
        derivedStateOf {
            val dayStart = selectedDate
            val dayEnd = dayStart + 24 * 60 * 60 * 1000 // Add 24 hours
            allScans.filter { scan ->
                scan.timestamp in dayStart until dayEnd
            }
        }
    }
    
    // Check if there are any scans at all
    val hasAnyScans = allScans.isNotEmpty()
    
    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }
    
    // Date Picker Dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            // Convert to start of day in local timezone
                            val calendar = Calendar.getInstance()
                            calendar.timeInMillis = millis
                            calendar.set(Calendar.HOUR_OF_DAY, 0)
                            calendar.set(Calendar.MINUTE, 0)
                            calendar.set(Calendar.SECOND, 0)
                            calendar.set(Calendar.MILLISECOND, 0)
                            selectedDate = calendar.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", color = AquaBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = DeepOcean
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DeepOcean,
                    titleContentColor = Color.White,
                    headlineContentColor = Color.White,
                    weekdayContentColor = Color.White.copy(alpha = 0.7f),
                    subheadContentColor = Color.White.copy(alpha = 0.7f),
                    yearContentColor = Color.White,
                    currentYearContentColor = AquaBlue,
                    selectedYearContentColor = Color.White,
                    selectedYearContainerColor = AquaBlue,
                    dayContentColor = Color.White,
                    selectedDayContentColor = Color.White,
                    selectedDayContainerColor = AquaBlue,
                    todayContentColor = AquaBlue,
                    todayDateBorderColor = AquaBlue
                )
            )
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepOceanDark,
                        DeepOcean,
                        AquaBlueDark.copy(alpha = 0.4f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
        ) {
            // Top bar with animation
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(300)) + slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                )
            ) {
                TopBar(
                    title = "Saved Scans",
                    scanCount = filteredScans.size,
                    totalCount = allScans.size,
                    onNavigateBack = onNavigateBack,
                    onCalendarClick = { showDatePicker = true }
                )
            }
            
            // Date selector bar
            AnimatedVisibility(
                visible = isVisible && hasAnyScans,
                enter = fadeIn(tween(300, delayMillis = 100)) + slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(400, delayMillis = 100, easing = FastOutSlowInEasing)
                )
            ) {
                DateSelectorBar(
                    selectedDate = selectedDate,
                    onDateChange = { selectedDate = it },
                    onCalendarClick = { showDatePicker = true }
                )
            }
            
            // Content - Empty state for no scans at all
            AnimatedVisibility(
                visible = !hasAnyScans && isVisible,
                enter = fadeIn(tween(400, delayMillis = 200)),
                exit = fadeOut(tween(200))
            ) {
                SavedScansEmptyState(onNavigateToCamera = onNavigateToCamera)
            }
            
            // Content - No scans for selected date
            AnimatedVisibility(
                visible = hasAnyScans && filteredScans.isEmpty() && isVisible,
                enter = fadeIn(tween(400, delayMillis = 200)),
                exit = fadeOut(tween(200))
            ) {
                NoScansForDateState(
                    selectedDate = selectedDate,
                    onViewAllDates = { selectedDate = 0L } // Setting to 0 won't match anything, but we can add "All" option
                )
            }
            
            // Scans list
            AnimatedVisibility(
                visible = filteredScans.isNotEmpty() && isVisible,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200))
            ) {
                LazyColumn(
        modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = filteredScans,
                        key = { _, scan -> scan.id }
                    ) { index, scan ->
                        AnimatedScanListItem(
                            scan = scan,
                            index = index,
                            onClick = { onNavigateToResults(scan.id.toString()) },
                            onDelete = { viewModel.deleteScan(scan.id) }
                        )
                    }
                }
            }
        }
    }
}

private fun getTodayStart(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@Composable
private fun TopBar(
    title: String,
    scanCount: Int,
    totalCount: Int,
    onNavigateBack: () -> Unit,
    onCalendarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
        
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (totalCount > 0) {
                Text(
                    text = "$scanCount of $totalCount scan${if (totalCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
        
        IconButton(
            onClick = onCalendarClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(GlassWhite)
        ) {
            Icon(
                imageVector = Icons.Filled.CalendarMonth,
                contentDescription = "Select Date",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun DateSelectorBar(
    selectedDate: Long,
    onDateChange: (Long) -> Unit,
    onCalendarClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val today = getTodayStart()
    
    // Generate dates: 3 days before and 3 days after selected
    val dates = remember(selectedDate) {
        (-3..3).map { offset ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selectedDate
            calendar.add(Calendar.DAY_OF_YEAR, offset)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous day button
        IconButton(
            onClick = {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = selectedDate
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                onDateChange(calendar.timeInMillis)
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronLeft,
                contentDescription = "Previous day",
                tint = Color.White.copy(alpha = 0.7f)
            )
    }
        
        // Date chips
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(dates) { _, date ->
                val isSelected = date == selectedDate
                val isToday = date == today
                val dayFormat = SimpleDateFormat("d", Locale.getDefault())
                val weekdayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) AquaBlue.copy(alpha = 0.3f)
                            else Color.Transparent
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            color = if (isSelected) AquaBlue else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onDateChange(date) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = weekdayFormat.format(Date(date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) AquaBlue else Color.White.copy(alpha = 0.6f),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayFormat.format(Date(date)),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(AquaBlue)
                            )
                    }
                }
            }
        }
        
        // Next day button
        IconButton(
            onClick = {
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = selectedDate
                calendar.add(Calendar.DAY_OF_YEAR, 1)
                onDateChange(calendar.timeInMillis)
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = "Next day",
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun NoScansForDateState(
    selectedDate: Long,
    onViewAllDates: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📅",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.scale(1.2f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "No Scans on This Date",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = dateFormat.format(Date(selectedDate)),
            style = MaterialTheme.typography.bodyLarge,
            color = AquaBlue
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Try selecting a different date or take a new scan",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AnimatedScanListItem(
    scan: ScanEntity,
    index: Int,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    
    // Staggered entrance animation
    var isItemVisible by remember { mutableStateOf(false) }
    val animatedAlpha = remember { Animatable(0f) }
    val animatedOffsetY = remember { Animatable(30f) }
    val animatedScale = remember { Animatable(0.95f) }
    
    LaunchedEffect(Unit) {
        delay(index * 50L) // Stagger delay
        isItemVisible = true
        scope.launch {
            animatedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
        scope.launch {
            animatedOffsetY.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        animatedScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    
    // Delete state
    var isDeleting by remember { mutableStateOf(false) }
    val deleteAnimatedScale = remember { Animatable(1f) }
    
    LaunchedEffect(isDeleting) {
        if (isDeleting) {
            deleteAnimatedScale.animateTo(
                targetValue = 0f,
                animationSpec = tween(200)
            )
            onDelete()
        }
    }
    
    Box(
        modifier = Modifier
            .graphicsLayer {
                alpha = animatedAlpha.value * deleteAnimatedScale.value
                translationY = animatedOffsetY.value
                scaleX = animatedScale.value * deleteAnimatedScale.value
                scaleY = animatedScale.value * deleteAnimatedScale.value
            }
    ) {
        SwipeToDeleteItem(
            onDelete = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                isDeleting = true
            }
        ) {
            ScanListItem(
                scan = scan,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val deleteThreshold = with(density) { 100.dp.toPx() }
    val scope = rememberCoroutineScope()
    val animatedOffset = remember { Animatable(0f) }
    
    LaunchedEffect(offsetX) {
        animatedOffset.snapTo(offsetX)
    }
    
    Box(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Delete background - only visible when swiping
        if (offsetX < -10f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(CoralAccent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = CoralAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.labelLarge,
                        color = CoralAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        
        // Swipeable content
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -deleteThreshold) {
                                onDelete()
                            } else {
                                scope.launch {
                                    animatedOffset.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                    offsetX = 0f
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = offsetX + dragAmount
                            // Only allow swiping left
                            offsetX = newOffset.coerceIn(-deleteThreshold * 1.5f, 0f)
                        }
                    )
                }
        ) {
            content()
        }
    }
}

@Composable
private fun ScanListItem(
    scan: ScanEntity,
    onClick: () -> Unit
) {
    val scanResult = scan.toScanResult()
    val issueStatus = scanResult.getIssueStatus()
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(scan.timestamp))
    val interactionSource = remember { MutableInteractionSource() }
    
    // Check for multi-item scan
    val identifications = scan.getIdentificationsList()
    val hasMultipleItems = identifications.isNotEmpty()
    val problemCount = identifications.count { it.isProblem }
    val healthyCount = identifications.count { !it.isProblem }
    
    GlassmorphicCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(color = Color.White.copy(alpha = 0.2f)),
                onClick = onClick
            ),
        contentPadding = 12.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            AsyncImage(
                model = Uri.parse(scan.imagePath),
                contentDescription = "Scan thumbnail",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Title - show tank health for multi-item, name for single
                if (hasMultipleItems && scan.tankHealth.isNotBlank()) {
                    Text(
                        text = "Tank: ${scan.tankHealth}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = scan.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Category and status OR counts
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasMultipleItems) {
                        // Show counts for multi-item scans
                        if (healthyCount > 0) {
                            ItemCountBadge(
                                count = healthyCount,
                                label = "healthy",
                                color = StatusHealthy
                            )
                        }
                        if (problemCount > 0) {
                            ItemCountBadge(
                                count = problemCount,
                                label = "issues",
                                color = StatusWarning
                            )
                        }
                    } else {
                        CategoryChip(category = scan.category)
                        IssueBadge(status = issueStatus)
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                // Time only (date is shown in selector)
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            
            // Chevron
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ItemCountBadge(
    count: Int,
    label: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SavedScansEmptyState(onNavigateToCamera: () -> Unit) {
    var isVisible by remember { mutableStateOf(false) }
    val animatedAlpha = remember { Animatable(0f) }
    val animatedScale = remember { Animatable(0.8f) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        delay(200)
        isVisible = true
        scope.launch {
            animatedAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(400)
            )
        }
        animatedScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .graphicsLayer {
                alpha = animatedAlpha.value
                scaleX = animatedScale.value
                scaleY = animatedScale.value
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Empty state icon with subtle animation
        Text(
            text = "🐠",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.scale(1.5f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "No Saved Scans Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = "Start scanning reef life to build your collection",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = onNavigateToCamera,
            colors = ButtonDefaults.buttonColors(
                containerColor = AquaBlue
            ),
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth(0.7f)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Start Scanning",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * ViewModel for SavedScansScreen
 */
class SavedScansViewModel(
    private val scanRepository: ScanRepository,
    private val tankId: Long
) : ViewModel() {
    
    val scans: StateFlow<List<ScanEntity>> = scanRepository.getScansForTank(tankId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun deleteScan(scanId: Long) {
        viewModelScope.launch {
            scanRepository.deleteScan(scanId)
        }
    }
}

/**
 * Factory for SavedScansViewModel
 */
class SavedScansViewModelFactory(
    private val context: Context,
    private val tankId: Long
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SavedScansViewModel::class.java)) {
            return SavedScansViewModel(
                scanRepository = ScanRepository(context),
                tankId = tankId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
