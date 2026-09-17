package com.example.renderer.pptx

import android.graphics.Color
import android.graphics.RectF
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Native, high-fidelity OpenXML PresentationML (.pptx) parser.
 * Reads presentation structures, slides, shapes, typography, tables, media, and theme styling.
 */
object PptxParser {

    private const val EMU_PER_PT = 12700f

    fun parse(file: File, displayName: String = file.name, firstSlideOnly: Boolean = false): PptxPresentation {
        val zipFile = ZipFile(file)
        return try {
            parseFromZip(zipFile, displayName, firstSlideOnly)
        } finally {
            zipFile.close()
        }
    }

    private fun parseFromZip(zipFile: ZipFile, displayName: String, firstSlideOnly: Boolean = false): PptxPresentation {
        val docBuilder = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder()

        // 1. Extract embedded media files (ppt/media/*)
        val mediaMap = mutableMapOf<String, ByteArray>()
        val entries = zipFile.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name.replace("\\", "/")
            if (name.contains("ppt/media/")) {
                val fileName = name.substringAfterLast("/")
                val bytes = zipFile.getInputStream(entry).use { it.readBytes() }
                mediaMap[name] = bytes
                mediaMap[fileName] = bytes
                mediaMap["../media/$fileName"] = bytes
                mediaMap["media/$fileName"] = bytes
            }
        }

        // 2. Parse Theme colors from ppt/theme/theme1.xml (if present)
        val themeColors = mutableMapOf<String, Int>()
        themeColors.putAll(PptxTheme.defaultThemeColors())
        val themeEntry = zipFile.getEntry("ppt/theme/theme1.xml")
        if (themeEntry != null) {
            try {
                zipFile.getInputStream(themeEntry).use { stream ->
                    val themeDoc = docBuilder.parse(stream)
                    parseThemeColors(themeDoc.documentElement, themeColors)
                }
            } catch (_: Exception) {}
        }
        val theme = PptxTheme(themeColors)

        // 3. Parse Presentation Properties (slide dimensions & slide order)
        var slideWidthPt = 960f  // Default 16:9 widescreen
        var slideHeightPt = 540f
        val slideRelationshipIds = mutableListOf<String>()

        val presEntry = zipFile.getEntry("ppt/presentation.xml")
        if (presEntry != null) {
            try {
                zipFile.getInputStream(presEntry).use { stream ->
                    val presDoc = docBuilder.parse(stream)
                    val root = presDoc.documentElement

                    // Slide Size: <p:sldSz cx="12192000" cy="6858000" ... />
                    val sldSzNodes = root.getElementsByTagNameNS("*", "sldSz")
                    if (sldSzNodes.length > 0) {
                        val szElem = sldSzNodes.item(0) as Element
                        val cx = szElem.getAttribute("cx").toLongOrNull()
                        val cy = szElem.getAttribute("cy").toLongOrNull()
                        if (cx != null && cx > 0) slideWidthPt = cx.toFloat() / EMU_PER_PT
                        if (cy != null && cy > 0) slideHeightPt = cy.toFloat() / EMU_PER_PT
                    }

                    // Slide ID list: <p:sldIdLst><p:sldId r:id="rId2"/>...
                    val sldIdNodes = root.getElementsByTagNameNS("*", "sldId")
                    for (i in 0 until sldIdNodes.length) {
                        val sldElem = sldIdNodes.item(i) as Element
                        val rId = sldElem.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                            .ifEmpty { sldElem.getAttribute("r:id") }
                        if (rId.isNotBlank()) {
                            slideRelationshipIds.add(rId)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Resolve presentation relationships (ppt/_rels/presentation.xml.rels)
        val relsMap = mutableMapOf<String, String>()
        val presRelsEntry = zipFile.getEntry("ppt/_rels/presentation.xml.rels")
        if (presRelsEntry != null) {
            try {
                zipFile.getInputStream(presRelsEntry).use { stream ->
                    val relsDoc = docBuilder.parse(stream)
                    val relNodes = relsDoc.documentElement.getElementsByTagNameNS("*", "Relationship")
                    for (i in 0 until relNodes.length) {
                        val rElem = relNodes.item(i) as Element
                        val id = rElem.getAttribute("Id")
                        val target = rElem.getAttribute("Target")
                        if (id.isNotBlank() && target.isNotBlank()) {
                            relsMap[id] = target
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Build ordered list of slide XML entry paths
        val slidePaths = mutableListOf<String>()
        if (slideRelationshipIds.isNotEmpty()) {
            slideRelationshipIds.forEach { rId ->
                val target = relsMap[rId]
                if (target != null) {
                    val normalized = if (target.startsWith("/")) target.substring(1)
                    else if (target.startsWith("ppt/")) target
                    else "ppt/$target"
                    slidePaths.add(normalized)
                }
            }
        }

        // Fallback if no rels found: scan for ppt/slides/slide*.xml directly
        if (slidePaths.isEmpty()) {
            val allEntries = zipFile.entries()
            val scanned = mutableListOf<String>()
            while (allEntries.hasMoreElements()) {
                val name = allEntries.nextElement().name.replace("\\", "/")
                if (name.startsWith("ppt/slides/slide") && name.endsWith(".xml")) {
                    scanned.add(name)
                }
            }
            // Natural numeric sort: slide1.xml, slide2.xml, slide10.xml
            scanned.sortBy { name ->
                name.filter { it.isDigit() }.toIntOrNull() ?: 0
            }
            slidePaths.addAll(scanned)
        }

        // 6. Parse individual slides
        val parsedSlides = mutableListOf<PptxSlide>()
        val pathsToParse = if (firstSlideOnly && slidePaths.isNotEmpty()) {
            listOf(slidePaths.first())
        } else {
            slidePaths
        }
        pathsToParse.forEachIndexed { index, path ->
            val slideEntry = zipFile.getEntry(path)
            if (slideEntry != null) {
                try {
                    val slideRelsMap = parseSlideRelationships(zipFile, path)
                    zipFile.getInputStream(slideEntry).use { stream ->
                        val slideDoc = docBuilder.parse(stream)
                        val slide = parseSlide(
                            index = index,
                            root = slideDoc.documentElement,
                            theme = theme,
                            mediaMap = mediaMap,
                            slideRels = slideRelsMap,
                            slideWidthPt = slideWidthPt,
                            slideHeightPt = slideHeightPt
                        )
                        parsedSlides.add(slide)
                    }
                } catch (_: Exception) {
                    // Provide safe fallback slide
                    parsedSlides.add(
                        PptxSlide(
                            index = index,
                            title = "Slide ${index + 1}",
                            background = PptxBackground.Default
                        )
                    )
                }
            }
        }

        return PptxPresentation(
            title = displayName,
            slideWidthPt = slideWidthPt,
            slideHeightPt = slideHeightPt,
            slides = parsedSlides,
            theme = theme,
            media = mediaMap
        )
    }

    private fun parseSlideRelationships(zipFile: ZipFile, slidePath: String): Map<String, String> {
        val rels = mutableMapOf<String, String>()
        val dir = slidePath.substringBeforeLast("/")
        val file = slidePath.substringAfterLast("/")
        val relsPath = "$dir/_rels/$file.rels"
        val entry = zipFile.getEntry(relsPath) ?: return rels

        try {
            zipFile.getInputStream(entry).use { stream ->
                val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
                val doc = builder.parse(stream)
                val nodes = doc.documentElement.getElementsByTagNameNS("*", "Relationship")
                for (i in 0 until nodes.length) {
                    val elem = nodes.item(i) as Element
                    val id = elem.getAttribute("Id")
                    val target = elem.getAttribute("Target")
                    if (id.isNotBlank() && target.isNotBlank()) {
                        rels[id] = target
                    }
                }
            }
        } catch (_: Exception) {}
        return rels
    }

    private fun parseThemeColors(root: Element, outMap: MutableMap<String, Int>) {
        val clrSchemeNodes = root.getElementsByTagNameNS("*", "clrScheme")
        if (clrSchemeNodes.length == 0) return
        val schemeElem = clrSchemeNodes.item(0) as Element

        val tokens = listOf("dk1", "lt1", "dk2", "lt2", "accent1", "accent2", "accent3", "accent4", "accent5", "accent6", "hlink", "folHlink")
        tokens.forEach { token ->
            val colorNodes = schemeElem.getElementsByTagNameNS("*", token)
            if (colorNodes.length > 0) {
                val tokenElem = colorNodes.item(0) as Element
                val color = parseColor(tokenElem, outMap)
                if (color != null) {
                    outMap[token.lowercase()] = color
                }
            }
        }
    }

    private fun parseSlide(
        index: Int,
        root: Element,
        theme: PptxTheme,
        mediaMap: Map<String, ByteArray>,
        slideRels: Map<String, String>,
        slideWidthPt: Float,
        slideHeightPt: Float
    ): PptxSlide {
        var slideTitle = ""
        var background: PptxBackground = PptxBackground.Default
        val elements = mutableListOf<PptxElement>()

        // Check Slide Background: <p:bg>
        val bgNodes = root.getElementsByTagNameNS("*", "bg")
        if (bgNodes.length > 0) {
            val bgElem = bgNodes.item(0) as Element
            background = parseBackground(bgElem, theme, mediaMap, slideRels)
        }

        // Shape tree: <p:spTree>
        val spTreeNodes = root.getElementsByTagNameNS("*", "spTree")
        if (spTreeNodes.length > 0) {
            val spTreeElem = spTreeNodes.item(0) as Element
            val childNodes = spTreeElem.childNodes
            for (i in 0 until childNodes.length) {
                val node = childNodes.item(i)
                if (node.nodeType != Node.ELEMENT_NODE) continue
                val elem = node as Element
                val localName = elem.localName ?: elem.nodeName.substringAfter(":")

                when (localName) {
                    "sp" -> {
                        val shape = parseShape(elem, theme, slideWidthPt, slideHeightPt)
                        if (shape != null) {
                            elements.add(shape)
                            if (slideTitle.isBlank() && isTitlePlaceholder(elem)) {
                                slideTitle = shape.textBody?.fullText?.lines()?.firstOrNull { it.isNotBlank() } ?: ""
                            }
                        }
                    }
                    "pic" -> {
                        val pic = parsePicture(elem, mediaMap, slideRels, slideWidthPt, slideHeightPt)
                        if (pic != null) {
                            elements.add(pic)
                        }
                    }
                    "graphicFrame" -> {
                        val table = parseTableFrame(elem, theme, slideWidthPt, slideHeightPt)
                        if (table != null) {
                            elements.add(table)
                        }
                    }
                    "grpSp" -> {
                        val grp = parseGroupShape(elem, theme, mediaMap, slideRels, slideWidthPt, slideHeightPt)
                        if (grp != null) {
                            elements.add(grp)
                        }
                    }
                }
            }
        }

        // If title still blank, search through elements for prominent heading
        if (slideTitle.isBlank()) {
            for (elem in elements) {
                if (elem is PptxElement.Shape && elem.textBody != null) {
                    val candidate = elem.textBody.fullText.lines().firstOrNull { it.isNotBlank() }
                    if (!candidate.isNullOrBlank() && candidate.length < 80) {
                        slideTitle = candidate
                        break
                    }
                }
            }
        }

        if (slideTitle.isBlank()) {
            slideTitle = "Slide ${index + 1}"
        }

        return PptxSlide(
            index = index,
            title = slideTitle,
            background = background,
            elements = elements
        )
    }

    private fun isTitlePlaceholder(elem: Element): Boolean {
        val phNodes = elem.getElementsByTagNameNS("*", "ph")
        if (phNodes.length > 0) {
            val phElem = phNodes.item(0) as Element
            val type = phElem.getAttribute("type").lowercase()
            return type == "title" || type == "ctrtitle" || type.contains("title")
        }
        val nvSpPrNodes = elem.getElementsByTagNameNS("*", "nvSpPr")
        if (nvSpPrNodes.length > 0) {
            val cNvPrNodes = (nvSpPrNodes.item(0) as Element).getElementsByTagNameNS("*", "cNvPr")
            if (cNvPrNodes.length > 0) {
                val name = (cNvPrNodes.item(0) as Element).getAttribute("name").lowercase()
                return name.contains("title")
            }
        }
        return false
    }

    private fun parseBackground(
        bgElem: Element,
        theme: PptxTheme,
        mediaMap: Map<String, ByteArray>,
        slideRels: Map<String, String>
    ): PptxBackground {
        // Solid fill
        val solidNodes = bgElem.getElementsByTagNameNS("*", "solidFill")
        if (solidNodes.length > 0) {
            val clr = parseColor(solidNodes.item(0) as Element, theme.colors)
            if (clr != null) return PptxBackground.Solid(clr)
        }

        // Gradient fill
        val gradNodes = bgElem.getElementsByTagNameNS("*", "gradFill")
        if (gradNodes.length > 0) {
            val gradElem = gradNodes.item(0) as Element
            val gsNodes = gradElem.getElementsByTagNameNS("*", "gs")
            if (gsNodes.length >= 2) {
                val c1 = parseColor(gsNodes.item(0) as Element, theme.colors) ?: 0xFF0F172A.toInt()
                val c2 = parseColor(gsNodes.item(gsNodes.length - 1) as Element, theme.colors) ?: 0xFF1E293B.toInt()
                return PptxBackground.Gradient(c1, c2, 90f)
            }
        }

        // Blip fill (image)
        val blipNodes = bgElem.getElementsByTagNameNS("*", "blip")
        if (blipNodes.length > 0) {
            val blipElem = blipNodes.item(0) as Element
            val rId = blipElem.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                .ifEmpty { blipElem.getAttribute("r:embed") }
            val target = slideRels[rId] ?: rId
            val bytes = mediaMap[target] ?: mediaMap[target.substringAfterLast("/")]
            if (bytes != null) return PptxBackground.Image(bytes)
        }

        return PptxBackground.Default
    }

    private fun parseShape(
        spElem: Element,
        theme: PptxTheme,
        slideWidthPt: Float,
        slideHeightPt: Float
    ): PptxElement.Shape? {
        val bounds = parseTransform(spElem, slideWidthPt, slideHeightPt) ?: return null

        var shapeType = PptxShapeType.RECT
        var cornerRadiusPt = 0f
        var rotationDeg = 0f

        val prstNodes = spElem.getElementsByTagNameNS("*", "prstGeom")
        if (prstNodes.length > 0) {
            val prstElem = prstNodes.item(0) as Element
            val prst = prstElem.getAttribute("prst").lowercase()
            shapeType = when (prst) {
                "rect" -> PptxShapeType.RECT
                "roundrect" -> {
                    cornerRadiusPt = 10f
                    PptxShapeType.ROUND_RECT
                }
                "ellipse" -> PptxShapeType.ELLIPSE
                "line" -> PptxShapeType.LINE
                "banner", "ribbon", "ribbon2" -> PptxShapeType.BANNER
                "wedgecndroundrectcallout", "roundrectcallout", "callout" -> PptxShapeType.CALLOUT
                "chevron" -> PptxShapeType.CHEVRON
                "star5", "star6" -> PptxShapeType.STAR
                "triangle" -> PptxShapeType.TRIANGLE
                else -> PptxShapeType.RECT
            }
        }

        // Check rotation in xfrm
        val xfrmNodes = spElem.getElementsByTagNameNS("*", "xfrm")
        if (xfrmNodes.length > 0) {
            val xfrm = xfrmNodes.item(0) as Element
            val rot = xfrm.getAttribute("rot").toLongOrNull()
            if (rot != null) {
                rotationDeg = rot.toFloat() / 60000f
            }
        }

        // Fill & Stroke
        val spPrNodes = spElem.getElementsByTagNameNS("*", "spPr")
        var fill: PptxFill = PptxFill.None
        var stroke: PptxStroke? = null

        if (spPrNodes.length > 0) {
            val spPr = spPrNodes.item(0) as Element
            fill = parseFill(spPr, theme)
            stroke = parseStroke(spPr, theme)
        }

        // Text Body
        var textBody: PptxTextBody? = null
        val txBodyNodes = spElem.getElementsByTagNameNS("*", "txBody")
        if (txBodyNodes.length > 0) {
            textBody = parseTextBody(txBodyNodes.item(0) as Element, theme, fill)
        }

        return PptxElement.Shape(
            bounds = bounds,
            shapeType = shapeType,
            fill = fill,
            stroke = stroke,
            cornerRadiusPt = cornerRadiusPt,
            textBody = textBody,
            rotationDeg = rotationDeg
        )
    }

    private fun parsePicture(
        picElem: Element,
        mediaMap: Map<String, ByteArray>,
        slideRels: Map<String, String>,
        slideWidthPt: Float,
        slideHeightPt: Float
    ): PptxElement.Picture? {
        val bounds = parseTransform(picElem, slideWidthPt, slideHeightPt) ?: return null

        val blipNodes = picElem.getElementsByTagNameNS("*", "blip")
        if (blipNodes.length == 0) return null
        val blipElem = blipNodes.item(0) as Element
        val rId = blipElem.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
            .ifEmpty { blipElem.getAttribute("r:embed") }

        val target = slideRels[rId] ?: rId
        val bytes = mediaMap[target]
            ?: mediaMap[target.substringAfterLast("/")]
            ?: mediaMap["media/${target.substringAfterLast("/")}"]
            ?: return null

        return PptxElement.Picture(
            bounds = bounds,
            imageBytes = bytes,
            description = picElem.getAttribute("name")
        )
    }

    private fun parseTableFrame(
        frameElem: Element,
        theme: PptxTheme,
        slideWidthPt: Float,
        slideHeightPt: Float
    ): PptxElement.Table? {
        val bounds = parseTransform(frameElem, slideWidthPt, slideHeightPt) ?: return null

        val tblNodes = frameElem.getElementsByTagNameNS("*", "tbl")
        if (tblNodes.length == 0) return null
        val tblElem = tblNodes.item(0) as Element

        // Column widths
        val colWidths = mutableListOf<Float>()
        val colNodes = tblElem.getElementsByTagNameNS("*", "gridCol")
        for (i in 0 until colNodes.length) {
            val w = (colNodes.item(i) as Element).getAttribute("w").toLongOrNull() ?: (EMU_PER_PT * 100).toLong()
            colWidths.add(w.toFloat() / EMU_PER_PT)
        }

        // Rows
        val rows = mutableListOf<PptxTableRow>()
        val trNodes = tblElem.getElementsByTagNameNS("*", "tr")
        for (r in 0 until trNodes.length) {
            val trElem = trNodes.item(r) as Element
            val h = (trElem.getAttribute("h").toLongOrNull() ?: (EMU_PER_PT * 24).toLong()).toFloat() / EMU_PER_PT
            val isHeader = (r == 0)

            val cells = mutableListOf<PptxTableCell>()
            val tcNodes = trElem.getElementsByTagNameNS("*", "tc")
            for (c in 0 until tcNodes.length) {
                val tcElem = tcNodes.item(c) as Element
                val tcPrNodes = tcElem.getElementsByTagNameNS("*", "tcPr")
                var cellFill: PptxFill = if (isHeader) PptxFill.Solid(0xFF1E293B.toInt()) else if (r % 2 == 1) PptxFill.Solid(0xFFF1F5F9.toInt()) else PptxFill.Solid(Color.WHITE)
                var topBorder = PptxStroke(0xFFCBD5E0.toInt(), 0.75f)
                var bottomBorder = PptxStroke(0xFFCBD5E0.toInt(), 0.75f)

                if (tcPrNodes.length > 0) {
                    val tcPr = tcPrNodes.item(0) as Element
                    val customFill = parseFill(tcPr, theme)
                    if (customFill !is PptxFill.None) cellFill = customFill
                }

                val txBodyNodes = tcElem.getElementsByTagNameNS("*", "txBody")
                val textBody = if (txBodyNodes.length > 0) {
                    parseTextBody(txBodyNodes.item(0) as Element, theme, cellFill)
                } else {
                    PptxTextBody()
                }

                cells.add(
                    PptxTableCell(
                        textBody = textBody,
                        fill = cellFill,
                        borders = PptxCellBorders(top = topBorder, bottom = bottomBorder)
                    )
                )
            }
            rows.add(PptxTableRow(cells = cells, heightPt = h, isHeader = isHeader))
        }

        return PptxElement.Table(
            bounds = bounds,
            rows = rows,
            columnWidthsPt = colWidths
        )
    }

    private fun parseGroupShape(
        grpElem: Element,
        theme: PptxTheme,
        mediaMap: Map<String, ByteArray>,
        slideRels: Map<String, String>,
        slideWidthPt: Float,
        slideHeightPt: Float
    ): PptxElement.Group? {
        val bounds = parseTransform(grpElem, slideWidthPt, slideHeightPt) ?: return null
        val children = mutableListOf<PptxElement>()

        val childNodes = grpElem.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val elem = node as Element
            val name = elem.localName ?: elem.nodeName.substringAfter(":")
            when (name) {
                "sp" -> parseShape(elem, theme, slideWidthPt, slideHeightPt)?.let { children.add(it) }
                "pic" -> parsePicture(elem, mediaMap, slideRels, slideWidthPt, slideHeightPt)?.let { children.add(it) }
                "grpSp" -> parseGroupShape(elem, theme, mediaMap, slideRels, slideWidthPt, slideHeightPt)?.let { children.add(it) }
            }
        }

        return PptxElement.Group(bounds = bounds, children = children)
    }

    private fun parseTransform(elem: Element, slideWidthPt: Float, slideHeightPt: Float): RectF? {
        val xfrmNodes = elem.getElementsByTagNameNS("*", "xfrm")
        if (xfrmNodes.length == 0) return null
        val xfrm = xfrmNodes.item(0) as Element

        val offNodes = xfrm.getElementsByTagNameNS("*", "off")
        val extNodes = xfrm.getElementsByTagNameNS("*", "ext")
        if (offNodes.length == 0 || extNodes.length == 0) return null

        val off = offNodes.item(0) as Element
        val ext = extNodes.item(0) as Element

        val x = (off.getAttribute("x").toLongOrNull() ?: 0L).toFloat() / EMU_PER_PT
        val y = (off.getAttribute("y").toLongOrNull() ?: 0L).toFloat() / EMU_PER_PT
        val cx = (ext.getAttribute("cx").toLongOrNull() ?: (EMU_PER_PT * 100).toLong()).toFloat() / EMU_PER_PT
        val cy = (ext.getAttribute("cy").toLongOrNull() ?: (EMU_PER_PT * 50).toLong()).toFloat() / EMU_PER_PT

        return RectF(x, y, x + cx, y + cy)
    }

    private fun parseFill(parent: Element, theme: PptxTheme): PptxFill {
        val noFillNodes = parent.getElementsByTagNameNS("*", "noFill")
        if (noFillNodes.length > 0) return PptxFill.None

        val solidNodes = parent.getElementsByTagNameNS("*", "solidFill")
        if (solidNodes.length > 0) {
            val color = parseColor(solidNodes.item(0) as Element, theme.colors)
            if (color != null) return PptxFill.Solid(color)
        }

        val gradNodes = parent.getElementsByTagNameNS("*", "gradFill")
        if (gradNodes.length > 0) {
            val gradElem = gradNodes.item(0) as Element
            val gsNodes = gradElem.getElementsByTagNameNS("*", "gs")
            if (gsNodes.length >= 2) {
                val c1 = parseColor(gsNodes.item(0) as Element, theme.colors) ?: 0xFF2563EB.toInt()
                val c2 = parseColor(gsNodes.item(gsNodes.length - 1) as Element, theme.colors) ?: 0xFF1D4ED8.toInt()
                return PptxFill.Gradient(c1, c2, 0f)
            }
        }

        return PptxFill.None
    }

    private fun parseStroke(parent: Element, theme: PptxTheme): PptxStroke? {
        val lnNodes = parent.getElementsByTagNameNS("*", "ln")
        if (lnNodes.length == 0) return null
        val ln = lnNodes.item(0) as Element

        val noFillNodes = ln.getElementsByTagNameNS("*", "noFill")
        if (noFillNodes.length > 0) return null

        val wEmu = ln.getAttribute("w").toLongOrNull() ?: (EMU_PER_PT * 1f).toLong()
        val widthPt = (wEmu.toFloat() / EMU_PER_PT).coerceAtLeast(0.5f)

        val color = parseColor(ln, theme.colors) ?: 0xFFCBD5E0.toInt()
        return PptxStroke(color = color, widthPt = widthPt)
    }

    private fun parseTextBody(txBodyElem: Element, theme: PptxTheme, parentFill: PptxFill): PptxTextBody {
        val bodyPrNodes = txBodyElem.getElementsByTagNameNS("*", "bodyPr")
        var verticalAlign = PptxVerticalAlign.CENTER
        if (bodyPrNodes.length > 0) {
            val bodyPr = bodyPrNodes.item(0) as Element
            verticalAlign = when (bodyPr.getAttribute("anchor").lowercase()) {
                "t" -> PptxVerticalAlign.TOP
                "b" -> PptxVerticalAlign.BOTTOM
                else -> PptxVerticalAlign.CENTER
            }
        }

        // Determine default text color based on parent shape fill
        val defaultTextColor = when (parentFill) {
            is PptxFill.Solid -> {
                val lum = Color.luminance(parentFill.color)
                if (lum < 0.45f) Color.WHITE else 0xFF1E293B.toInt()
            }
            is PptxFill.Gradient -> Color.WHITE
            PptxFill.None -> 0xFF1E293B.toInt()
        }

        val paragraphs = mutableListOf<PptxParagraph>()
        val pNodes = txBodyElem.getElementsByTagNameNS("*", "p")
        for (i in 0 until pNodes.length) {
            val pElem = pNodes.item(i) as Element
            paragraphs.add(parseParagraph(pElem, theme, defaultTextColor))
        }

        return PptxTextBody(
            paragraphs = paragraphs,
            verticalAlign = verticalAlign
        )
    }

    private fun parseParagraph(pElem: Element, theme: PptxTheme, defaultColor: Int): PptxParagraph {
        var alignment = PptxTextAlign.LEFT
        var bulletChar: String? = null
        var bulletColor: Int? = null
        var spaceBeforePt = 0f
        var spaceAfterPt = 2f

        val pPrNodes = pElem.getElementsByTagNameNS("*", "pPr")
        if (pPrNodes.length > 0) {
            val pPr = pPrNodes.item(0) as Element
            alignment = when (pPr.getAttribute("algn").lowercase()) {
                "ctr" -> PptxTextAlign.CENTER
                "r" -> PptxTextAlign.RIGHT
                "just" -> PptxTextAlign.JUSTIFY
                else -> PptxTextAlign.LEFT
            }

            val buCharNodes = pPr.getElementsByTagNameNS("*", "buChar")
            if (buCharNodes.length > 0) {
                bulletChar = (buCharNodes.item(0) as Element).getAttribute("char")
            } else {
                val buAutoNodes = pPr.getElementsByTagNameNS("*", "buAutoNum")
                if (buAutoNodes.length > 0) {
                    bulletChar = "•"
                }
            }

            val buClrNodes = pPr.getElementsByTagNameNS("*", "buClr")
            if (buClrNodes.length > 0) {
                bulletColor = parseColor(buClrNodes.item(0) as Element, theme.colors)
            }
        }

        val runs = mutableListOf<PptxRun>()
        val childNodes = pElem.childNodes
        for (j in 0 until childNodes.length) {
            val node = childNodes.item(j)
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val elem = node as Element
            val name = elem.localName ?: elem.nodeName.substringAfter(":")

            when (name) {
                "r" -> {
                    val rPrNodes = elem.getElementsByTagNameNS("*", "rPr")
                    var fontSizePt = 14f
                    var isBold = false
                    var isItalic = false
                    var isUnderline = false
                    var textColor = defaultColor
                    var fontFamily = "sans-serif"

                    if (rPrNodes.length > 0) {
                        val rPr = rPrNodes.item(0) as Element
                        val sz = rPr.getAttribute("sz").toIntOrNull()
                        if (sz != null && sz > 0) {
                            fontSizePt = sz.toFloat() / 100f
                        }
                        isBold = rPr.getAttribute("b") == "1"
                        isItalic = rPr.getAttribute("i") == "1"
                        isUnderline = rPr.getAttribute("u") == "sng"

                        val parsedClr = parseColor(rPr, theme.colors)
                        if (parsedClr != null) textColor = parsedClr

                        val latinNodes = rPr.getElementsByTagNameNS("*", "latin")
                        if (latinNodes.length > 0) {
                            val face = (latinNodes.item(0) as Element).getAttribute("typeface")
                            if (face.isNotBlank()) fontFamily = face
                        }
                    }

                    val tNodes = elem.getElementsByTagNameNS("*", "t")
                    val text = if (tNodes.length > 0) tNodes.item(0).textContent ?: "" else ""

                    if (text.isNotEmpty()) {
                        runs.add(
                            PptxRun(
                                text = text,
                                fontFamily = fontFamily,
                                fontSizePt = fontSizePt,
                                isBold = isBold,
                                isItalic = isItalic,
                                isUnderline = isUnderline,
                                color = textColor
                            )
                        )
                    }
                }
                "br" -> {
                    runs.add(PptxRun(text = "\n", fontSizePt = 14f, color = defaultColor))
                }
            }
        }

        return PptxParagraph(
            runs = runs,
            alignment = alignment,
            bulletChar = bulletChar,
            bulletColor = bulletColor,
            spaceBeforePt = spaceBeforePt,
            spaceAfterPt = spaceAfterPt
        )
    }

    private fun parseColor(elem: Element, themeColors: Map<String, Int>): Int? {
        val srgbNodes = elem.getElementsByTagNameNS("*", "srgbClr")
        if (srgbNodes.length > 0) {
            val hex = (srgbNodes.item(0) as Element).getAttribute("val")
            try {
                return Color.parseColor("#$hex")
            } catch (_: Exception) {}
        }

        val schemeNodes = elem.getElementsByTagNameNS("*", "schemeClr")
        if (schemeNodes.length > 0) {
            val token = (schemeNodes.item(0) as Element).getAttribute("val").lowercase()
            val clr = themeColors[token]
            if (clr != null) return clr
        }

        val sysNodes = elem.getElementsByTagNameNS("*", "sysClr")
        if (sysNodes.length > 0) {
            val lastClr = (sysNodes.item(0) as Element).getAttribute("lastClr")
            if (lastClr.isNotBlank()) {
                try {
                    return Color.parseColor("#$lastClr")
                } catch (_: Exception) {}
            }
        }

        return null
    }
}
