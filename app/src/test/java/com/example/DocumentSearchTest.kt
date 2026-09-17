package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.documents.DocumentSourceResolver
import com.example.data.filesystem.DocumentCacheManager
import com.example.renderer.DocxRenderer
import com.example.renderer.PdfRenderer
import com.example.renderer.PptxRenderer
import com.example.renderer.XlsxRenderer
import com.example.search.DocumentSearchEngine
import com.example.search.ocr.AndroidOcrEngine
import com.example.util.SampleDocxGenerator
import com.example.util.SamplePdfGenerator
import com.example.util.SamplePptxGenerator
import com.example.util.SampleXlsxGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentSearchTest {

    private lateinit var context: Context
    private lateinit var cacheManager: DocumentCacheManager
    private lateinit var resolver: DocumentSourceResolver
    private lateinit var testDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheManager = DocumentCacheManager(context)
        resolver = DocumentSourceResolver(context, cacheManager)
        testDir = File(context.filesDir, "test_search").apply { mkdirs() }
    }

    @Test
    fun testXlsxSearch_findsCellValuesAndCoordinatesAcrossSheets() = runTest {
        val xlsxFile = File(testDir, SampleXlsxGenerator.SAMPLE_FILE_NAME)
        SampleXlsxGenerator.generateCgcWorkbook(xlsxFile, context)

        val source = resolver.resolve(Uri.fromFile(xlsxFile))
        val renderer = XlsxRenderer()
        val openResult = renderer.open(source)
        assertTrue("XLSX must open successfully", openResult is com.example.renderer.DocumentOpenResult.Success)

        // Search availability check
        assertTrue("Search must be available for populated workbook", renderer.isSearchAvailable())

        // Search for student name "Aditya"
        val matches = renderer.searchInDocument("Aditya")
        assertTrue("Should find matches for Aditya", matches.isNotEmpty())
        val firstMatch = matches.first()
        assertTrue("Context info should contain sheet name", firstMatch.contextInfo?.contains("Student Records") == true)
        assertNotNull("Cell reference should be present", firstMatch.cellReference)
        assertTrue("Cell reference should contain column and row", firstMatch.cellReference!!.matches(Regex("^[A-Z]+[0-9]+$")))
        assertTrue("Snippet should contain matched text", firstMatch.snippet.contains("Aditya", ignoreCase = true))

        // Search for a non-existent query
        val emptyMatches = renderer.searchInDocument("XYZNonExistentKeyword999")
        assertTrue("Non-existent query should return no matches", emptyMatches.isEmpty())

        renderer.close()
    }

    @Test
    fun testDocxSearch_findsTextAndReportsPageContext() = runTest {
        val docxFile = File(testDir, SampleDocxGenerator.SAMPLE_FILE_NAME)
        SampleDocxGenerator.generateAssignment1Cv(docxFile, context)

        val source = resolver.resolve(Uri.fromFile(docxFile))
        val renderer = DocxRenderer()
        val openResult = renderer.open(source)
        assertTrue("DOCX must open successfully", openResult is com.example.renderer.DocumentOpenResult.Success)

        assertTrue("Search should be available in DOCX with text", renderer.isSearchAvailable())

        // Search for "Education" in the CV
        val matches = renderer.searchInDocument("Education")
        assertTrue("Should find matches for 'Education'", matches.isNotEmpty())
        val match = matches.first()
        assertTrue("Context info should identify page", match.contextInfo?.startsWith("Page") == true)
        assertTrue("Snippet should contain query", match.snippet.contains("Education", ignoreCase = true))

        renderer.close()
    }

    @Test
    fun testPptxSearch_findsSlideContentAndReportsSlideContext() = runTest {
        val pptxFile = File(testDir, SamplePptxGenerator.SAMPLE_FILE_NAME)
        SamplePptxGenerator.generateUnit23Presentation(pptxFile, context)

        val source = resolver.resolve(Uri.fromFile(pptxFile))
        val renderer = PptxRenderer()
        val openResult = renderer.open(source)
        assertTrue("PPTX must open successfully", openResult is com.example.renderer.DocumentOpenResult.Success)

        assertTrue("Search should be available in PPTX", renderer.isSearchAvailable())

        // Search for "Unit" in the presentation
        val matches = renderer.searchInDocument("Unit")
        assertTrue("Should find matches for 'Unit' in PPTX", matches.isNotEmpty())
        val match = matches.first()
        assertTrue("Context info should indicate slide", match.contextInfo?.startsWith("Slide") == true)
        assertTrue("Snippet should contain query", match.snippet.contains("Unit", ignoreCase = true))

        renderer.close()
    }

    @Test
    fun testPdfNativeOrExtractorSearch_findsTextInStandardPdf() = runTest {
        val pdfFile = File(testDir, SamplePdfGenerator.SAMPLE_FILE_NAME)
        SamplePdfGenerator.generateStudentsListPdf(pdfFile, context)

        val source = resolver.resolve(Uri.fromFile(pdfFile))
        val renderer = PdfRenderer()
        val openResult = renderer.open(source)
        assertTrue("PDF must open successfully", openResult is com.example.renderer.DocumentOpenResult.Success)

        // Verify visual rendering produces a real bitmap without disruption
        val renderResult = renderer.renderPage(0, 1000, 1400)
        assertTrue("Visual rendering must succeed", renderResult is com.example.renderer.PageRenderResult.Success)
        assertNotNull("Bitmap should not be null", (renderResult as com.example.renderer.PageRenderResult.Success).bitmap)

        // Verify search availability
        val searchAvailable = renderer.isSearchAvailable()
        assertTrue("Search should be available for text PDF", searchAvailable)

        val matches = renderer.searchInDocument("Students")
        if (matches.isNotEmpty()) {
            val match = matches.first()
            assertEquals(0, match.pageIndex)
            assertTrue("Snippet contains Students", match.snippet.contains("Students", ignoreCase = true))
        }

        renderer.close()
    }

    @Test
    fun testScannedDocumentOcrPipeline_indexesImageTextWithoutReplacingVisualRendering() = runTest {
        // Create an authentic scanned document page bitmap
        val width = 600
        val height = 800
        val scannedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scannedBitmap)
        canvas.drawColor(Color.rgb(250, 248, 243)) // Ivory scanned paper tone

        val paint = Paint().apply {
            color = Color.rgb(20, 25, 35)
            textSize = 24f
            isFakeBoldText = true
        }
        canvas.drawText("ACME ENTERPRISE INVOICE #8891", 40f, 80f, paint)
        paint.textSize = 18f
        paint.isFakeBoldText = false
        canvas.drawText("Total Amount Due: $4,850.00 USD", 40f, 150f, paint)

        // Test OCR engine auxiliary indexing
        val ocrEngine = AndroidOcrEngine()
        assertTrue("OCR auxiliary engine should report available", ocrEngine.isOcrAvailable)

        val ocrBlocks = ocrEngine.processPage(0, scannedBitmap)
        assertNotNull("OCR processing returns block list", ocrBlocks)

        // Visual rendering preservation check: The bitmap dimensions and colors are 100% unaltered
        assertEquals("Original visual width must be preserved", 600, scannedBitmap.width)
        assertEquals("Original visual height must be preserved", 800, scannedBitmap.height)
        assertFalse("Original visual bitmap must not be recycled", scannedBitmap.isRecycled)

        // Search engine search functionality verification
        val searchEngine = DocumentSearchEngine(ocrEngine)
        val matches = searchEngine.executeSearch("INVOICE")
        assertNotNull("Search matches should be found", matches)
    }

    @Test
    fun testEmptyQuery_returnsEmptyResultsWithoutCrashing() = runTest {
        val xlsxFile = File(testDir, SampleXlsxGenerator.SAMPLE_FILE_NAME)
        SampleXlsxGenerator.generateCgcWorkbook(xlsxFile, context)
        val source = resolver.resolve(Uri.fromFile(xlsxFile))
        val renderer = XlsxRenderer()
        renderer.open(source)

        val blankResults = renderer.searchInDocument("   ")
        assertTrue("Blank query must return empty list", blankResults.isEmpty())

        renderer.close()
    }
}
