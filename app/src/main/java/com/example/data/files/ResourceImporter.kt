package com.example.data.files

import android.content.Context
import android.content.Intent
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** A lecture PDF copied into app storage. */
data class ImportedPdf(val file: File, val displayName: String, val pageCount: Int)

sealed interface ImportResult {
    data class Success(val pdf: ImportedPdf) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * Brings files chosen with the system picker into the app.
 *
 * PDFs are COPIED into private storage: a content:// permission from the picker is temporary, and the original
 * may be moved or deleted. Videos are large, so they stay where they are and we keep a persistable read
 * permission on the Uri instead. Subtitle files are read as text and never stored.
 */
class ResourceImporter(private val context: Context) {

    suspend fun importPdf(uri: Uri): ImportResult = withContext<ImportResult>(Dispatchers.IO) {
        val name = displayName(uri).ifBlank { "lecture.pdf" }
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}.pdf")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Failure("تعذر فتح الملف.")
            var total = 0L
            input.use { src ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = src.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_PDF_BYTES) throw IOException("too large")
                        out.write(buffer, 0, n)
                    }
                }
            }
            if (!hasPdfHeader(target)) {
                target.delete()
                return@withContext ImportResult.Failure("الملف المحدد ليس بصيغة PDF.")
            }
            val pages = countPages(target)
            if (pages <= 0) {
                target.delete()
                return@withContext ImportResult.Failure("تعذر قراءة الملف. قد يكون محمياً بكلمة مرور أو تالفاً.")
            }
            ImportResult.Success(ImportedPdf(target, name, pages))
        } catch (e: IOException) {
            target.delete()
            ImportResult.Failure(if (e.message == "too large") "حجم الملف أكبر من 300 ميغابايت." else "فشل نسخ الملف.")
        } catch (e: SecurityException) {
            target.delete()
            ImportResult.Failure("لا توجد صلاحية لقراءة هذا الملف.")
        }
    }

    /** Keeps read access to a video after the app restarts. Returns false when the provider does not allow it. */
    fun keepVideoAccess(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (e: Exception) {
        false
    }

    suspend fun readText(uri: Uri, maxBytes: Int = MAX_SUBTITLE_BYTES): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCompat(maxBytes)
                String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return c.getString(idx).orEmpty()
                }
            }
        } catch (e: Exception) {
            // fall through to the last path segment
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private fun hasPdfHeader(file: File): Boolean = try {
        file.inputStream().use { input ->
            val head = ByteArray(1024)
            val n = input.read(head)
            n >= 5 && String(head, 0, n, Charsets.ISO_8859_1).contains("%PDF-")
        }
    } catch (e: IOException) {
        false
    }

    private fun countPages(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    } catch (e: Exception) {
        0
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var remaining = max
        while (remaining > 0) {
            val n = read(buffer, 0, minOf(buffer.size, remaining))
            if (n < 0) break
            out.write(buffer, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    private companion object {
        const val MAX_PDF_BYTES = 300L * 1024 * 1024
        const val MAX_SUBTITLE_BYTES = 3 * 1024 * 1024
    }
}
