package com.example.renderer.pptx

import android.graphics.Color
import android.graphics.RectF

/**
 * Representation of a PowerPoint presentation.
 * Dimensions are stored in typographical points (1/72 inch).
 * Standard 16:9 widescreen is 960 x 540 pt (13.33 x 7.5 in).
 * Standard 4:3 is 720 x 540 pt (10.0 x 7.5 in).
 */
data class PptxPresentation(
    val title: String,
    val slideWidthPt: Float = 960f,
    val slideHeightPt: Float = 540f,
    val slides: List<PptxSlide> = emptyList(),
    val theme: PptxTheme = PptxTheme(),
    val media: Map<String, ByteArray> = emptyMap()
) {
    val aspectRatio: Float
        get() = if (slideHeightPt > 0f) slideWidthPt / slideHeightPt else 16f / 9f

    val isLandscape: Boolean
        get() = slideWidthPt >= slideHeightPt
}

/**
 * Theme color scheme mapping Microsoft Office theme color tokens
 * (dk1, lt1, dk2, lt2, accent1..accent6, hlink, folHlink) to ARGB integer colors.
 */
data class PptxTheme(
    val colors: Map<String, Int> = defaultThemeColors()
) {
    fun resolveColor(schemeClr: String?, defaultColor: Int = Color.BLACK): Int {
        if (schemeClr == null) return defaultColor
        return colors[schemeClr.lowercase()] ?: defaultColor
    }

    companion object {
        fun defaultThemeColors(): Map<String, Int> = mapOf(
            "dk1" to 0xFF1E293B.toInt(),       // Dark slate
            "lt1" to 0xFFFFFFFF.toInt(),       // Pure white
            "dk2" to 0xFF334155.toInt(),       // Medium slate
            "lt2" to 0xFFF8FAFC.toInt(),       // Light off-white
            "accent1" to 0xFF2563EB.toInt(),   // Royal blue
            "accent2" to 0xFF0D9488.toInt(),   // Teal
            "accent3" to 0xFFF59E0B.toInt(),   // Amber
            "accent4" to 0xFFE11D48.toInt(),   // Rose / Crimson
            "accent5" to 0xFF7C3AED.toInt(),   // Violet
            "accent6" to 0xFF059669.toInt(),   // Emerald green
            "hlink" to 0xFF2563EB.toInt(),
            "folhlink" to 0xFF7C3AED.toInt()
        )
    }
}

/**
 * Slide background representation.
 */
sealed class PptxBackground {
    data class Solid(val color: Int) : PptxBackground()
    data class Gradient(val startColor: Int, val endColor: Int, val angleDeg: Float = 90f) : PptxBackground()
    data class Image(val bytes: ByteArray) : PptxBackground()
    data object Default : PptxBackground()
}

/**
 * Represents a single presentation slide.
 */
data class PptxSlide(
    val index: Int,
    val title: String = "",
    val background: PptxBackground = PptxBackground.Default,
    val elements: List<PptxElement> = emptyList()
) {
    val fullText: String by lazy {
        buildString {
            if (title.isNotBlank()) append(title).append("\n")
            elements.forEach { elem ->
                when (elem) {
                    is PptxElement.Shape -> elem.textBody?.fullText?.let { append(it).append("\n") }
                    is PptxElement.Table -> {
                        elem.rows.forEach { row ->
                            row.cells.forEach { cell ->
                                append(cell.textBody.fullText).append(" ")
                            }
                            append("\n")
                        }
                    }
                    is PptxElement.Group -> {
                        elem.children.forEach { child ->
                            if (child is PptxElement.Shape) child.textBody?.fullText?.let { append(it).append("\n") }
                        }
                    }
                    else -> {}
                }
            }
        }.trim()
    }
}

enum class PptxShapeType {
    RECT,
    ROUND_RECT,
    ELLIPSE,
    LINE,
    BANNER,
    CALLOUT,
    CHEVRON,
    STAR,
    TRIANGLE,
    UNKNOWN
}

sealed class PptxFill {
    data class Solid(val color: Int) : PptxFill()
    data class Gradient(val startColor: Int, val endColor: Int, val angleDeg: Float = 0f) : PptxFill()
    data object None : PptxFill()
}

data class PptxStroke(
    val color: Int = 0xFFCBD5E0.toInt(),
    val widthPt: Float = 1f,
    val isDashed: Boolean = false
)

enum class PptxTextAlign {
    LEFT, CENTER, RIGHT, JUSTIFY
}

enum class PptxVerticalAlign {
    TOP, CENTER, BOTTOM
}

data class PptxRun(
    val text: String,
    val fontFamily: String = "sans-serif",
    val fontSizePt: Float = 14f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val color: Int = 0xFF1E293B.toInt(),
    val highlightColor: Int? = null
)

data class PptxParagraph(
    val runs: List<PptxRun> = emptyList(),
    val alignment: PptxTextAlign = PptxTextAlign.LEFT,
    val bulletChar: String? = null,
    val bulletColor: Int? = null,
    val indentPt: Float = 0f,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 2f,
    val lineSpacingMultiplier: Float = 1.15f
) {
    val fullText: String
        get() = runs.joinToString("") { it.text }
}

data class PptxTextBody(
    val paragraphs: List<PptxParagraph> = emptyList(),
    val verticalAlign: PptxVerticalAlign = PptxVerticalAlign.CENTER,
    val paddingLeftPt: Float = 6f,
    val paddingTopPt: Float = 6f,
    val paddingRightPt: Float = 6f,
    val paddingBottomPt: Float = 6f
) {
    val fullText: String
        get() = paragraphs.joinToString("\n") { it.fullText }
}

data class PptxCellBorders(
    val top: PptxStroke? = null,
    val bottom: PptxStroke? = null,
    val left: PptxStroke? = null,
    val right: PptxStroke? = null
)

data class PptxTableCell(
    val textBody: PptxTextBody = PptxTextBody(),
    val fill: PptxFill = PptxFill.None,
    val borders: PptxCellBorders = PptxCellBorders(),
    val paddingLeftPt: Float = 4f,
    val paddingRightPt: Float = 4f,
    val paddingTopPt: Float = 4f,
    val paddingBottomPt: Float = 4f
)

data class PptxTableRow(
    val cells: List<PptxTableCell> = emptyList(),
    val heightPt: Float = 24f,
    val isHeader: Boolean = false
)

sealed class PptxElement {
    abstract val bounds: RectF

    data class Shape(
        override val bounds: RectF,
        val shapeType: PptxShapeType = PptxShapeType.RECT,
        val fill: PptxFill = PptxFill.None,
        val stroke: PptxStroke? = null,
        val cornerRadiusPt: Float = 0f,
        val textBody: PptxTextBody? = null,
        val rotationDeg: Float = 0f
    ) : PptxElement()

    data class Picture(
        override val bounds: RectF,
        val imageBytes: ByteArray,
        val description: String = "",
        val stroke: PptxStroke? = null,
        val cornerRadiusPt: Float = 0f
    ) : PptxElement()

    data class Table(
        override val bounds: RectF,
        val rows: List<PptxTableRow> = emptyList(),
        val columnWidthsPt: List<Float> = emptyList()
    ) : PptxElement()

    data class Group(
        override val bounds: RectF,
        val children: List<PptxElement> = emptyList()
    ) : PptxElement()
}
