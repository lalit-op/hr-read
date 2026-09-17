package com.example

import com.example.domain.model.DocumentType
import com.example.util.UserErrorMessageResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserErrorMessageResolverTest {

    @Test
    fun `password protected error resolution`() {
        val result = UserErrorMessageResolver.resolve(
            throwable = null,
            fallbackMessage = "PDF is password-protected and encrypted",
            documentType = DocumentType.PDF
        )
        assertEquals("Password Protected Document", result.title)
        assertTrue(result.message.contains("password"))
        assertFalse(result.message.contains("Exception"))
        assertFalse(result.message.contains("encrypted"))
        assertTrue(result.canRetry)
        assertFalse(result.isAccessLost)
    }

    @Test
    fun `unsupported format error resolution`() {
        val result = UserErrorMessageResolver.resolve(
            throwable = null,
            fallbackMessage = "Unsupported format: archive.zip",
            documentType = DocumentType.UNSUPPORTED
        )
        assertEquals("Format Not Supported", result.title)
        assertEquals("This document format is not supported.", result.message)
        assertFalse(result.canRetry)
        assertFalse(result.isAccessLost)
    }

    @Test
    fun `security exception access lost error resolution`() {
        val result = UserErrorMessageResolver.resolve(
            throwable = SecurityException("Permission Denial: reading content://media/external/123"),
            fallbackMessage = null,
            documentType = DocumentType.WORD
        )
        assertEquals("Access Permission Lost", result.title)
        assertTrue(result.isAccessLost)
        assertFalse(result.message.contains("Permission Denial"))
        assertFalse(result.message.contains("content://"))
        assertFalse(result.message.contains("SecurityException"))
    }

    @Test
    fun `file not found error resolution`() {
        val result = UserErrorMessageResolver.resolve(
            throwable = FileNotFoundException("/storage/emulated/0/Download/report.pdf not found"),
            fallbackMessage = null,
            documentType = DocumentType.PDF
        )
        assertEquals("File Not Available", result.title)
        assertEquals("File is no longer available.", result.message)
        assertTrue(result.isAccessLost)
        assertFalse(result.message.contains("/storage/emulated"))
    }

    @Test
    fun `out of memory error resolution`() {
        val result = UserErrorMessageResolver.resolve(
            throwable = OutOfMemoryError("Failed to allocate 128MB bitmap"),
            fallbackMessage = null,
            documentType = DocumentType.PDF
        )
        assertEquals("Insufficient Memory", result.title)
        assertFalse(result.message.contains("128MB"))
        assertFalse(result.message.contains("OutOfMemoryError"))
        assertTrue(result.canRetry)
    }

    @Test
    fun `corrupted presentation error resolution`() {
        val result = UserErrorMessageResolver.resolve(
            throwable = IllegalStateException("Corrupt zip archive header at offset 0x45"),
            fallbackMessage = "Failed rendering PowerPoint presentation",
            documentType = DocumentType.POWERPOINT
        )
        assertEquals("Unable to Read Presentation", result.title)
        assertEquals("Unable to read this presentation. The slide file may be damaged or incomplete.", result.message)
        assertFalse(result.message.contains("0x45"))
        assertFalse(result.message.contains("offset"))
        assertFalse(result.message.contains("zip archive"))
    }

    @Test
    fun `sanitize removes stack traces and paths`() {
        val raw = "java.lang.NullPointerException: object is null\n\tat com.example.Foo.bar(Foo.kt:42)\n\tat android.os.Handler.dispatch(Handler.java:100)"
        val sanitized = UserErrorMessageResolver.sanitize(raw)
        assertFalse(sanitized.contains("NullPointerException"))
        assertFalse(sanitized.contains("Foo.kt"))
        assertFalse(sanitized.contains("at "))
        assertEquals("Unable to open this document. Please try again.", sanitized)
    }
}
