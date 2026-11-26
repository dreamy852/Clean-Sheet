package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class DocumentImporter(private val context: Context) {

    suspend fun importPdf(uri: Uri): ImportedDocument = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString().take(10)
        val docDir = prepareDocDir(docId)
        val fileName = resolveDisplayName(uri) ?: "Exam_Document.pdf"

        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Unable to open the selected PDF file.")

        val pages = mutableListOf<DocumentPage>()
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val pageCount = renderer.pageCount
                if (pageCount <= 0) {
                    throw IllegalStateException("The selected PDF contains no pages.")
                }

                for (index in 0 until pageCount) {
                    renderer.openPage(index).use { page ->
                        val maxSide = max(page.width, page.height).coerceAtLeast(1)
                        val scale = TARGET_RENDER_PX.toFloat() / maxSide.toFloat()
                        val targetW = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val targetH = (page.height * scale).roundToInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        try {
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                            )

                            val outFile = File(docDir, "page_${index}_orig.png")
                            saveBitmapPng(bitmap, outFile)
                            pages.add(
                                DocumentPage(
                                    index = index,
                                    originalImagePath = outFile.absolutePath,
                                    widthPx = targetW,
                                    heightPx = targetH,
                                ),
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }

        ImportedDocument(
            id = docId,
            title = fileName,
            sourceType = DocumentSourceType.PDF,
            pages = pages,
        )
    }

    suspend fun importImages(uris: List<Uri>): ImportedDocument = withContext(Dispatchers.IO) {
        require(uris.isNotEmpty()) { "No images selected." }
        val docId = UUID.randomUUID().toString().take(10)
        val docDir = prepareDocDir(docId)

        val title = if (uris.size == 1) {
            resolveDisplayName(uris.first()) ?: "Exam_Page.jpg"
        } else {
            "${uris.size}-Page Exam Scan"
        }

        val pages = uris.mapIndexed { index, uri ->
            val bitmap = decodeNormalizedBitmap(uri)
            try {
                val outFile = File(docDir, "page_${index}_orig.png")
                saveBitmapPng(bitmap, outFile)
                DocumentPage(
                    index = index,
                    originalImagePath = outFile.absolutePath,
                    widthPx = bitmap.width,
                    heightPx = bitmap.height,
                )
            } finally {
                bitmap.recycle()
            }
        }

        ImportedDocument(
            id = docId,
            title = title,
            sourceType = DocumentSourceType.IMAGES,
            pages = pages,
        )
    }

    private fun decodeNormalizedBitmap(uri: Uri): Bitmap {
        val resolver = context.contentResolver
        val orientation = resolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IllegalStateException("Unable to read image stream.")

        val sampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, MAX_IMAGE_DIM_PX)
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        } ?: throw IllegalStateException("Failed to decode image.")

        val rotated = applyExifTransform(decoded, orientation)
        if (rotated !== decoded) decoded.recycle()

        val maxSide = max(rotated.width, rotated.height)
        val scaled = if (maxSide > MAX_IMAGE_DIM_PX) {
            val ratio = MAX_IMAGE_DIM_PX.toFloat() / maxSide.toFloat()
            val w = (rotated.width * ratio).roundToInt().coerceAtLeast(1)
            val h = (rotated.height * ratio).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(rotated, w, h, true).also {
                if (it !== rotated) rotated.recycle()
            }
        } else {
            rotated
        }

        // Composite onto opaque white background so JPEG encoding never produces black transparency
        if (!scaled.hasAlpha()) return scaled
        val whiteBacked = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(whiteBacked)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(scaled, 0f, 0f, null)
        scaled.recycle()
        return whiteBacked
    }

    private fun applyExifTransform(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun computeSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var sample = 1
        val maxSide = max(width, height)
        while (maxSide / (sample * 2) >= maxDim) {
            sample *= 2
        }
        return sample
    }

    private fun prepareDocDir(docId: String): File {
        val pagesRoot = File(context.cacheDir, "pages")
        if (pagesRoot.exists()) {
            // Keep cache tidy across sessions
            pagesRoot.listFiles()?.forEach { child ->
                if (child.isDirectory && child.name != docId) {
                    child.deleteRecursively()
                }
            }
        }
        return File(pagesRoot, docId).apply { mkdirs() }
    }

    private fun saveBitmapPng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        }.getOrNull()
    }

    companion object {
        private const val TARGET_RENDER_PX = 1650
        private const val MAX_IMAGE_DIM_PX = 1800
    }
}
