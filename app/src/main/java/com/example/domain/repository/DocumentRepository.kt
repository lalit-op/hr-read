package com.example.domain.repository

import android.net.Uri
import com.example.domain.model.Document
import com.example.domain.model.DocumentType
import com.example.domain.model.ReaderSettings
import com.example.domain.model.ResolvedDocumentSource
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun getRecentDocuments(): Flow<List<Document>>
    fun getFavoriteDocuments(): Flow<List<Document>>
    fun getDocumentsByType(type: DocumentType): Flow<List<Document>>
    fun getAllDocuments(): Flow<List<Document>>
    fun getDocumentByUri(uri: String): Flow<Document?>

    suspend fun recordDocumentOpened(uri: Uri): Document
    suspend fun updatePageProgress(uri: String, lastPage: Int, totalPages: Int?)
    suspend fun toggleFavorite(uri: String, isFavorite: Boolean)
    suspend fun removeRecentDocument(uri: String)
    suspend fun clearAllRecent()

    suspend fun resolveDocumentSource(uri: Uri): ResolvedDocumentSource
    suspend fun persistUriPermission(uri: Uri)

    suspend fun syncDeviceDocuments(type: DocumentType? = null)
    suspend fun indexDiscoveredDocuments(documents: List<Document>)

    fun scanDeviceDocuments(type: DocumentType? = null): Flow<List<Document>>

    fun getReaderSettings(): Flow<ReaderSettings>
    suspend fun updateReaderSettings(settings: ReaderSettings)

    suspend fun getCacheSizeBytes(): Long
    suspend fun clearCache()
}
