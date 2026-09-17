package com.example.renderer.pdf

import android.graphics.RectF
import com.example.renderer.PageDimensions
import com.example.search.SearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.math.max

data class PdfTextLine(
    val text: String,
    val boundingBox: RectF? = null
)

/**
 * High-performance, self-contained PDF text extraction engine.
 *
 * Extracts text content streams directly from PDF object structures,
 * supporting FlateDecode decompression, literal text strings, hex strings,
 * and TJ array kerning operators. Decoupled from visual rendering.
 */
class PdfTextExtractor {

    fun extractPageMetadata(file: File): Pair<Int, List<PageDimensions>> {
        try {
            val bytes = file.readBytes()
            val content = String(bytes, Charsets.ISO_8859_1)

            // Count /Type /Page (excluding /Pages)
            val pageMatches = Regex("/Type\\s*/Page\\b").findAll(content).toList()
            var count = pageMatches.size

            if (count == 0) {
                val countMatch = Regex("/Type\\s*/Pages.*?/Count\\s+(\\d+)", RegexOption.DOT_MATCHES_ALL).find(content)
                count = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }

            val dimensions = mutableListOf<PageDimensions>()
            val mediaBoxRegex = Regex("/MediaBox\\s*\\[\\s*([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s*\\]")
            val mediaBoxMatches = mediaBoxRegex.findAll(content).toList()

            for (i in 0 until count) {
                val mbMatch = mediaBoxMatches.getOrNull(i) ?: mediaBoxMatches.lastOrNull()
                if (mbMatch != null) {
                    val x1 = mbMatch.groupValues[1].toFloatOrNull() ?: 0f
                    val y1 = mbMatch.groupValues[2].toFloatOrNull() ?: 0f
                    val x2 = mbMatch.groupValues[3].toFloatOrNull() ?: 595f
                    val y2 = mbMatch.groupValues[4].toFloatOrNull() ?: 842f
                    val w = kotlin.math.abs(x2 - x1).coerceAtLeast(1f)
                    val h = kotlin.math.abs(y2 - y1).coerceAtLeast(1f)
                    dimensions.add(PageDimensions(w, h))
                } else {
                    dimensions.add(PageDimensions(595f, 842f))
                }
            }

            return Pair(count, dimensions)
        } catch (_: Exception) {
            return Pair(0, emptyList())
        }
    }

    suspend fun extractAllPages(file: File): Map<Int, String> = withContext(Dispatchers.IO) {
        val pageTexts = mutableMapOf<Int, String>()
        try {
            val bytes = file.readBytes()
            extractFromBytes(bytes, pageTexts)
        } catch (_: Exception) {
            // Graceful fallback if file cannot be read
        }
        pageTexts
    }

    suspend fun extractAllPages(inputStream: InputStream): Map<Int, String> = withContext(Dispatchers.IO) {
        val pageTexts = mutableMapOf<Int, String>()
        try {
            val bytes = inputStream.readBytes()
            extractFromBytes(bytes, pageTexts)
        } catch (_: Exception) {
            // Graceful fallback
        }
        pageTexts
    }

    private fun extractFromBytes(bytes: ByteArray, pageTexts: MutableMap<Int, String>) {
        val content = String(bytes, Charsets.ISO_8859_1)

        // Find all Page objects: /Type\s*/Page\b (excluding /Pages)
        val pageRegex = Regex("/Type\\s*/Page\\b")
        val pageMatches = pageRegex.findAll(content).toList()

        if (pageMatches.isEmpty()) {
            // Fallback: search all stream ... endstream blocks in the entire PDF
            val allStreamsText = extractTextFromStreams(bytes)
            if (allStreamsText.isNotBlank()) {
                pageTexts[0] = allStreamsText
            }
            return
        }

        // For each page object, identify its /Contents object reference(s)
        var pageIndex = 0
        for (match in pageMatches) {
            val pageObjStart = content.lastIndexOf("obj", match.range.first)
            val pageObjEnd = content.indexOf("endobj", match.range.last)

            if (pageObjStart != -1 && pageObjEnd != -1 && pageObjEnd > pageObjStart) {
                val pageObjBody = content.substring(pageObjStart, pageObjEnd)
                val contentsRegex = Regex("/Contents\\s+(\\d+)\\s+(\\d+)\\s+R")
                val contentsMatch = contentsRegex.find(pageObjBody)

                val pageSb = StringBuilder()
                if (contentsMatch != null) {
                    val objNum = contentsMatch.groupValues[1]
                    val streamText = extractObjectStreamText(content, bytes, objNum)
                    pageSb.append(streamText)
                } else {
                    // Check for multiple /Contents [ 1 0 R 2 0 R ]
                    val arrayRegex = Regex("/Contents\\s*\\[([^\\]]+)\\]")
                    val arrayMatch = arrayRegex.find(pageObjBody)
                    if (arrayMatch != null) {
                        val refs = Regex("(\\d+)\\s+\\d+\\s+R").findAll(arrayMatch.groupValues[1])
                        for (ref in refs) {
                            val streamText = extractObjectStreamText(content, bytes, ref.groupValues[1])
                            if (streamText.isNotEmpty()) {
                                if (pageSb.isNotEmpty()) pageSb.append("\n")
                                pageSb.append(streamText)
                            }
                        }
                    } else {
                        // Check if stream is inline inside the page object itself
                        val inlineStream = extractStreamFromSlice(bytes, pageObjStart, pageObjEnd)
                        if (inlineStream != null) {
                            pageSb.append(parseOperators(inlineStream))
                        }
                    }
                }

                pageTexts[pageIndex] = pageSb.toString()
                pageIndex++
            }
        }

        // If page object traversal yielded empty text, fallback to extracting all content streams
        if (pageTexts.values.all { it.isBlank() }) {
            val allText = extractTextFromStreams(bytes)
            if (allText.isNotBlank()) {
                pageTexts[0] = allText
            }
        }
    }

    private fun extractObjectStreamText(content: String, bytes: ByteArray, objNum: String): String {
        val targetObjRegex = Regex("\\b${objNum}\\s+\\d+\\s+obj\\b")
        val match = targetObjRegex.find(content) ?: return ""

        val objStart = match.range.first
        val objEnd = content.indexOf("endobj", objStart)
        if (objEnd == -1) return ""

        val streamBytes = extractStreamFromSlice(bytes, objStart, objEnd) ?: return ""
        return parseOperators(streamBytes)
    }

    private fun extractStreamFromSlice(bytes: ByteArray, startOffset: Int, endOffset: Int): ByteArray? {
        val slice = bytes.copyOfRange(startOffset, endOffset)
        val sliceStr = String(slice, Charsets.ISO_8859_1)

        val streamIdx = sliceStr.indexOf("stream")
        val endStreamIdx = sliceStr.indexOf("endstream")

        if (streamIdx == -1 || endStreamIdx == -1 || endStreamIdx <= streamIdx) return null

        // Stream binary data begins after "stream\r\n" or "stream\n"
        var dataStart = streamIdx + 6
        if (dataStart < slice.size && slice[dataStart] == '\r'.code.toByte()) dataStart++
        if (dataStart < slice.size && slice[dataStart] == '\n'.code.toByte()) dataStart++

        val dataEnd = endStreamIdx
        if (dataStart >= dataEnd || dataStart >= slice.size) return null

        val streamData = slice.copyOfRange(dataStart, dataEnd)
        val isFlate = sliceStr.substring(0, streamIdx).contains("/FlateDecode")

        return if (isFlate) {
            decompressFlate(streamData) ?: streamData
        } else {
            streamData
        }
    }

    private fun extractTextFromStreams(bytes: ByteArray): String {
        val sb = StringBuilder()
        val str = String(bytes, Charsets.ISO_8859_1)
        var searchFrom = 0

        while (true) {
            val streamIdx = str.indexOf("stream", searchFrom)
            if (streamIdx == -1) break
            val endStreamIdx = str.indexOf("endstream", streamIdx)
            if (endStreamIdx == -1) break

            var dataStart = streamIdx + 6
            if (dataStart < bytes.size && bytes[dataStart] == '\r'.code.toByte()) dataStart++
            if (dataStart < bytes.size && bytes[dataStart] == '\n'.code.toByte()) dataStart++

            if (dataStart < endStreamIdx) {
                val streamData = bytes.copyOfRange(dataStart, endStreamIdx)
                val header = str.substring((streamIdx - 150).coerceAtLeast(0), streamIdx)
                val decompressed = if (header.contains("/FlateDecode")) {
                    decompressFlate(streamData) ?: streamData
                } else {
                    streamData
                }

                val text = parseOperators(decompressed)
                if (text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(text)
                }
            }

            searchFrom = endStreamIdx + 9
        }

        return sb.toString()
    }

    private fun decompressFlate(data: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater(false)
            val buffer = ByteArray(4096)
            val out = ByteArrayOutputStream()
            InflaterInputStream(ByteArrayInputStream(data), inflater).use { iis ->
                var read: Int
                while (iis.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
            }
            out.toByteArray()
        } catch (_: Exception) {
            try {
                // Retry with nowrap = true for raw deflate streams without zlib headers
                val inflater = Inflater(true)
                val buffer = ByteArray(4096)
                val out = ByteArrayOutputStream()
                InflaterInputStream(ByteArrayInputStream(data), inflater).use { iis ->
                    var read: Int
                    while (iis.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
                out.toByteArray()
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Parses decompressed PDF operator instructions between BT and ET.
     */
    private fun parseOperators(streamBytes: ByteArray): String {
        val streamStr = String(streamBytes, Charsets.UTF_8)
        val sb = StringBuilder()

        val btBlocks = Regex("BT[\\s\\S]*?ET").findAll(streamStr)
        for (block in btBlocks) {
            val text = extractStringsFromBlock(block.value)
            if (text.isNotBlank()) {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(text)
            }
        }

        return sb.toString().trim()
    }

    private fun extractStringsFromBlock(block: String): String {
        val sb = StringBuilder()
        var i = 0
        val len = block.length

        while (i < len) {
            val ch = block[i]
            if (ch == '(') {
                // Literal string
                val str = StringBuilder()
                var depth = 1
                i++
                while (i < len && depth > 0) {
                    val c = block[i]
                    if (c == '\\' && i + 1 < len) {
                        val next = block[i + 1]
                        when (next) {
                            'n' -> str.append('\n')
                            'r' -> str.append('\r')
                            't' -> str.append('\t')
                            '(' -> str.append('(')
                            ')' -> str.append(')')
                            '\\' -> str.append('\\')
                            else -> str.append(next)
                        }
                        i += 2
                    } else if (c == '(') {
                        depth++
                        str.append(c)
                        i++
                    } else if (c == ')') {
                        depth--
                        if (depth > 0) str.append(c)
                        i++
                    } else {
                        str.append(c)
                        i++
                    }
                }
                val cleaned = str.toString().trim()
                if (cleaned.isNotEmpty()) {
                    if (sb.isNotEmpty() && !sb.endsWith(" ") && !sb.endsWith("\n")) {
                        sb.append(" ")
                    }
                    sb.append(cleaned)
                }
            } else if (ch == '<' && i + 1 < len && block[i + 1] != '<') {
                // Hex string
                val hex = StringBuilder()
                i++
                while (i < len && block[i] != '>') {
                    if (!block[i].isWhitespace()) hex.append(block[i])
                    i++
                }
                if (i < len) i++ // skip '>'
                val decoded = decodeHex(hex.toString())
                if (decoded.isNotBlank()) {
                    if (sb.isNotEmpty() && !sb.endsWith(" ")) sb.append(" ")
                    sb.append(decoded)
                }
            } else if (ch == 'T' && i + 1 < len && (block[i + 1] == '*' || block[i + 1] == 'd' || block[i + 1] == 'D')) {
                // Newline operator
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                i += 2
            } else {
                i++
            }
        }

        return sb.toString().trim()
    }

    private fun decodeHex(hex: String): String {
        return try {
            val bytes = ByteArray(hex.length / 2)
            for (i in bytes.indices) {
                val index = i * 2
                val v = hex.substring(index, index + 2).toInt(16)
                bytes[i] = v.toByte()
            }
            String(bytes, Charsets.UTF_8).filter { it.code in 32..126 || it == '\n' || it == '\t' }
        } catch (_: Exception) {
            ""
        }
    }

    data class ExtractionResult(
        val pageTexts: Map<Int, String>,
        val pageLines: Map<Int, List<PdfTextLine>>
    )

    suspend fun extractAllPagesWithLines(file: File): ExtractionResult = withContext(Dispatchers.IO) {
        val pageTexts = mutableMapOf<Int, String>()
        val pageLines = mutableMapOf<Int, List<PdfTextLine>>()
        try {
            val bytes = file.readBytes()
            extractFromBytesWithLines(bytes, pageTexts, pageLines)
        } catch (_: Exception) {
        }
        ExtractionResult(pageTexts, pageLines)
    }

    private fun extractFromBytesWithLines(
        bytes: ByteArray,
        pageTexts: MutableMap<Int, String>,
        pageLines: MutableMap<Int, List<PdfTextLine>>
    ) {
        val content = String(bytes, Charsets.ISO_8859_1)
        val pageRegex = Regex("/Type\\s*/Page\\b")
        val pageMatches = pageRegex.findAll(content).toList()

        if (pageMatches.isEmpty()) {
            val allStreamsText = extractTextFromStreams(bytes)
            if (allStreamsText.isNotBlank()) {
                pageTexts[0] = allStreamsText
            }
            return
        }

        var pageIndex = 0
        for (match in pageMatches) {
            val pageObjStart = content.lastIndexOf("obj", match.range.first)
            val pageObjEnd = content.indexOf("endobj", match.range.last)

            if (pageObjStart != -1 && pageObjEnd != -1 && pageObjEnd > pageObjStart) {
                val pageObjBody = content.substring(pageObjStart, pageObjEnd)
                val (pageWidth, pageHeight) = parseMediaBox(pageObjBody)

                val contentsRegex = Regex("/Contents\\s+(\\d+)\\s+(\\d+)\\s+R")
                val contentsMatch = contentsRegex.find(pageObjBody)

                val pageSb = StringBuilder()
                val linesList = mutableListOf<PdfTextLine>()

                if (contentsMatch != null) {
                    val objNum = contentsMatch.groupValues[1]
                    val streamBytes = extractObjectStreamBytes(content, bytes, objNum)
                    if (streamBytes != null) {
                        pageSb.append(parseOperators(streamBytes))
                        linesList.addAll(parseOperatorsWithCoordinates(streamBytes, pageWidth, pageHeight))
                    }
                } else {
                    val arrayRegex = Regex("/Contents\\s*\\[([^\\]]+)\\]")
                    val arrayMatch = arrayRegex.find(pageObjBody)
                    if (arrayMatch != null) {
                        val refs = Regex("(\\d+)\\s+\\d+\\s+R").findAll(arrayMatch.groupValues[1])
                        for (ref in refs) {
                            val streamBytes = extractObjectStreamBytes(content, bytes, ref.groupValues[1])
                            if (streamBytes != null) {
                                val txt = parseOperators(streamBytes)
                                if (txt.isNotEmpty()) {
                                    if (pageSb.isNotEmpty()) pageSb.append("\n")
                                    pageSb.append(txt)
                                }
                                linesList.addAll(parseOperatorsWithCoordinates(streamBytes, pageWidth, pageHeight))
                            }
                        }
                    } else {
                        val inlineStream = extractStreamFromSlice(bytes, pageObjStart, pageObjEnd)
                        if (inlineStream != null) {
                            pageSb.append(parseOperators(inlineStream))
                            linesList.addAll(parseOperatorsWithCoordinates(inlineStream, pageWidth, pageHeight))
                        }
                    }
                }

                pageTexts[pageIndex] = pageSb.toString()
                pageLines[pageIndex] = linesList
                pageIndex++
            }
        }
    }

    private fun parseMediaBox(pageObjBody: String): Pair<Float, Float> {
        val mbRegex = Regex("/MediaBox\\s*\\[\\s*([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s*\\]")
        val match = mbRegex.find(pageObjBody) ?: return Pair(595f, 842f)
        val w = match.groupValues[3].toFloatOrNull() ?: 595f
        val h = match.groupValues[4].toFloatOrNull() ?: 842f
        return Pair(w, h)
    }

    private fun extractObjectStreamBytes(content: String, bytes: ByteArray, objNum: String): ByteArray? {
        val targetObjRegex = Regex("\\b${objNum}\\s+\\d+\\s+obj\\b")
        val match = targetObjRegex.find(content) ?: return null
        val objStart = match.range.first
        val objEnd = content.indexOf("endobj", objStart)
        if (objEnd == -1) return null
        return extractStreamFromSlice(bytes, objStart, objEnd)
    }

    fun parseOperatorsWithCoordinates(
        streamBytes: ByteArray,
        pageWidth: Float,
        pageHeight: Float
    ): List<PdfTextLine> {
        val streamStr = String(streamBytes, Charsets.UTF_8)
        val lines = mutableListOf<PdfTextLine>()

        val btBlocks = Regex("BT[\\s\\S]*?ET").findAll(streamStr)
        for (block in btBlocks) {
            val blockStr = block.value
            var curX = 0f
            var curY = 0f
            var curFontSize = 12f

            val stmts = blockStr.split("\n", "\r")
            for (rawStmt in stmts) {
                val stmt = rawStmt.trim()
                if (stmt.isEmpty()) continue

                val tfMatch = Regex("/\\w+\\s+([\\d\\.]+)\\s+Tf").find(stmt)
                if (tfMatch != null) {
                    curFontSize = tfMatch.groupValues[1].toFloatOrNull() ?: curFontSize
                }

                val tmMatch = Regex("([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+Tm").find(stmt)
                if (tmMatch != null) {
                    curX = tmMatch.groupValues[5].toFloatOrNull() ?: curX
                    curY = tmMatch.groupValues[6].toFloatOrNull() ?: curY
                }

                val tdMatch = Regex("([\\d\\.-]+)\\s+([\\d\\.-]+)\\s+T[dD]").find(stmt)
                if (tdMatch != null) {
                    val dx = tdMatch.groupValues[1].toFloatOrNull() ?: 0f
                    val dy = tdMatch.groupValues[2].toFloatOrNull() ?: 0f
                    curX += dx
                    curY += dy
                }

                val tjIdx = stmt.indexOf("Tj")
                if (tjIdx != -1) {
                    val strParenStart = stmt.indexOf('(')
                    val strParenEnd = stmt.lastIndexOf(')')
                    if (strParenStart != -1 && strParenEnd > strParenStart) {
                        val text = stmt.substring(strParenStart + 1, strParenEnd)
                            .replace("\\(", "(")
                            .replace("\\)", ")")
                            .replace("\\n", " ")
                            .trim()

                        if (text.isNotEmpty() && pageWidth > 0 && pageHeight > 0) {
                            val estWidth = (text.length * curFontSize * 0.52f).coerceAtLeast(10f)
                            val normLeft = (curX / pageWidth).coerceIn(0f, 1f)
                            val normRight = ((curX + estWidth) / pageWidth).coerceIn(normLeft, 1f)
                            val normTop = ((pageHeight - (curY + curFontSize)) / pageHeight).coerceIn(0f, 1f)
                            val normBottom = ((pageHeight - curY) / pageHeight).coerceIn(normTop, 1f)

                            val rect = RectF(normLeft, normTop, normRight, normBottom)
                            lines.add(PdfTextLine(text = text, boundingBox = rect))
                            curX += estWidth
                        }
                    }
                }
            }
        }

        return lines
    }

    fun searchMatches(
        pageTexts: Map<Int, String>,
        query: String,
        pageLines: Map<Int, List<PdfTextLine>> = emptyMap()
    ): List<SearchMatch> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchMatch>()
        val trimmedQuery = query.trim()

        for ((pageIndex, text) in pageTexts) {
            if (text.isBlank()) continue
            val linesForPage = pageLines[pageIndex] ?: emptyList()

            if (linesForPage.isNotEmpty()) {
                for (line in linesForPage) {
                    val matchIdx = line.text.indexOf(trimmedQuery, ignoreCase = true)
                    if (matchIdx != -1) {
                        val snippetStart = (matchIdx - 30).coerceAtLeast(0)
                        val snippetEnd = (matchIdx + trimmedQuery.length + 30).coerceAtMost(line.text.length)
                        val prefix = if (snippetStart > 0) "..." else ""
                        val suffix = if (snippetEnd < line.text.length) "..." else ""
                        val snippet = prefix + line.text.substring(snippetStart, snippetEnd).trim() + suffix

                        val matchBoxes = if (line.boundingBox != null) {
                            val lineBox = line.boundingBox
                            val totalLen = max(1, line.text.length)
                            val charWidth = (lineBox.right - lineBox.left) / totalLen
                            val mLeft = (lineBox.left + matchIdx * charWidth).coerceIn(0f, 1f)
                            val mRight = (mLeft + trimmedQuery.length * charWidth).coerceIn(mLeft, 1f)
                            listOf(RectF(mLeft, lineBox.top, mRight, lineBox.bottom))
                        } else {
                            emptyList()
                        }

                        results.add(
                            SearchMatch(
                                pageIndex = pageIndex,
                                lineText = line.text.trim(),
                                snippet = snippet,
                                boundingBoxes = matchBoxes,
                                contextInfo = "Page ${pageIndex + 1}"
                            )
                        )
                    }
                }
            } else {
                val lines = text.lines()
                for (line in lines) {
                    val matchIdx = line.indexOf(trimmedQuery, ignoreCase = true)
                    if (matchIdx != -1) {
                        val snippetStart = (matchIdx - 30).coerceAtLeast(0)
                        val snippetEnd = (matchIdx + trimmedQuery.length + 30).coerceAtMost(line.length)
                        val prefix = if (snippetStart > 0) "..." else ""
                        val suffix = if (snippetEnd < line.length) "..." else ""
                        val snippet = prefix + line.substring(snippetStart, snippetEnd).trim() + suffix

                        results.add(
                            SearchMatch(
                                pageIndex = pageIndex,
                                lineText = line.trim(),
                                snippet = snippet,
                                boundingBoxes = emptyList(),
                                contextInfo = "Page ${pageIndex + 1}"
                            )
                        )
                    }
                }
            }
        }

        return results
    }
}
