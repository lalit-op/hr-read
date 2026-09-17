package com.example.viewer

import android.graphics.Bitmap
import android.graphics.RectF
import com.example.domain.model.DocumentType
import com.example.search.SearchMatch

enum class FitMode {
    FIT_WIDTH,
    FIT_PAGE
}

enum class PageMode {
    PAGE_BY_PAGE,
    CONTINUOUS
}

sealed class ViewerStatus {
    data object Idle : ViewerStatus()
    data class Loading(val message: String = "Loading document...") : ViewerStatus()
    data class Ready(val pageCount: Int, val title: String) : ViewerStatus()
    data class EnginePending(
        val documentType: DocumentType,
        val reason: String,
        val title: String,
        val fileSize: String,
        val cachedPath: String?
    ) : ViewerStatus()
    data class PasswordRequired(
        val title: String,
        val message: String = "This document is password protected and cannot be opened."
    ) : ViewerStatus()
    data class Error(
        val message: String,
        val title: String = "Unable to open document",
        val isAccessLost: Boolean = false,
        val canRetry: Boolean = true
    ) : ViewerStatus()
}

data class DocumentViewerState(
    val uri: String = "",
    val title: String = "",
    val documentType: DocumentType = DocumentType.UNSUPPORTED,
    val status: ViewerStatus = ViewerStatus.Idle,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentBitmap: Bitmap? = null,
    val isPageLoading: Boolean = false,
    val areControlsVisible: Boolean = true,
    val pageMode: PageMode = PageMode.PAGE_BY_PAGE,
    val fitMode: FitMode = FitMode.FIT_WIDTH,
    val scale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val isFavorite: Boolean = false,
    val isSearchActive: Boolean = false,
    val isSearchAvailable: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchMatch> = emptyList(),
    val isSearching: Boolean = false,
    val activeHighlightBoxes: List<RectF> = emptyList(),
    val activeHighlightPage: Int? = null,
    val focusTarget: Pair<Float, Float>? = null,
    val selectedCellReference: String? = null,
    val navigationMessage: String? = null,
    val isThumbnailsVisible: Boolean = false,
    val errorMessage: String? = null
)

