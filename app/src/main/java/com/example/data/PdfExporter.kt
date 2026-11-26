package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfExporter(private val context: Context) {

    suspend fun exportToLocalAndOptionalUri(
        document: ImportedDocument,
        targetUri: Uri? = null,
    ): ExportState.Success = withContext(Dispatchers.IO) {
        val pagesToExport = document.pages.filter { it.isCleaned }
        if (pagesToExport.isEmpty()) {
            throw IllegalStateException("No cleaned pages available to export yet.")
        }

        val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseTitle = document.title
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]+"), "_")
            .trim('_')
            .ifBlank { "InkClean_Document" }
        val fileName = "${baseTitle}_cleaned_$timestamp.pdf"
        val localFile = File(exportsDir, fileName)

        val pdfDocument = PdfDocument()
        try {
            pagesToExport.forEachIndexed { exportIndex, docPage ->
                val imagePath = docPage.cleanedImagePath ?: docPage.originalImagePath
                val bitmap = BitmapFactory.decodeFile(imagePath)
                    ?: throw IllegalStateException("Could not decode cleaned page ${docPage.index + 1}.")
                try {
                    // Use normalized point size capped cleanly for crisp high-DPI PDF rendering
                    val pageWidth = bitmap.width.coerceAtLeast(1)
                    val pageHeight = bitmap.height.coerceAtLeast(1)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, exportIndex + 1).create()
                    val pdfPage = pdfDocument.startPage(pageInfo)
                    val canvas: Canvas = pdfPage.canvas
                    canvas.drawColor(Color.WHITE)
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        Rect(0, 0, pageWidth, pageHeight),
                        null,
                    )
                    pdfDocument.finishPage(pdfPage)
                } finally {
                    bitmap.recycle()
                }
            }

            FileOutputStream(localFile).use { localOut ->
                pdfDocument.writeTo(localOut)
            }

            if (targetUri != null) {
                context.contentResolver.openOutputStream(targetUri)?.use { uriOut ->
                    localFile.inputStream().use { input ->
                        input.copyTo(uriOut)
                    }
                } ?: throw IllegalStateException("Unable to write PDF to the chosen destination.")
            }
        } finally {
            pdfDocument.close()
        }

        ExportState.Success(
            fileName = fileName,
            localFile = localFile,
            savedUriString = targetUri?.toString(),
            pageCount = pagesToExport.size,
            cleanedPageCount = pagesToExport.size,
        )
    }
}
