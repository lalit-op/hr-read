package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.documents.DocumentSourceResolver
import com.example.data.filesystem.DocumentCacheManager
import com.example.renderer.DocumentOpenResult
import com.example.renderer.PageRenderResult
import com.example.renderer.PptxRenderer
import com.example.renderer.pptx.PptxElement
import com.example.renderer.pptx.PptxParser
import com.example.util.SamplePptxGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
class PptxRendererTest {

    private lateinit var context: Context
    private lateinit var pptxFile: File
    private lateinit var renderer: PptxRenderer
    private lateinit var resolver: DocumentSourceResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testDir = File(context.filesDir, "test_pptx").apply { mkdirs() }
        pptxFile = File(testDir, SamplePptxGenerator.SAMPLE_FILE_NAME)
        SamplePptxGenerator.generateUnit23Presentation(pptxFile, context)

        val cacheManager = DocumentCacheManager(context)
        resolver = DocumentSourceResolver(context, cacheManager)
        renderer = PptxRenderer()
    }

    @Test
    fun testPptxParser_parsesPresentationStructureAccurately() {
        val presentation = PptxParser.parse(pptxFile, SamplePptxGenerator.SAMPLE_FILE_NAME)
        assertEquals(SamplePptxGenerator.SAMPLE_FILE_NAME, presentation.title)
        assertEquals("Presentation should contain 6 slides", 6, presentation.slides.size)
        assertTrue("Slide width must be positive", presentation.slideWidthPt > 0f)
        assertTrue("Slide height must be positive", presentation.slideHeightPt > 0f)
        assertTrue("Aspect ratio should be widescreen (> 1.7)", presentation.aspectRatio > 1.7f)

        // Slide 1 has title text
        val s1 = presentation.slides[0]
        assertTrue("Slide 1 should contain Unit 2.3", s1.fullText.contains("Unit 2.3"))

        // Slide 4 has table
        val s4 = presentation.slides[3]
        assertTrue("Slide 4 should contain table element", s4.elements.any { it is PptxElement.Table })
    }

    @Test
    fun testOpenSamplePresentation_successAndAccurateSlideCount() = runTest {
        val source = resolver.resolve(Uri.fromFile(pptxFile))
        assertNotNull("Source must resolve", source)

        val openResult = renderer.open(source)
        assertTrue("Document open result should be Success", openResult is DocumentOpenResult.Success)

        val success = openResult as DocumentOpenResult.Success
        assertEquals("Unit 2.3 (2).pptx should have 6 slides", 6, success.pageCount)

        // Verify dimensions are widescreen
        val dims = renderer.getPageDimensions(0)
        assertNotNull(dims)
        assertTrue("Aspect ratio should be ~16:9 (> 1.7)", dims!!.aspectRatio > 1.7f)
    }

    @Test
    fun testRenderAllSlides_returnsHighFidelityBitmaps() = runTest {
        val source = resolver.resolve(Uri.fromFile(pptxFile))
        assertNotNull(source)
        val openResult = renderer.open(source)
        assertTrue(openResult is DocumentOpenResult.Success)
        val pageCount = (openResult as DocumentOpenResult.Success).pageCount

        for (pageIndex in 0 until pageCount) {
            val renderResult = renderer.renderPage(pageIndex, 960, 540)
            assertTrue("Slide $pageIndex should render successfully", renderResult is PageRenderResult.Success)

            val success = renderResult as PageRenderResult.Success
            assertNotNull("Rendered bitmap must not be null", success.bitmap)
            assertEquals("Bitmap width should match target width", 960, success.bitmap.width)
            assertEquals("Bitmap height should match target height", 540, success.bitmap.height)
        }
    }

    @Test
    fun testSearchInPptx_findsKeywordsAcrossSlides() = runTest {
        val source = resolver.resolve(Uri.fromFile(pptxFile))
        assertNotNull(source)
        renderer.open(source)

        // Slide 1 has "Unit 2.3"
        val resultsTitle = renderer.searchInDocument("Unit 2.3")
        assertTrue("Should find 'Unit 2.3' in presentation", resultsTitle.isNotEmpty())
        assertEquals(0, resultsTitle.first().pageIndex)

        // Slide 3 has "Architecture"
        val resultsArch = renderer.searchInDocument("Architecture")
        assertTrue("Should find 'Architecture' in slide text", resultsArch.isNotEmpty())
    }

    @Test
    fun testClose_releasesResourcesSafely() = runTest {
        val source = resolver.resolve(Uri.fromFile(pptxFile))
        assertNotNull(source)
        renderer.open(source)
        renderer.renderPage(0, 480, 270)
        renderer.close()

        val dims = renderer.getPageDimensions(0)
        assertEquals(null, dims)
    }
}
