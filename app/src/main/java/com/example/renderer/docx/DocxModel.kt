package com.example.renderer.docx

import android.graphics.Color

/**
 * Representation of page layout section properties (page size, margins, orientation).
 * Dimensions are stored in typographical points (1/72 inch).
 */
data class DocxSectionProps(
    val pageWidth: Float = 595.28f,  // Default A4 (210mm x 297mm)
    val pageHeight: Float = 841.89f,
    val marginTop: Float = 72f,      // 1 inch = 72 pt = 1440 twips
    val marginBottom: Float = 72f,
    val marginLeft: Float = 72f,
    val marginRight: Float = 72f,
    val headerMargin: Float = 36f,
    val footerMargin: Float = 36f,
    val isLandscape: Boolean = false
) {
    val printableWidth: Float
        get() = (pageWidth - marginLeft - marginRight).coerceAtLeast(100f)

    val printableHeight: Float
        get() = (pageHeight - marginTop - marginBottom).coerceAtLeast(100f)
}

enum class DocxAlignment {
    LEFT, CENTER, RIGHT, JUSTIFY
}

data class DocxBorder(
    val widthPt: Float = 0.75f,
    val color: Int = 0xFFCBD5E0.toInt(),
    val style: String = "single"
)

data class DocxDrawing(
    val relationshipId: String,
    val widthPt: Float,
    val heightPt: Float,
    val description: String = ""
)

data class DocxRun(
    val text: String = "",
    val fontFamily: String = "sans-serif",
    val fontSizePt: Float = 11f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: Int = 0xFF1A202C.toInt(),
    val highlightColor: Int? = null,
    val drawing: DocxDrawing? = null
)

data class DocxParagraph(
    val runs: List<DocxRun> = emptyList(),
    val alignment: DocxAlignment = DocxAlignment.LEFT,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 4f,
    val lineSpacingMultiplier: Float = 1.15f,
    val backgroundColor: Int? = null,
    val indentLeftPt: Float = 0f,
    val indentRightPt: Float = 0f,
    val isPageBreak: Boolean = false,
    val hasBottomBorder: Boolean = false,
    val bottomBorderColor: Int = 0xFFCBD5E0.toInt(),
    val bottomBorderWidthPt: Float = 1f
) {
    val fullText: String
        get() = runs.joinToString("") { it.text }
}

data class DocxTableCell(
    val paragraphs: List<DocxParagraph> = emptyList(),
    val widthPt: Float = 0f,
    val colSpan: Int = 1,
    val backgroundColor: Int? = null,
    val topBorder: DocxBorder? = null,
    val bottomBorder: DocxBorder? = null,
    val leftBorder: DocxBorder? = null,
    val rightBorder: DocxBorder? = null,
    val paddingTopPt: Float = 4f,
    val paddingBottomPt: Float = 4f,
    val paddingLeftPt: Float = 6f,
    val paddingRightPt: Float = 6f
)

data class DocxTableRow(
    val cells: List<DocxTableCell> = emptyList(),
    val minHeightPt: Float = 0f,
    val isHeader: Boolean = false
)

data class DocxTable(
    val rows: List<DocxTableRow> = emptyList(),
    val columnWidthsPt: List<Float> = emptyList(),
    val alignment: DocxAlignment = DocxAlignment.LEFT,
    val spaceBeforePt: Float = 6f,
    val spaceAfterPt: Float = 8f
)

sealed interface DocxBlock {
    data class ParagraphBlock(val paragraph: DocxParagraph) : DocxBlock
    data class TableBlock(val table: DocxTable) : DocxBlock
    object PageBreakBlock : DocxBlock
}

data class DocxHeaderFooter(
    val paragraphs: List<DocxParagraph> = emptyList()
)

data class DocxDocument(
    val title: String,
    val sectionProps: DocxSectionProps,
    val header: DocxHeaderFooter? = null,
    val footer: DocxHeaderFooter? = null,
    val blocks: List<DocxBlock> = emptyList(),
    val media: Map<String, ByteArray> = emptyMap()
)
