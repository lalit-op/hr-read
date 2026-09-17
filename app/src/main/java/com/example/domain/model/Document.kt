package com.example.domain.model

/**
 * Domain model representing a document file in HR Read.
 *
 * Information is sourced reliably from ContentResolver, MediaStore, or local Room database.
 */
data class Document(
    val id: Long = 0,
    val uri: String,
    val displayName: String,
    val mimeType: String,
    val extension: String,
    val type: DocumentType,
    val fileSize: Long = 0L,
    val modifiedDate: Long = 0L,
    val lastOpenedDate: Long? = null,
    val isFavorite: Boolean = false,
    val pageCount: Int? = null,
    val lastViewedPage: Int = 0,
    val thumbnailPath: String? = null,
    val isSearchable: Boolean? = null
) {
    val formattedFileSize: String
        get() {
            if (fileSize <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(fileSize.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
            return String.format(java.util.Locale.US, "%.1f %s", fileSize / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    val formattedModifiedDate: String
        get() {
            if (modifiedDate <= 0) return "Unknown date"
            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(modifiedDate))
        }
}
