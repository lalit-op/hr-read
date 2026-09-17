package com.example.data.filesystem

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

class DocumentCacheManager(private val context: Context) {

    companion object {
        private const val MAX_CACHE_BYTES = 120 * 1024 * 1024L // 120 MB max cache limit
        private const val TARGET_CACHE_BYTES = 80 * 1024 * 1024L // Evict down to 80 MB
        private const val STALE_TEMP_EXPIRATION_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val cacheDir: File by lazy {
        File(context.cacheDir, "hr_document_cache").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    suspend fun getOrCreateCachedCopy(
        uri: Uri,
        expectedExtension: String,
        expectedSize: Long,
        openStream: () -> InputStream
    ): File = withContext(Dispatchers.IO) {
        cleanStaleTempFiles()

        val safeHash = sha256(uri.toString())
        val cleanExt = expectedExtension.trimStart('.').ifEmpty { "bin" }
        val targetFile = File(cacheDir, "doc_${safeHash}.$cleanExt")

        // If cached copy already exists, is non-empty, and matches expected size (if known), reuse it.
        if (targetFile.exists() && targetFile.length() > 0) {
            if (expectedSize <= 0 || targetFile.length() == expectedSize) {
                targetFile.setLastModified(System.currentTimeMillis())
                return@withContext targetFile
            }
        }

        // Before copying new large file, trim cache if needed to prevent unbounded storage
        trimCacheIfNeeded()

        // Stream from ContentResolver to temporary file, then atomic rename
        val tempFile = File(cacheDir, "temp_${safeHash}_${System.currentTimeMillis()}.$cleanExt")
        openStream().use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }
        tempFile.renameTo(targetFile)
        targetFile.setLastModified(System.currentTimeMillis())

        // Post-check trimming
        trimCacheIfNeeded()

        targetFile
    }

    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        if (!cacheDir.exists()) return@withContext 0L
        cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    suspend fun clearCache(): Boolean = withContext(Dispatchers.IO) {
        if (!cacheDir.exists()) return@withContext true
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        true
    }

    private fun trimCacheIfNeeded() {
        try {
            if (!cacheDir.exists()) return
            val files = cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("doc_") } ?: return
            var currentBytes = files.sumOf { it.length() }
            if (currentBytes > MAX_CACHE_BYTES) {
                // Sort by least recently used (oldest lastModified first)
                val sorted = files.sortedBy { it.lastModified() }
                for (file in sorted) {
                    if (currentBytes <= TARGET_CACHE_BYTES) break
                    val len = file.length()
                    if (file.delete()) {
                        currentBytes -= len
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun cleanStaleTempFiles() {
        try {
            if (!cacheDir.exists()) return
            val now = System.currentTimeMillis()
            val tempFiles = cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("temp_") } ?: return
            for (file in tempFiles) {
                if (now - file.lastModified() > STALE_TEMP_EXPIRATION_MS) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
