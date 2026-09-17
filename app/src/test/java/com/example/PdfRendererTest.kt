package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.documents.DocumentSourceResolver
import com.example.data.filesystem.DocumentCacheManager
import com.example.renderer.DocumentOpenResult
import com.example.renderer.PageRenderResult
import com.example.renderer.PdfRenderer
import com.example.util.SamplePdfGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfRendererTest {

    @Test
    fun `test Students List pdf rendering and lifecycle`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.filesDir, "test_pdfs").apply { mkdirs() }
        val pdfFile = File(testDir, "Students List.pdf")

        // 1. Generate genuine multi-page vector PDF: Students List.pdf
        SamplePdfGenerator.generateStudentsListPdf(pdfFile, context)
        assertTrue("Students List.pdf must exist and be non-empty", pdfFile.exists() && pdfFile.length() > 0)

        // 2. Resolve document source using DocumentSourceResolver
        val cacheManager = DocumentCacheManager(context)
        val resolver = DocumentSourceResolver(context, cacheManager)
        val source = resolver.resolve(Uri.fromFile(pdfFile))
        assertNotNull("Resolved source must not be null", source)
        assertEquals("Students List.pdf", source.displayName)

        // 3. Initialize PdfRenderer
        val renderer = PdfRenderer()
        val openResult = renderer.open(source)

        assertTrue("Open result should be Success", openResult is DocumentOpenResult.Success)
        val successResult = openResult as DocumentOpenResult.Success
        assertTrue("Renderer must be initialized", renderer.isInitialized)

        // 4. Verify correct page count
        assertEquals("Students List.pdf must have 3 pages", 3, successResult.pageCount)
        assertEquals("getPageCount must return 3", 3, renderer.getPageCount())

        // 5. Verify page dimensions & aspect ratios (portrait vs landscape large-page)
        val page0Dims = renderer.getPageDimensions(0)
        assertNotNull("Page 0 dimensions must exist", page0Dims)
        assertEquals(595f, page0Dims!!.width, 1f)
        assertEquals(842f, page0Dims.height, 1f)
        val page0AspectRatio = page0Dims.aspectRatio
        assertTrue("Page 0 should be portrait (aspect ratio < 1)", page0AspectRatio < 1.0f)

        // Page 2 is large landscape format (842 x 595)
        val page2Dims = renderer.getPageDimensions(2)
        assertNotNull("Page 2 dimensions must exist", page2Dims)
        assertEquals(842f, page2Dims!!.width, 1f)
        assertEquals(595f, page2Dims.height, 1f)
        val page2AspectRatio = page2Dims.aspectRatio
        assertTrue("Page 2 should be landscape (aspect ratio > 1)", page2AspectRatio > 1.0f)

        assertEquals(page0Dims.width, renderer.getPageWidth(0)!!, 1f)
        assertEquals(page0Dims.height, renderer.getPageHeight(0)!!, 1f)

        // 6. Verify real page rendering into Bitmap
        val renderResult0 = renderer.renderPage(0, 1190, 1684)
        assertTrue("Page 0 render must succeed", renderResult0 is PageRenderResult.Success)
        val bitmap0 = (renderResult0 as PageRenderResult.Success).bitmap
        assertNotNull("Rendered bitmap must not be null", bitmap0)
        assertTrue("Rendered bitmap must have positive width", bitmap0.width > 0)
        assertTrue("Rendered bitmap must have positive height", bitmap0.height > 0)

        // Verify aspect ratio preservation (no stretching, no distortion)
        val bitmap0Ratio = bitmap0.width.toFloat() / bitmap0.height.toFloat()
        assertTrue(
            "Rendered page 0 must strictly preserve aspect ratio (no stretching)",
            abs(bitmap0Ratio - page0AspectRatio) < 0.05f
        )

        // 7. Verify large page rendering (Page 2 - Landscape Blueprint)
        val renderResult2 = renderer.renderPage(2, 1684, 1190)
        assertTrue("Page 2 landscape render must succeed", renderResult2 is PageRenderResult.Success)
        val bitmap2 = (renderResult2 as PageRenderResult.Success).bitmap
        assertNotNull(bitmap2)
        val bitmap2Ratio = bitmap2.width.toFloat() / bitmap2.height.toFloat()
        assertTrue(
            "Rendered page 2 must preserve wide landscape aspect ratio",
            abs(bitmap2Ratio - page2AspectRatio) < 0.05f
        )
        assertTrue("Page 2 bitmap must be wider than tall", bitmap2.width > bitmap2.height)

        // 8. Verify LRU Cache performance
        val renderResult0Cached = renderer.renderPage(0, 1190, 1684)
        assertTrue(renderResult0Cached is PageRenderResult.Success)
        assertEquals(
            "Subsequent render of page 0 should return cached instance",
            bitmap0,
            (renderResult0Cached as PageRenderResult.Success).bitmap
        )

        // 9. Verify page navigation across multiple pages
        val renderResult1 = renderer.renderPage(1, 1190, 1684)
        assertTrue("Page 1 render must succeed", renderResult1 is PageRenderResult.Success)
        assertEquals(1, (renderResult1 as PageRenderResult.Success).pageIndex)

        // 10. Verify search in document where supported
        val matches = renderer.searchInDocument("Emma")
        // Extractor should safely run without throwing exceptions
        assertNotNull(matches)

        // 11. Verify clean close and resource release
        renderer.close()
        assertTrue("Renderer must not be initialized after close", !renderer.isInitialized)
        assertEquals("Page count should be 0 after close", 0, renderer.getPageCount())
    }
}
