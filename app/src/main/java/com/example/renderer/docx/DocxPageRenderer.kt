package com.example.renderer.docx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import kotlin.math.min

object DocxPageRenderer {

    private val bgPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun renderPageToBitmap(page: DocxPage, targetWidth: Int, targetHeight: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Draw page paper background
        canvas.drawColor(Color.WHITE)

        // 2. Compute scaling factors
        val scaleX = targetWidth.toFloat() / page.width
        val scaleY = targetHeight.toFloat() / page.height
        val scale = min(scaleX, scaleY)

        canvas.save()
        // Center on canvas if aspect ratios slightly diverge
        val offsetX = (targetWidth - page.width * scale) / 2f
        val offsetY = (targetHeight - page.height * scale) / 2f
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // 3. Render Header
        renderHeader(canvas, page)

        // 4. Render Main Content Elements
        for (element in page.elements) {
            when (element) {
                is DocxRenderElement.ParagraphElement -> {
                    renderParagraph(canvas, element.paragraph, element.x, element.y, element.width)
                }

                is DocxRenderElement.TableElement -> {
                    renderTable(canvas, element)
                }

                is DocxRenderElement.ImageElement -> {
                    renderImage(canvas, element)
                }

                is DocxRenderElement.DividerElement -> {
                    borderPaint.color = element.color
                    borderPaint.strokeWidth = element.strokeWidthPt
                    canvas.drawLine(
                        element.x,
                        element.y,
                        element.x + element.width,
                        element.y,
                        borderPaint
                    )
                }
            }
        }

        // 5. Render Footer
        renderFooter(canvas, page)

        canvas.restore()
        return bitmap
    }

    private fun renderHeader(canvas: Canvas, page: DocxPage) {
        val header = page.header
        val section = page.sectionProps
        val headerY = section.headerMargin
        val headerWidth = section.printableWidth
        val headerX = section.marginLeft

        if (header != null && header.paragraphs.isNotEmpty()) {
            var currY = headerY
            for (p in header.paragraphs) {
                renderParagraph(canvas, p, headerX, currY, headerWidth, page.pageIndex, page.totalPages)
                currY += 14f
            }
            // Draw subtle header divider rule
            borderPaint.color = 0xFFE2E8F0.toInt()
            borderPaint.strokeWidth = 0.5f
            canvas.drawLine(headerX, currY + 4f, headerX + headerWidth, currY + 4f, borderPaint)
        }
    }

    private fun renderFooter(canvas: Canvas, page: DocxPage) {
        val footer = page.footer
        val section = page.sectionProps
        val footerY = page.height - section.footerMargin - 18f
        val footerWidth = section.printableWidth
        val footerX = section.marginLeft

        // Subtle footer divider line
        borderPaint.color = 0xFFE2E8F0.toInt()
        borderPaint.strokeWidth = 0.5f
        canvas.drawLine(footerX, footerY - 6f, footerX + footerWidth, footerY - 6f, borderPaint)

        if (footer != null && footer.paragraphs.isNotEmpty()) {
            var currY = footerY
            for (p in footer.paragraphs) {
                renderParagraph(canvas, p, footerX, currY, footerWidth, page.pageIndex, page.totalPages)
                currY += 12f
            }
        } else {
            // Default footer page number: "Page X of Y"
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF718096.toInt()
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(
                "Page ${page.pageIndex + 1} of ${page.totalPages}",
                footerX + footerWidth,
                footerY + 8f,
                footerPaint
            )
        }
    }

    private fun renderParagraph(
        canvas: Canvas,
        paragraph: DocxParagraph,
        x: Float,
        y: Float,
        availableWidth: Float,
        pageIndex: Int = 0,
        totalPages: Int = 1
    ) {
        if (paragraph.runs.isEmpty()) return

        // Paragraph background fill if set
        paragraph.backgroundColor?.let { bgCol ->
            bgPaint.color = bgCol
            val pHeight = DocxPageLayouter.measureParagraphHeight(paragraph, availableWidth)
            canvas.drawRect(x - 2f, y - 2f, x + availableWidth + 2f, y + pHeight + 2f, bgPaint)
        }

        val builder = SpannableStringBuilder()
        var defaultSize = 11f

        for (run in paragraph.runs) {
            var runText = run.text
            // Substitute dynamic page fields
            runText = runText.replace("{PAGE}", "${pageIndex + 1}")
                .replace("{NUMPAGES}", "$totalPages")

            if (runText.isEmpty()) continue

            val start = builder.length
            builder.append(runText)
            val end = builder.length

            if (run.fontSizePt > defaultSize) defaultSize = run.fontSizePt

            // Color span
            builder.setSpan(ForegroundColorSpan(run.color), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Size span (in pixels: pt * 1.33f approx or direct float)
            builder.setSpan(AbsoluteSizeSpan(run.fontSizePt.toInt(), false), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

            // Bold & Italic styling
            val style = when {
                run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
                run.isBold -> Typeface.BOLD
                run.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                builder.setSpan(StyleSpan(style), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Underline
            if (run.isUnderline) {
                builder.setSpan(UnderlineSpan(), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Highlight background
            run.highlightColor?.let { hl ->
                builder.setSpan(BackgroundColorSpan(hl), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            // Font family
            val family = mapFontFamily(run.fontFamily)
            builder.setSpan(TypefaceSpan(family), start, end, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (builder.isEmpty()) return

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = defaultSize
        }

        val alignment = when (paragraph.alignment) {
            DocxAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            DocxAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val widthInt = availableWidth.toInt().coerceAtLeast(10)
        val staticLayout = StaticLayout.Builder.obtain(builder, 0, builder.length, textPaint, widthInt)
            .setAlignment(alignment)
            .setLineSpacing(0f, paragraph.lineSpacingMultiplier)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.translate(x, y)
        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun renderTable(canvas: Canvas, element: DocxRenderElement.TableElement) {
        val table = element.table
        val colWidths = element.columnWidths
        val rowHeights = element.rowHeights
        var currY = element.y

        for (rIdx in table.rows.indices) {
            val row = table.rows[rIdx]
            val rowHeight = if (rIdx < rowHeights.size) rowHeights[rIdx] else 20f
            var currX = element.x
            var colIdx = 0

            for (cell in row.cells) {
                var cellWidth = 0f
                for (span in 0 until cell.colSpan) {
                    if (colIdx + span < colWidths.size) {
                        cellWidth += colWidths[colIdx + span]
                    }
                }
                colIdx += cell.colSpan

                val cellRect = RectF(currX, currY, currX + cellWidth, currY + rowHeight)

                // 1. Draw Cell Background Shading
                cell.backgroundColor?.let { bgCol ->
                    bgPaint.color = bgCol
                    canvas.drawRect(cellRect, bgPaint)
                }

                // 2. Draw Cell Borders
                // Top border
                cell.topBorder?.let { b ->
                    borderPaint.color = b.color
                    borderPaint.strokeWidth = b.widthPt
                    canvas.drawLine(cellRect.left, cellRect.top, cellRect.right, cellRect.top, borderPaint)
                }
                // Bottom border
                cell.bottomBorder?.let { b ->
                    borderPaint.color = b.color
                    borderPaint.strokeWidth = b.widthPt
                    canvas.drawLine(cellRect.left, cellRect.bottom, cellRect.right, cellRect.bottom, borderPaint)
                }
                // Left border
                cell.leftBorder?.let { b ->
                    borderPaint.color = b.color
                    borderPaint.strokeWidth = b.widthPt
                    canvas.drawLine(cellRect.left, cellRect.top, cellRect.left, cellRect.bottom, borderPaint)
                }
                // Right border
                cell.rightBorder?.let { b ->
                    borderPaint.color = b.color
                    borderPaint.strokeWidth = b.widthPt
                    canvas.drawLine(cellRect.right, cellRect.top, cellRect.right, cellRect.bottom, borderPaint)
                }

                // 3. Draw Cell Paragraphs
                var pY = currY + cell.paddingTopPt
                val contentWidth = (cellWidth - cell.paddingLeftPt - cell.paddingRightPt).coerceAtLeast(10f)
                val pX = currX + cell.paddingLeftPt

                for (p in cell.paragraphs) {
                    renderParagraph(canvas, p, pX, pY, contentWidth)
                    pY += DocxPageLayouter.measureParagraphHeight(p, contentWidth) + p.spaceAfterPt
                }

                currX += cellWidth
            }

            currY += rowHeight
        }
    }

    private fun renderImage(canvas: Canvas, element: DocxRenderElement.ImageElement) {
        val bmp = element.bitmap
        val srcRect = Rect(0, 0, bmp.width, bmp.height)
        val dstRect = RectF(element.x, element.y, element.x + element.width, element.y + element.height)
        canvas.drawBitmap(bmp, srcRect, dstRect, imagePaint)
    }

    private fun mapFontFamily(family: String): String {
        return when (family.lowercase()) {
            "calibri", "arial", "helvetica", "sans-serif" -> "sans-serif"
            "times new roman", "times", "georgia", "serif" -> "serif"
            "courier new", "courier", "consolas", "monospace" -> "monospace"
            else -> "sans-serif"
        }
    }
}
