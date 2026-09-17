package com.example.renderer

import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import com.example.renderer.xlsx.Workbook
import com.example.renderer.xlsx.Worksheet
import com.example.renderer.xlsx.XlsxBitmapRenderer
import com.example.renderer.xlsx.XlsxParser
import com.example.search.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dedicated contract for Microsoft Excel (.xls, .xlsx) spreadsheet visual rendering engine.
 */
interface XlsxRendererContract : DocumentRenderer {
    fun getWorkbook(): Workbook?
    fun getSheetNames(): List<String>
    fun getActiveSheetIndex(): Int
    fun selectSheet(sheetIndex: Int): Worksheet?
    fun getActiveWorksheet(): Worksheet?
}

/**
 * Concrete, high-performance architecture implementation of [XlsxRendererContract].
 *
 * Implements the required pipeline:
 * XLSX -> XlsxRenderer -> Workbook -> Worksheet -> virtualized grid -> Android UI
 */
class XlsxRenderer : XlsxRendererContract {

    override val documentType: DocumentType = DocumentType.EXCEL
    override var isInitialized: Boolean = false
        private set

    private var seekableFile: File? = null
    private var workbook: Workbook? = null
    private var currentSheetIndex: Int = 0

    override suspend fun open(source: ResolvedDocumentSource): DocumentOpenResult = withContext(Dispatchers.IO) {
        close()
        try {
            val file = source.provideSeekableFile()
            seekableFile = file

            val parsedWorkbook = XlsxParser.parse(file, source.displayName)
            workbook = parsedWorkbook
            currentSheetIndex = 0
            isInitialized = true

            // Eagerly pre-load only the initial active worksheet so UI renders without delay
            if (parsedWorkbook.sheetCount > 0) {
                parsedWorkbook.getWorksheet(0)
            }

            DocumentOpenResult.Success(
                pageCount = parsedWorkbook.sheetCount.coerceAtLeast(1),
                title = source.displayName,
                isSeekable = true
            )
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            close()
            android.util.Log.e("XlsxRenderer", "Failed opening spreadsheet workbook", t)
            DocumentOpenResult.Error("Failed opening spreadsheet workbook: ${t.localizedMessage}", t)
        }
    }

    override fun getWorkbook(): Workbook? = workbook

    override fun getSheetNames(): List<String> = workbook?.sheetNames ?: emptyList()

    override fun getActiveSheetIndex(): Int = currentSheetIndex

    override fun selectSheet(sheetIndex: Int): Worksheet? {
        val wb = workbook ?: return null
        val safeIndex = sheetIndex.coerceIn(0, (wb.sheetCount - 1).coerceAtLeast(0))
        currentSheetIndex = safeIndex
        return wb.getWorksheet(safeIndex)
    }

    override fun getActiveWorksheet(): Worksheet? {
        val wb = workbook ?: return null
        return wb.getWorksheet(currentSheetIndex)
    }

    override fun getPageCount(): Int = workbook?.sheetCount ?: 0

    override fun getPageDimensions(pageIndex: Int): PageDimensions? {
        val wb = workbook ?: return null
        val sheet = wb.getWorksheet(pageIndex)
        val w = (sheet.columnCount.coerceAtLeast(8) * 120f).coerceIn(800f, 4000f)
        val h = (sheet.rowCount.coerceAtLeast(20) * 32f).coerceIn(1000f, 6000f)
        return PageDimensions(width = w, height = h)
    }

    override suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): PageRenderResult = withContext(Dispatchers.Default) {
        val wb = workbook ?: return@withContext PageRenderResult.Error("Workbook not initialized")
        try {
            currentCoroutineContext().ensureActive()
            val sheet = wb.getWorksheet(pageIndex)
            val bitmap = XlsxBitmapRenderer.renderSheetToBitmap(sheet, targetWidth, targetHeight)
            PageRenderResult.Success(bitmap, pageIndex)
        } catch (cancelEx: kotlinx.coroutines.CancellationException) {
            throw cancelEx
        } catch (t: Throwable) {
            android.util.Log.e("XlsxRenderer", "Failed rendering sheet preview", t)
            PageRenderResult.Error("Failed rendering sheet preview: ${t.localizedMessage}", t)
        }
    }

    override suspend fun searchInDocument(query: String): List<SearchMatch> = withContext(Dispatchers.Default) {
        val wb = workbook ?: return@withContext emptyList()
        val queryLower = query.lowercase().trim()
        if (queryLower.isEmpty()) return@withContext emptyList()

        try {
            val matches = mutableListOf<SearchMatch>()

            for (sheetIdx in 0 until wb.sheetCount) {
                currentCoroutineContext().ensureActive()
                val sheet = wb.getWorksheet(sheetIdx)
                for (r in 0 until sheet.rowCount) {
                    currentCoroutineContext().ensureActive()
                    val row = sheet.getRow(r) ?: continue
                    for ((colIdx, cell) in row.cells) {
                        val text = cell.formattedValue
                        if (text.lowercase().contains(queryLower)) {
                            matches.add(
                                SearchMatch(
                                    pageIndex = sheetIdx,
                                    lineText = text,
                                    snippet = "Sheet: ${sheet.name} | Cell ${cell.cellReference}: $text",
                                    contextInfo = "Sheet: ${sheet.name}",
                                    cellReference = cell.cellReference
                                )
                            )
                            if (matches.size >= 100) break
                        }
                    }
                    if (matches.size >= 100) break
                }
                if (matches.size >= 100) break
            }

            matches
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (t: Throwable) {
            android.util.Log.w("XlsxRenderer", "Spreadsheet search error", t)
            emptyList()
        }
    }

    override suspend fun isSearchAvailable(): Boolean {
        if (!isInitialized) return false
        val wb = workbook ?: return false
        return wb.sheetCount > 0
    }

    override fun close() {
        try {
            workbook?.clearCache()
        } catch (_: Exception) {}
        workbook = null
        seekableFile = null
        currentSheetIndex = 0
        isInitialized = false
    }
}
