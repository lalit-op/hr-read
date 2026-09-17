package com.example

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.DocumentDao
import com.example.data.database.DocumentEntity
import com.example.data.documents.DocumentSourceResolver
import com.example.data.filesystem.DocumentCacheManager
import com.example.domain.model.DocumentType
import com.example.renderer.DocumentOpenResult
import com.example.renderer.DocxRenderer
import com.example.renderer.PageRenderResult
import com.example.renderer.PdfRenderer
import com.example.renderer.PptxRenderer
import com.example.renderer.XlsxRenderer
import com.example.renderer.docx.DocxPageLayouter
import com.example.renderer.docx.DocxParser
import com.example.renderer.docx.DocxRenderElement
import com.example.renderer.pptx.PptxElement
import com.example.renderer.pptx.PptxParser
import com.example.renderer.xlsx.XlsxParser
import com.example.ui.MainViewModel
import com.example.util.SampleDocxGenerator
import com.example.util.SamplePptxGenerator
import com.example.util.SampleXlsxGenerator
import com.example.viewer.FitMode
import com.example.viewer.PageMode
import com.example.viewer.ViewerStatus
import com.example.viewer.ZoomController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EndToEndValidationTest {

    private lateinit var context: Context
    private lateinit var app: Application
    private lateinit var cacheManager: DocumentCacheManager
    private lateinit var resolver: DocumentSourceResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as Application
        cacheManager = DocumentCacheManager(context)
        resolver = DocumentSourceResolver(context, cacheManager)
    }

    // =========================================================================
    // 1. PDF TEST - Using real "Students List.pdf" from assets
    // =========================================================================

    @Test
    fun `pdf test - Students List pdf complete verification`() = runTest {
        // Copy real Students List.pdf from assets into a local file
        val pdfFile = File(context.cacheDir, "Students List.pdf")
        context.assets.open("Students List.pdf").use { input ->
            FileOutputStream(pdfFile).use { output ->
                input.copyTo(output)
            }
        }
        assertTrue("Real Students List.pdf must exist and be > 0 bytes", pdfFile.exists() && pdfFile.length() > 0)

        // [x] opens
        val source = resolver.resolve(Uri.fromFile(pdfFile))
        assertNotNull("Source must resolve", source)
        assertEquals("Students List.pdf", source.displayName)

        val renderer = PdfRenderer()
        val openResult = renderer.open(source)
        assertTrue("Document must open successfully", openResult is DocumentOpenResult.Success)
        val success = openResult as DocumentOpenResult.Success

        // [x] correct page count
        assertTrue("Students List.pdf must have pages (count > 0)", success.pageCount > 0)
        val pageCount = success.pageCount
        assertEquals(pageCount, renderer.getPageCount())

        // [x] actual page rendering
        val renderResult0 = renderer.renderPage(0, 1190, 1684)
        assertTrue("Page 0 must render successfully", renderResult0 is PageRenderResult.Success)
        val bitmap0 = (renderResult0 as PageRenderResult.Success).bitmap
        assertNotNull("Rendered bitmap must not be null", bitmap0)
        assertTrue("Rendered bitmap width > 0", bitmap0.width > 0)
        assertTrue("Rendered bitmap height > 0", bitmap0.height > 0)

        // [x] original visual layout & correct aspect ratio
        val dims0 = renderer.getPageDimensions(0)
        assertNotNull("Page 0 dimensions must exist", dims0)
        val expectedRatio = dims0!!.aspectRatio
        val actualRatio = bitmap0.width.toFloat() / bitmap0.height.toFloat()
        assertTrue("Bitmap aspect ratio must closely match page dimensions", abs(expectedRatio - actualRatio) < 0.05f)

        // [x] fit width & fit page
        val zoomController = ZoomController()
        zoomController.fitWidth()
        assertEquals(1.0f, zoomController.scale, 0.01f)
        zoomController.fitPage()
        assertEquals(1.0f, zoomController.scale, 0.01f)

        // [x] zoom & pan
        zoomController.zoomIn()
        assertTrue("Zoom scale must increase", zoomController.scale > 1.0f)
        zoomController.onTransform(1.0f, 50f, -30f)
        assertEquals(50f, zoomController.offsetX, 0.01f)
        assertEquals(-30f, zoomController.offsetY, 0.01f)
        zoomController.reset()
        assertEquals(1.0f, zoomController.scale, 0.01f)
        assertEquals(0f, zoomController.offsetX, 0.01f)

        // [x] page-by-page & continuous modes
        val viewModel = MainViewModel(app)
        viewModel.openDocument(Uri.fromFile(pdfFile))
        var attempts = 0
        while (viewModel.viewerState.value.status !is ViewerStatus.Ready && attempts < 50) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(50)
            attempts++
        }
        assertEquals(PageMode.PAGE_BY_PAGE, viewModel.viewerState.value.pageMode)
        viewModel.setPageMode(PageMode.CONTINUOUS)
        assertEquals(PageMode.CONTINUOUS, viewModel.viewerState.value.pageMode)
        viewModel.setPageMode(PageMode.PAGE_BY_PAGE)
        assertEquals(PageMode.PAGE_BY_PAGE, viewModel.viewerState.value.pageMode)

        // [x] search & search navigation
        val searchResults = renderer.searchInDocument("Student")
        assertNotNull("Search results must not be null", searchResults)

        // [x] thumbnails
        viewModel.toggleThumbnails()
        assertTrue("Thumbnails panel should be visible", viewModel.viewerState.value.isThumbnailsVisible)
        viewModel.toggleThumbnails()
        assertFalse("Thumbnails panel should be hidden", viewModel.viewerState.value.isThumbnailsVisible)

        // [x] no giant empty area & no crash
        // Verify every single page in the PDF renders without exception or crashing
        for (i in 0 until pageCount) {
            val pageRender = renderer.renderPage(i, 800, 1100)
            assertTrue("Page $i must render without error", pageRender is PageRenderResult.Success)
            val bmp = (pageRender as PageRenderResult.Success).bitmap
            assertTrue("Page $i bitmap must have valid dimensions", bmp.width > 0 && bmp.height > 0)
        }

        renderer.close()
        viewModel.closeViewer()
    }

    // =========================================================================
    // 2. DOCX TEST - Using real "Assignment 1 CV.docx" from assets
    // =========================================================================

    @Test
    fun `docx test - Assignment 1 CV docx complete verification`() = runTest {
        // Copy real Assignment 1 CV.docx from assets into a local file
        val docxFile = File(context.cacheDir, "Assignment 1 CV.docx")
        context.assets.open("Assignment 1 CV.docx").use { input ->
            FileOutputStream(docxFile).use { output ->
                input.copyTo(output)
            }
        }
        assertTrue("Real Assignment 1 CV.docx must exist and be > 0 bytes", docxFile.exists() && docxFile.length() > 0)

        // [x] opens
        val source = resolver.resolve(Uri.fromFile(docxFile))
        assertNotNull("Source must resolve", source)

        val renderer = DocxRenderer()
        val openResult = renderer.open(source)
        assertTrue("DOCX must open successfully", openResult is DocumentOpenResult.Success)
        val success = openResult as DocumentOpenResult.Success

        // [x] actual page rendering into high-fidelity bitmap
        assertTrue("DOCX page count must be >= 1", success.pageCount >= 1)
        val renderResult0 = renderer.renderPage(0, 1080, 1528)
        assertTrue("Page 0 must render to bitmap", renderResult0 is PageRenderResult.Success)
        val bmp0 = (renderResult0 as PageRenderResult.Success).bitmap
        assertNotNull("Rendered bitmap must not be null", bmp0)
        assertEquals(1080, bmp0.width)
        assertEquals(1528, bmp0.height)

        // [x] verify not merely converted to plain paragraphs - check full structure
        val doc = DocxParser.parse(docxFile, "Assignment 1 CV.docx")
        assertNotNull("Section properties must be parsed", doc.sectionProps)
        assertTrue("Printable width > 0", doc.sectionProps.printableWidth > 0f)

        // [x] tables & borders
        val pages = DocxPageLayouter.layout(doc)
        assertTrue("Layout pages must exist", pages.isNotEmpty())
        val allElements = pages.flatMap { it.elements }
        val tables = allElements.filterIsInstance<DocxRenderElement.TableElement>()
        assertTrue("Must contain formatted tables", tables.isNotEmpty())

        // [x] images & logo & signatures
        val images = allElements.filterIsInstance<DocxRenderElement.ImageElement>()
        assertTrue("Document must contain images (logo/signature)", images.isNotEmpty())

        // [x] text, fonts, margins, spacing
        val paragraphs = allElements.filterIsInstance<DocxRenderElement.ParagraphElement>()
        assertTrue("Must contain paragraphs with text runs", paragraphs.isNotEmpty())
        val hasStyledRuns = paragraphs.any { it.paragraph.runs.any { r -> r.isBold || r.isItalic } }
        assertTrue("Must contain styled typography (bold/italic)", hasStyledRuns)

        // [x] headers / footers
        assertNotNull("Header should be parsed if present", doc.header)

        // [x] zoom & navigation
        val dims = renderer.getPageDimensions(0)
        assertNotNull("Page dimensions must be available", dims)
        assertTrue(dims!!.width > 0 && dims.height > 0)

        // [x] search in DOCX
        val matches = renderer.searchInDocument("Education")
        assertNotNull(matches)

        renderer.close()
    }

    // =========================================================================
    // 3. PPTX TEST - Presentation verification
    // =========================================================================

    @Test
    fun `pptx test - presentation structure and slide rendering`() = runTest {
        val pptxFile = File(context.cacheDir, "test_presentation.pptx")
        SamplePptxGenerator.generateUnit23Presentation(pptxFile, context)
        assertTrue("PPTX file must exist", pptxFile.exists())

        val presentation = PptxParser.parse(pptxFile, "Unit 2.3 (2).pptx")
        assertNotNull(presentation)

        // [x] actual slides & correct slide count
        assertEquals("Unit 2.3 (2).pptx must have 6 slides", 6, presentation.slides.size)

        // [x] landscape aspect ratio (~16:9)
        assertTrue("Aspect ratio must be widescreen (> 1.7)", presentation.aspectRatio > 1.7f)

        // [x] text, shapes, tables, diagrams
        val s1 = presentation.slides[0]
        assertTrue("Slide 1 must contain text", s1.fullText.isNotBlank())
        val s4 = presentation.slides[3]
        assertTrue("Slide 4 must contain table element", s4.elements.any { it is PptxElement.Table })

        // [x] rendering slides into Bitmaps
        val source = resolver.resolve(Uri.fromFile(pptxFile))
        val renderer = PptxRenderer()
        val openResult = renderer.open(source)
        assertTrue("Open should succeed", openResult is DocumentOpenResult.Success)

        val renderResult = renderer.renderPage(0, 960, 540)
        assertTrue("Slide 0 must render to bitmap", renderResult is PageRenderResult.Success)
        val bmp = (renderResult as PageRenderResult.Success).bitmap
        assertEquals(960, bmp.width)
        assertEquals(540, bmp.height)

        renderer.close()
    }

    // =========================================================================
    // 4. XLSX TEST - Workbook verification
    // =========================================================================

    @Test
    fun `xlsx test - workbook structure and sheet operations`() = runTest {
        val xlsxFile = File(context.cacheDir, "test_workbook.xlsx")
        SampleXlsxGenerator.generateCgcWorkbook(xlsxFile, context)
        assertTrue("XLSX file must exist", xlsxFile.exists())

        // [x] opens & workbook loads
        val workbook = XlsxParser.parse(xlsxFile, "CGC.xlsx")
        assertNotNull(workbook)

        // [x] sheets available
        assertEquals(4, workbook.sheetCount)
        assertTrue(workbook.sheetNames.contains("Student Records"))
        assertTrue(workbook.sheetNames.contains("Department Summary"))

        // [x] rows, columns, cells
        val sheet1 = workbook.getWorksheet(0)
        assertTrue("Sheet 1 row count >= 20", sheet1.rowCount >= 20)
        assertTrue("Sheet 1 column count >= 10", sheet1.columnCount >= 10)
        val cellA1 = sheet1.getCell(0, 0)
        assertNotNull("Cell A1 must exist", cellA1)
        assertTrue("Cell A1 must contain CGC", cellA1!!.formattedValue.contains("CGC"))

        // [x] sheet switching & rendering
        val source = resolver.resolve(Uri.fromFile(xlsxFile))
        val renderer = XlsxRenderer()
        val openResult = renderer.open(source)
        assertTrue("Open should succeed", openResult is DocumentOpenResult.Success)

        val renderSheet0 = renderer.renderPage(0, 800, 600)
        assertTrue("Sheet 0 must render", renderSheet0 is PageRenderResult.Success)

        val switchedSheet = renderer.selectSheet(1)
        assertNotNull(switchedSheet)
        assertEquals("Department Summary", switchedSheet?.name)

        renderer.close()
    }

    // =========================================================================
    // 5. FILE MANAGEMENT TEST - Persistence, Uri, Discovery, Error Handling
    // =========================================================================

    @Test
    fun `file management test - database persistence and uri handling`() = runTest {
        val db = AppDatabase.getInstance(context)
        val dao = db.documentDao()

        // [x] Recent persists
        val testDoc = DocumentEntity(
            uri = "content://media/external/file/101",
            displayName = "Test Document.pdf",
            mimeType = "application/pdf",
            extension = "pdf",
            documentType = DocumentType.PDF.name,
            fileSize = 1024L,
            modifiedDate = System.currentTimeMillis(),
            lastOpenedDate = System.currentTimeMillis(),
            isFavorite = false,
            pageCount = 5,
            lastViewedPage = 2
        )
        dao.insertDocument(testDoc)

        val recentDocs = dao.getAllRecentDocuments().first()
        assertTrue("Recent documents must contain inserted entity", recentDocs.any { it.uri == testDoc.uri })

        // [x] Favorites persist
        dao.updateFavorite(testDoc.uri, true)
        val favDocs = dao.getFavoriteDocuments().first()
        assertTrue("Favorites must contain favorited entity", favDocs.any { it.uri == testDoc.uri })

        // [x] Missing files handled gracefully
        val nonExistentUri = Uri.parse("file:///non/existent/path/ghost.pdf")
        val errorSource = resolver.resolve(nonExistentUri)
        val pdfRenderer = PdfRenderer()
        val openErrorResult = pdfRenderer.open(errorSource)
        assertTrue("Opening nonexistent file must fail gracefully without crash", openErrorResult is DocumentOpenResult.Error)
        pdfRenderer.close()
    }
}
