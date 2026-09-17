package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE last_opened_date IS NOT NULL ORDER BY last_opened_date DESC")
    fun getAllRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE is_favorite = 1 ORDER BY CASE WHEN last_opened_date IS NOT NULL THEN 0 ELSE 1 END, last_opened_date DESC, display_name ASC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE document_type = :type ORDER BY CASE WHEN last_opened_date IS NOT NULL THEN 0 ELSE 1 END, last_opened_date DESC, modified_date DESC, display_name ASC")
    fun getDocumentsByType(type: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents ORDER BY CASE WHEN last_opened_date IS NOT NULL THEN 0 ELSE 1 END, last_opened_date DESC, modified_date DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    fun getDocumentByUri(uri: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnore(document: DocumentEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOrIgnoreAll(documents: List<DocumentEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET display_name = :displayName, mime_type = :mimeType, extension = :extension, file_size = :fileSize, modified_date = :modifiedDate WHERE uri = :uri")
    suspend fun updateFileInfo(uri: String, displayName: String, mimeType: String, extension: String, fileSize: Long, modifiedDate: Long)

    @Query("UPDATE documents SET is_favorite = :isFavorite WHERE uri = :uri")
    suspend fun updateFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE documents SET last_opened_date = :timestamp, last_viewed_page = :lastPage, page_count = COALESCE(:pageCount, page_count) WHERE uri = :uri")
    suspend fun updateLastOpened(uri: String, timestamp: Long, lastPage: Int, pageCount: Int?)

    @Query("UPDATE documents SET last_opened_date = NULL WHERE uri = :uri")
    suspend fun removeFromRecent(uri: String)

    @Query("UPDATE documents SET last_opened_date = NULL")
    suspend fun clearAllRecent()

    @Query("DELETE FROM documents WHERE uri = :uri")
    suspend fun deleteDocumentByUri(uri: String)
}
