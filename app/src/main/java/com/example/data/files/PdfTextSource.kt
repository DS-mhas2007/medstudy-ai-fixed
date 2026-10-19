package com.example.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * Real text extraction for a lecture PDF (the old reader showed hard-coded sample sentences for every file).
 *
 *  1. Reads the PDF text layer with PdfBox, which is exact and offline.
 *  2. If the page has no text layer (scanned slide, exported image) it renders the page and runs on-device OCR
 *     (ML Kit, Latin script: English lecture text and medical terms).
 *
 * One instance per opened file; call [close] when the reader is closed.
 */
class PdfTextSource(private val context: Context, private val file: File) {
    private var document: PDDocument? = null

    suspend fun pageText(pageIndex: Int): String = withContext(Dispatchers.IO) {
        val fromLayer = try {
            extractTextLayer(pageIndex)
        } catch (e: Exception) {
            ""
        }
        if (fromLayer.length >= MIN_USEFUL_CHARS) fromLayer else {
            val ocr = try {
                ocrPage(pageIndex)
            } catch (e: Exception) {
                ""
            }
            if (ocr.length > fromLayer.length) ocr else fromLayer
        }
    }

    fun close() {
        try {
            document?.close()
        } catch (e: Exception) {
            // nothing to do
        }
        document = null
    }

    private fun extractTextLayer(pageIndex: Int): String {
        val doc = document ?: run {
            PDFBoxResourceLoader.init(context.applicationContext)
            PDDocument.load(file).also { document = it }
        }
        if (pageIndex !in 0 until doc.numberOfPages) return ""
        val stripper = PDFTextStripper()
        stripper.sortByPosition = true
        stripper.startPage = pageIndex + 1
        stripper.endPage = pageIndex + 1
        return clean(stripper.getText(doc))
    }

    private suspend fun ocrPage(pageIndex: Int): String {
        val bitmap = renderPage(pageIndex) ?: return ""
        return try {
            recognize(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderPage(pageIndex: Int): Bitmap? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val scale = 2f
                    val bmp = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }

    private suspend fun recognize(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                recognizer.close()
                if (cont.isActive) cont.resume(clean(result.text))
            }
            .addOnFailureListener {
                recognizer.close()
                if (cont.isActive) cont.resume("")
            }
        cont.invokeOnCancellation { recognizer.close() }
    }

    private fun clean(text: String): String = text
        .replace("\u0000", "")
        .replace(Regex("""[ \t]+"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()

    private companion object {
        const val MIN_USEFUL_CHARS = 20
    }
}
