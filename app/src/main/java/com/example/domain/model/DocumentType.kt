package com.example.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Supported document types and formats in HR Read.
 */
enum class DocumentType(
    val displayName: String,
    val primaryExtension: String,
    val extensions: Set<String>,
    val mimeTypes: Set<String>,
    val accentColor: Color
) {
    PDF(
        displayName = "PDF",
        primaryExtension = "pdf",
        extensions = setOf("pdf"),
        mimeTypes = setOf("application/pdf"),
        accentColor = Color(0xFFE53935) // Deep Red
    ),
    WORD(
        displayName = "Word",
        primaryExtension = "docx",
        extensions = setOf("doc", "docx", "dot", "dotx"),
        mimeTypes = setOf(
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.template"
        ),
        accentColor = Color(0xFF1E88E5) // Vibrant Blue
    ),
    POWERPOINT(
        displayName = "PowerPoint",
        primaryExtension = "pptx",
        extensions = setOf("ppt", "pptx", "pot", "potx", "pps", "ppsx"),
        mimeTypes = setOf(
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.presentationml.slideshow"
        ),
        accentColor = Color(0xFFFB8C00) // Deep Amber / Orange
    ),
    EXCEL(
        displayName = "Excel",
        primaryExtension = "xlsx",
        extensions = setOf("xls", "xlsx", "xlt", "xltx", "csv"),
        mimeTypes = setOf(
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
            "text/csv"
        ),
        accentColor = Color(0xFF43A047) // Emerald Green
    ),
    TEXT(
        displayName = "Text",
        primaryExtension = "txt",
        extensions = setOf("txt", "log", "json", "xml", "md", "csv", "ini", "conf"),
        mimeTypes = setOf("text/plain", "text/markdown", "application/json", "application/xml"),
        accentColor = Color(0xFF546E7A) // Slate Grey
    ),
    IMAGE(
        displayName = "Images",
        primaryExtension = "jpg",
        extensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif"),
        mimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/bmp", "image/gif"),
        accentColor = Color(0xFF8E24AA) // Purple
    ),
    UNSUPPORTED(
        displayName = "Other",
        primaryExtension = "",
        extensions = emptySet(),
        mimeTypes = emptySet(),
        accentColor = Color(0xFF78909C)
    );

    companion object {
        val primaryCategories: List<DocumentType>
            get() = listOf(
                PDF,
                WORD,
                POWERPOINT,
                EXCEL,
                TEXT,
                IMAGE
            )
    }
}
