package com.bitcraftapps.reefscan.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.bitcraftapps.reefscan.data.model.GalleryImage
import com.bitcraftapps.reefscan.ui.components.RatingBar
import com.bitcraftapps.reefscan.ui.theme.AquaBlue
import com.bitcraftapps.reefscan.ui.theme.AquaBlueDark
import com.bitcraftapps.reefscan.ui.theme.DeepOcean
import com.bitcraftapps.reefscan.ui.theme.DeepOceanDark
import com.bitcraftapps.reefscan.ui.theme.GlassWhite
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun getGalleryTodayStart(): Long {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TankGalleryScreen(
    tankId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToDateGallery: (String) -> Unit = {}, // Keep for backwards compat but unused
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModelFactory(LocalContext.current, tankId))
) {
    val allImages by viewModel.allImages.collectAsState()
    val tank by viewModel.tank.collectAsState()
    val context = LocalContext.current
    
    // Date filter state - default to today
    var selectedDate by remember { mutableStateOf(getGalleryTodayStart()) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    // Filter images by selected date
    val filteredImages by remember(allImages, selectedDate) {
        derivedStateOf {
            val dayStart = selectedDate
            val dayEnd = dayStart + 24 * 60 * 60 * 1000 // Add 24 hours
            allImages.filter { image ->
                image.timestamp in dayStart until dayEnd
            }
        }
    }
    
    val hasAnyImages = allImages.isNotEmpty()
    
    // Add photo states
    var showAddDialog by remember { mutableStateOf(false) }
    var tempUri by remember { mutableStateOf<Uri?>(null) }
    
    // Full screen viewer
    var showFullScreen by remember { mutableStateOf(false) }
    var initialIndex by remember { mutableStateOf(0) }
    
    // Delete confirmation
    var imageToDelete by remember { mutableStateOf<GalleryImage?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
        showAddDialog = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempUri != null) {
            viewModel.addImages(listOf(tempUri!!))
        }
        showAddDialog = false
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
            colors = DatePickerDefaults.colors(containerColor = DeepOcean)
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
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AquaBlue,
                    contentColor = DeepOceanDark
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Photo")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(top = 24.dp)
            ) {
                // Top bar
                GalleryTopBar(
                    title = "Gallery",
                    photoCount = filteredImages.size,
                    totalCount = allImages.size,
                    onNavigateBack = onNavigateBack,
                    onCalendarClick = { showDatePicker = true }
                )
                
                // Date selector bar
                if (hasAnyImages) {
                    GalleryDateSelectorBar(
                        selectedDate = selectedDate,
                        onDateChange = { selectedDate = it },
                        onCalendarClick = { showDatePicker = true }
                    )
                }
                
                // Content
                when {
                    !hasAnyImages -> {
                        // Empty state
                        GalleryEmptyState(onAddClick = { showAddDialog = true })
                    }
                    filteredImages.isEmpty() -> {
                        // No photos for selected date
                        NoPhotosForDateState(selectedDate = selectedDate)
                    }
                    else -> {
                        // Photo grid
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredImages.size) { index ->
                                ImageThumbnail(
                                    galleryImage = filteredImages[index],
                                    onClick = {
                                        initialIndex = index
                                        showFullScreen = true
                                    },
                                    onDeleteClick = {
                                        imageToDelete = filteredImages[index]
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Full Screen Image Viewer
        if (showFullScreen && filteredImages.isNotEmpty()) {
            FullScreenImageViewer(
                images = filteredImages,
                initialIndex = initialIndex,
                onDismiss = { showFullScreen = false },
                onDelete = { image ->
                    imageToDelete = image
                },
                onShare = { image ->
                    shareImage(context, image.path)
                },
                onRatingChanged = { image, rating ->
                    viewModel.setRating(image, rating)
                }
            )
        }

        // Add Photo Dialog
        if (showAddDialog) {
            AddPhotoDialog(
                onDismiss = { showAddDialog = false },
                onCameraClick = {
                    val photoFile = createGalleryImageFile(context)
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        photoFile
                    )
                    tempUri = uri
                    cameraLauncher.launch(uri)
                },
                onGalleryClick = {
                    galleryLauncher.launch("image/*")
                }
            )
        }

        // Delete Confirmation Dialog
        if (imageToDelete != null) {
            AlertDialog(
                onDismissRequest = { imageToDelete = null },
                title = { Text("Delete Photo?") },
                text = { Text("Are you sure you want to delete this photo? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            imageToDelete?.let { img ->
                                viewModel.deleteImage(img)
                                if (filteredImages.size <= 1 && showFullScreen) {
                                    showFullScreen = false
                                }
                            }
                            imageToDelete = null
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { imageToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = DeepOcean,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun GalleryTopBar(
    title: String,
    photoCount: Int,
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
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (totalCount > 0) {
                Text(
                    text = "$photoCount of $totalCount photo${if (totalCount != 1) "s" else ""}",
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
private fun GalleryDateSelectorBar(
    selectedDate: Long,
    onDateChange: (Long) -> Unit,
    onCalendarClick: () -> Unit
) {
    val today = getGalleryTodayStart()
    
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
private fun GalleryEmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = AquaBlue.copy(alpha = 0.3f),
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Photos Yet",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Start building your tank's visual timeline",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = onAddClick) {
            Text("Add your first photo", color = AquaBlue)
        }
    }
}

@Composable
private fun NoPhotosForDateState(selectedDate: Long) {
    val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Photos on This Date",
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
            text = "Try selecting a different date",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AddPhotoDialog(
    onDismiss: () -> Unit,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = DeepOcean,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Add Photo",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCameraClick)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Camera",
                            tint = AquaBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Camera", color = Color.White)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onGalleryClick)
                            .padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = AquaBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Gallery", color = Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullScreenImageViewer(
    images: List<GalleryImage>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onDelete: (GalleryImage) -> Unit,
    onShare: (GalleryImage) -> Unit,
    onRatingChanged: (GalleryImage, Int) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val safeInitialIndex = initialIndex.coerceIn(0, images.lastIndex)
        val pagerState = rememberPagerState(initialPage = safeInitialIndex) { images.size }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val image = images.getOrNull(page)
                if (image != null) {
                    Image(
                        painter = rememberAsyncImagePainter(File(image.path)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { onDismiss() }
                    )
                }
            }
            
            // Top Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }

                Row {
                    IconButton(
                        onClick = {
                            val currentImage = images.getOrNull(pagerState.currentPage)
                            currentImage?.let { onShare(it) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    
                    IconButton(
                        onClick = {
                            val currentImage = images.getOrNull(pagerState.currentPage)
                            currentImage?.let { onDelete(it) }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color.White
                        )
                    }
                }
            }
            
            // Bottom Controls (Rating)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val currentImage = images.getOrNull(pagerState.currentPage)
                if (currentImage != null) {
                    Text(
                        text = "Rate this photo",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    RatingBar(
                        rating = currentImage.rating,
                        onRatingChanged = { newRating ->
                            onRatingChanged(currentImage, newRating)
                        },
                        starSize = 32.dp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DateGalleryScreen(
    tankId: Long,
    dateString: String,
    onNavigateBack: () -> Unit,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModelFactory(LocalContext.current, tankId))
) {
    val images by viewModel.images.collectAsState()
    // State for full screen viewer
    var showFullScreen by remember { mutableStateOf(false) }
    var initialIndex by remember { mutableStateOf(0) }
    
    // State for delete confirmation
    var imageToDelete by remember { mutableStateOf<GalleryImage?>(null) }
    
    // State for Add Photo
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var tempUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris)
        }
        showAddDialog = false
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempUri != null) {
            viewModel.addImages(listOf(tempUri!!))
        }
        showAddDialog = false
    }

    LaunchedEffect(dateString) {
        viewModel.loadImagesForDate(dateString)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        DeepOceanDark,
                        DeepOcean,
                        DeepOcean,
                        AquaBlueDark.copy(alpha = 0.6f)
                    )
                )
            )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AquaBlue,
                    contentColor = DeepOceanDark
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Photo")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, bottom = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (images.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No images found",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(images.size) { index ->
                            ImageThumbnail(
                                galleryImage = images[index],
                                onClick = {
                                    initialIndex = index
                                    showFullScreen = true
                                },
                                onDeleteClick = {
                                    imageToDelete = images[index]
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // Full Screen Image Viewer with Pager
        if (showFullScreen && images.isNotEmpty()) {
            Dialog(
                onDismissRequest = { showFullScreen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                // Ensure initial page is valid
                val safeInitialIndex = initialIndex.coerceIn(0, images.lastIndex)
                val pagerState = rememberPagerState(initialPage = safeInitialIndex) { images.size }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    // Horizontal Pager for Swiping
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val image = images.getOrNull(page)
                        if (image != null) {
                            Image(
                                painter = rememberAsyncImagePainter(File(image.path)),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable { showFullScreen = false }
                            )
                        }
                    }
                    
                    // Top Controls (Close, Share, Delete)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showFullScreen = false }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        Row {
                            // Share Button
                            IconButton(
                                onClick = {
                                    val currentImage = images.getOrNull(pagerState.currentPage)
                                    currentImage?.let { shareImage(context, it.path) }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White
                                )
                            }
                            
                            // Delete Button
                            IconButton(
                                onClick = {
                                    val currentImage = images.getOrNull(pagerState.currentPage)
                                    if (currentImage != null) {
                                        imageToDelete = currentImage
                                        // Don't close full screen here, wait for confirmation
                                        // But user might want to delete and stay? 
                                        // If we delete, the pager might jump.
                                        // Simpler to show confirmation dialog on top.
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                    
                    // Bottom Controls (Rating)
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val currentImage = images.getOrNull(pagerState.currentPage)
                        if (currentImage != null) {
                            Text(
                                text = "Rate this photo",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            RatingBar(
                                rating = currentImage.rating,
                                onRatingChanged = { newRating ->
                                    viewModel.setRating(currentImage, newRating)
                                },
                                starSize = 32.dp
                            )
                        }
                    }
                }
            }
        }

        // Add Photo Dialog
        if (showAddDialog) {
            Dialog(onDismissRequest = { showAddDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DeepOcean,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Add Photo",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val photoFile = createGalleryImageFile(context)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            photoFile
                                        )
                                        tempUri = uri
                                        cameraLauncher.launch(uri)
                                    }
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Camera",
                                    tint = AquaBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Camera", color = Color.White)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { galleryLauncher.launch("image/*") }
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery",
                                    tint = AquaBlue,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Gallery", color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        if (imageToDelete != null) {
            AlertDialog(
                onDismissRequest = { imageToDelete = null },
                title = { Text("Delete Photo?") },
                text = { Text("Are you sure you want to delete this photo? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            imageToDelete?.let { img ->
                                viewModel.deleteImage(img)
                                // If we are in full screen and delete the last image, we should probably close full screen
                                if (images.size <= 1 && showFullScreen) {
                                    showFullScreen = false
                                }
                            }
                            imageToDelete = null
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { imageToDelete = null }) {
                        Text("Cancel")
                    }
                },
                containerColor = DeepOcean,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun ImageThumbnail(
    galleryImage: GalleryImage,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = rememberAsyncImagePainter(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(galleryImage.path))
                        .crossfade(true)
                        .build()
                ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            
            // Top Right Delete Icon
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(onClick = onDeleteClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Rating Overlay if present
            if (galleryImage.rating > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = galleryImage.rating.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun shareImage(context: Context, imagePath: String) {
    try {
        val file = File(imagePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Image"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// Helper to create temp file for camera
private fun createGalleryImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val storageDir = context.getExternalFilesDir("tank_images") // Using same dir as tanks, or gallery temp
    return File.createTempFile(
        "JPEG_${timeStamp}_",
        ".jpg",
        storageDir
    )
}
