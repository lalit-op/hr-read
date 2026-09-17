package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.documents.DocumentSourceResolver
import com.example.data.filesystem.DocumentCacheManager
import com.example.renderer.DocxRenderer
import com.example.renderer.DocumentOpenResult
import com.example.renderer.PageRenderResult
import com.example.renderer.docx.DocxPageLayouter
import com.example.renderer.docx.DocxParser
import com.example.renderer.docx.DocxRenderElement
import com.example.util.SampleDocxGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocxRendererTest {

    @Test
    fun `test Assignment 1 CV docx rendering and element verification`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testDir = File(context.filesDir, "test_docx").apply { mkdirs() }
        val docxFile = File(testDir, SampleDocxGenerator.SAMPLE_FILE_NAME)

        // 1. Generate genuine OpenXML DOCX: Assignment 1 CV.docx
        SampleDocxGenerator.generateAssignment1Cv(docxFile, context)
        assertTrue("Assignment 1 CV.docx must exist and be non-empty", docxFile.exists() && docxFile.length() > 0)

        // Also ensure copy is saved to assets if the folder exists
        listOf(
            File("src/main/assets", SampleDocxGenerator.SAMPLE_FILE_NAME),
            File("../app/src/main/assets", SampleDocxGenerator.SAMPLE_FILE_NAME)
        ).forEach { assetTarget ->
            try {
                if (assetTarget.parentFile?.exists() == true) {
                    docxFile.copyTo(assetTarget, overwrite = true)
                }
            } catch (_: Exception) {}
        }

        // 2. Parse directly to verify OpenXML document model and structural elements
        val doc = DocxParser.parse(docxFile, SampleDocxGenerator.SAMPLE_FILE_NAME)
        assertEquals(SampleDocxGenerator.SAMPLE_FILE_NAME, doc.title)
        assertNotNull("Section properties must be parsed", doc.sectionProps)
        assertTrue("Printable width must be positive", doc.sectionProps.printableWidth > 300f)
        assertTrue("Printable height must be positive", doc.sectionProps.printableHeight > 500f)

        // Verify media images extracted (logo and signature)
        assertTrue("Embedded media must be extracted", doc.media.isNotEmpty())
        assertTrue("Embedded logo rId4 must exist", doc.media.containsKey("rId4"))
        assertTrue("Embedded signature rId5 must exist", doc.media.containsKey("rId5"))

        // Verify Header and Footer parsed
        assertNotNull("Header must be parsed", doc.header)
        assertNotNull("Footer must be parsed", doc.footer)
        assertTrue("Header contains applicant name", doc.header!!.paragraphs.any { it.fullText.contains("Mercer") })

        // 3. Layout pages and verify pagination
        val pages = DocxPageLayouter.layout(doc)
        assertTrue("Document must have at least 2 pages due to page break", pages.size >= 2)

        val page1 = pages[0]
        val page2 = pages[1]

        // Verify Page 1 elements: Logo, Typography, Tables, Borders, Margins
        val p1Images = page1.elements.filterIsInstance<DocxRenderElement.ImageElement>()
        assertTrue("Page 1 must contain crest logo image", p1Images.isNotEmpty())
        val logoImage = p1Images.first()
        assertTrue("Logo must have valid dimensions", logoImage.width > 30f && logoImage.height > 30f)

        val p1Paragraphs = page1.elements.filterIsInstance<DocxRenderElement.ParagraphElement>()
        assertTrue("Page 1 must have candidate typography", p1Paragraphs.any { it.paragraph.fullText.contains("MERCER") })
        val nameP = p1Paragraphs.first { it.paragraph.fullText.contains("MERCER") }
        assertTrue("Name must be bold", nameP.paragraph.runs.any { it.isBold })

        val p1Tables = page1.elements.filterIsInstance<DocxRenderElement.TableElement>()
        assertTrue("Page 1 must contain formatted tables (Education and Skills)", p1Tables.size >= 2)
        val eduTable = p1Tables.first()
        assertTrue("Education table must have header row", eduTable.table.rows.first().isHeader)
        assertTrue("Education table must have cell background shading", eduTable.table.rows.first().cells.any { it.backgroundColor != null })

        // Verify Page 2 elements: Professional experience, Declaration, Signature
        val p2Paragraphs = page2.elements.filterIsInstance<DocxRenderElement.ParagraphElement>()
        assertTrue("Page 2 must contain Professional Experience", p2Paragraphs.any { it.paragraph.fullText.contains("EXPERIENCE") })
        assertTrue("Page 2 must contain Declaration", p2Paragraphs.any { it.paragraph.fullText.contains("DECLARATION") })

        val p2Images = page2.elements.filterIsInstance<DocxRenderElement.ImageElement>()
        assertTrue("Page 2 must contain signature drawing", p2Images.isNotEmpty())

        // 4. Test DocxRenderer through the complete DocumentSourceResolver pipeline
        val cacheManager = DocumentCacheManager(context)
        val resolver = DocumentSourceResolver(context, cacheManager)
        val source = resolver.resolve(Uri.fromFile(docxFile))
        assertNotNull("Resolved source must not be null", source)

        val renderer = DocxRenderer()
        val openResult = renderer.open(source)

        assertTrue("Open result should be Success", openResult is DocumentOpenResult.Success)
        val success = openResult as DocumentOpenResult.Success
        assertEquals(SampleDocxGenerator.SAMPLE_FILE_NAME, success.title)
        assertTrue("Page count must be at least 2", success.pageCount >= 2)
        assertEquals(success.pageCount, renderer.getPageCount())

        // 5. Test page dimensions
        val dims0 = renderer.getPageDimensions(0)
        assertNotNull("Page 0 dimensions must exist", dims0)
        assertEquals(595.28f, dims0!!.width, 5f)
        assertEquals(841.89f, dims0.height, 5f)

        // 6. Test rendering page 0 into high-fidelity Bitmap
        val renderResult0 = renderer.renderPage(0, 1080, 1528)
        assertTrue("Page 0 render result must be Success", renderResult0 is PageRenderResult.Success)
        val page0Success = renderResult0 as PageRenderResult.Success
        assertNotNull("Page 0 bitmap must be generated", page0Success.bitmap)
        assertEquals(1080, page0Success.bitmap.width)
        assertEquals(1528, page0Success.bitmap.height)

        // 7. Test rendering page 1 into high-fidelity Bitmap
        val renderResult1 = renderer.renderPage(1, 1080, 1528)
        assertTrue("Page 1 render result must be Success", renderResult1 is PageRenderResult.Success)
        val page1Success = renderResult1 as PageRenderResult.Success
        assertNotNull("Page 1 bitmap must be generated", page1Success.bitmap)

        // 8. Test text search across pages (separate from visual rendering)
        val searchResults = renderer.searchInDocument("Mercer")
        assertTrue("Search for 'Mercer' should find occurrences", searchResults.isNotEmpty())
        assertTrue("Occurrences should include Page 0", searchResults.any { it.pageIndex == 0 })

        val stanfordResults = renderer.searchInDocument("Stanford")
        assertTrue("Search for 'Stanford' should find Education entry", stanfordResults.isNotEmpty())
        assertEquals(0, stanfordResults.first().pageIndex)

        // 9. Test text extraction
        val text0 = renderer.extractText(0)
        assertNotNull("Text on page 0 must be extractable", text0)
        assertTrue("Page 0 text must contain Mercer", text0!!.contains("MERCER"))
        assertTrue("Page 0 text must contain Stanford", text0.contains("Stanford"))

        // 10. Test cleanup
        renderer.close()
        assertEquals(0, renderer.getPageCount())
    }
}
