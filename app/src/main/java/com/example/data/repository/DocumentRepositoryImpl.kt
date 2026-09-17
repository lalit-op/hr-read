package com.example.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.database.DocumentDao
import com.example.data.database.DocumentEntity
import com.example.data.documents.DocumentSourceResolver
import com.example.data.documents.MediaStoreDocumentScanner
import com.example.data.filesystem.DocumentCacheManager
import com.example.domain.model.AppThemeSetting
import com.example.domain.model.DefaultPageFitting
import com.example.domain.model.DefaultReaderMode
import com.example.domain.model.Document
import com.example.domain.model.DocumentType
import com.example.domain.model.ReaderSettings
import com.example.domain.model.ResolvedDocumentSource
import com.example.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DocumentRepositoryImpl(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val sourceResolver: DocumentSourceResolver,
    private val mediaStoreScanner: MediaStoreDocumentScanner,
    private val cacheManager: DocumentCacheManager
) : DocumentRepository {

    private val prefs = context.getSharedPreferences("hr_reader_settings", Context.MODE_PRIVATE)
    private val _settingsFlow = MutableStateFlow(readSettings())

    override fun getRecentDocuments(): Flow<List<Document>> {
        return documentDao.getAllRecentDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getFavoriteDocuments(): Flow<List<Document>> {
        return documentDao.getFavoriteDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDocumentsByType(type: DocumentType): Flow<List<Document>> {
        return documentDao.getDocumentsByType(type.name).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllDocuments(): Flow<List<Document>> {
        return documentDao.getAllDocuments().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDocumentByUri(uri: String): Flow<Document?> {
        return documentDao.getDocumentByUri(uri).map { it?.toDomain() }
    }

    override suspend fun persistUriPermission(uri: Uri) = withContext(Dispatchers.IO) {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: SecurityException) {
                // Non-persistable content provider or temporary grant
            } catch (_: Exception) {
                // Other provider exceptions
            }
        }
    }

    override suspend fun recordDocumentOpened(uri: Uri): Document = withContext(Dispatchers.IO) {
        // Persist permission so the document remains accessible after restart/recreation
        persistUriPermission(uri)

        val resolved = sourceResolver.resolve(uri)
        val now = System.currentTimeMillis()

        val existing = documentDao.findByUri(uri.toString())
        val entityToSave = if (existing != null) {
            existing.copy(
                displayName = resolved.displayName,
                mimeType = resolved.mimeType,
                extension = resolved.extension,
                fileSize = if (resolved.fileSize > 0) resolved.fileSize else existing.fileSize,
                modifiedDate = if (resolved.lastModified > 0) resolved.lastModified else existing.modifiedDate,
                lastOpenedDate = now
            )
        } else {
            DocumentEntity(
                uri = uri.toString(),
                displayName = resolved.displayName,
                mimeType = resolved.mimeType,
                extension = resolved.extension,
                documentType = resolved.documentType.name,
                fileSize = resolved.fileSize,
                modifiedDate = resolved.lastModified,
                lastOpenedDate = now,
                isFavorite = false,
                lastViewedPage = 0
            )
        }

        val id = documentDao.insertDocument(entityToSave)
        entityToSave.copy(id = if (existing != null) existing.id else id).toDomain()
    }

    override suspend fun updatePageProgress(uri: String, lastPage: Int, totalPages: Int?): Unit = withContext(Dispatchers.IO) {
        documentDao.updateLastOpened(uri, System.currentTimeMillis(), lastPage, totalPages)
    }

    override suspend fun toggleFavorite(uri: String, isFavorite: Boolean): Unit = withContext(Dispatchers.IO) {
        val existing = documentDao.findByUri(uri)
        if (existing != null) {
            documentDao.updateFavorite(uri, isFavorite)
        } else {
            // Document favorited from list before opening
            val parsedUri = Uri.parse(uri)
            persistUriPermission(parsedUri)
            val resolved = sourceResolver.resolve(parsedUri)
            val entity = DocumentEntity(
                uri = uri,
                displayName = resolved.displayName,
                mimeType = resolved.mimeType,
                extension = resolved.extension,
                documentType = resolved.documentType.name,
                fileSize = resolved.fileSize,
                modifiedDate = resolved.lastModified,
                lastOpenedDate = null,
                isFavorite = isFavorite
            )
            documentDao.insertDocument(entity)
        }
    }

    override suspend fun removeRecentDocument(uri: String): Unit = withContext(Dispatchers.IO) {
        documentDao.removeFromRecent(uri)
    }

    override suspend fun clearAllRecent(): Unit = withContext(Dispatchers.IO) {
        documentDao.clearAllRecent()
    }

    override suspend fun resolveDocumentSource(uri: Uri): ResolvedDocumentSource {
        return sourceResolver.resolve(uri)
    }

    override suspend fun syncDeviceDocuments(type: DocumentType?) = withContext(Dispatchers.IO) {
        val scanned = mediaStoreScanner.scanDocuments(type)
        indexDiscoveredDocuments(scanned)
    }

    override suspend fun indexDiscoveredDocuments(documents: List<Document>) = withContext(Dispatchers.IO) {
        for (doc in documents) {
            val existing = documentDao.findByUri(doc.uri)
            if (existing != null) {
                // Update file stats if changed while strictly preserving user state (favorite, lastOpenedDate, page)
                documentDao.updateFileInfo(
                    uri = doc.uri,
                    displayName = doc.displayName,
                    mimeType = doc.mimeType,
                    extension = doc.extension,
                    fileSize = doc.fileSize,
                    modifiedDate = doc.modifiedDate
                )
            } else {
                // Insert new discovered document
                val entity = DocumentEntity.fromDomain(doc).copy(id = 0)
                documentDao.insertOrIgnore(entity)
            }
        }
    }

    override fun scanDeviceDocuments(type: DocumentType?): Flow<List<Document>> = flow {
        emit(mediaStoreScanner.scanDocuments(type))
    }.flowOn(Dispatchers.IO)

    override fun getReaderSettings(): Flow<ReaderSettings> {
        return _settingsFlow.asStateFlow()
    }

    override suspend fun updateReaderSettings(settings: ReaderSettings) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString("theme", settings.theme.name)
            .putString("reader_mode", settings.defaultReaderMode.name)
            .putString("page_fitting", settings.defaultPageFitting.name)
            .putBoolean("ocr_enabled", settings.isOcrEnabled)
            .putBoolean("night_mode", settings.isAutoNightModeEnabled)
            .putBoolean("keep_screen_on", settings.keepScreenOn)
            .apply()
        _settingsFlow.value = settings
    }

    override suspend fun getCacheSizeBytes(): Long {
        return cacheManager.getCacheSizeBytes()
    }

    override suspend fun clearCache() {
        cacheManager.clearCache()
    }

    private fun readSettings(): ReaderSettings {
        val themeStr = prefs.getString("theme", AppThemeSetting.SYSTEM.name) ?: AppThemeSetting.SYSTEM.name
        val modeStr = prefs.getString("reader_mode", DefaultReaderMode.CONTINUOUS_VERTICAL.name) ?: DefaultReaderMode.CONTINUOUS_VERTICAL.name
        val fitStr = prefs.getString("page_fitting", DefaultPageFitting.FIT_TO_WIDTH.name) ?: DefaultPageFitting.FIT_TO_WIDTH.name

        return ReaderSettings(
            theme = try { AppThemeSetting.valueOf(themeStr) } catch (_: Exception) { AppThemeSetting.SYSTEM },
            defaultReaderMode = try { DefaultReaderMode.valueOf(modeStr) } catch (_: Exception) { DefaultReaderMode.CONTINUOUS_VERTICAL },
            defaultPageFitting = try { DefaultPageFitting.valueOf(fitStr) } catch (_: Exception) { DefaultPageFitting.FIT_TO_WIDTH },
            isOcrEnabled = prefs.getBoolean("ocr_enabled", false),
            isAutoNightModeEnabled = prefs.getBoolean("night_mode", false),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true)
        )
    }
}
