package com.example.domain.model

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream

/**
 * Encapsulates a resolved document source, providing access to original URI,
 * stream access, parcel file descriptors, and seekable cached files where needed.
 */
interface ResolvedDocumentSource {
    val originalUri: Uri
    val displayName: String
    val mimeType: String
    val extension: String
    val fileSize: Long
    val lastModified: Long
    val documentType: DocumentType

    /**
     * Opens a fresh [InputStream] for reading content.
     */
    fun openInputStream(): InputStream

    /**
     * Opens a seekable [ParcelFileDescriptor] if supported by ContentResolver or local cache.
     */
    fun openParcelFileDescriptor(mode: String = "r"): ParcelFileDescriptor?

    /**
     * Provides a seekable local [File]. If the source is already a filesystem file,
     * returns it directly; otherwise creates a safe copy in the app-private cache.
     */
    suspend fun provideSeekableFile(): File

    /**
     * Releases any transient resources holding this source open.
     */
    fun release()
}
