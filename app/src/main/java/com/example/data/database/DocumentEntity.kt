package com.example.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Document
import com.example.domain.model.DocumentType

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["uri"], unique = true),
        Index(value = ["last_opened_date"]),
        Index(value = ["is_favorite"]),
        Index(value = ["document_type"])
    ]
)
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "extension")
    val extension: String,

    @ColumnInfo(name = "document_type")
    val documentType: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0L,

    @ColumnInfo(name = "modified_date")
    val modifiedDate: Long = 0L,

    @ColumnInfo(name = "last_opened_date")
    val lastOpenedDate: Long? = null,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "page_count")
    val pageCount: Int? = null,

    @ColumnInfo(name = "last_viewed_page")
    val lastViewedPage: Int = 0,

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    @ColumnInfo(name = "is_searchable")
    val isSearchable: Boolean? = null
) {
    fun toDomain(): Document {
        val parsedType = try {
            DocumentType.valueOf(documentType)
        } catch (_: Exception) {
            DocumentType.UNSUPPORTED
        }
        return Document(
            id = id,
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            extension = extension,
            type = parsedType,
            fileSize = fileSize,
            modifiedDate = modifiedDate,
            lastOpenedDate = lastOpenedDate,
            isFavorite = isFavorite,
            pageCount = pageCount,
            lastViewedPage = lastViewedPage,
            thumbnailPath = thumbnailPath,
            isSearchable = isSearchable
        )
    }

    companion object {
        fun fromDomain(doc: Document): DocumentEntity {
            return DocumentEntity(
                id = doc.id,
                uri = doc.uri,
                displayName = doc.displayName,
                mimeType = doc.mimeType,
                extension = doc.extension,
                documentType = doc.type.name,
                fileSize = doc.fileSize,
                modifiedDate = doc.modifiedDate,
                lastOpenedDate = doc.lastOpenedDate,
                isFavorite = doc.isFavorite,
                pageCount = doc.pageCount,
                lastViewedPage = doc.lastViewedPage,
                thumbnailPath = doc.thumbnailPath,
                isSearchable = doc.isSearchable
            )
        }
    }
}
