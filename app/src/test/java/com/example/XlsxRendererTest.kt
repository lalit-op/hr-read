package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.documents.DocumentSourceResolver
import com.example.data.filesystem.DocumentCacheManager
import com.example.renderer.DocumentOpenResult
import com.example.renderer.PageRenderResult
import com.example.renderer.XlsxRenderer
import com.example.renderer.xlsx.CellType
import com.example.renderer.xlsx.XlsxParser
import com.example.util.SampleXlsxGenerator
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
class XlsxRendererTest {

    private lateinit var context: Context
    private lateinit var cgcFile: File
    private lateinit var renderer: XlsxRenderer
    private lateinit var resolver: DocumentSourceResolver

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val testDir = File(context.filesDir, "test_xlsx").apply { mkdirs() }
        cgcFile = File(testDir, SampleXlsxGenerator.SAMPLE_FILE_NAME)
        SampleXlsxGenerator.generateCgcWorkbook(cgcFile, context)

        val cacheManager = DocumentCacheManager(context)
        resolver = DocumentSourceResolver(context, cacheManager)
        renderer = XlsxRenderer()
    }

    @Test
    fun testXlsxParser_parsesCgcWorkbookStructure() {
        val workbook = XlsxParser.parse(cgcFile, SampleXlsxGenerator.SAMPLE_FILE_NAME)
        assertNotNull(workbook)
        assertEquals(SampleXlsxGenerator.SAMPLE_FILE_NAME, workbook.title)
        assertEquals("Workbook should contain 4 sheets", 4, workbook.sheetCount)

        val expectedSheets = listOf("Student Records", "Department Summary", "Fee Status", "Grading Scale")
        assertEquals("Sheet names must match expected worksheets", expectedSheets, workbook.sheetNames)

        // Verify Sheet 1 (Student Records)
        val sheet1 = workbook.getWorksheet(0)
        assertEquals("Student Records", sheet1.name)
        assertTrue("Sheet 1 should contain rows", sheet1.rowCount >= 20)
        assertTrue("Sheet 1 should contain columns", sheet1.columnCount >= 11)

        // Check title cell
        val titleCell = sheet1.getCell(0, 0)
        assertNotNull("Cell A1 should exist", titleCell)
        assertTrue("Title cell should contain CGC", titleCell!!.formattedValue.contains("CGC"))
        assertNotNull("Cell A1 should have background style", titleCell.style.backgroundColor)

        // Check student data cell (Row 5 is first student Aarav Sharma, col B is index 1)
        val studentCell = sheet1.getCell(4, 1)
        assertNotNull("Student cell B5 should exist", studentCell)
        assertEquals("Aarav Sharma", studentCell!!.formattedValue)
        assertEquals(CellType.STRING, studentCell.type)

        // Check custom column widths and row heights
        assertTrue("Column widths should be populated", sheet1.columnWidths.isNotEmpty())
        assertTrue("Row heights should be populated", sheet1.rowHeights.isNotEmpty())
    }

    @Test
    fun testXlsxRenderer_openAndSheetSelection() = runTest {
        val source = resolver.resolve(Uri.fromFile(cgcFile))
        val result = renderer.open(source)

        assertTrue("Open should succeed", result is DocumentOpenResult.Success)
        val success = result as DocumentOpenResult.Success
        assertEquals(4, success.pageCount)
        assertEquals(SampleXlsxGenerator.SAMPLE_FILE_NAME, success.title)

        assertEquals(0, renderer.getActiveSheetIndex())
        val initialSheet = renderer.getActiveWorksheet()
        assertNotNull(initialSheet)
        assertEquals("Student Records", initialSheet?.name)

        // Switch to Sheet 2: Department Summary
        val sheet2 = renderer.selectSheet(1)
        assertNotNull(sheet2)
        assertEquals("Department Summary", sheet2?.name)
        assertEquals(1, renderer.getActiveSheetIndex())
        assertTrue("Department summary should contain KPI rows", (sheet2?.rowCount ?: 0) >= 15)

        // Switch to Sheet 3: Fee Status
        val sheet3 = renderer.selectSheet(2)
        assertNotNull(sheet3)
        assertEquals("Fee Status", sheet3?.name)
        assertEquals(2, renderer.getActiveSheetIndex())

        // Switch to Sheet 4: Grading Scale
        val sheet4 = renderer.selectSheet(3)
        assertNotNull(sheet4)
        assertEquals("Grading Scale", sheet4?.name)
        assertEquals(3, renderer.getActiveSheetIndex())
    }

    @Test
    fun testXlsxRenderer_renderSheetToBitmap() = runTest {
        val source = resolver.resolve(Uri.fromFile(cgcFile))
        renderer.open(source)

        val renderResult = renderer.renderPage(0, 800, 600)
        assertTrue("Render result should succeed", renderResult is PageRenderResult.Success)
        val success = renderResult as PageRenderResult.Success
        assertNotNull("Bitmap must not be null", success.bitmap)
        assertEquals(800, success.bitmap.width)
        assertEquals(600, success.bitmap.height)
    }

    @Test
    fun testXlsxRenderer_searchAcrossWorksheets() = runTest {
        val source = resolver.resolve(Uri.fromFile(cgcFile))
        renderer.open(source)

        // Search for student in Sheet 1
        val studentMatches = renderer.searchInDocument("Ananya")
        assertTrue("Should find student Ananya", studentMatches.isNotEmpty())
        assertEquals(0, studentMatches.first().pageIndex)
        assertTrue(studentMatches.first().snippet.contains("Student Records"))

        // Search for course in Sheet 2
        val courseMatches = renderer.searchInDocument("Operating Systems")
        assertTrue("Should find Operating Systems", courseMatches.isNotEmpty())
        assertEquals(1, courseMatches.first().pageIndex)
        assertTrue(courseMatches.first().snippet.contains("Department Summary"))
    }

    @Test
    fun testXlsxParser_cellReferenceCalculations() {
        assertEquals(0, XlsxParser.colNameToIndex("A"))
        assertEquals(1, XlsxParser.colNameToIndex("B"))
        assertEquals(25, XlsxParser.colNameToIndex("Z"))
        assertEquals(26, XlsxParser.colNameToIndex("AA"))

        assertEquals("A", XlsxParser.indexToColName(0))
        assertEquals("B", XlsxParser.indexToColName(1))
        assertEquals("Z", XlsxParser.indexToColName(25))
        assertEquals("AA", XlsxParser.indexToColName(26))

        val (r, c) = XlsxParser.parseCellReference("C5")
        assertEquals(4, r)
        assertEquals(2, c)
    }
}
