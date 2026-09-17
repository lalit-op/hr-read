package com.example.renderer.xlsx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.min

/**
 * High-performance vector canvas renderer for spreadsheet worksheets.
 * Renders a complete visual worksheet snapshot with headers, gridlines, cell styling, and values.
 */
object XlsxBitmapRenderer {

    fun renderSheetToBitmap(
        worksheet: Worksheet,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val safeW = targetWidth.coerceIn(320, 2400)
        val safeH = targetHeight.coerceIn(240, 3200)

        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Clean white spreadsheet canvas
        canvas.drawColor(Color.WHITE)

        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F3F4")
            style = Paint.Style.FILL
        }

        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5F6368")
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val gridLinePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1.5f
            style = Paint.Style.STROKE
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#202124")
            textSize = 22f
        }

        val cellBgPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        val rowHeaderWidth = 80f
        val colHeaderHeight = 44f

        // Draw top-left header junction
        canvas.drawRect(0f, 0f, rowHeaderWidth, colHeaderHeight, headerBgPaint)
        canvas.drawLine(0f, colHeaderHeight, safeW.toFloat(), colHeaderHeight, gridLinePaint)
        canvas.drawLine(rowHeaderWidth, 0f, rowHeaderWidth, safeH.toFloat(), gridLinePaint)

        // Compute column widths for render
        val colCount = min(worksheet.columnCount.coerceAtLeast(8), 26)
        val defaultColW = 160f
        val colPositions = FloatArray(colCount + 1)
        colPositions[0] = rowHeaderWidth
        for (c in 0 until colCount) {
            val customW = worksheet.columnWidths[c]
            val widthPx = if (customW != null && customW > 0f) {
                (customW * 14f).coerceIn(90f, 400f)
            } else {
                defaultColW
            }
            colPositions[c + 1] = colPositions[c] + widthPx
        }

        // Draw Column Headers (A, B, C...)
        for (c in 0 until colCount) {
            val left = colPositions[c]
            val right = colPositions[c + 1]
            if (left >= safeW) break

            canvas.drawRect(left, 0f, right, colHeaderHeight, headerBgPaint)
            canvas.drawLine(right, 0f, right, safeH.toFloat(), gridLinePaint)

            val colName = XlsxParser.indexToColName(c)
            val textX = (left + right) / 2f
            val textY = colHeaderHeight / 2f - (headerTextPaint.descent() + headerTextPaint.ascent()) / 2f
            canvas.drawText(colName, textX, textY, headerTextPaint)
        }

        // Compute row heights and draw Rows
        val rowCount = min(worksheet.rowCount.coerceAtLeast(20), 100)
        var currentY = colHeaderHeight
        val defaultRowH = 44f

        for (r in 0 until rowCount) {
            val customH = worksheet.rowHeights[r]
            val rowH = if (customH != null && customH > 0f) {
                (customH * 2f).coerceIn(36f, 160f)
            } else {
                defaultRowH
            }

            val top = currentY
            val bottom = top + rowH
            if (top >= safeH) break

            // Draw row header (1, 2, 3...)
            canvas.drawRect(0f, top, rowHeaderWidth, bottom, headerBgPaint)
            canvas.drawLine(0f, bottom, safeW.toFloat(), bottom, gridLinePaint)

            val rowLabel = (r + 1).toString()
            val textY = (top + bottom) / 2f - (headerTextPaint.descent() + headerTextPaint.ascent()) / 2f
            canvas.drawText(rowLabel, rowHeaderWidth / 2f, textY, headerTextPaint)

            // Draw cells in row
            val rowData = worksheet.getRow(r)
            for (c in 0 until colCount) {
                val left = colPositions[c]
                val right = colPositions[c + 1]
                if (left >= safeW) break

                val cell = rowData?.cells?.get(c)
                if (cell != null) {
                    // Cell background
                    if (cell.style.backgroundColor != null) {
                        cellBgPaint.color = cell.style.backgroundColor
                        canvas.drawRect(left + 1f, top + 1f, right - 1f, bottom - 1f, cellBgPaint)
                    }

                    // Cell text
                    val displayText = cell.formattedValue
                    if (displayText.isNotEmpty()) {
                        textPaint.color = cell.style.textColor
                        textPaint.typeface = Typeface.create(
                            Typeface.DEFAULT,
                            if (cell.style.isBold) Typeface.BOLD else Typeface.NORMAL
                        )

                        // Truncate text if it overflows cell width
                        val availableW = right - left - 16f
                        val clipped = textPaint.breakText(displayText, true, availableW, null)
                        val textToDraw = if (clipped < displayText.length) {
                            displayText.substring(0, clipped.coerceAtLeast(1)) + "…"
                        } else {
                            displayText
                        }

                        val cellTextY = (top + bottom) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                        val cellTextX = when (cell.style.horizontalAlignment) {
                            CellHorizontalAlignment.RIGHT -> right - 12f - textPaint.measureText(textToDraw)
                            CellHorizontalAlignment.CENTER -> (left + right) / 2f - textPaint.measureText(textToDraw) / 2f
                            else -> left + 12f
                        }

                        canvas.drawText(textToDraw, cellTextX, cellTextY, textPaint)
                    }
                }
            }

            currentY = bottom
        }

        return bitmap
    }

    /**
     * Renders a lightweight, low-resolution visual spreadsheet thumbnail preview.
     */
    fun renderThumbnail(
        worksheet: Worksheet,
        targetWidth: Int = 240,
        targetHeight: Int = 180
    ): Bitmap {
        val w = targetWidth.coerceIn(160, 480)
        val h = targetHeight.coerceIn(120, 360)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F1F3F4")
            style = Paint.Style.FILL
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5F6368")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val gridLinePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 1f
            style = Paint.Style.STROKE
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#202124")
            textSize = 11f
        }
        val cellBgPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        val rowHeaderWidth = 34f
        val colHeaderHeight = 24f

        // Top-left header junction
        canvas.drawRect(0f, 0f, rowHeaderWidth, colHeaderHeight, headerBgPaint)
        canvas.drawLine(0f, colHeaderHeight, w.toFloat(), colHeaderHeight, gridLinePaint)
        canvas.drawLine(rowHeaderWidth, 0f, rowHeaderWidth, h.toFloat(), gridLinePaint)

        val colCount = min(worksheet.columnCount.coerceAtLeast(6), 12)
        val defaultColW = 52f
        val colPositions = FloatArray(colCount + 1)
        colPositions[0] = rowHeaderWidth
        for (c in 0 until colCount) {
            colPositions[c + 1] = colPositions[c] + defaultColW
        }

        // Draw Column Headers (A, B, C...)
        for (c in 0 until colCount) {
            val left = colPositions[c]
            val right = colPositions[c + 1]
            if (left >= w) break
            canvas.drawRect(left, 0f, right, colHeaderHeight, headerBgPaint)
            canvas.drawLine(right, 0f, right, h.toFloat(), gridLinePaint)

            val colName = XlsxParser.indexToColName(c)
            val textX = (left + right) / 2f
            val textY = colHeaderHeight / 2f - (headerTextPaint.descent() + headerTextPaint.ascent()) / 2f
            canvas.drawText(colName, textX, textY, headerTextPaint)
        }

        // Draw Rows
        val rowCount = min(worksheet.rowCount.coerceAtLeast(8), 18)
        var currentY = colHeaderHeight
        val rowH = 20f

        for (r in 0 until rowCount) {
            val top = currentY
            val bottom = top + rowH
            if (top >= h) break

            // Draw row header (1, 2, 3...)
            canvas.drawRect(0f, top, rowHeaderWidth, bottom, headerBgPaint)
            canvas.drawLine(0f, bottom, w.toFloat(), bottom, gridLinePaint)

            val rowLabel = (r + 1).toString()
            val textY = (top + bottom) / 2f - (headerTextPaint.descent() + headerTextPaint.ascent()) / 2f
            canvas.drawText(rowLabel, rowHeaderWidth / 2f, textY, headerTextPaint)

            // Draw cells in row
            val rowData = worksheet.getRow(r)
            for (c in 0 until colCount) {
                val left = colPositions[c]
                val right = colPositions[c + 1]
                if (left >= w) break

                val cell = rowData?.cells?.get(c)
                if (cell != null) {
                    if (cell.style.backgroundColor != null) {
                        cellBgPaint.color = cell.style.backgroundColor
                        canvas.drawRect(left + 1f, top + 1f, right - 1f, bottom - 1f, cellBgPaint)
                    }
                    val displayText = cell.formattedValue
                    if (displayText.isNotEmpty()) {
                        textPaint.color = cell.style.textColor
                        canvas.save()
                        canvas.clipRect(left + 2f, top + 1f, right - 2f, bottom - 1f)
                        val baseline = (top + bottom) / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                        canvas.drawText(displayText, left + 3f, baseline, textPaint)
                        canvas.restore()
                    }
                }
            }
            currentY = bottom
        }

        // Border around spreadsheet thumbnail
        canvas.drawRect(0f, 0f, w.toFloat() - 1f, h.toFloat() - 1f, gridLinePaint)

        return bitmap
    }
}
