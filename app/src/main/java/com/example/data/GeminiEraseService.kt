package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class GeminiEraseService {

    // Note: Intentionally NO HTTP header/body logging interceptor so x-goog-api-key never reaches Logcat.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(110, TimeUnit.SECONDS)
        .build()

    suspend fun eraseHandwritingFromPage(
        originalImagePath: String,
        apiKey: String,
        modelId: String,
    ): String = withContext(Dispatchers.IO) {
        val originalFile = File(originalImagePath)
        require(originalFile.exists()) { "Source page image not found." }

        val jpegBase64 = encodeImageAsBase64Jpeg(originalFile)

        val requestPayload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put(
                            "parts",
                            JSONArray()
                                .put(
                                    JSONObject().apply {
                                        put(
                                            "inlineData",
                                            JSONObject().apply {
                                                put("mimeType", "image/jpeg")
                                                put("data", jpegBase64)
                                            },
                                        )
                                    },
                                )
                                .put(
                                    JSONObject().apply {
                                        put("text", HANDWRITING_ERASE_PROMPT)
                                    },
                                ),
                        )
                    },
                ),
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseModalities", JSONArray().put("IMAGE").put("TEXT"))
                },
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent"
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val parsedApiError = extractApiErrorMessage(rawBody)
                val friendlyMessage = when (response.code) {
                    400 -> "Bad request: ${parsedApiError ?: "Check the selected model or image format."}"
                    401, 403 -> "Authentication failed (${response.code}): ${parsedApiError ?: "Check your Gemini API key in the Secure Enclave Vault."}"
                    404 -> "Model \"$modelId\" was not found (404). Try switching the Nano Banana model in the Key Vault."
                    429 -> "Rate limit or quota exceeded (429). Wait briefly and retry."
                    else -> "Gemini API error (${response.code}): ${parsedApiError ?: "Unexpected server response."}"
                }
                Log.e(TAG, "Gemini erase failed with HTTP ${response.code}")
                throw IllegalStateException(friendlyMessage)
            }

            val cleanedBytes = extractReturnedImageBytes(rawBody)
                ?: throw IllegalStateException(
                    "Model did not return a cleaned image. Try Nano Banana 2.1 or Nano Banana Pro in the Key Vault.",
                )

            val bitmap = BitmapFactory.decodeByteArray(cleanedBytes, 0, cleanedBytes.size)
                ?: throw IllegalStateException("Returned image data could not be decoded.")

            val cleanedFile = File(
                originalFile.parentFile,
                originalFile.nameWithoutExtension.replace("_orig", "") + "_cleaned.png",
            )
            try {
                FileOutputStream(cleanedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } finally {
                bitmap.recycle()
            }
            cleanedFile.absolutePath
        }
    }

    private fun encodeImageAsBase64Jpeg(imageFile: File): String {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: throw IllegalStateException("Failed to read rendered page bitmap.")
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } finally {
            bitmap.recycle()
        }
    }

    private fun extractReturnedImageBytes(rawJson: String): ByteArray? {
        val root = JSONObject(rawJson)
        val candidates = root.optJSONArray("candidates") ?: return null
        for (c in 0 until candidates.length()) {
            val candidate = candidates.optJSONObject(c) ?: continue
            val content = candidate.optJSONObject("content") ?: continue
            val parts = content.optJSONArray("parts") ?: continue
            for (p in 0 until parts.length()) {
                val part = parts.optJSONObject(p) ?: continue
                val inlineData = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                val dataStr = inlineData?.optString("data")
                if (!dataStr.isNullOrBlank()) {
                    return Base64.decode(dataStr, Base64.DEFAULT)
                }
            }
        }
        return null
    }

    private fun extractApiErrorMessage(rawJson: String): String? {
        return runCatching {
            val root = JSONObject(rawJson)
            root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    companion object {
        private const val TAG = "GeminiEraseService"

        private const val HANDWRITING_ERASE_PROMPT =
            "Edit this exam paper or document page image to completely remove all handwritten text, " +
                "handwritten answers, pen or pencil marks, circling, underlines, checkmarks, grading scores, " +
                "and margin scribbles. Strictly preserve every single printed character, printed question, " +
                "mathematical formula, printed diagram, table border, checkbox, and paper structure exactly " +
                "as printed, leaving clean paper background where handwriting was removed. Do not alter, " +
                "blur, or invent any printed text."
    }
}
