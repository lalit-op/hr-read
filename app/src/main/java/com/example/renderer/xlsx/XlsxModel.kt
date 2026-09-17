package com.example.renderer.xlsx

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-level domain model of an Excel Workbook (.xlsx, .xls).
 */
data class Workbook(
    val title: String,
    val sheets: List<WorksheetSummary>,
    private val sheetLoader: (Int) -> Worksheet
) {
    val sheetCount: Int get() = sheets.size

    val sheetNames: List<String> get() = sheets.map { it.name }

    // Thread-safe bounded in-memory LRU cache for lazily loaded worksheets (max 4 sheets)
    private val loadedSheets = object : java.util.LinkedHashMap<Int, Worksheet>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Worksheet>?): Boolean {
            return size > 4
        }
    }

    fun getWorksheet(index: Int): Worksheet {
        val safeIndex = index.coerceIn(0, (sheets.size - 1).coerceAtLeast(0))
        return synchronized(loadedSheets) {
            loadedSheets.getOrPut(safeIndex) {
                sheetLoader(safeIndex)
            }
        }
    }

    fun isSheetLoaded(index: Int): Boolean {
        return synchronized(loadedSheets) { loadedSheets.containsKey(index) }
    }

    fun clearCache() {
        synchronized(loadedSheets) {
            loadedSheets.clear()
        }
    }
}

data class WorksheetSummary(
    val index: Int,
    val name: String,
    val sheetId: String,
    val relationId: String,
    val targetPath: String
)

/**
 * Domain model of an individual spreadsheet worksheet.
 */
data class Worksheet(
    val name: String,
    val sheetIndex: Int,
    val rowCount: Int,
    val columnCount: Int,
    val rows: Map<Int, RowData>,
    val columnWidths: Map<Int, Float> = emptyMap(), // Custom width in character units
    val rowHeights: Map<Int, Float> = emptyMap(),    // Custom height in points
    val mergedRegions: List<CellRange> = emptyList()
) {
    fun getCell(row: Int, col: Int): CellData? {
        return rows[row]?.cells?.get(col)
    }

    fun getRow(row: Int): RowData? {
        return rows[row]
    }

    /**
     * Computes column width in Dp with a sensible default of 96.dp
     */
    fun getColumnWidthDp(col: Int): Dp {
        val customWidth = columnWidths[col]
        return if (customWidth != null && customWidth > 0f) {
            // Standard Excel char width to dp conversion: ~8.2 dp per char + 16 dp padding
            val dpVal = (customWidth * 8.2f + 16f).coerceIn(48f, 320f)
            dpVal.dp
        } else {
            96.dp
        }
    }

    /**
     * Computes row height in Dp with a sensible default of 28.dp
     */
    fun getRowHeightDp(row: Int): Dp {
        val customHeight = rowHeights[row]
        return if (customHeight != null && customHeight > 0f) {
            // Pt to dp conversion: 1 pt ~ 1.33 dp
            val dpVal = (customHeight * 1.33f).coerceIn(24f, 120f)
            dpVal.dp
        } else {
            28.dp
        }
    }

    /**
     * Finds if a cell is part of a merged region.
     * Returns the origin cell range if it is part of one.
     */
    fun getMergedRangeForCell(row: Int, col: Int): CellRange? {
        return mergedRegions.firstOrNull { it.contains(row, col) }
    }
}

data class RowData(
    val rowIndex: Int,
    val heightPt: Float? = null,
    val cells: Map<Int, CellData> = emptyMap()
)

enum class CellType {
    STRING,
    NUMBER,
    BOOLEAN,
    DATE,
    FORMULA,
    BLANK
}

data class CellData(
    val rowIndex: Int,
    val columnIndex: Int,
    val cellReference: String, // e.g. "A1", "C5"
    val rawValue: String,
    val formattedValue: String = rawValue,
    val formula: String? = null,
    val type: CellType = CellType.STRING,
    val style: CellStyle = CellStyle.DEFAULT
)

enum class CellHorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT,
    GENERAL
}

enum class CellVerticalAlignment {
    TOP,
    CENTER,
    BOTTOM
}

data class CellBorder(
    val hasLeft: Boolean = false,
    val hasRight: Boolean = false,
    val hasTop: Boolean = false,
    val hasBottom: Boolean = false,
    val color: Int = 0xFFD0D7DE.toInt()
) {
    val hasAny: Boolean get() = hasLeft || hasRight || hasTop || hasBottom

    companion object {
        val DEFAULT_GRID = CellBorder(hasLeft = true, hasRight = true, hasTop = true, hasBottom = true, color = 0xFFE0E0E0.toInt())
        val NONE = CellBorder()
    }
}

data class CellStyle(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val fontSizeSp: Float = 13f,
    val textColor: Int = 0xFF1F2328.toInt(),
    val backgroundColor: Int? = null,
    val horizontalAlignment: CellHorizontalAlignment = CellHorizontalAlignment.GENERAL,
    val verticalAlignment: CellVerticalAlignment = CellVerticalAlignment.CENTER,
    val border: CellBorder = CellBorder.DEFAULT_GRID,
    val numberFormat: String? = null
) {
    val composeTextColor: Color get() = Color(textColor)
    val composeBackgroundColor: Color? get() = backgroundColor?.let { Color(it) }
    val fontWeight: FontWeight get() = if (isBold) FontWeight.Bold else FontWeight.Normal
    val fontStyle: FontStyle get() = if (isItalic) FontStyle.Italic else FontStyle.Normal

    companion object {
        val DEFAULT = CellStyle()
    }
}

data class CellRange(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int
) {
    fun contains(row: Int, col: Int): Boolean {
        return row in startRow..endRow && col in startCol..endCol
    }

    val isSingleCell: Boolean get() = startRow == endRow && startCol == endCol
}
