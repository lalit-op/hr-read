package com.example.util

import android.util.Log
import com.example.domain.model.DocumentType

enum class DocumentErrorCategory {
    UNSUPPORTED_FORMAT,
    CORRUPTED_DOCUMENT,
    PASSWORD_PROTECTED,
    MISSING_FILE,
    INACCESSIBLE_URI,
    LOST_PERMISSION,
    RENDERER_FAILURE,
    CONVERSION_FAILURE,
    INSUFFICIENT_MEMORY,
    MISSING_DEPENDENCY,
    INVALID_WORKBOOK,
    INVALID_PRESENTATION,
    GENERIC
}

data class ResolvedUserError(
    val category: DocumentErrorCategory,
    val title: String,
    val message: String,
    val canRetry: Boolean,
    val isAccessLost: Boolean
)

object UserErrorMessageResolver {

    private const val TAG = "HRReadError"

    /**
     * Resolves a throwable or error string into a clean, human-readable user message.
     * Logs technical diagnostics to Android Logcat without exposing them to the UI.
     */
    fun resolve(
        throwable: Throwable?,
        fallbackMessage: String? = null,
        documentType: DocumentType? = null
    ): ResolvedUserError {
        if (throwable != null) {
            safeLogError("Document error encountered: ${throwable.javaClass.simpleName} - ${throwable.message}", throwable)
        } else if (!fallbackMessage.isNullOrBlank()) {
            safeLogError("Document error encountered: $fallbackMessage", null)
        }

        val rawText = buildString {
            if (throwable != null) {
                append(throwable.javaClass.name)
                append(" ")
                append(throwable.message.orEmpty())
                append(" ")
                var cause = throwable.cause
                while (cause != null) {
                    append(cause.javaClass.name)
                    append(" ")
                    append(cause.message.orEmpty())
                    append(" ")
                    cause = cause.cause
                }
            }
            if (!fallbackMessage.isNullOrBlank()) {
                append(" ")
                append(fallbackMessage)
            }
        }.lowercase()

        // 1. Password Protected
        if (rawText.contains("password") || rawText.contains("encrypted") || rawText.contains("passwordrequired")) {
            return ResolvedUserError(
                category = DocumentErrorCategory.PASSWORD_PROTECTED,
                title = "Password Protected Document",
                message = "This document is password protected and cannot be opened.",
                canRetry = true,
                isAccessLost = false
            )
        }

        // 2. Unsupported Format
        if (documentType == DocumentType.UNSUPPORTED ||
            rawText.contains("not supported") ||
            rawText.contains("unsupported format") ||
            rawText.contains("unrecognized format") ||
            rawText.contains("unknown format")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.UNSUPPORTED_FORMAT,
                title = "Format Not Supported",
                message = "This document format is not supported.",
                canRetry = false,
                isAccessLost = false
            )
        }

        // 3. Permission Lost / Access Denied
        if (rawText.contains("securityexception") ||
            rawText.contains("permission denial") ||
            rawText.contains("revoked") ||
            rawText.contains("access denied") ||
            rawText.contains("eacces")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.LOST_PERMISSION,
                title = "Access Permission Lost",
                message = "File access permission was lost. Select the file again to restore access.",
                canRetry = false,
                isAccessLost = true
            )
        }

        // 4. Missing File / Not Found
        if (rawText.contains("filenotfoundexception") ||
            rawText.contains("no such file") ||
            rawText.contains("does not exist") ||
            rawText.contains("not found") ||
            rawText.contains("file is no longer available")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.MISSING_FILE,
                title = "File Not Available",
                message = "File is no longer available.",
                canRetry = false,
                isAccessLost = true
            )
        }

        // 5. Inaccessible URI
        if (rawText.contains("inaccessible") ||
            rawText.contains("cannot open stream") ||
            rawText.contains("unable to open stream")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.INACCESSIBLE_URI,
                title = "Unable to Access File",
                message = "Unable to access this file. Please select the file again.",
                canRetry = false,
                isAccessLost = true
            )
        }

        // 6. Insufficient Memory
        if (rawText.contains("outofmemoryerror") ||
            rawText.contains("out of memory") ||
            rawText.contains("heap limit") ||
            rawText.contains("memory limit")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.INSUFFICIENT_MEMORY,
                title = "Insufficient Memory",
                message = "Insufficient memory to open this document. Please close other applications and try again.",
                canRetry = true,
                isAccessLost = false
            )
        }

        // 7. Missing System Dependency / Library
        if (rawText.contains("noclassdeffounderror") ||
            rawText.contains("unsatisfiedlinkerror") ||
            rawText.contains("missing library") ||
            rawText.contains("native library")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.MISSING_DEPENDENCY,
                title = "Component Unavailable",
                message = "Unable to open this document. A required system component is unavailable.",
                canRetry = true,
                isAccessLost = false
            )
        }

        // 8. Format-specific corrupted or invalid structures
        if (documentType == DocumentType.EXCEL || rawText.contains("workbook") || rawText.contains("spreadsheet") || rawText.contains("sheet")) {
            if (isCorruptedIndicator(rawText)) {
                return ResolvedUserError(
                    category = DocumentErrorCategory.INVALID_WORKBOOK,
                    title = "Unable to Read Spreadsheet",
                    message = "Unable to read this spreadsheet. The workbook file may be damaged or incomplete.",
                    canRetry = true,
                    isAccessLost = false
                )
            }
        }

        if (documentType == DocumentType.POWERPOINT || rawText.contains("presentation") || rawText.contains("slide") || rawText.contains("pptx")) {
            if (isCorruptedIndicator(rawText)) {
                return ResolvedUserError(
                    category = DocumentErrorCategory.INVALID_PRESENTATION,
                    title = "Unable to Read Presentation",
                    message = "Unable to read this presentation. The slide file may be damaged or incomplete.",
                    canRetry = true,
                    isAccessLost = false
                )
            }
        }

        if (documentType == DocumentType.WORD || rawText.contains("docx") || rawText.contains("document.xml")) {
            if (isCorruptedIndicator(rawText)) {
                return ResolvedUserError(
                    category = DocumentErrorCategory.CORRUPTED_DOCUMENT,
                    title = "Unable to Read Word Document",
                    message = "Unable to open this document. The file may be damaged or corrupted.",
                    canRetry = true,
                    isAccessLost = false
                )
            }
        }

        // 9. Generic Corrupted Document
        if (isCorruptedIndicator(rawText)) {
            return ResolvedUserError(
                category = DocumentErrorCategory.CORRUPTED_DOCUMENT,
                title = "Unable to Open Document",
                message = "Unable to open this document. The file may be damaged or corrupted.",
                canRetry = true,
                isAccessLost = false
            )
        }

        // 10. Renderer / Conversion Failure
        if (rawText.contains("renderer failure") ||
            rawText.contains("failed rendering") ||
            rawText.contains("render error") ||
            rawText.contains("conversion failure") ||
            rawText.contains("failed to convert")
        ) {
            return ResolvedUserError(
                category = DocumentErrorCategory.RENDERER_FAILURE,
                title = "Unable to Display Document",
                message = "Unable to display this document. Please try again.",
                canRetry = true,
                isAccessLost = false
            )
        }

        // 11. Generic fallback (safe, non-technical, helpful)
        return ResolvedUserError(
            category = DocumentErrorCategory.GENERIC,
            title = "Unable to Open Document",
            message = "Unable to open this document. Please try again.",
            canRetry = true,
            isAccessLost = false
        )
    }

    private fun isCorruptedIndicator(lowerText: String): Boolean {
        return lowerText.contains("zipexception") ||
                lowerText.contains("eofexception") ||
                lowerText.contains("streamcorruptedexception") ||
                lowerText.contains("corrupt") ||
                lowerText.contains("damaged") ||
                lowerText.contains("malformed") ||
                lowerText.contains("premature end") ||
                lowerText.contains("unexpected end") ||
                lowerText.contains("invalid header") ||
                lowerText.contains("parse error") ||
                lowerText.contains("saxparseexception") ||
                lowerText.contains("xmlpullparserexception")
    }

    /**
     * Sanitizes any arbitrary error string to ensure no technical details,
     * paths, or class names leak to users.
     */
    fun sanitize(message: String?): String {
        if (message.isNullOrBlank()) return "Unable to open this document. Please try again."

        // Check if message looks technical (contains Exception, class path, file path, stack trace)
        if (message.contains("Exception") ||
            message.contains("Error") ||
            message.contains("/") ||
            message.contains("\\") ||
            message.contains("at ") ||
            message.contains("$") ||
            message.contains("content://") ||
            message.contains("file://") ||
            message.contains(".java") ||
            message.contains(".kt")
        ) {
            return resolve(null, fallbackMessage = message).message
        }

        return message.trim().take(120)
    }

    private fun safeLogError(msg: String, throwable: Throwable?) {
        try {
            if (throwable != null) {
                Log.e(TAG, msg, throwable)
            } else {
                Log.w(TAG, msg)
            }
        } catch (_: Throwable) {
            // Shadowed/unmocked in pure JVM tests
        }
    }
}
