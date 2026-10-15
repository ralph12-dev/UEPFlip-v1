package com.example.uepflip_flashcard.data.service

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

/**
 * Why a PDF could not be turned into text. Carried as a reason (not a message) so the caller can
 * surface a localised, accurate string instead of blaming every failure on scanned images.
 */
class PdfExtractionException(
    val reason: Reason,
    cause: Throwable? = null
) : Exception(cause) {
    enum class Reason { TOO_LARGE, UNREADABLE }
}

/**
 * Service for extracting text from PDF documents using Apache PDFBox.
 * This is used to feed content into the AI flashcard generator.
 */
class PdfTextExtractor(private val context: Context) {
    companion object {
        private const val TAG = "PdfTextExtractor"

        /** PDFBox holds the whole document in memory, so refuse anything beyond a sane ceiling. */
        const val MAX_PDF_BYTES = 25L * 1024 * 1024
    }

    /**
     * Extracts text content from the PDF at [uri].
     *
     * @throws PdfExtractionException when the file is too large, encrypted or unreadable; the caller
     *   decides what to tell the user.
     */
    fun extractText(uri: Uri): String {
        if (sizeOf(uri) > MAX_PDF_BYTES) {
            Log.w(TAG, "Refusing to load a PDF over $MAX_PDF_BYTES bytes")
            throw PdfExtractionException(PdfExtractionException.Reason.TOO_LARGE)
        }

        var document: PDDocument? = null
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw PdfExtractionException(PdfExtractionException.Reason.UNREADABLE)
            // `use` closes the caller's stream too; PDDocument.load does not own it.
            inputStream.use { stream ->
                document = PDDocument.load(stream)
                PDFTextStripper().getText(document).also {
                    Log.d(TAG, "Extracted ${it.length} characters from PDF")
                }
            }
        } catch (e: PdfExtractionException) {
            throw e
        } catch (e: Exception) {
            // Corrupt, encrypted or simply not a PDF. Reported as one reason; the cause is logged.
            Log.e(TAG, "Failed to extract text from PDF", e)
            throw PdfExtractionException(PdfExtractionException.Reason.UNREADABLE, e)
        } finally {
            document?.close()
        }
    }

    /** Best-effort file size; -1 when the provider does not report one (then we simply try). */
    private fun sizeOf(uri: Uri): Long = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the size of $uri", e)
        -1L
    }
}
