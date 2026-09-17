package com.example.data.documents

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.domain.model.DocumentType
import java.io.InputStream
import java.util.Locale

object DocumentTypeDetector {

    // Magic numbers for file signatures
    private val PDF_MAGIC = byteArrayOf(0x25.toByte(), 0x50.toByte(), 0x44.toByte(), 0x46.toByte()) // %PDF
    private val ZIP_MAGIC = byteArrayOf(0x50.toByte(), 0x4B.toByte(), 0x03.toByte(), 0x04.toByte()) // PK.. (DOCX, PPTX, XLSX)
    private val OLE_MAGIC = byteArrayOf(
        0xD0.toByte(), 0xCF.toByte(), 0x11.toByte(), 0xE0.toByte(),
        0xA1.toByte(), 0xB1.toByte(), 0x1A.toByte(), 0xE1.toByte()
    ) // Legacy DOC, XLS, PPT
    private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()) // .PNG
    private val JPEG_MAGIC = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) // JPEG SOI

    /**
     * Determines the [DocumentType] using MIME type, file extension, and magic header bytes if necessary.
     */
    fun detectType(
        uri: Uri,
        contentResolver: ContentResolver?,
        declaredMimeType: String?,
        fileName: String?
    ): DocumentType {
        val ext = extractExtension(fileName ?: uri.lastPathSegment.orEmpty()).lowercase(Locale.ROOT)
        val resolvedMime = declaredMimeType?.lowercase(Locale.ROOT)
            ?: contentResolver?.getType(uri)?.lowercase(Locale.ROOT)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.lowercase(Locale.ROOT)

        // 1. Direct extension matching when distinct
        when (ext) {
            "pdf" -> return DocumentType.PDF
            "doc", "docx", "dot", "dotx" -> return DocumentType.WORD
            "ppt", "pptx", "pot", "potx", "pps", "ppsx" -> return DocumentType.POWERPOINT
            "xls", "xlsx", "xlt", "xltx" -> return DocumentType.EXCEL
            "txt", "log", "json", "xml", "md", "ini", "conf" -> return DocumentType.TEXT
            "jpg", "jpeg", "png", "webp", "bmp", "gif" -> return DocumentType.IMAGE
        }

        // 2. MIME type evaluation
        if (resolvedMime != null) {
            when {
                resolvedMime == "application/pdf" -> return DocumentType.PDF
                resolvedMime.contains("wordprocessingml") || resolvedMime == "application/msword" -> return DocumentType.WORD
                resolvedMime.contains("presentationml") || resolvedMime == "application/vnd.ms-powerpoint" -> return DocumentType.POWERPOINT
                resolvedMime.contains("spreadsheetml") || resolvedMime == "application/vnd.ms-excel" -> return DocumentType.EXCEL
                resolvedMime.startsWith("text/") || resolvedMime == "application/json" || resolvedMime == "application/xml" -> return DocumentType.TEXT
                resolvedMime.startsWith("image/") -> return DocumentType.IMAGE
            }
        }

        // 3. Fallback: inspect file signature / magic bytes if stream is openable
        if (contentResolver != null) {
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val magic = readHeaderBytes(stream, 8)
                    val detectedFromSignature = detectFromMagicBytes(magic, ext)
                    if (detectedFromSignature != DocumentType.UNSUPPORTED) {
                        return detectedFromSignature
                    }
                }
            } catch (_: Exception) {
                // Stream couldn't be read for signature
            }
        }

        return DocumentType.UNSUPPORTED
    }

    fun extractExtension(name: String): String {
        val lastDot = name.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < name.length - 1) {
            name.substring(lastDot + 1).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    private fun readHeaderBytes(stream: InputStream, count: Int): ByteArray {
        val buffer = ByteArray(count)
        var totalRead = 0
        while (totalRead < count) {
            val read = stream.read(buffer, totalRead, count - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return buffer
    }

    private fun detectFromMagicBytes(bytes: ByteArray, extensionHint: String): DocumentType {
        if (bytes.size >= 4 && bytes.take(4).toByteArray().contentEquals(PDF_MAGIC)) {
            return DocumentType.PDF
        }
        if (bytes.size >= 4 && bytes.take(4).toByteArray().contentEquals(PNG_MAGIC)) {
            return DocumentType.IMAGE
        }
        if (bytes.size >= 3 && bytes.take(3).toByteArray().contentEquals(JPEG_MAGIC)) {
            return DocumentType.IMAGE
        }

        // ZIP-based OpenXML documents (.docx, .pptx, .xlsx)
        if (bytes.size >= 4 && bytes.take(4).toByteArray().contentEquals(ZIP_MAGIC)) {
            return when (extensionHint) {
                "docx", "docm" -> DocumentType.WORD
                "pptx", "pptm" -> DocumentType.POWERPOINT
                "xlsx", "xlsm" -> DocumentType.EXCEL
                else -> DocumentType.WORD // Default to Word if unknown ZIP-based document
            }
        }

        // OLE2 Compound Document binary (.doc, .xls, .ppt)
        if (bytes.size >= 8 && bytes.contentEquals(OLE_MAGIC)) {
            return when (extensionHint) {
                "doc" -> DocumentType.WORD
                "ppt" -> DocumentType.POWERPOINT
                "xls" -> DocumentType.EXCEL
                else -> DocumentType.WORD
            }
        }

        // Plain text check: verify ASCII / UTF-8 printable bytes without null terminators
        if (bytes.isNotEmpty() && bytes.none { it == 0.toByte() }) {
            return DocumentType.TEXT
        }

        return DocumentType.UNSUPPORTED
    }
}
