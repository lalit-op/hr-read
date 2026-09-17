package com.example.renderer

import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource

/**
 * Renderer for file types not supported by HR Read.
 * Returns a clear, user-friendly unsupported format result.
 */
class UnsupportedRenderer : DocumentRenderer {

    override val documentType: DocumentType = DocumentType.UNSUPPORTED

    override var isInitialized: Boolean = false
        private set

    override suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult {
        return DocumentOpenResult.Error("This document format is not supported.")
    }

    override fun getPageCount(): Int = 0

    override fun getPageDimensions(pageIndex: Int): PageDimensions? = null

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult {
        return PageRenderResult.Error("This document format is not supported.")
    }

    override fun close() {
        isInitialized = false
    }
}
