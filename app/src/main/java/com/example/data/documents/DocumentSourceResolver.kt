package com.example.data.documents

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.data.filesystem.DocumentCacheManager
import com.example.domain.model.DocumentType
import com.example.domain.model.ResolvedDocumentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class DocumentSourceResolver(
    private val context: Context,
    private val cacheManager: DocumentCacheManager
) {
    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun resolve(uri: Uri): ResolvedDocumentSource = withContext(Dispatchers.IO) {
        var displayName = "Document"
        var fileSize = 0L
        var lastModified = System.currentTimeMillis()

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (_: Exception) {
                // Fallback name if provider blocks metadata query
                displayName = uri.lastPathSegment ?: "Document"
            }
        } else if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            if (file.exists()) {
                displayName = file.name
                fileSize = file.length()
                lastModified = file.lastModified()
            }
        }

        val declaredMime = try {
            contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        val extension = DocumentTypeDetector.extractExtension(displayName)
        val docType = DocumentTypeDetector.detectType(uri, contentResolver, declaredMime, displayName)
        val mimeType = declaredMime ?: docType.mimeTypes.firstOrNull() ?: "application/octet-stream"

        ResolvedDocumentSourceImpl(
            context = context,
            cacheManager = cacheManager,
            originalUri = uri,
            displayName = displayName,
            mimeType = mimeType,
            extension = extension,
            fileSize = fileSize,
            lastModified = lastModified,
            documentType = docType
        )
    }

    private class ResolvedDocumentSourceImpl(
        private val context: Context,
        private val cacheManager: DocumentCacheManager,
        override val originalUri: Uri,
        override val displayName: String,
        override val mimeType: String,
        override val extension: String,
        override val fileSize: Long,
        override val lastModified: Long,
        override val documentType: DocumentType
    ) : ResolvedDocumentSource {

        private var activePfd: ParcelFileDescriptor? = null

        override fun openInputStream(): InputStream {
            return context.contentResolver.openInputStream(originalUri)
                ?: throw FileNotFoundException("Unable to open stream for URI: $originalUri")
        }

        override fun openParcelFileDescriptor(mode: String): ParcelFileDescriptor? {
            return try {
                activePfd?.close()
                val pfd = context.contentResolver.openFileDescriptor(originalUri, mode)
                activePfd = pfd
                pfd
            } catch (_: Exception) {
                null
            }
        }

        override suspend fun provideSeekableFile(): File {
            if (originalUri.scheme == "file") {
                val f = File(originalUri.path ?: "")
                if (f.exists() && f.canRead()) return f
            }

            return cacheManager.getOrCreateCachedCopy(
                uri = originalUri,
                expectedExtension = extension,
                expectedSize = fileSize,
                openStream = { openInputStream() }
            )
        }

        override fun release() {
            try {
                activePfd?.close()
            } catch (_: Exception) {}
            activePfd = null
        }
    }
}
