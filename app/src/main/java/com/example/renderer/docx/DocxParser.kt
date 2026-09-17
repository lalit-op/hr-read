package com.example.renderer.docx

import android.graphics.Color
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * OpenXML (DOCX) Parser.
 *
 * Extracts document structure, typography, styling, margins, tables,
 * borders, headers, footers, and embedded media (logos, signatures, drawings).
 */
object DocxParser {

    private const val TWIPS_PER_POINT = 20f
    private const val EMUS_PER_POINT = 12700f // 914400 EMUs per inch / 72 pt per inch

    fun parse(file: File, documentTitle: String): DocxDocument {
        ZipFile(file).use { zip ->
            // 1. Parse relationship map (rId -> target file)
            val relsMap = parseRelationships(zip)

            // 2. Extract media images into byte arrays keyed by relationship ID
            val mediaMap = mutableMapOf<String, ByteArray>()
            for ((rId, target) in relsMap) {
                if (target.contains("media/") || target.endsWith(".png") || target.endsWith(".jpeg") || target.endsWith(".jpg")) {
                    val normalizedTarget = if (target.startsWith("/")) target.substring(1) else "word/$target"
                    val entry = zip.getEntry(normalizedTarget) ?: zip.getEntry(target)
                    if (entry != null) {
                        zip.getInputStream(entry).use { input ->
                            mediaMap[rId] = input.readBytes()
                        }
                    }
                }
            }

            // 3. Parse headers and footers if present
            var header: DocxHeaderFooter? = null
            var footer: DocxHeaderFooter? = null
            for ((rId, target) in relsMap) {
                if (target.contains("header") && target.endsWith(".xml")) {
                    val normalized = if (target.startsWith("/")) target.substring(1) else "word/$target"
                    val entry = zip.getEntry(normalized) ?: zip.getEntry(target)
                    if (entry != null) {
                        zip.getInputStream(entry).use { input ->
                            header = parseHeaderFooter(input, mediaMap)
                        }
                    }
                } else if (target.contains("footer") && target.endsWith(".xml")) {
                    val normalized = if (target.startsWith("/")) target.substring(1) else "word/$target"
                    val entry = zip.getEntry(normalized) ?: zip.getEntry(target)
                    if (entry != null) {
                        zip.getInputStream(entry).use { input ->
                            footer = parseHeaderFooter(input, mediaMap)
                        }
                    }
                }
            }

            // 4. Parse document.xml (body content and section properties)
            val docEntry = zip.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("Invalid DOCX: missing word/document.xml")

            zip.getInputStream(docEntry).use { input ->
                return parseDocumentXml(input, documentTitle, header, footer, mediaMap)
            }
        }
    }

    private fun parseRelationships(zip: ZipFile): Map<String, String> {
        val relsMap = mutableMapOf<String, String>()
        val relsEntry = zip.getEntry("word/_rels/document.xml.rels") ?: return relsMap
        zip.getInputStream(relsEntry).use { input ->
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(input)
            val relNodes = doc.getElementsByTagName("Relationship")
            for (i in 0 until relNodes.length) {
                val elem = relNodes.item(i) as? Element ?: continue
                val id = elem.getAttribute("Id")
                val target = elem.getAttribute("Target")
                if (id.isNotBlank() && target.isNotBlank()) {
                    relsMap[id] = target
                }
            }
        }
        return relsMap
    }

    private fun parseHeaderFooter(input: InputStream, mediaMap: Map<String, ByteArray>): DocxHeaderFooter {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(input)
        val paragraphs = mutableListOf<DocxParagraph>()

        val pNodes = doc.getElementsByTagName("w:p")
        for (i in 0 until pNodes.length) {
            val pElem = pNodes.item(i) as? Element ?: continue
            paragraphs.add(parseParagraph(pElem, mediaMap))
        }
        return DocxHeaderFooter(paragraphs)
    }

    private fun parseDocumentXml(
        input: InputStream,
        title: String,
        header: DocxHeaderFooter?,
        footer: DocxHeaderFooter?,
        mediaMap: Map<String, ByteArray>
    ): DocxDocument {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(input)

        val bodyList = doc.getElementsByTagName("w:body")
        if (bodyList.length == 0) {
            return DocxDocument(title, DocxSectionProps(), header, footer, emptyList(), mediaMap)
        }

        val bodyElem = bodyList.item(0) as Element
        val blocks = mutableListOf<DocxBlock>()
        var sectionProps = DocxSectionProps()

        val children = bodyElem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val elem = node as Element

            when (elem.nodeName) {
                "w:p" -> {
                    val p = parseParagraph(elem, mediaMap)
                    if (p.isPageBreak) {
                        blocks.add(DocxBlock.PageBreakBlock)
                    }
                    if (p.runs.isNotEmpty() || p.hasBottomBorder || p.spaceBeforePt > 0 || p.spaceAfterPt > 0) {
                        blocks.add(DocxBlock.ParagraphBlock(p))
                    }
                }

                "w:tbl" -> {
                    val table = parseTable(elem, mediaMap)
                    if (table.rows.isNotEmpty()) {
                        blocks.add(DocxBlock.TableBlock(table))
                    }
                }

                "w:sectPr" -> {
                    sectionProps = parseSectionProps(elem)
                }
            }
        }

        return DocxDocument(
            title = title,
            sectionProps = sectionProps,
            header = header,
            footer = footer,
            blocks = blocks,
            media = mediaMap
        )
    }

    private fun parseParagraph(elem: Element, mediaMap: Map<String, ByteArray>): DocxParagraph {
        var alignment = DocxAlignment.LEFT
        var spaceBeforePt = 0f
        var spaceAfterPt = 4f
        var lineSpacingMultiplier = 1.15f
        var backgroundColor: Int? = null
        var indentLeftPt = 0f
        var indentRightPt = 0f
        var isPageBreak = false
        var hasBottomBorder = false
        var bottomBorderColor = 0xFFCBD5E0.toInt()
        var bottomBorderWidthPt = 1f

        // Parse w:pPr
        val pPrList = elem.getElementsByTagName("w:pPr")
        if (pPrList.length > 0) {
            val pPr = pPrList.item(0) as Element

            // Alignment (w:jc)
            val jcList = pPr.getElementsByTagName("w:jc")
            if (jcList.length > 0) {
                val jcVal = (jcList.item(0) as Element).getAttribute("w:val")
                alignment = when (jcVal.lowercase()) {
                    "center" -> DocxAlignment.CENTER
                    "right" -> DocxAlignment.RIGHT
                    "both", "justify" -> DocxAlignment.JUSTIFY
                    else -> DocxAlignment.LEFT
                }
            }

            // Spacing (w:spacing)
            val spacingList = pPr.getElementsByTagName("w:spacing")
            if (spacingList.length > 0) {
                val sElem = spacingList.item(0) as Element
                val beforeStr = sElem.getAttribute("w:before")
                val afterStr = sElem.getAttribute("w:after")
                val lineStr = sElem.getAttribute("w:line")

                if (beforeStr.isNotBlank()) {
                    beforeStr.toFloatOrNull()?.let { spaceBeforePt = it / TWIPS_PER_POINT }
                }
                if (afterStr.isNotBlank()) {
                    afterStr.toFloatOrNull()?.let { spaceAfterPt = it / TWIPS_PER_POINT }
                }
                if (lineStr.isNotBlank()) {
                    lineStr.toFloatOrNull()?.let {
                        if (it > 100f) {
                            lineSpacingMultiplier = (it / 240f).coerceIn(1.0f, 2.5f)
                        }
                    }
                }
            }

            // Indentation (w:ind)
            val indList = pPr.getElementsByTagName("w:ind")
            if (indList.length > 0) {
                val iElem = indList.item(0) as Element
                val leftStr = iElem.getAttribute("w:left")
                val rightStr = iElem.getAttribute("w:right")
                if (leftStr.isNotBlank()) leftStr.toFloatOrNull()?.let { indentLeftPt = it / TWIPS_PER_POINT }
                if (rightStr.isNotBlank()) rightStr.toFloatOrNull()?.let { indentRightPt = it / TWIPS_PER_POINT }
            }

            // Background shading (w:shd)
            val shdList = pPr.getElementsByTagName("w:shd")
            if (shdList.length > 0) {
                val fillStr = (shdList.item(0) as Element).getAttribute("w:fill")
                backgroundColor = parseColor(fillStr)
            }

            // Bottom border (w:pBdr -> w:bottom)
            val pBdrList = pPr.getElementsByTagName("w:pBdr")
            if (pBdrList.length > 0) {
                val pBdr = pBdrList.item(0) as Element
                val bList = pBdr.getElementsByTagName("w:bottom")
                if (bList.length > 0) {
                    hasBottomBorder = true
                    val bElem = bList.item(0) as Element
                    val colorStr = bElem.getAttribute("w:color")
                    parseColor(colorStr)?.let { bottomBorderColor = it }
                    val szStr = bElem.getAttribute("w:sz")
                    szStr.toFloatOrNull()?.let { bottomBorderWidthPt = (it / 8f).coerceAtLeast(0.5f) }
                }
            }
        }

        // Parse runs (w:r)
        val runs = mutableListOf<DocxRun>()
        val children = elem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val childElem = node as Element

            if (childElem.nodeName == "w:r") {
                val run = parseRun(childElem, mediaMap)
                if (run != null) {
                    runs.add(run)
                }
                // Check for page break in run
                val brList = childElem.getElementsByTagName("w:br")
                for (b in 0 until brList.length) {
                    val br = brList.item(b) as Element
                    if (br.getAttribute("w:type") == "page") {
                        isPageBreak = true
                    }
                }
            }
        }

        return DocxParagraph(
            runs = runs,
            alignment = alignment,
            spaceBeforePt = spaceBeforePt,
            spaceAfterPt = spaceAfterPt,
            lineSpacingMultiplier = lineSpacingMultiplier,
            backgroundColor = backgroundColor,
            indentLeftPt = indentLeftPt,
            indentRightPt = indentRightPt,
            isPageBreak = isPageBreak,
            hasBottomBorder = hasBottomBorder,
            bottomBorderColor = bottomBorderColor,
            bottomBorderWidthPt = bottomBorderWidthPt
        )
    }

    private fun parseRun(elem: Element, mediaMap: Map<String, ByteArray>): DocxRun? {
        var text = ""
        var fontFamily = "sans-serif"
        var fontSizePt = 11f
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var color = 0xFF1A202C.toInt()
        var highlightColor: Int? = null
        var drawing: DocxDrawing? = null

        // Parse w:rPr
        val rPrList = elem.getElementsByTagName("w:rPr")
        if (rPrList.length > 0) {
            val rPr = rPrList.item(0) as Element

            // Font family (w:rFonts)
            val fontList = rPr.getElementsByTagName("w:rFonts")
            if (fontList.length > 0) {
                val fElem = fontList.item(0) as Element
                val ascii = fElem.getAttribute("w:ascii").ifBlank { fElem.getAttribute("w:hAnsi") }
                if (ascii.isNotBlank()) {
                    fontFamily = ascii
                }
            }

            // Bold (w:b)
            val bList = rPr.getElementsByTagName("w:b")
            if (bList.length > 0) {
                val bVal = (bList.item(0) as Element).getAttribute("w:val")
                isBold = bVal != "0" && bVal != "false"
            }

            // Italic (w:i)
            val iList = rPr.getElementsByTagName("w:i")
            if (iList.length > 0) {
                val iVal = (iList.item(0) as Element).getAttribute("w:val")
                isItalic = iVal != "0" && iVal != "false"
            }

            // Underline (w:u)
            val uList = rPr.getElementsByTagName("w:u")
            if (uList.length > 0) {
                val uVal = (uList.item(0) as Element).getAttribute("w:val")
                isUnderline = uVal != "none"
            }

            // Color (w:color)
            val colorList = rPr.getElementsByTagName("w:color")
            if (colorList.length > 0) {
                val cVal = (colorList.item(0) as Element).getAttribute("w:val")
                parseColor(cVal)?.let { color = it }
            }

            // Font size (w:sz: half points)
            val szList = rPr.getElementsByTagName("w:sz")
            if (szList.length > 0) {
                val sVal = (szList.item(0) as Element).getAttribute("w:val")
                sVal.toFloatOrNull()?.let { fontSizePt = it / 2f }
            }

            // Highlight (w:highlight)
            val hlList = rPr.getElementsByTagName("w:highlight")
            if (hlList.length > 0) {
                val hlVal = (hlList.item(0) as Element).getAttribute("w:val")
                highlightColor = parseHighlight(hlVal)
            }
        }

        // Text (w:t)
        val tList = elem.getElementsByTagName("w:t")
        val sb = StringBuilder()
        for (i in 0 until tList.length) {
            val tElem = tList.item(i) as Element
            sb.append(tElem.textContent ?: "")
        }
        text = sb.toString()

        // Drawings / Images (w:drawing)
        val drawList = elem.getElementsByTagName("w:drawing")
        if (drawList.length > 0) {
            val drawElem = drawList.item(0) as Element
            drawing = parseDrawing(drawElem)
        }

        if (text.isEmpty() && drawing == null) {
            return null
        }

        return DocxRun(
            text = text,
            fontFamily = fontFamily,
            fontSizePt = fontSizePt,
            isBold = isBold,
            isItalic = isItalic,
            isUnderline = isUnderline,
            color = color,
            highlightColor = highlightColor,
            drawing = drawing
        )
    }

    private fun parseDrawing(drawElem: Element): DocxDrawing? {
        // Find blip embed ID
        val blipList = drawElem.getElementsByTagName("a:blip")
        if (blipList.length == 0) return null
        val blip = blipList.item(0) as Element
        val embedId = blip.getAttribute("r:embed")
        if (embedId.isBlank()) return null

        var widthPt = 120f
        var heightPt = 120f

        // Find dimensions in wp:extent
        val extentList = drawElem.getElementsByTagName("wp:extent")
        if (extentList.length > 0) {
            val extent = extentList.item(0) as Element
            val cx = extent.getAttribute("cx").toFloatOrNull()
            val cy = extent.getAttribute("cy").toFloatOrNull()
            if (cx != null && cy != null) {
                widthPt = cx / EMUS_PER_POINT
                heightPt = cy / EMUS_PER_POINT
            }
        }

        val descrList = drawElem.getElementsByTagName("wp:docPr")
        val descr = if (descrList.length > 0) (descrList.item(0) as Element).getAttribute("descr") else ""

        return DocxDrawing(
            relationshipId = embedId,
            widthPt = widthPt,
            heightPt = heightPt,
            description = descr
        )
    }

    private fun parseTable(elem: Element, mediaMap: Map<String, ByteArray>): DocxTable {
        val columnWidths = mutableListOf<Float>()
        val gridList = elem.getElementsByTagName("w:tblGrid")
        if (gridList.length > 0) {
            val grid = gridList.item(0) as Element
            val colList = grid.getElementsByTagName("w:gridCol")
            for (c in 0 until colList.length) {
                val col = colList.item(c) as Element
                val wStr = col.getAttribute("w:w")
                val width = wStr.toFloatOrNull()?.div(TWIPS_PER_POINT) ?: 100f
                columnWidths.add(width)
            }
        }

        val rows = mutableListOf<DocxTableRow>()
        val children = elem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val rowElem = node as Element
            if (rowElem.nodeName == "w:tr") {
                rows.add(parseTableRow(rowElem, mediaMap))
            }
        }

        return DocxTable(
            rows = rows,
            columnWidthsPt = columnWidths
        )
    }

    private fun parseTableRow(rowElem: Element, mediaMap: Map<String, ByteArray>): DocxTableRow {
        val cells = mutableListOf<DocxTableCell>()
        var minHeightPt = 0f
        var isHeader = false

        // Row properties (w:trPr)
        val trPrList = rowElem.getElementsByTagName("w:trPr")
        if (trPrList.length > 0) {
            val trPr = trPrList.item(0) as Element
            if (trPr.getElementsByTagName("w:tblHeader").length > 0) {
                isHeader = true
            }
            val trHeightList = trPr.getElementsByTagName("w:trHeight")
            if (trHeightList.length > 0) {
                val hStr = (trHeightList.item(0) as Element).getAttribute("w:val")
                hStr.toFloatOrNull()?.let { minHeightPt = it / TWIPS_PER_POINT }
            }
        }

        val children = rowElem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val cellElem = node as Element
            if (cellElem.nodeName == "w:tc") {
                cells.add(parseTableCell(cellElem, mediaMap))
            }
        }

        return DocxTableRow(
            cells = cells,
            minHeightPt = minHeightPt,
            isHeader = isHeader
        )
    }

    private fun parseTableCell(cellElem: Element, mediaMap: Map<String, ByteArray>): DocxTableCell {
        var widthPt = 0f
        var colSpan = 1
        var backgroundColor: Int? = null
        var topBorder: DocxBorder? = null
        var bottomBorder: DocxBorder? = null
        var leftBorder: DocxBorder? = null
        var rightBorder: DocxBorder? = null

        // Cell properties (w:tcPr)
        val tcPrList = cellElem.getElementsByTagName("w:tcPr")
        if (tcPrList.length > 0) {
            val tcPr = tcPrList.item(0) as Element

            // Width (w:tcW)
            val tcWList = tcPr.getElementsByTagName("w:tcW")
            if (tcWList.length > 0) {
                val wStr = (tcWList.item(0) as Element).getAttribute("w:w")
                wStr.toFloatOrNull()?.let { widthPt = it / TWIPS_PER_POINT }
            }

            // Column span (w:gridSpan)
            val gsList = tcPr.getElementsByTagName("w:gridSpan")
            if (gsList.length > 0) {
                val gsStr = (gsList.item(0) as Element).getAttribute("w:val")
                gsStr.toIntOrNull()?.let { colSpan = it }
            }

            // Shading (w:shd)
            val shdList = tcPr.getElementsByTagName("w:shd")
            if (shdList.length > 0) {
                val fillStr = (shdList.item(0) as Element).getAttribute("w:fill")
                backgroundColor = parseColor(fillStr)
            }

            // Borders (w:tcBorders)
            val bdrList = tcPr.getElementsByTagName("w:tcBorders")
            if (bdrList.length > 0) {
                val bdr = bdrList.item(0) as Element
                topBorder = parseBorder(bdr, "w:top")
                bottomBorder = parseBorder(bdr, "w:bottom")
                leftBorder = parseBorder(bdr, "w:left")
                rightBorder = parseBorder(bdr, "w:right")
            }
        }

        // Paragraphs inside cell
        val paragraphs = mutableListOf<DocxParagraph>()
        val children = cellElem.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val childElem = node as Element
            if (childElem.nodeName == "w:p") {
                paragraphs.add(parseParagraph(childElem, mediaMap))
            }
        }

        return DocxTableCell(
            paragraphs = paragraphs,
            widthPt = widthPt,
            colSpan = colSpan,
            backgroundColor = backgroundColor,
            topBorder = topBorder,
            bottomBorder = bottomBorder,
            leftBorder = leftBorder,
            rightBorder = rightBorder
        )
    }

    private fun parseBorder(parentElem: Element, borderTag: String): DocxBorder? {
        val list = parentElem.getElementsByTagName(borderTag)
        if (list.length == 0) return null
        val bElem = list.item(0) as Element
        val valStr = bElem.getAttribute("w:val")
        if (valStr == "none" || valStr == "nil") return null

        var widthPt = 0.5f
        bElem.getAttribute("w:sz").toFloatOrNull()?.let { widthPt = (it / 8f).coerceAtLeast(0.5f) }

        var color = 0xFFCBD5E0.toInt()
        val cStr = bElem.getAttribute("w:color")
        parseColor(cStr)?.let { color = it }

        return DocxBorder(widthPt = widthPt, color = color, style = valStr)
    }

    private fun parseSectionProps(elem: Element): DocxSectionProps {
        var width = 595.28f  // A4
        var height = 841.89f
        var top = 72f
        var bottom = 72f
        var left = 72f
        var right = 72f
        var header = 36f
        var footer = 36f
        var isLandscape = false

        // Page Size (w:pgSz)
        val szList = elem.getElementsByTagName("w:pgSz")
        if (szList.length > 0) {
            val szElem = szList.item(0) as Element
            szElem.getAttribute("w:w").toFloatOrNull()?.let { width = it / TWIPS_PER_POINT }
            szElem.getAttribute("w:h").toFloatOrNull()?.let { height = it / TWIPS_PER_POINT }
            if (szElem.getAttribute("w:orient") == "landscape") {
                isLandscape = true
            }
        }

        // Margins (w:pgMar)
        val marList = elem.getElementsByTagName("w:pgMar")
        if (marList.length > 0) {
            val mar = marList.item(0) as Element
            mar.getAttribute("w:top").toFloatOrNull()?.let { top = it / TWIPS_PER_POINT }
            mar.getAttribute("w:bottom").toFloatOrNull()?.let { bottom = it / TWIPS_PER_POINT }
            mar.getAttribute("w:left").toFloatOrNull()?.let { left = it / TWIPS_PER_POINT }
            mar.getAttribute("w:right").toFloatOrNull()?.let { right = it / TWIPS_PER_POINT }
            mar.getAttribute("w:header").toFloatOrNull()?.let { header = it / TWIPS_PER_POINT }
            mar.getAttribute("w:footer").toFloatOrNull()?.let { footer = it / TWIPS_PER_POINT }
        }

        return DocxSectionProps(
            pageWidth = width,
            pageHeight = height,
            marginTop = top,
            marginBottom = bottom,
            marginLeft = left,
            marginRight = right,
            headerMargin = header,
            footerMargin = footer,
            isLandscape = isLandscape
        )
    }

    private fun parseColor(hex: String): Int? {
        if (hex.isBlank() || hex.equals("auto", ignoreCase = true) || hex.equals("none", ignoreCase = true)) {
            return null
        }
        return try {
            val clean = hex.trim().removePrefix("#")
            if (clean.length == 6) {
                (0xFF000000 or clean.toLong(16)).toInt()
            } else if (clean.length == 8) {
                clean.toLong(16).toInt()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseHighlight(name: String): Int? {
        return when (name.lowercase()) {
            "yellow" -> 0xFFFFFF00.toInt()
            "green" -> 0xFF00FF00.toInt()
            "cyan" -> 0xFF00FFFF.toInt()
            "magenta" -> 0xFFFF00FF.toInt()
            "blue" -> 0xFF0000FF.toInt()
            "red" -> 0xFFFF0000.toInt()
            "darkblue" -> 0xFF00008B.toInt()
            "darkcyan" -> 0xFF008B8B.toInt()
            "darkgreen" -> 0xFF006400.toInt()
            "darkmagenta" -> 0xFF8B008B.toInt()
            "darkred" -> 0xFF8B0000.toInt()
            "darkyellow" -> 0xFFB8860B.toInt()
            "darkgray" -> 0xFFA9A9A9.toInt()
            "lightgray" -> 0xFFD3D3D3.toInt()
            else -> null
        }
    }
}
