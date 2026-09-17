package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.renderer.PdfRenderer
import com.example.ui.MainViewModel
import com.example.util.SamplePdfGenerator
import com.example.viewer.FitMode
import com.example.viewer.PageMode
import com.example.viewer.ViewerStatus
import com.example.viewer.ZoomController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfReaderExperienceTest {

    @Test
    fun `test ZoomController operations`() {
        val zoomController = ZoomController()

        // Initial state
        assertEquals(1.0f, zoomController.scale, 0.001f)
        assertEquals(0f, zoomController.offsetX, 0.001f)
        assertEquals(0f, zoomController.offsetY, 0.001f)

        // Zoom In
        zoomController.zoomIn()
        assertTrue("Zoom in should increase scale", zoomController.scale > 1.0f)
        assertEquals(1.3f, zoomController.scale, 0.001f)

        // Zoom Out
        zoomController.zoomOut()
        assertEquals(1.0f, zoomController.scale, 0.001f)

        // Fit Width
        zoomController.fitWidth()
        assertEquals(1.0f, zoomController.scale, 0.001f)

        // Fit Page
        zoomController.fitPage()
        assertEquals(1.0f, zoomController.scale, 0.001f)

        // Max scale constraint
        repeat(15) { zoomController.zoomIn() }
        assertTrue("Scale must not exceed max scale 5.0f", zoomController.scale <= 5.0f)

        // Reset
        zoomController.reset()
        assertEquals(1.0f, zoomController.scale, 0.001f)
        assertEquals(0f, zoomController.offsetX, 0.001f)
        assertEquals(0f, zoomController.offsetY, 0.001f)
    }

    @Test
    fun `test MainViewModel PDF Reader Experience with Students List pdf`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val testDir = File(app.filesDir, "test_pdfs").apply { mkdirs() }
        val pdfFile = File(testDir, "Students List.pdf")

        // 1. Generate multi-page PDF
        SamplePdfGenerator.generateStudentsListPdf(pdfFile, app)
        assertTrue(pdfFile.exists() && pdfFile.length() > 0)

        val viewModel = MainViewModel(app)

        // 2. Open document in ViewModel
        viewModel.openDocument(Uri.fromFile(pdfFile))

        var attempts = 0
        while (viewModel.viewerState.value.status !is ViewerStatus.Ready && attempts < 50) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(100)
            attempts++
        }
        ShadowLooper.idleMainLooper()

        val state = viewModel.viewerState.value
        assertTrue("Viewer status should be Ready", state.status is ViewerStatus.Ready)
        assertEquals("Students List.pdf", state.title)
        assertEquals(3, state.totalPages)
        assertEquals(0, state.currentPage)

        // Default page mode should be PAGE_BY_PAGE
        assertEquals(PageMode.PAGE_BY_PAGE, state.pageMode)
        assertEquals(FitMode.FIT_WIDTH, state.fitMode)

        // 3. Test switching to Continuous Scrolling mode
        viewModel.setPageMode(PageMode.CONTINUOUS)
        assertEquals(PageMode.CONTINUOUS, viewModel.viewerState.value.pageMode)

        // Switch back to Page-by-page mode
        viewModel.setPageMode(PageMode.PAGE_BY_PAGE)
        assertEquals(PageMode.PAGE_BY_PAGE, viewModel.viewerState.value.pageMode)

        // 4. Test switching Fit Mode
        viewModel.setFitMode(FitMode.FIT_PAGE)
        assertEquals(FitMode.FIT_PAGE, viewModel.viewerState.value.fitMode)

        viewModel.setFitMode(FitMode.FIT_WIDTH)
        assertEquals(FitMode.FIT_WIDTH, viewModel.viewerState.value.fitMode)

        // 5. Test page navigation across pages
        viewModel.selectPage(1)
        attempts = 0
        while (viewModel.viewerState.value.currentPage != 1 && attempts < 50) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(100)
            attempts++
        }
        assertEquals(1, viewModel.viewerState.value.currentPage)

        // Navigate to Landscape page (page index 2)
        viewModel.selectPage(2)
        attempts = 0
        while (viewModel.viewerState.value.currentPage != 2 && attempts < 50) {
            ShadowLooper.idleMainLooper()
            Thread.sleep(100)
            attempts++
        }
        assertEquals(2, viewModel.viewerState.value.currentPage)

        // 6. Test renderer access
        val renderer = viewModel.getCurrentRenderer()
        assertNotNull("Current renderer should be available when document is open", renderer)
        assertTrue(renderer is PdfRenderer)
        assertEquals(3, renderer!!.getPageCount())

        // 7. Test toggle viewer controls
        val initialControls = viewModel.viewerState.value.areControlsVisible
        viewModel.toggleViewerControls()
        assertEquals(!initialControls, viewModel.viewerState.value.areControlsVisible)
        viewModel.toggleViewerControls()
        assertEquals(initialControls, viewModel.viewerState.value.areControlsVisible)

        // 8. Test close viewer
        viewModel.closeViewer()
        ShadowLooper.idleMainLooper()
        assertEquals(ViewerStatus.Idle, viewModel.viewerState.value.status)
        assertEquals(null, viewModel.getCurrentRenderer())
    }
}
