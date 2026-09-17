package com.example.renderer.pptx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.graphics.withSave

/**
 * High-fidelity native Canvas renderer for PowerPoint slides.
 * Renders slides directly to ARGB_8888 Bitmaps with crisp typography,
 * shape geometries, gradients, tables, and embedded media assets.
 */
object PptxSlideRenderer {

    fun render(
        slide: PptxSlide,
        presentationWidthPt: Float,
        presentationHeightPt: Float,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val sx = targetWidth.toFloat() / presentationWidthPt.coerceAtLeast(100f)
        val sy = targetHeight.toFloat() / presentationHeightPt.coerceAtLeast(100f)

        // 1. Draw Slide Background
        drawBackground(canvas, slide.background, targetWidth.toFloat(), targetHeight.toFloat())

        // 2. Draw Slide Elements in correct document layering
        slide.elements.forEach { element ->
            drawElement(canvas, element, sx, sy)
        }

        return bitmap
    }

    private fun drawBackground(
        canvas: Canvas,
        background: PptxBackground,
        width: Float,
        height: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (background) {
            is PptxBackground.Solid -> {
                paint.color = background.color
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
            }
            is PptxBackground.Gradient -> {
                paint.shader = LinearGradient(
                    0f, 0f, 0f, height,
                    background.startColor,
                    background.endColor,
                    Shader.TileMode.CLAMP
                )
                paint.style = Paint.Style.FILL
                canvas.drawRect(0f, 0f, width, height, paint)
            }
            is PptxBackground.Image -> {
                try {
                    val bgBmp = BitmapFactory.decodeByteArray(background.bytes, 0, background.bytes.size)
                    if (bgBmp != null) {
                        val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        val dst = RectF(0f, 0f, width, height)
                        canvas.drawBitmap(bgBmp, null, dst, filterPaint)
                    } else {
                        drawDefaultBackground(canvas, width, height)
                    }
                } catch (_: Exception) {
                    drawDefaultBackground(canvas, width, height)
                }
            }
            PptxBackground.Default -> {
                drawDefaultBackground(canvas, width, height)
            }
        }
    }

    private fun drawDefaultBackground(canvas: Canvas, width: Float, height: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawElement(canvas: Canvas, element: PptxElement, sx: Float, sy: Float) {
        when (element) {
            is PptxElement.Shape -> drawShape(canvas, element, sx, sy)
            is PptxElement.Picture -> drawPicture(canvas, element, sx, sy)
            is PptxElement.Table -> drawTable(canvas, element, sx, sy)
            is PptxElement.Group -> {
                element.children.forEach { child ->
                    drawElement(canvas, child, sx, sy)
                }
            }
        }
    }

    private fun drawShape(canvas: Canvas, shape: PptxElement.Shape, sx: Float, sy: Float) {
        val rect = RectF(
            shape.bounds.left * sx,
            shape.bounds.top * sy,
            shape.bounds.right * sx,
            shape.bounds.bottom * sy
        )

        canvas.withSave {
            if (shape.rotationDeg != 0f) {
                canvas.rotate(shape.rotationDeg, rect.centerX(), rect.centerY())
            }

            // Draw Fill
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            when (val fill = shape.fill) {
                is PptxFill.Solid -> {
                    fillPaint.color = fill.color
                    drawShapeGeometry(canvas, shape.shapeType, rect, shape.cornerRadiusPt * sx, fillPaint)
                }
                is PptxFill.Gradient -> {
                    fillPaint.shader = LinearGradient(
                        rect.left, rect.top, rect.right, rect.bottom,
                        fill.startColor, fill.endColor,
                        Shader.TileMode.CLAMP
                    )
                    drawShapeGeometry(canvas, shape.shapeType, rect, shape.cornerRadiusPt * sx, fillPaint)
                }
                PptxFill.None -> {}
            }

            // Draw Stroke
            shape.stroke?.let { stroke ->
                val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = stroke.color
                    strokeWidth = (stroke.widthPt * sx).coerceAtLeast(1f)
                }
                drawShapeGeometry(canvas, shape.shapeType, rect, shape.cornerRadiusPt * sx, strokePaint)
            }

            // Draw Text Body
            shape.textBody?.let { textBody ->
                drawTextBody(canvas, textBody, rect, sx, sy)
            }
        }
    }

    private fun drawShapeGeometry(
        canvas: Canvas,
        shapeType: PptxShapeType,
        rect: RectF,
        radius: Float,
        paint: Paint
    ) {
        when (shapeType) {
            PptxShapeType.RECT, PptxShapeType.BANNER -> {
                canvas.drawRect(rect, paint)
            }
            PptxShapeType.ROUND_RECT, PptxShapeType.CALLOUT -> {
                val r = if (radius > 0f) radius else 12f
                canvas.drawRoundRect(rect, r, r, paint)
            }
            PptxShapeType.ELLIPSE -> {
                canvas.drawOval(rect, paint)
            }
            PptxShapeType.LINE -> {
                canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), paint)
            }
            PptxShapeType.CHEVRON -> {
                val path = Path().apply {
                    val w = rect.width()
                    val arrow = w * 0.25f
                    moveTo(rect.left, rect.top)
                    lineTo(rect.right - arrow, rect.top)
                    lineTo(rect.right, rect.centerY())
                    lineTo(rect.right - arrow, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    lineTo(rect.left + arrow, rect.centerY())
                    close()
                }
                canvas.drawPath(path, paint)
            }
            PptxShapeType.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(rect.centerX(), rect.top)
                    lineTo(rect.right, rect.bottom)
                    lineTo(rect.left, rect.bottom)
                    close()
                }
                canvas.drawPath(path, paint)
            }
            PptxShapeType.STAR -> {
                canvas.drawRoundRect(rect, 8f, 8f, paint)
            }
            PptxShapeType.UNKNOWN -> {
                canvas.drawRect(rect, paint)
            }
        }
    }

    private fun drawPicture(canvas: Canvas, picture: PptxElement.Picture, sx: Float, sy: Float) {
        val rect = RectF(
            picture.bounds.left * sx,
            picture.bounds.top * sy,
            picture.bounds.right * sx,
            picture.bounds.bottom * sy
        )

        try {
            val bmp = BitmapFactory.decodeByteArray(picture.imageBytes, 0, picture.imageBytes.size)
            if (bmp != null) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, null, rect, paint)
                bmp.recycle()
            }
        } catch (_: Exception) {}

        picture.stroke?.let { stroke ->
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = stroke.color
                strokeWidth = (stroke.widthPt * sx).coerceAtLeast(1f)
            }
            canvas.drawRect(rect, strokePaint)
        }
    }

    private fun drawTable(canvas: Canvas, table: PptxElement.Table, sx: Float, sy: Float) {
        var currentY = table.bounds.top * sy
        val startX = table.bounds.left * sx

        // Compute column widths in pixels
        val colWidthsPx = if (table.columnWidthsPt.isNotEmpty()) {
            table.columnWidthsPt.map { it * sx }
        } else {
            val count = table.rows.firstOrNull()?.cells?.size ?: 1
            val colW = (table.bounds.width() * sx) / count
            List(count) { colW }
        }

        table.rows.forEachIndexed { rowIndex, row ->
            val rowHeightPx = (row.heightPt * sy).coerceAtLeast(24f * sy)
            var currentX = startX

            row.cells.forEachIndexed { colIndex, cell ->
                val colWidthPx = colWidthsPx.getOrElse(colIndex) { 100f * sx }
                val cellRect = RectF(currentX, currentY, currentX + colWidthPx, currentY + rowHeightPx)

                // Draw cell background
                when (val fill = cell.fill) {
                    is PptxFill.Solid -> {
                        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = fill.color
                            style = Paint.Style.FILL
                        }
                        canvas.drawRect(cellRect, p)
                    }
                    else -> {}
                }

                // Draw borders
                val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFCBD5E0.toInt()
                    strokeWidth = 1f
                    style = Paint.Style.STROKE
                }
                canvas.drawRect(cellRect, borderPaint)

                // Draw cell text
                drawTextBody(canvas, cell.textBody, cellRect, sx, sy)

                currentX += colWidthPx
            }
            currentY += rowHeightPx
        }
    }

    private data class FormattedWord(
        val word: String,
        val run: PptxRun,
        val width: Float
    )

    private data class FormattedLine(
        val words: List<FormattedWord>,
        val lineWidth: Float,
        val maxAscent: Float,
        val maxDescent: Float,
        val alignment: PptxTextAlign,
        val bulletChar: String?,
        val bulletColor: Int?,
        val indentPx: Float
    ) {
        val lineHeight: Float
            get() = (maxDescent - maxAscent).coerceAtLeast(16f)
    }

    private fun drawTextBody(
        canvas: Canvas,
        textBody: PptxTextBody,
        bounds: RectF,
        sx: Float,
        sy: Float
    ) {
        val padLeft = textBody.paddingLeftPt * sx
        val padTop = textBody.paddingTopPt * sy
        val padRight = textBody.paddingRightPt * sx
        val padBottom = textBody.paddingBottomPt * sy

        val contentWidth = (bounds.width() - padLeft - padRight).coerceAtLeast(20f)
        val contentHeight = (bounds.height() - padTop - padBottom).coerceAtLeast(20f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val lines = mutableListOf<FormattedLine>()

        // Measure and layout paragraphs into lines
        textBody.paragraphs.forEach { paragraph ->
            val indentPx = paragraph.indentPt * sx
            val availableWidth = (contentWidth - indentPx).coerceAtLeast(20f)

            var currentLineWords = mutableListOf<FormattedWord>()
            var currentLineWidth = 0f
            var maxAscent = -12f * sy
            var maxDescent = 4f * sy
            var isFirstLineInParagraph = true

            val rawRuns = paragraph.runs
            if (rawRuns.isEmpty() && paragraph.fullText.isBlank()) {
                // Empty spacer paragraph
                lines.add(
                    FormattedLine(
                        words = emptyList(),
                        lineWidth = 0f,
                        maxAscent = -8f * sy,
                        maxDescent = 4f * sy,
                        alignment = paragraph.alignment,
                        bulletChar = null,
                        bulletColor = null,
                        indentPx = indentPx
                    )
                )
                return@forEach
            }

            rawRuns.forEach { run ->
                configurePaint(textPaint, run, sy)
                val fontMetrics = textPaint.fontMetrics
                val ascent = fontMetrics.ascent
                val descent = fontMetrics.descent

                val text = run.text
                if (text == "\n") {
                    lines.add(
                        FormattedLine(
                            words = currentLineWords,
                            lineWidth = currentLineWidth,
                            maxAscent = maxAscent,
                            maxDescent = maxDescent,
                            alignment = paragraph.alignment,
                            bulletChar = if (isFirstLineInParagraph) paragraph.bulletChar else null,
                            bulletColor = paragraph.bulletColor,
                            indentPx = indentPx
                        )
                    )
                    currentLineWords = mutableListOf()
                    currentLineWidth = 0f
                    maxAscent = -12f * sy
                    maxDescent = 4f * sy
                    isFirstLineInParagraph = false
                    return@forEach
                }

                val tokens = text.split(" ")
                tokens.forEachIndexed { tokenIndex, token ->
                    val wordText = if (tokenIndex < tokens.size - 1) "$token " else token
                    if (wordText.isEmpty()) return@forEachIndexed

                    val wordWidth = textPaint.measureText(wordText)

                    if (currentLineWidth + wordWidth > availableWidth && currentLineWords.isNotEmpty()) {
                        lines.add(
                            FormattedLine(
                                words = currentLineWords,
                                lineWidth = currentLineWidth,
                                maxAscent = maxAscent,
                                maxDescent = maxDescent,
                                alignment = paragraph.alignment,
                                bulletChar = if (isFirstLineInParagraph) paragraph.bulletChar else null,
                                bulletColor = paragraph.bulletColor,
                                indentPx = indentPx
                            )
                        )
                        currentLineWords = mutableListOf()
                        currentLineWidth = 0f
                        maxAscent = ascent
                        maxDescent = descent
                        isFirstLineInParagraph = false
                    }

                    currentLineWords.add(FormattedWord(wordText, run, wordWidth))
                    currentLineWidth += wordWidth
                    if (ascent < maxAscent) maxAscent = ascent
                    if (descent > maxDescent) maxDescent = descent
                }
            }

            if (currentLineWords.isNotEmpty()) {
                lines.add(
                    FormattedLine(
                        words = currentLineWords,
                        lineWidth = currentLineWidth,
                        maxAscent = maxAscent,
                        maxDescent = maxDescent,
                        alignment = paragraph.alignment,
                        bulletChar = if (isFirstLineInParagraph) paragraph.bulletChar else null,
                        bulletColor = paragraph.bulletColor,
                        indentPx = indentPx
                    )
                )
            }
        }

        if (lines.isEmpty()) return

        // Compute total text height
        val totalTextHeight = lines.sumOf { (it.lineHeight * 1.1).toDouble() }.toFloat()

        // Vertical Alignment calculation
        var startY = when (textBody.verticalAlign) {
            PptxVerticalAlign.TOP -> bounds.top + padTop
            PptxVerticalAlign.CENTER -> {
                val centered = bounds.top + padTop + ((contentHeight - totalTextHeight) / 2f).coerceAtLeast(0f)
                centered
            }
            PptxVerticalAlign.BOTTOM -> {
                bounds.bottom - padBottom - totalTextHeight
            }
        }

        // Draw each line
        lines.forEach { line ->
            val baseline = startY - line.maxAscent

            var startX = bounds.left + padLeft + line.indentPx
            if (line.bulletChar != null) {
                // Draw bullet
                val bulletPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = line.bulletColor ?: (line.words.firstOrNull()?.run?.color ?: 0xFF1E293B.toInt())
                    textSize = (-line.maxAscent * 0.9f).coerceAtLeast(12f)
                }
                canvas.drawText(line.bulletChar, startX, baseline, bulletPaint)
                startX += bulletPaint.measureText("${line.bulletChar}  ")
            }

            // Horizontal alignment
            var wordX = when (line.alignment) {
                PptxTextAlign.CENTER -> {
                    val remaining = contentWidth - line.lineWidth
                    bounds.left + padLeft + (remaining / 2f).coerceAtLeast(0f)
                }
                PptxTextAlign.RIGHT -> {
                    bounds.right - padRight - line.lineWidth
                }
                else -> startX
            }

            line.words.forEach { formattedWord ->
                configurePaint(textPaint, formattedWord.run, sy)
                canvas.drawText(formattedWord.word, wordX, baseline, textPaint)
                wordX += formattedWord.width
            }

            startY += line.lineHeight * 1.15f
        }
    }

    private fun configurePaint(paint: Paint, run: PptxRun, sy: Float) {
        paint.reset()
        paint.isAntiAlias = true
        paint.color = run.color
        paint.textSize = (run.fontSizePt * sy * 1.25f).coerceAtLeast(10f)
        paint.isFakeBoldText = run.isBold
        paint.textSkewX = if (run.isItalic) -0.22f else 0f
        paint.isUnderlineText = run.isUnderline

        val style = when {
            run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
            run.isBold -> Typeface.BOLD
            run.isItalic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }

        paint.typeface = when {
            run.fontFamily.contains("serif", ignoreCase = true) -> Typeface.create(Typeface.SERIF, style)
            run.fontFamily.contains("mono", ignoreCase = true) -> Typeface.create(Typeface.MONOSPACE, style)
            else -> Typeface.create(Typeface.SANS_SERIF, style)
        }
    }
}
