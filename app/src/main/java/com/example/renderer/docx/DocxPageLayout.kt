package com.example.renderer.docx

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import kotlin.math.max

sealed interface DocxRenderElement {
    val x: Float
    val y: Float
    val width: Float
    val height: Float

    data class ParagraphElement(
        val paragraph: DocxParagraph,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float
    ) : DocxRenderElement

    data class TableElement(
        val table: DocxTable,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val columnWidths: List<Float>,
        val rowHeights: List<Float>
    ) : DocxRenderElement

    data class ImageElement(
        val bitmap: Bitmap,
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val alignment: DocxAlignment = DocxAlignment.LEFT
    ) : DocxRenderElement

    data class DividerElement(
        override val x: Float,
        override val y: Float,
        override val width: Float,
        override val height: Float,
        val color: Int,
        val strokeWidthPt: Float
    ) : DocxRenderElement
}

data class DocxPage(
    val pageIndex: Int,
    val width: Float,
    val height: Float,
    val sectionProps: DocxSectionProps,
    val elements: List<DocxRenderElement>,
    val header: DocxHeaderFooter?,
    val footer: DocxHeaderFooter?,
    val totalPages: Int = 1
)

object DocxPageLayouter {

    private val textMeasurePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    fun layout(document: DocxDocument, firstPageOnly: Boolean = false): List<DocxPage> {
        val section = document.sectionProps
        val pageWidth = section.pageWidth
        val pageHeight = section.pageHeight
        val marginLeft = section.marginLeft
        val marginRight = section.marginRight
        val marginTop = section.marginTop
        val marginBottom = section.marginBottom
        val printableWidth = section.printableWidth
        val printableHeight = section.printableHeight

        // Decode media bitmaps
        val decodedBitmaps = mutableMapOf<String, Bitmap>()
        for ((rId, bytes) in document.media) {
            try {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    decodedBitmaps[rId] = bmp
                }
            } catch (_: Exception) {}
        }

        val pages = mutableListOf<MutableList<DocxRenderElement>>()
        var currentElements = mutableListOf<DocxRenderElement>()
        var currentY = marginTop

        fun startNewPage() {
            pages.add(currentElements)
            currentElements = mutableListOf()
            currentY = marginTop
        }

        for (block in document.blocks) {
            if (firstPageOnly && pages.isNotEmpty()) {
                break
            }
            when (block) {
                is DocxBlock.PageBreakBlock -> {
                    if (currentElements.isNotEmpty()) {
                        startNewPage()
                    }
                }

                is DocxBlock.ParagraphBlock -> {
                    val p = block.paragraph

                    // Check if paragraph contains a standalone image/drawing
                    val drawingRun = p.runs.firstOrNull { it.drawing != null }
                    if (drawingRun != null && p.fullText.isBlank()) {
                        val drawing = drawingRun.drawing!!
                        val bitmap = decodedBitmaps[drawing.relationshipId]
                        if (bitmap != null) {
                            var imgWidth = drawing.widthPt.coerceAtMost(printableWidth)
                            var imgHeight = drawing.heightPt
                            if (imgWidth <= 0 || imgHeight <= 0) {
                                imgWidth = 140f
                                imgHeight = (140f * bitmap.height / bitmap.width).coerceAtLeast(40f)
                            }
                            if (imgWidth > printableWidth) {
                                val ratio = printableWidth / imgWidth
                                imgWidth = printableWidth
                                imgHeight *= ratio
                            }

                            val neededHeight = p.spaceBeforePt + imgHeight + p.spaceAfterPt
                            if (currentY + neededHeight > pageHeight - marginBottom && currentElements.isNotEmpty()) {
                                startNewPage()
                            }

                            currentY += p.spaceBeforePt
                            val imgX = when (p.alignment) {
                                DocxAlignment.CENTER -> marginLeft + (printableWidth - imgWidth) / 2f
                                DocxAlignment.RIGHT -> marginLeft + printableWidth - imgWidth
                                else -> marginLeft + p.indentLeftPt
                            }

                            currentElements.add(
                                DocxRenderElement.ImageElement(
                                    bitmap = bitmap,
                                    x = imgX,
                                    y = currentY,
                                    width = imgWidth,
                                    height = imgHeight,
                                    alignment = p.alignment
                                )
                            )
                            currentY += imgHeight + p.spaceAfterPt
                            continue
                        }
                    }

                    // Standard text paragraph measurement
                    val pHeight = measureParagraphHeight(p, printableWidth)
                    val totalPHeight = p.spaceBeforePt + pHeight + p.spaceAfterPt

                    if (currentY + totalPHeight > pageHeight - marginBottom && currentElements.isNotEmpty()) {
                        startNewPage()
                    }

                    currentY += p.spaceBeforePt
                    val pWidth = (printableWidth - p.indentLeftPt - p.indentRightPt).coerceAtLeast(50f)
                    val pX = marginLeft + p.indentLeftPt

                    currentElements.add(
                        DocxRenderElement.ParagraphElement(
                            paragraph = p,
                            x = pX,
                            y = currentY,
                            width = pWidth,
                            height = pHeight
                        )
                    )
                    currentY += pHeight

                    if (p.hasBottomBorder) {
                        currentElements.add(
                            DocxRenderElement.DividerElement(
                                x = pX,
                                y = currentY + 2f,
                                width = pWidth,
                                height = p.bottomBorderWidthPt,
                                color = p.bottomBorderColor,
                                strokeWidthPt = p.bottomBorderWidthPt
                            )
                        )
                        currentY += p.bottomBorderWidthPt + 3f
                    }

                    currentY += p.spaceAfterPt
                }

                is DocxBlock.TableBlock -> {
                    val table = block.table
                    val (colWidths, rowHeights, tableHeight) = measureTable(table, printableWidth)
                    val totalTableHeight = table.spaceBeforePt + tableHeight + table.spaceAfterPt

                    if (currentY + totalTableHeight > pageHeight - marginBottom && currentElements.isNotEmpty()) {
                        // If table doesn't fit in remaining space, start new page
                        startNewPage()
                    }

                    currentY += table.spaceBeforePt
                    val tableX = when (table.alignment) {
                        DocxAlignment.CENTER -> marginLeft + (printableWidth - colWidths.sum()) / 2f
                        DocxAlignment.RIGHT -> marginLeft + printableWidth - colWidths.sum()
                        else -> marginLeft
                    }

                    currentElements.add(
                        DocxRenderElement.TableElement(
                            table = table,
                            x = tableX,
                            y = currentY,
                            width = colWidths.sum(),
                            height = tableHeight,
                            columnWidths = colWidths,
                            rowHeights = rowHeights
                        )
                    )
                    currentY += tableHeight + table.spaceAfterPt
                }
            }
        }

        if (currentElements.isNotEmpty() || pages.isEmpty()) {
            pages.add(currentElements)
        }

        val totalPages = pages.size
        return pages.mapIndexed { index, elements ->
            DocxPage(
                pageIndex = index,
                width = pageWidth,
                height = pageHeight,
                sectionProps = section,
                elements = elements,
                header = document.header,
                footer = document.footer,
                totalPages = totalPages
            )
        }
    }

    fun measureParagraphHeight(p: DocxParagraph, availableWidth: Float): Float {
        if (p.runs.isEmpty()) {
            return 12f
        }

        var maxFontSize = 11f
        for (run in p.runs) {
            if (run.fontSizePt > maxFontSize) maxFontSize = run.fontSizePt
        }

        val fullText = p.fullText
        if (fullText.isEmpty()) {
            return maxFontSize * p.lineSpacingMultiplier
        }

        textMeasurePaint.textSize = maxFontSize
        val textWidth = textMeasurePaint.measureText(fullText)
        val lineCount = max(1, Math.ceil((textWidth / availableWidth).toDouble()).toInt())
        val lineHeight = maxFontSize * 1.35f * p.lineSpacingMultiplier

        return lineCount * lineHeight
    }

    private fun measureTable(table: DocxTable, printableWidth: Float): Triple<List<Float>, List<Float>, Float> {
        val colCount = table.rows.maxOfOrNull { it.cells.sumOf { cell -> cell.colSpan } } ?: 1

        // Determine column widths
        val colWidths: List<Float> = if (table.columnWidthsPt.size >= colCount) {
            val totalDefined = table.columnWidthsPt.take(colCount).sum()
            if (totalDefined > 0f) {
                val ratio = (printableWidth / totalDefined).coerceAtMost(1.0f)
                table.columnWidthsPt.take(colCount).map { it * ratio }
            } else {
                List(colCount) { printableWidth / colCount }
            }
        } else {
            List(colCount) { printableWidth / colCount }
        }

        val rowHeights = mutableListOf<Float>()
        var totalHeight = 0f

        for (row in table.rows) {
            var maxCellHeight = row.minHeightPt.coerceAtLeast(18f)

            var colIdx = 0
            for (cell in row.cells) {
                var cellWidth = 0f
                for (span in 0 until cell.colSpan) {
                    if (colIdx + span < colWidths.size) {
                        cellWidth += colWidths[colIdx + span]
                    }
                }
                colIdx += cell.colSpan

                val contentWidth = (cellWidth - cell.paddingLeftPt - cell.paddingRightPt).coerceAtLeast(20f)
                var cellContentHeight = cell.paddingTopPt + cell.paddingBottomPt

                for (p in cell.paragraphs) {
                    cellContentHeight += measureParagraphHeight(p, contentWidth) + p.spaceAfterPt
                }

                if (cellContentHeight > maxCellHeight) {
                    maxCellHeight = cellContentHeight
                }
            }

            rowHeights.add(maxCellHeight)
            totalHeight += maxCellHeight
        }

        return Triple(colWidths, rowHeights, totalHeight)
    }
}
