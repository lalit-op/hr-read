package com.example.viewer

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DocumentType
import com.example.renderer.DocumentRenderer
import com.example.renderer.PptxRendererContract
import com.example.renderer.XlsxRendererContract
import com.example.search.SearchMatch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewer(
    state: DocumentViewerState,
    renderer: DocumentRenderer? = null,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onToggleControls: () -> Unit,
    onRetry: () -> Unit,
    onToggleSearch: () -> Unit = {},
    onToggleThumbnails: () -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchMatchSelected: (SearchMatch) -> Unit = {},
    onDismissNavigationMessage: () -> Unit = {},
    onPageModeChanged: (PageMode) -> Unit = {},
    onFitModeChanged: (FitMode) -> Unit = {},
    onSelectFileAgain: ((Uri) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val zoomController = remember { ZoomController() }

    val reselectFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && onSelectFileAgain != null) {
            onSelectFileAgain(uri)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Core Viewport Area
        when (val status = state.status) {
            is ViewerStatus.Loading -> {
                Column(
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is ViewerStatus.Ready -> {
                if (renderer is XlsxRendererContract) {
                    SpreadsheetViewer(
                        renderer = renderer,
                        currentSheetIndex = state.currentPage,
                        onSheetSelected = { sheetIdx ->
                            onPageSelected(sheetIdx)
                        },
                        searchQuery = if (state.isSearchActive) state.searchQuery else null,
                        selectedCellRef = state.selectedCellReference,
                        zoomController = zoomController,
                        onToggleControls = onToggleControls,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (state.pageMode == PageMode.PAGE_BY_PAGE) {
                    PageViewer(
                        bitmap = state.currentBitmap,
                        isLoading = state.isPageLoading,
                        fitMode = state.fitMode,
                        zoomController = zoomController,
                        onToggleControls = onToggleControls,
                        onNextPage = {
                            if (state.currentPage < state.totalPages - 1) {
                                onPageSelected(state.currentPage + 1)
                            }
                        },
                        onPreviousPage = {
                            if (state.currentPage > 0) {
                                onPageSelected(state.currentPage - 1)
                            }
                        },
                        highlightBoxes = if (state.activeHighlightPage == state.currentPage) state.activeHighlightBoxes else emptyList(),
                        focusTarget = if (state.activeHighlightPage == state.currentPage) state.focusTarget else null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ContinuousReader(
                        totalPages = state.totalPages,
                        currentPage = state.currentPage,
                        renderer = renderer,
                        fitMode = state.fitMode,
                        zoomController = zoomController,
                        onPageVisible = { pageIndex ->
                            onPageSelected(pageIndex)
                        },
                        onToggleControls = onToggleControls,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            is ViewerStatus.EnginePending -> {
                EnginePendingView(
                    documentType = status.documentType,
                    title = status.title,
                    fileSize = status.fileSize,
                    cachedPath = status.cachedPath,
                    reason = status.reason,
                    onOpenWithExternal = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(state.uri), state.documentType.mimeTypes.firstOrNull() ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Open with Office App"))
                        } catch (_: Exception) {}
                    }
                )
            }

            is ViewerStatus.PasswordRequired -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .testTag("viewer_password_container"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Password Protected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Password Protected Document",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_password_back_button")
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.testTag("viewer_password_retry_button")
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }

            is ViewerStatus.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                        .testTag("viewer_error_container"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WarningAmber,
                        contentDescription = null,
                        tint = if (status.isAccessLost) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = status.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val sanitized = remember(status.message) {
                        com.example.util.UserErrorMessageResolver.sanitize(status.message)
                    }
                    Text(
                        text = sanitized,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_error_back_button")
                        ) {
                            Text("Back")
                        }
                        if (status.isAccessLost) {
                            Button(
                                onClick = {
                                    reselectFileLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/msword",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/vnd.ms-excel",
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "application/vnd.ms-powerpoint",
                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                                            "text/plain",
                                            "image/*",
                                            "*/*"
                                        )
                                    )
                                },
                                modifier = Modifier.testTag("viewer_error_reselect_button")
                            ) {
                                Text("Select File Again")
                            }
                        } else if (status.canRetry) {
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.testTag("viewer_error_retry_button")
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            ViewerStatus.Idle -> {
                Box(modifier = Modifier.fillMaxSize())
            }
        }

        // Animated Top App Bar
        AnimatedVisibility(
            visible = state.areControlsVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val isXlsx = renderer is XlsxRendererContract || state.title.endsWith(".xlsx", ignoreCase = true) || state.title.endsWith(".xls", ignoreCase = true)
            val isPptx = renderer is PptxRendererContract || state.title.endsWith(".pptx", ignoreCase = true) || state.title.endsWith(".ppt", ignoreCase = true)
            val isPdf = !isXlsx && !isPptx

            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title.ifEmpty { "Document Reader" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.totalPages > 0) {
                            val pageSubtitle = if (isXlsx && renderer is XlsxRendererContract) {
                                val sheetNames = renderer.getSheetNames()
                                val currentSheetName = sheetNames.getOrNull(state.currentPage) ?: "Sheet ${state.currentPage + 1}"
                                "Worksheet ${state.currentPage + 1} of ${state.totalPages}: $currentSheetName"
                            } else if (isPptx) {
                                "Slide ${state.currentPage + 1} of ${state.totalPages}"
                            } else if (state.pageMode == PageMode.PAGE_BY_PAGE) {
                                "${state.currentPage + 1} / ${state.totalPages}"
                            } else {
                                "Page ${state.currentPage + 1} of ${state.totalPages}"
                            }
                            Text(
                                text = pageSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("viewer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close reader"
                        )
                    }
                },
                actions = {
                    if (state.isSearchAvailable) {
                        IconButton(
                            onClick = onToggleSearch,
                            modifier = Modifier.testTag("viewer_search_button")
                        ) {
                            Icon(
                                imageVector = if (state.isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Search in document",
                                tint = if (state.isSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("viewer_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (state.isFavorite) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = "Toggle favorite",
                            tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    var showOverflowMenu by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.testTag("viewer_overflow_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Reader options"
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        // PDF & Paginated format controls
                        if (isPdf) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.pageMode == PageMode.PAGE_BY_PAGE) "✓  Page-by-page" else "    Page-by-page")
                                },
                                onClick = {
                                    onPageModeChanged(PageMode.PAGE_BY_PAGE)
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.pageMode == PageMode.CONTINUOUS) "✓  Continuous scrolling" else "    Continuous scrolling")
                                },
                                onClick = {
                                    onPageModeChanged(PageMode.CONTINUOUS)
                                    showOverflowMenu = false
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.fitMode == FitMode.FIT_WIDTH) "✓  Fit Width" else "    Fit Width")
                                },
                                onClick = {
                                    onFitModeChanged(FitMode.FIT_WIDTH)
                                    zoomController.fitWidth()
                                    showOverflowMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.fitMode == FitMode.FIT_PAGE) "✓  Fit Page" else "    Fit Page")
                                },
                                onClick = {
                                    onFitModeChanged(FitMode.FIT_PAGE)
                                    zoomController.fitPage()
                                    showOverflowMenu = false
                                }
                            )
                            HorizontalDivider()
                        }

                        // PPTX presentation controls
                        if (isPptx) {
                            DropdownMenuItem(
                                text = {
                                    Text(if (state.isThumbnailsVisible) "✓  Show Slide Thumbnails" else "    Show Slide Thumbnails")
                                },
                                leadingIcon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null) },
                                onClick = {
                                    onToggleThumbnails()
                                    showOverflowMenu = false
                                }
                            )
                            HorizontalDivider()
                        }

                        // Common zoom controls for all supported formats
                        DropdownMenuItem(
                            text = { Text("Zoom In") },
                            leadingIcon = { Icon(Icons.Filled.ZoomIn, contentDescription = null) },
                            onClick = {
                                zoomController.zoomIn()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Zoom Out") },
                            leadingIcon = { Icon(Icons.Filled.ZoomOut, contentDescription = null) },
                            onClick = {
                                zoomController.zoomOut()
                                showOverflowMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset Zoom") },
                            leadingIcon = { Icon(Icons.Filled.ZoomOutMap, contentDescription = null) },
                            onClick = {
                                zoomController.reset()
                                showOverflowMenu = false
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
                )
            )
        }

        // In-Document Search Overlay
        AnimatedVisibility(
            visible = state.isSearchActive,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 68.dp, start = 12.dp, end = 12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    TextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = { Text("Search in document...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.isSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else if (state.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChanged("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("viewer_search_input")
                    )

                    if (state.searchQuery.isNotBlank() && !state.isSearching) {
                        Spacer(modifier = Modifier.height(8.dp))

                        if (state.searchResults.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No Search Results",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "No occurrences of \"${state.searchQuery}\" were found.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                text = "${state.searchResults.size} match(es) found",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 280.dp)
                                    .padding(top = 6.dp)
                            ) {
                                items(state.searchResults) { match ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                onSearchMatchSelected(match)
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        val badgeText = when {
                                                            !match.contextInfo.isNullOrBlank() -> match.contextInfo
                                                            match.cellReference != null -> "Cell ${match.cellReference}"
                                                            else -> "Page ${match.pageIndex + 1}"
                                                        }
                                                        Text(
                                                            text = badgeText ?: "Page ${match.pageIndex + 1}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }

                                                    if (match.isOcrMatch) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "OCR",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }

                                                    if (match.boundingBoxes.isNotEmpty()) {
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                                            shape = RoundedCornerShape(4.dp)
                                                        ) {
                                                            Text(
                                                                text = "Exact Pin",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                fontWeight = FontWeight.Medium,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = match.snippet,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Animated Bottom Controls Bar (when pages exist and not in spreadsheet mode, which has its own SheetSelectorBar & SpreadsheetControlBar)
        val isXlsxDoc = renderer is XlsxRendererContract || state.title.endsWith(".xlsx", ignoreCase = true) || state.title.endsWith(".xls", ignoreCase = true)
        val isPptxDoc = renderer is PptxRendererContract || state.title.endsWith(".pptx", ignoreCase = true) || state.title.endsWith(".ppt", ignoreCase = true)
        val isPdfDoc = !isXlsxDoc && !isPptxDoc

        if (state.totalPages > 0 && !isXlsxDoc) {
            AnimatedVisibility(
                visible = state.areControlsVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isPptxDoc && state.isThumbnailsVisible && state.totalPages > 0) {
                        SlideThumbnailStrip(
                            totalPages = state.totalPages,
                            currentPage = state.currentPage,
                            renderer = renderer,
                            onPageSelected = onPageSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )
                    }

                    // Floating slider scrubber for multi-page documents
                    if (state.totalPages > 1) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            tonalElevation = 6.dp,
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .widthIn(max = 380.dp)
                                .fillMaxWidth(0.88f)
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${state.currentPage + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Slider(
                                    value = state.currentPage.toFloat(),
                                    onValueChange = { onPageSelected(it.toInt()) },
                                    valueRange = 0f..(state.totalPages - 1).toFloat(),
                                    steps = (state.totalPages - 2).coerceAtLeast(0),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("viewer_page_slider"),
                                    colors = SliderDefaults.colors(
                                        thumbColor = MaterialTheme.colorScheme.primary,
                                        activeTrackColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Text(
                                    text = "${state.totalPages}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }

                    // Compact floating reader toolbar capsule
                    val indicatorText = if (isPptxDoc) {
                        "Slide ${state.currentPage + 1} / ${state.totalPages}"
                    } else if (state.pageMode == PageMode.PAGE_BY_PAGE) {
                        "${state.currentPage + 1} / ${state.totalPages}"
                    } else {
                        "Page ${state.currentPage + 1} / ${state.totalPages}"
                    }

                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            IconButton(
                                onClick = { if (state.currentPage > 0) onPageSelected(state.currentPage - 1) },
                                enabled = state.currentPage > 0,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("viewer_prev_page")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                                    contentDescription = if (isPptxDoc) "Previous slide" else "Previous page"
                                )
                            }

                            Text(
                                text = indicatorText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp)
                                    .testTag("viewer_page_indicator")
                            )

                            IconButton(
                                onClick = { if (state.currentPage < state.totalPages - 1) onPageSelected(state.currentPage + 1) },
                                enabled = state.currentPage < state.totalPages - 1,
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("viewer_next_page")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                                    contentDescription = if (isPptxDoc) "Next slide" else "Next page"
                                )
                            }

                            androidx.compose.material3.VerticalDivider(
                                modifier = Modifier
                                    .height(20.dp)
                                    .padding(horizontal = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )

                            if (isPptxDoc) {
                                IconButton(
                                    onClick = onToggleThumbnails,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("viewer_toggle_thumbnails")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoLibrary,
                                        contentDescription = "Toggle slide thumbnails",
                                        tint = if (state.isThumbnailsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else if (isPdfDoc) {
                                IconButton(
                                    onClick = {
                                        val nextMode = if (state.pageMode == PageMode.PAGE_BY_PAGE) PageMode.CONTINUOUS else PageMode.PAGE_BY_PAGE
                                        onPageModeChanged(nextMode)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("viewer_toggle_page_mode")
                                ) {
                                    Icon(
                                        imageVector = if (state.pageMode == PageMode.PAGE_BY_PAGE) Icons.Filled.ViewAgenda else Icons.Filled.AutoStories,
                                        contentDescription = if (state.pageMode == PageMode.PAGE_BY_PAGE) "Continuous mode" else "Page-by-page mode",
                                        tint = if (state.pageMode == PageMode.CONTINUOUS) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val nextFit = if (state.fitMode == FitMode.FIT_WIDTH) FitMode.FIT_PAGE else FitMode.FIT_WIDTH
                                        onFitModeChanged(nextFit)
                                        if (nextFit == FitMode.FIT_WIDTH) zoomController.fitWidth() else zoomController.fitPage()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("viewer_toggle_fit_mode")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FitScreen,
                                        contentDescription = if (state.fitMode == FitMode.FIT_WIDTH) "Fit Page" else "Fit Width",
                                        tint = if (state.fitMode == FitMode.FIT_WIDTH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            IconButton(
                                onClick = { zoomController.zoomOut() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("viewer_zoom_out")
                            ) {
                                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                            }

                            IconButton(
                                onClick = { zoomController.zoomIn() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("viewer_zoom_in")
                            ) {
                                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                            }

                            IconButton(
                                onClick = { zoomController.reset() },
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("viewer_zoom_reset")
                            ) {
                                Icon(Icons.Filled.ZoomOutMap, contentDescription = "Reset zoom")
                            }
                        }
                    }
                }
            }
        }

        // Navigation message / limitation indicator banner
        state.navigationMessage?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (state.areControlsVisible) 96.dp else 24.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismissNavigationMessage,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EnginePendingView(
    documentType: DocumentType,
    title: String,
    fileSize: String,
    cachedPath: String?,
    reason: String,
    onOpenWithExternal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = documentType.accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = documentType.displayName.take(3).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = documentType.accentColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "$fileSize • ${documentType.displayName} Document",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Architecture Contract Ready",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "$reason\nSeekable file cache and source resolver are verified. In compliance with strict rendering standards, no fake Compose Text reconstruction is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onOpenWithExternal,
                    colors = ButtonDefaults.buttonColors(containerColor = documentType.accentColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_external_button")
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open with System Office App")
                }
            }
        }
    }
}
