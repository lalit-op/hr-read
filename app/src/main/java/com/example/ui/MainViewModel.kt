package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.documents.DocumentSourceResolver
import com.example.data.documents.MediaStoreDocumentScanner
import com.example.data.filesystem.DocumentCacheManager
import com.example.data.repository.DocumentRepositoryImpl
import com.example.domain.model.Document
import com.example.domain.model.DocumentType
import com.example.domain.model.ReaderSettings
import com.example.domain.repository.DocumentRepository
import com.example.renderer.DocumentOpenResult
import com.example.renderer.DocumentRenderer
import com.example.renderer.DocumentRendererFactory
import com.example.renderer.PageRenderResult
import com.example.ui.navigation.Screen
import com.example.viewer.DocumentViewerState
import com.example.viewer.FitMode
import com.example.viewer.PageMode
import com.example.viewer.ViewerStatus
import com.example.search.SearchMatch
import com.example.thumbnail.DocumentThumbnailManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cacheManager = DocumentCacheManager(application)
    private val database = AppDatabase.getInstance(application)
    private val sourceResolver = DocumentSourceResolver(application, cacheManager)
    private val mediaScanner = MediaStoreDocumentScanner(application)

    val repository: DocumentRepository = DocumentRepositoryImpl(
        context = application,
        documentDao = database.documentDao(),
        sourceResolver = sourceResolver,
        mediaStoreScanner = mediaScanner,
        cacheManager = cacheManager
    )

    val thumbnailManager = DocumentThumbnailManager(application, sourceResolver)

    private val rendererFactory = DocumentRendererFactory()
    private var currentRenderer: DocumentRenderer? = null
    private var pageRenderJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null

    // Navigation state
    private val _currentTab = MutableStateFlow<Screen>(Screen.Home)
    val currentTab: StateFlow<Screen> = _currentTab.asStateFlow()

    private val _selectedCategory = MutableStateFlow<DocumentType?>(null)
    val selectedCategory: StateFlow<DocumentType?> = _selectedCategory.asStateFlow()

    // Data streams from Room Database
    val recentDocuments: StateFlow<List<Document>> = repository.getRecentDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<Document>> = repository.getFavoriteDocuments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val readerSettings: StateFlow<ReaderSettings> = repository.getReaderSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    private val syncedCategories = mutableSetOf<DocumentType>()

    // Category documents reactively backed by Room Database
    @OptIn(ExperimentalCoroutinesApi::class)
    val categoryDocuments: StateFlow<List<Document>> = _selectedCategory
        .flatMapLatest { cat ->
            if (cat == null) {
                flowOf(emptyList())
            } else {
                repository.getDocumentsByType(cat)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isScanningCategory = MutableStateFlow(false)
    val isScanningCategory: StateFlow<Boolean> = _isScanningCategory.asStateFlow()

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    // Full screen viewer state
    private val _viewerState = MutableStateFlow(DocumentViewerState())
    val viewerState: StateFlow<DocumentViewerState> = _viewerState.asStateFlow()

    init {
        refreshCacheSize()
    }

    fun selectTab(screen: Screen) {
        _currentTab.value = screen
        _selectedCategory.value = null
    }

    fun openCategory(type: DocumentType) {
        _selectedCategory.value = type
        if (!syncedCategories.contains(type)) {
            syncCategory(type)
        }
    }

    fun closeCategory() {
        _selectedCategory.value = null
    }

    fun syncCategory(type: DocumentType, force: Boolean = false) {
        viewModelScope.launch {
            _isScanningCategory.value = true
            try {
                repository.syncDeviceDocuments(type)
                syncedCategories.add(type)
            } catch (_: Exception) {
            } finally {
                _isScanningCategory.value = false
            }
        }
    }

    fun openDocument(uri: Uri) {
        viewModelScope.launch {
            // Cancel any in-flight rendering or search
            pageRenderJob?.cancel()
            pageRenderJob = null
            searchJob?.cancel()
            searchJob = null

            // Reset and close any prior renderer
            currentRenderer?.close()
            currentRenderer = null

            _viewerState.value = DocumentViewerState(
                uri = uri.toString(),
                status = ViewerStatus.Loading("Resolving document source...")
            )

            try {
                // Record in Room Database
                val recordedDoc = repository.recordDocumentOpened(uri)
                val resolved = repository.resolveDocumentSource(uri)

                _viewerState.value = _viewerState.value.copy(
                    title = resolved.displayName,
                    documentType = resolved.documentType,
                    isFavorite = recordedDoc.isFavorite,
                    status = ViewerStatus.Loading("Initializing visual renderer...")
                )

                val renderer = rendererFactory.createRenderer(resolved.documentType)
                currentRenderer = renderer

                val openResult = renderer.open(resolved)

                when (openResult) {
                    is DocumentOpenResult.Success -> {
                        val totalPages = openResult.pageCount
                        val startPage = recordedDoc.lastViewedPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))

                        // Default presentations to slide-by-slide mode
                        val defaultPageMode = if (resolved.documentType == DocumentType.POWERPOINT) {
                            PageMode.PAGE_BY_PAGE
                        } else {
                            _viewerState.value.pageMode
                        }

                        val searchAvailable = try { renderer.isSearchAvailable() } catch (_: Exception) { false }

                        _viewerState.value = _viewerState.value.copy(
                            title = openResult.title,
                            totalPages = totalPages,
                            currentPage = startPage,
                            pageMode = defaultPageMode,
                            isSearchAvailable = searchAvailable,
                            status = ViewerStatus.Ready(pageCount = totalPages, title = openResult.title)
                        )

                        // Render the initial page
                        renderCurrentPage(startPage)
                    }

                    is DocumentOpenResult.EnginePending -> {
                        val cachedFile = try { resolved.provideSeekableFile() } catch (_: Exception) { null }
                        _viewerState.value = _viewerState.value.copy(
                            title = resolved.displayName,
                            status = ViewerStatus.EnginePending(
                                documentType = openResult.documentType,
                                reason = openResult.reason,
                                title = resolved.displayName,
                                fileSize = recordedDoc.formattedFileSize,
                                cachedPath = cachedFile?.name
                            )
                        )
                    }

                    is DocumentOpenResult.PasswordRequired -> {
                        val resolvedError = com.example.util.UserErrorMessageResolver.resolve(
                            throwable = null,
                            fallbackMessage = "password",
                            documentType = resolved.documentType
                        )
                        _viewerState.value = _viewerState.value.copy(
                            title = openResult.documentTitle,
                            status = ViewerStatus.PasswordRequired(
                                title = openResult.documentTitle,
                                message = resolvedError.message
                            )
                        )
                    }

                    is DocumentOpenResult.Error -> {
                        val resolvedError = com.example.util.UserErrorMessageResolver.resolve(
                            throwable = openResult.throwable,
                            fallbackMessage = openResult.message,
                            documentType = resolved.documentType
                        )
                        _viewerState.value = _viewerState.value.copy(
                            status = ViewerStatus.Error(
                                message = resolvedError.message,
                                title = resolvedError.title,
                                isAccessLost = resolvedError.isAccessLost,
                                canRetry = resolvedError.canRetry
                            )
                        )
                    }
                }
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                val resolvedError = com.example.util.UserErrorMessageResolver.resolve(
                    throwable = t,
                    fallbackMessage = t.message,
                    documentType = _viewerState.value.documentType
                )
                _viewerState.value = _viewerState.value.copy(
                    status = ViewerStatus.Error(
                        message = resolvedError.message,
                        title = resolvedError.title,
                        isAccessLost = resolvedError.isAccessLost,
                        canRetry = resolvedError.canRetry
                    )
                )
            } finally {
                refreshCacheSize()
            }
        }
    }

    fun selectPage(pageIndex: Int) {
        val state = _viewerState.value
        if (pageIndex < 0 || pageIndex >= state.totalPages) return
        _viewerState.value = state.copy(currentPage = pageIndex)
        renderCurrentPage(pageIndex)

        // Save progress to database
        viewModelScope.launch {
            repository.updatePageProgress(state.uri, pageIndex, state.totalPages)
        }
    }

    private fun renderCurrentPage(pageIndex: Int) {
        val renderer = currentRenderer ?: return
        pageRenderJob?.cancel()
        pageRenderJob = viewModelScope.launch {
            _viewerState.value = _viewerState.value.copy(isPageLoading = true)

            // Calculate crisp high-DPI resolution based on actual page dimensions and aspect ratio
            val dims = renderer.getPageDimensions(pageIndex)
            val nativeW = dims?.width ?: 600f
            val nativeH = dims?.height ?: 800f
            val targetW = (nativeW * 2.8f).toInt().coerceIn(1200, 2400)
            val targetH = (nativeH * 2.8f).toInt().coerceIn(1600, 3200)

            when (val renderResult = renderer.renderPage(pageIndex, targetW, targetH)) {
                is PageRenderResult.Success -> {
                    _viewerState.value = _viewerState.value.copy(
                        currentBitmap = renderResult.bitmap,
                        isPageLoading = false
                    )
                }
                is PageRenderResult.Error -> {
                    val resolvedError = com.example.util.UserErrorMessageResolver.resolve(
                        throwable = renderResult.throwable,
                        fallbackMessage = renderResult.message,
                        documentType = currentRenderer?.documentType
                    )
                    _viewerState.value = _viewerState.value.copy(
                        isPageLoading = false,
                        errorMessage = resolvedError.message
                    )
                }
            }
        }
    }

    fun toggleSearch() {
        val current = _viewerState.value
        val newActive = !current.isSearchActive
        _viewerState.value = current.copy(
            isSearchActive = newActive,
            searchQuery = if (!newActive) "" else current.searchQuery,
            searchResults = if (!newActive) emptyList() else current.searchResults,
            isSearching = false
        )
        if (!newActive) {
            searchJob?.cancel()
        }
    }

    fun searchDocument(query: String) {
        searchJob?.cancel()
        val renderer = currentRenderer ?: return
        _viewerState.value = _viewerState.value.copy(
            searchQuery = query,
            isSearching = query.isNotBlank()
        )
        if (query.isBlank()) {
            _viewerState.value = _viewerState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            try {
                val matches = renderer.searchInDocument(query)
                _viewerState.value = _viewerState.value.copy(
                    searchResults = matches,
                    isSearching = false
                )
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                android.util.Log.e("MainViewModel", "Search failed for query: $query", t)
                _viewerState.value = _viewerState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
        }
    }

    fun selectSearchMatch(match: SearchMatch) {
        val page = match.pageIndex
        val hasCoords = match.boundingBoxes.isNotEmpty()
        val focus = if (hasCoords) {
            val box = match.boundingBoxes.first()
            Pair((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        } else null

        val navMsg = if (!hasCoords && _viewerState.value.documentType == DocumentType.PDF) {
            "Navigated to Page ${page + 1} • Exact text coordinates are unavailable for this document."
        } else {
            null
        }

        _viewerState.value = _viewerState.value.copy(
            currentPage = page,
            isSearchActive = false,
            activeHighlightBoxes = match.boundingBoxes,
            activeHighlightPage = page,
            focusTarget = focus,
            selectedCellReference = match.cellReference,
            navigationMessage = navMsg
        )

        renderCurrentPage(page)
    }

    fun clearNavigationMessage() {
        _viewerState.value = _viewerState.value.copy(navigationMessage = null)
    }

    fun clearHighlight() {
        _viewerState.value = _viewerState.value.copy(
            activeHighlightBoxes = emptyList(),
            activeHighlightPage = null,
            focusTarget = null
        )
    }

    fun toggleViewerControls() {
        val current = _viewerState.value
        _viewerState.value = current.copy(areControlsVisible = !current.areControlsVisible)
    }

    fun toggleThumbnails() {
        val current = _viewerState.value
        _viewerState.value = current.copy(isThumbnailsVisible = !current.isThumbnailsVisible)
    }

    fun setPageMode(pageMode: PageMode) {
        _viewerState.value = _viewerState.value.copy(pageMode = pageMode)
    }

    fun setFitMode(fitMode: FitMode) {
        _viewerState.value = _viewerState.value.copy(fitMode = fitMode)
    }

    fun getCurrentRenderer(): DocumentRenderer? = currentRenderer

    fun toggleFavorite(uri: String, currentFavorite: Boolean) {
        viewModelScope.launch {
            val newFav = !currentFavorite
            repository.toggleFavorite(uri, newFav)
            if (_viewerState.value.uri == uri) {
                _viewerState.value = _viewerState.value.copy(isFavorite = newFav)
            }
        }
    }

    fun closeViewer() {
        pageRenderJob?.cancel()
        searchJob?.cancel()
        currentRenderer?.close()
        currentRenderer = null
        _viewerState.value = DocumentViewerState(status = ViewerStatus.Idle)
    }

    fun removeRecentDocument(uri: String) {
        viewModelScope.launch {
            repository.removeRecentDocument(uri)
        }
    }

    fun clearAllRecent() {
        viewModelScope.launch {
            repository.clearAllRecent()
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            _cacheSizeBytes.value = repository.getCacheSizeBytes()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearCache()
            thumbnailManager.clearCache()
            refreshCacheSize()
        }
    }

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch {
            repository.updateReaderSettings(settings)
        }
    }

    override fun onCleared() {
        super.onCleared()
        currentRenderer?.close()
        currentRenderer = null
        thumbnailManager.clearCache()
    }
}
