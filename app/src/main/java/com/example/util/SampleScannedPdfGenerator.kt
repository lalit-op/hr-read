package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.search.IndexedEntry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/**
 * Generates an authentic scanned document PDF containing only image data (no native text streams).
 * Used to verify the OCR auxiliary search pipeline.
 *
 * Visual rendering displays the high-fidelity scanned image.
 * Auxiliary OCR extracts and indexes text into the search index without altering the visual source.
 */
object SampleScannedPdfGenerator {

    const val SAMPLE_FILE_NAME = "Scanned Invoice.pdf"

    // Known ground-truth text elements and their layout bounding boxes [left, top, right, bottom] in [0..1]
    val GROUND_TRUTH_TEXT = listOf(
        Pair("ACME ENTERPRISE SOLUTIONS - INVOICE", RectF(0.08f, 0.06f, 0.90f, 0.11f)),
        Pair("Invoice Number: INV-2026-8891", RectF(0.08f, 0.14f, 0.55f, 0.18f)),
        Pair("Billing Date: September 15, 2026", RectF(0.08f, 0.19f, 0.55f, 0.23f)),
        Pair("Billed To: HR Read Corporation", RectF(0.08f, 0.25f, 0.55f, 0.29f)),
        Pair("Item: Annual Multi-Platform Document Reader License", RectF(0.08f, 0.36f, 0.85f, 0.40f)),
        Pair("Quantity: 50 Enterprise Seats", RectF(0.08f, 0.42f, 0.55f, 0.46f)),
        Pair("Payment Status: APPROVED & PROCESSED", RectF(0.08f, 0.52f, 0.65f, 0.57f)),
        Pair("Total Amount Due: $4,850.00 USD", RectF(0.08f, 0.60f, 0.65f, 0.65f)),
        Pair("Authorized Signature: Dr. Evelyn Reed", RectF(0.08f, 0.72f, 0.60f, 0.76f))
    )

    fun generateScannedInvoicePdf(destinationFile: File, context: Context? = null): File {
        destinationFile.parentFile?.mkdirs()

        // Create 600x800 bitmap rendered as a scanned printed paper sheet
        val width = 600
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Off-white / ivory scanned paper background
        canvas.drawColor(Color.rgb(250, 248, 243))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 32, 40)
            textSize = 24f
            isFakeBoldText = true
        }

        // Draw header bar
        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 75, 120)
        }
        canvas.drawRect(40f, 40f, 560f, 90f, headerPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 20f
            isFakeBoldText = true
        }
        canvas.drawText("ACME ENTERPRISE - INVOICE", 60f, 72f, titlePaint)

        // Draw scanned text lines
        paint.textSize = 17f
        paint.isFakeBoldText = false
        paint.color = Color.rgb(35, 40, 48)

        canvas.drawText("Invoice Number: INV-2026-8891", 48f, 135f, paint)
        canvas.drawText("Billing Date: September 15, 2026", 48f, 175f, paint)
        canvas.drawText("Billed To: HR Read Corporation", 48f, 220f, paint)

        // Separator line
        val linePaint = Paint().apply {
            color = Color.rgb(190, 195, 205)
            strokeWidth = 2f
        }
        canvas.drawLine(48f, 255f, 552f, 255f, linePaint)

        canvas.drawText("Item: Annual Multi-Platform Document Reader License", 48f, 310f, paint)
        canvas.drawText("Quantity: 50 Enterprise Seats", 48f, 355f, paint)

        // Status badge
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 242, 225)
        }
        canvas.drawRoundRect(RectF(48f, 410f, 420f, 455f), 8f, 8f, badgePaint)
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 110, 50)
            textSize = 16f
            isFakeBoldText = true
        }
        canvas.drawText("Payment Status: APPROVED & PROCESSED", 60f, 440f, badgeTextPaint)

        // Total
        val totalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 30, 45)
            textSize = 21f
            isFakeBoldText = true
        }
        canvas.drawText("Total Amount Due: $4,850.00 USD", 48f, 510f, totalPaint)

        // Signature line
        canvas.drawLine(48f, 600f, 320f, 600f, linePaint)
        paint.textSize = 14f
        paint.color = Color.rgb(90, 100, 115)
        canvas.drawText("Authorized Signature: Dr. Evelyn Reed", 48f, 625f, paint)

        // Convert bitmap RGB bytes (raw 24-bit RGB) for PDF /XObject /Image
        val rgbBytes = ByteArray(width * height * 3)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var pIdx = 0
        var bIdx = 0
        while (pIdx < pixels.size) {
            val pixel = pixels[pIdx]
            rgbBytes[bIdx++] = Color.red(pixel).toByte()
            rgbBytes[bIdx++] = Color.green(pixel).toByte()
            rgbBytes[bIdx++] = Color.blue(pixel).toByte()
            pIdx++
        }

        // Deflate compress image data
        val deflatedOut = ByteArrayOutputStream()
        DeflaterOutputStream(deflatedOut, Deflater(Deflater.BEST_COMPRESSION)).use { dos ->
            dos.write(rgbBytes)
        }
        val deflatedImage = deflatedOut.toByteArray()

        // Build authentic image-only PDF file without text streams
        val pdfContent = buildImageOnlyPdf(width, height, deflatedImage)
        FileOutputStream(destinationFile).use { fos ->
            fos.write(pdfContent)
        }

        return destinationFile
    }

    private fun buildImageOnlyPdf(width: Int, height: Int, compressedImage: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()

        fun writeString(s: String) {
            bos.write(s.toByteArray(Charsets.ISO_8859_1))
        }

        val offsets = mutableListOf<Int>()

        // Header
        writeString("%PDF-1.4\n%\u00E2\u00E3\u00CF\u00D3\n")

        // Obj 1: Catalog
        offsets.add(bos.size())
        writeString("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // Obj 2: Pages
        offsets.add(bos.size())
        writeString("2 0 obj\n<< /Type /Pages /Kids [ 3 0 R ] /Count 1 >>\nendobj\n")

        // Obj 3: Page (references image XObject in Resources, Contents draws image covering page)
        offsets.add(bos.size())
        writeString("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [ 0 0 595 842 ]\n")
        writeString("/Resources << /XObject << /Im1 5 0 R >> >>\n")
        writeString("/Contents 4 0 R\n>>\nendobj\n")

        // Obj 4: Content stream (Purely draws the image XObject /Im1 do - NO text operators!)
        val contentDrawing = "q 595 0 0 842 0 0 cm /Im1 Do Q\n"
        offsets.add(bos.size())
        writeString("4 0 obj\n<< /Length ${contentDrawing.length} >>\nstream\n")
        writeString(contentDrawing)
        writeString("endstream\nendobj\n")

        // Obj 5: Image XObject with raw RGB stream
        offsets.add(bos.size())
        writeString("5 0 obj\n<< /Type /XObject /Subtype /Image\n")
        writeString("/Width $width /Height $height /ColorSpace /DeviceRGB /BitsPerComponent 8\n")
        writeString("/Filter /FlateDecode /Length ${compressedImage.size} >>\nstream\n")
        bos.write(compressedImage)
        writeString("\nendstream\nendobj\n")

        // Cross-reference table
        val xrefOffset = bos.size()
        writeString("xref\n0 6\n")
        writeString("0000000000 65535 f \n")
        for (off in offsets) {
            writeString(String.format("%010d 00000 n \n", off))
        }

        // Trailer
        writeString("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")

        return bos.toByteArray()
    }
}
