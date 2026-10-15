package com.example.uepflip_flashcard.data.service

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.InputStream
import java.io.OutputStream

/**
 * Service for extracting text from PDF documents using Apache PDFBox.
 * This is used to feed content into the AI flashcard generator.
 */
class PdfTextExtractor(private val context: Context) {
    companion object {
        private const val TAG = "PdfTextExtractor"
    }

    /**
     * Extracts all text content from a PDF document at the given URI.
     * @param uri The URI of the PDF document
     * @return Extracted text content, or empty string on failure
     */
    fun extractText(uri: Uri): String {
        var text = ""
        var document: PDDocument? = null
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open input stream for $uri")
            document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            text = stripper.getText(document)
            Log.d(TAG, "Extracted ${text.length} characters from PDF")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract text from PDF", e)
        } finally {
            document?.close()
        }
        return text
    }

    /**
     * Writes text content to an OutputStream.
     * @param text The text content to write
     * @param outputStream The OutputStream to write to
     */
    fun writeText(text: String, outputStream: OutputStream) {
        try {
            outputStream.write(text.toByteArray())
            outputStream.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write text to output stream", e)
        }
    }
}