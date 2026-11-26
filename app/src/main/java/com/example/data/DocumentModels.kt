package com.example.data

import java.io.File

enum class DocumentSourceType {
    PDF,
    IMAGES,
}

enum class PageEraseStatus {
    PENDING,
    PROCESSING,
    CLEANED,
    ERROR,
}

data class DocumentPage(
    val index: Int,
    val originalImagePath: String,
    val cleanedImagePath: String? = null,
    val status: PageEraseStatus = PageEraseStatus.PENDING,
    val errorMessage: String? = null,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
) {
    val isCleaned: Boolean
        get() = status == PageEraseStatus.CLEANED && !cleanedImagePath.isNullOrBlank()
}

data class ImportedDocument(
    val id: String,
    val title: String,
    val sourceType: DocumentSourceType,
    val pages: List<DocumentPage>,
) {
    val cleanedCount: Int get() = pages.count { it.isCleaned }
    val hasAnyCleaned: Boolean get() = cleanedCount > 0
    val allCleaned: Boolean get() = pages.isNotEmpty() && cleanedCount == pages.size
}

enum class ComparisonMode {
    SIDE_BY_SIDE,
    SLIDER_WIPE,
    CLEANED_ONLY,
}

enum class NanoBananaModel(
    val id: String,
    val displayName: String,
    val subtitle: String,
) {
    NANO_BANANA_2_1(
        id = "gemini-nano-banana-2.1",
        displayName = "Nano Banana 2.1",
        subtitle = "Default fast image editing & ink removal",
    ),
    NANO_BANANA_2_PREVIEW(
        id = "gemini-3.1-flash-image-preview",
        displayName = "Nano Banana 2 (Preview)",
        subtitle = "Gemini 3.1 Flash Image preview model",
    ),
    NANO_BANANA_PRO(
        id = "gemini-3-pro-image",
        displayName = "Nano Banana Pro",
        subtitle = "Maximum precision for dense math & diagrams",
    ),
    NANO_BANANA_LITE(
        id = "gemini-3.1-flash-lite-image",
        displayName = "Nano Banana 2 Lite",
        subtitle = "Ultra-low latency lightweight cleanup",
    ),
    NANO_BANANA_ORIGINAL(
        id = "gemini-2.5-flash-image",
        displayName = "Gemini 2.5 Flash Image",
        subtitle = "Classic Nano Banana image model",
    );

    companion object {
        fun fromId(id: String): NanoBananaModel {
            return entries.firstOrNull { it.id == id } ?: NANO_BANANA_2_1
        }
    }
}

sealed interface ExportState {
    data object Idle : ExportState
    data object Exporting : ExportState
    data class Success(
        val fileName: String,
        val localFile: File,
        val savedUriString: String?,
        val pageCount: Int,
        val cleanedPageCount: Int,
    ) : ExportState

    data class Error(val message: String) : ExportState
}
