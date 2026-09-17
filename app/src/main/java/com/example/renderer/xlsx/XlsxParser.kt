package com.example.renderer.xlsx

import android.graphics.Color
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Native OpenXML SpreadsheetML (.xlsx) streaming and structure parser.
 *
 * Separates workbook metadata, style tables, and shared string tables from
 * individual worksheet payload data, enabling lazy on-demand worksheet loading.
 */
object XlsxParser {

    /**
     * Parses the workbook metadata and returns a [Workbook] capable of lazily
     * loading and caching individual worksheets upon demand.
     */
    fun parse(file: File, displayName: String = file.name): Workbook {
        // First verify zip readability and inspect relationships
        val zipFile = ZipFile(file)
        return try {
            val rels = parseWorkbookRels(zipFile)
            val sheetSummaries = parseWorkbookSheets(zipFile, rels)
            val sharedStrings = parseSharedStrings(zipFile)
            val styles = parseStyles(zipFile)

            Workbook(
                title = displayName,
                sheets = sheetSummaries,
                sheetLoader = { sheetIndex ->
                    val summary = sheetSummaries.getOrNull(sheetIndex)
                        ?: sheetSummaries.firstOrNull()
                        ?: WorksheetSummary(0, "Sheet1", "1", "rId1", "xl/worksheets/sheet1.xml")
                    loadWorksheet(file, summary, sharedStrings, styles)
                }
            )
        } finally {
            zipFile.close()
        }
    }

    /**
     * Converts a column letter designation (e.g. "A", "Z", "AA", "BC") to a 0-based column index.
     */
    fun colNameToIndex(colName: String): Int {
        var result = 0
        for (c in colName.uppercase()) {
            if (c in 'A'..'Z') {
                result = result * 26 + (c - 'A' + 1)
            }
        }
        return (result - 1).coerceAtLeast(0)
    }

    /**
     * Converts a 0-based column index to an Excel letter designation (e.g. 0 -> "A", 25 -> "Z", 26 -> "AA").
     */
    fun indexToColName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /**
     * Parses an A1-style cell reference (e.g. "C5", "AA12") into a Pair of (rowIndex, columnIndex) 0-based.
     */
    fun parseCellReference(ref: String): Pair<Int, Int> {
        var colStr = ""
        var rowStr = ""
        for (ch in ref) {
            if (ch.isLetter()) {
                colStr += ch
            } else if (ch.isDigit()) {
                rowStr += ch
            }
        }
        val colIndex = colNameToIndex(colStr)
        val rowIndex = (rowStr.toIntOrNull()?.minus(1))?.coerceAtLeast(0) ?: 0
        return Pair(rowIndex, colIndex)
    }

    // ---------------------------------------------------------------------------------------------
    // Private Parsing Helpers
    // ---------------------------------------------------------------------------------------------

    private fun parseWorkbookRels(zipFile: ZipFile): Map<String, String> {
        val relsEntry = zipFile.getEntry("xl/_rels/workbook.xml.rels")
            ?: zipFile.getEntry("xl/_rels/Workbook.xml.rels")
            ?: return emptyMap()

        val map = mutableMapOf<String, String>()
        zipFile.getInputStream(relsEntry).use { stream ->
            val parser = createPullParser(stream)
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                        val fullPath = if (target.startsWith("/")) {
                            target.removePrefix("/")
                        } else if (target.startsWith("xl/")) {
                            target
                        } else {
                            "xl/$target"
                        }
                        map[id] = fullPath
                    }
                }
                eventType = parser.next()
            }
        }
        return map
    }

    private fun parseWorkbookSheets(zipFile: ZipFile, rels: Map<String, String>): List<WorksheetSummary> {
        val wbEntry = zipFile.getEntry("xl/workbook.xml")
            ?: zipFile.getEntry("xl/Workbook.xml")
            ?: return listOf(WorksheetSummary(0, "Sheet1", "1", "rId1", "xl/worksheets/sheet1.xml"))

        val sheets = mutableListOf<WorksheetSummary>()
        zipFile.getInputStream(wbEntry).use { stream ->
            val parser = createPullParser(stream)
            var eventType = parser.eventType
            var sheetIndex = 0
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: "Sheet${sheetIndex + 1}"
                    val sheetId = parser.getAttributeValue(null, "sheetId") ?: "${sheetIndex + 1}"
                    val rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                        ?: parser.getAttributeValue(null, "r:id")
                        ?: parser.getAttributeValue(null, "id")
                        ?: "rId${sheetIndex + 1}"

                    val targetPath = rels[rId] ?: "xl/worksheets/sheet${sheetIndex + 1}.xml"
                    sheets.add(
                        WorksheetSummary(
                            index = sheetIndex,
                            name = name,
                            sheetId = sheetId,
                            relationId = rId,
                            targetPath = targetPath
                        )
                    )
                    sheetIndex++
                }
                eventType = parser.next()
            }
        }

        return if (sheets.isEmpty()) {
            listOf(WorksheetSummary(0, "Sheet1", "1", "rId1", "xl/worksheets/sheet1.xml"))
        } else {
            sheets
        }
    }

    private fun parseSharedStrings(zipFile: ZipFile): List<String> {
        val entry = zipFile.getEntry("xl/sharedStrings.xml")
            ?: zipFile.getEntry("xl/SharedStrings.xml")
            ?: return emptyList()

        val list = mutableListOf<String>()
        zipFile.getInputStream(entry).use { stream ->
            val parser = createPullParser(stream)
            var eventType = parser.eventType
            var inSi = false
            val currentString = StringBuilder()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            inSi = true
                            currentString.clear()
                        } else if (inSi && parser.name == "t") {
                            val text = parser.nextText()
                            currentString.append(text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si") {
                            list.add(currentString.toString())
                            inSi = false
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        return list
    }

    private fun parseStyles(zipFile: ZipFile): List<CellStyle> {
        val entry = zipFile.getEntry("xl/styles.xml")
            ?: zipFile.getEntry("xl/Styles.xml")
            ?: return listOf(CellStyle.DEFAULT)

        val fonts = mutableListOf<ParsedFont>()
        val fills = mutableListOf<Int?>()
        val borders = mutableListOf<CellBorder>()
        val cellXfs = mutableListOf<CellStyle>()

        zipFile.getInputStream(entry).use { stream ->
            val parser = createPullParser(stream)
            var eventType = parser.eventType

            var section = ""

            // Font parsing state
            var currentFontBold = false
            var currentFontItalic = false
            var currentFontSize = 12f
            var currentFontColor = 0xFF1F2328.toInt()

            // Fill parsing state
            var currentFillColor: Int? = null

            // Border parsing state
            var bLeft = false
            var bRight = false
            var bTop = false
            var bBottom = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "fonts" -> section = "fonts"
                            "fills" -> section = "fills"
                            "borders" -> section = "borders"
                            "cellXfs" -> section = "cellXfs"

                            "font" -> {
                                currentFontBold = false
                                currentFontItalic = false
                                currentFontSize = 12f
                                currentFontColor = 0xFF1F2328.toInt()
                            }
                            "b" -> if (section == "fonts") currentFontBold = true
                            "i" -> if (section == "fonts") currentFontItalic = true
                            "sz" -> if (section == "fonts") {
                                currentFontSize = parser.getAttributeValue(null, "val")?.toFloatOrNull() ?: 12f
                            }
                            "color" -> if (section == "fonts") {
                                val rgb = parser.getAttributeValue(null, "rgb")
                                if (!rgb.isNullOrEmpty()) {
                                    currentFontColor = parseRgbColor(rgb, currentFontColor)
                                }
                            }

                            "fill" -> {
                                currentFillColor = null
                            }
                            "fgColor" -> if (section == "fills") {
                                val rgb = parser.getAttributeValue(null, "rgb")
                                if (!rgb.isNullOrEmpty()) {
                                    currentFillColor = parseRgbColor(rgb, 0)
                                }
                            }

                            "border" -> {
                                bLeft = false
                                bRight = false
                                bTop = false
                                bBottom = false
                            }
                            "left" -> if (section == "borders" && parser.getAttributeValue(null, "style") != null) bLeft = true
                            "right" -> if (section == "borders" && parser.getAttributeValue(null, "style") != null) bRight = true
                            "top" -> if (section == "borders" && parser.getAttributeValue(null, "style") != null) bTop = true
                            "bottom" -> if (section == "borders" && parser.getAttributeValue(null, "style") != null) bBottom = true

                            "xf" -> if (section == "cellXfs") {
                                val fontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                                val fillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                                val borderId = parser.getAttributeValue(null, "borderId")?.toIntOrNull() ?: 0

                                val font = fonts.getOrNull(fontId)
                                val bg = fills.getOrNull(fillId)
                                val border = borders.getOrNull(borderId) ?: CellBorder.DEFAULT_GRID

                                // Alignment may be child tag
                                var alignH = CellHorizontalAlignment.GENERAL
                                var alignV = CellVerticalAlignment.CENTER

                                // Check next tokens for alignment before closing xf
                                val xfDepth = parser.depth
                                while (parser.next() != XmlPullParser.END_TAG || parser.depth > xfDepth) {
                                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "alignment") {
                                        val hStr = parser.getAttributeValue(null, "horizontal")
                                        val vStr = parser.getAttributeValue(null, "vertical")
                                        alignH = when (hStr?.lowercase()) {
                                            "left" -> CellHorizontalAlignment.LEFT
                                            "center" -> CellHorizontalAlignment.CENTER
                                            "right" -> CellHorizontalAlignment.RIGHT
                                            else -> CellHorizontalAlignment.GENERAL
                                        }
                                        alignV = when (vStr?.lowercase()) {
                                            "top" -> CellVerticalAlignment.TOP
                                            "bottom" -> CellVerticalAlignment.BOTTOM
                                            else -> CellVerticalAlignment.CENTER
                                        }
                                    }
                                }

                                cellXfs.add(
                                    CellStyle(
                                        isBold = font?.isBold ?: false,
                                        isItalic = font?.isItalic ?: false,
                                        fontSizeSp = font?.fontSize ?: 12f,
                                        textColor = font?.color ?: 0xFF1F2328.toInt(),
                                        backgroundColor = bg,
                                        horizontalAlignment = alignH,
                                        verticalAlignment = alignV,
                                        border = border
                                    )
                                )
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "font" -> fonts.add(ParsedFont(currentFontBold, currentFontItalic, currentFontSize, currentFontColor))
                            "fill" -> fills.add(currentFillColor)
                            "border" -> borders.add(CellBorder(hasLeft = bLeft, hasRight = bRight, hasTop = bTop, hasBottom = bBottom))
                            "fonts", "fills", "borders", "cellXfs" -> section = ""
                        }
                    }
                }
                eventType = parser.next()
            }
        }

        return if (cellXfs.isEmpty()) listOf(CellStyle.DEFAULT) else cellXfs
    }

    private fun loadWorksheet(
        file: File,
        summary: WorksheetSummary,
        sharedStrings: List<String>,
        styles: List<CellStyle>
    ): Worksheet {
        val zipFile = ZipFile(file)
        return try {
            val entry = zipFile.getEntry(summary.targetPath)
                ?: zipFile.getEntry(summary.targetPath.lowercase())
                ?: return Worksheet(summary.name, summary.index, 0, 0, emptyMap())

            val rows = mutableMapOf<Int, MutableMap<Int, CellData>>()
            val rowHeights = mutableMapOf<Int, Float>()
            val colWidths = mutableMapOf<Int, Float>()
            val mergedList = mutableListOf<CellRange>()

            var maxRowIndex = 0
            var maxColIndex = 0

            zipFile.getInputStream(entry).use { stream ->
                val parser = createPullParser(stream)
                var eventType = parser.eventType

                var currentRowIndex = 0
                var currentRowHeight: Float? = null

                var currentCellRef = ""
                var currentCellType = ""
                var currentCellStyleIndex = 0
                var currentCellValue: String? = null
                var currentFormula: String? = null

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "col" -> {
                                    val min = parser.getAttributeValue(null, "min")?.toIntOrNull() ?: 1
                                    val max = parser.getAttributeValue(null, "max")?.toIntOrNull() ?: min
                                    val width = parser.getAttributeValue(null, "width")?.toFloatOrNull()
                                    if (width != null) {
                                        for (c in (min - 1)..(max - 1)) {
                                            colWidths[c] = width
                                        }
                                    }
                                }
                                "row" -> {
                                    val r = parser.getAttributeValue(null, "r")?.toIntOrNull()
                                    currentRowIndex = (r?.minus(1)) ?: currentRowIndex
                                    val ht = parser.getAttributeValue(null, "ht")?.toFloatOrNull()
                                    currentRowHeight = ht
                                    if (ht != null) {
                                        rowHeights[currentRowIndex] = ht
                                    }
                                    if (currentRowIndex > maxRowIndex) {
                                        maxRowIndex = currentRowIndex
                                    }
                                }
                                "c" -> {
                                    currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                                    currentCellType = parser.getAttributeValue(null, "t") ?: ""
                                    currentCellStyleIndex = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                                    currentCellValue = null
                                    currentFormula = null
                                }
                                "f" -> {
                                    currentFormula = parser.nextText()
                                }
                                "v" -> {
                                    currentCellValue = parser.nextText()
                                }
                                "is" -> {
                                    // Inline string
                                    // Parse inner <t>
                                    val isDepth = parser.depth
                                    while (parser.next() != XmlPullParser.END_TAG || parser.depth > isDepth) {
                                        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "t") {
                                            currentCellValue = parser.nextText()
                                        }
                                    }
                                }
                                "mergeCell" -> {
                                    val ref = parser.getAttributeValue(null, "ref")
                                    if (!ref.isNullOrBlank() && ref.contains(":")) {
                                        val parts = ref.split(":")
                                        if (parts.size == 2) {
                                            val (sRow, sCol) = parseCellReference(parts[0])
                                            val (eRow, eCol) = parseCellReference(parts[1])
                                            mergedList.add(CellRange(sRow, sCol, eRow, eCol))
                                        }
                                    }
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "c" -> {
                                    if (currentCellRef.isNotEmpty()) {
                                        val (rIdx, cIdx) = parseCellReference(currentCellRef)
                                        if (rIdx > maxRowIndex) maxRowIndex = rIdx
                                        if (cIdx > maxColIndex) maxColIndex = cIdx

                                        // Resolve value
                                        val rawVal = currentCellValue ?: ""
                                        val resolvedText = when (currentCellType) {
                                            "s" -> {
                                                val strIndex = rawVal.toIntOrNull() ?: -1
                                                sharedStrings.getOrNull(strIndex) ?: rawVal
                                            }
                                            "b" -> if (rawVal == "1") "TRUE" else "FALSE"
                                            else -> rawVal
                                        }

                                        val cellTypeEnum = when {
                                            currentCellType == "s" || currentCellType == "inlineStr" -> CellType.STRING
                                            currentCellType == "b" -> CellType.BOOLEAN
                                            currentFormula != null -> CellType.FORMULA
                                            resolvedText.toDoubleOrNull() != null -> CellType.NUMBER
                                            resolvedText.isEmpty() -> CellType.BLANK
                                            else -> CellType.STRING
                                        }

                                        val cellStyle = styles.getOrNull(currentCellStyleIndex) ?: CellStyle.DEFAULT

                                        val cell = CellData(
                                            rowIndex = rIdx,
                                            columnIndex = cIdx,
                                            cellReference = currentCellRef,
                                            rawValue = rawVal,
                                            formattedValue = resolvedText,
                                            formula = currentFormula,
                                            type = cellTypeEnum,
                                            style = cellStyle
                                        )

                                        val rowMap = rows.getOrPut(rIdx) { mutableMapOf() }
                                        rowMap[cIdx] = cell
                                    }
                                }
                                "row" -> {
                                    currentRowIndex++
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            val finalRows = rows.mapValues { entry ->
                RowData(
                    rowIndex = entry.key,
                    heightPt = rowHeights[entry.key],
                    cells = entry.value
                )
            }

            val totalRows = if (finalRows.isNotEmpty()) maxRowIndex + 1 else 0
            val totalCols = if (finalRows.isNotEmpty()) maxColIndex + 1 else 0

            Worksheet(
                name = summary.name,
                sheetIndex = summary.index,
                rowCount = totalRows,
                columnCount = totalCols,
                rows = finalRows,
                columnWidths = colWidths,
                rowHeights = rowHeights,
                mergedRegions = mergedList
            )
        } finally {
            zipFile.close()
        }
    }

    private fun parseRgbColor(rgbHex: String, defaultColor: Int): Int {
        return try {
            val clean = rgbHex.removePrefix("#")
            if (clean.length == 8) {
                // AARRGGBB
                clean.toLong(16).toInt()
            } else if (clean.length == 6) {
                // RRGGBB
                (0xFF000000 or clean.toLong(16)).toInt()
            } else {
                defaultColor
            }
        } catch (_: Exception) {
            defaultColor
        }
    }

    private fun createPullParser(inputStream: InputStream): XmlPullParser {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")
        return parser
    }

    private data class ParsedFont(
        val isBold: Boolean,
        val isItalic: Boolean,
        val fontSize: Float,
        val color: Int
    )
}
