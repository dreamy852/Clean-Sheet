package com.example.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ComparisonMode
import com.example.data.DocumentImporter
import com.example.data.ExportState
import com.example.data.GeminiEraseService
import com.example.data.ImportedDocument
import com.example.data.PageEraseStatus
import com.example.data.PdfExporter
import com.example.security.KeyVaultStatus
import com.example.security.SecureEnclaveKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InkCleanUiState(
    val document: ImportedDocument? = null,
    val selectedPageIndex: Int = 0,
    val comparisonMode: ComparisonMode = ComparisonMode.SIDE_BY_SIDE,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val isErasing: Boolean = false,
    val erasingProgressText: String? = null,
    val vaultStatus: KeyVaultStatus,
    val showKeyVaultSheet: Boolean = false,
    val keyVaultError: String? = null,
    val pendingEraseAfterKeySave: Boolean = false,
    val pendingEraseSinglePageIndex: Int? = null,
    val exportState: ExportState = ExportState.Idle,
) {
    val selectedPage
        get() = document?.pages?.getOrNull(selectedPageIndex)
}

class InkCleanViewModel(application: Application) : AndroidViewModel(application) {

    private val keyStore = SecureEnclaveKeyStore(application.applicationContext)
    private val importer = DocumentImporter(application.applicationContext)
    private val eraseService = GeminiEraseService()
    private val pdfExporter = PdfExporter(application.applicationContext)

    private val _uiState = MutableStateFlow(
        InkCleanUiState(vaultStatus = keyStore.getStatus()),
    )
    val uiState: StateFlow<InkCleanUiState> = _uiState.asStateFlow()

    private var eraseJob: Job? = null

    fun importPdf(uri: Uri) {
        eraseJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importError = null,
                    exportState = ExportState.Idle,
                )
            }
            try {
                val doc = importer.importPdf(uri)
                _uiState.update {
                    it.copy(
                        document = doc,
                        selectedPageIndex = 0,
                        isImporting = false,
                        comparisonMode = ComparisonMode.SIDE_BY_SIDE,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF import failed", e)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importError = e.localizedMessage ?: "Failed to import PDF.",
                    )
                }
            }
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        eraseJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importError = null,
                    exportState = ExportState.Idle,
                )
            }
            try {
                val doc = importer.importImages(uris)
                _uiState.update {
                    it.copy(
                        document = doc,
                        selectedPageIndex = 0,
                        isImporting = false,
                        comparisonMode = ComparisonMode.SIDE_BY_SIDE,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image import failed", e)
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importError = e.localizedMessage ?: "Failed to import image(s).",
                    )
                }
            }
        }
    }

    fun dismissImportError() {
        _uiState.update { it.copy(importError = null) }
    }

    fun selectPage(index: Int) {
        val pageCount = _uiState.value.document?.pages?.size ?: return
        if (index in 0 until pageCount) {
            _uiState.update { it.copy(selectedPageIndex = index) }
        }
    }

    fun setComparisonMode(mode: ComparisonMode) {
        _uiState.update { it.copy(comparisonMode = mode) }
    }

    fun openKeyVaultSheet() {
        _uiState.update {
            it.copy(
                showKeyVaultSheet = true,
                keyVaultError = null,
                vaultStatus = keyStore.getStatus(),
            )
        }
    }

    fun closeKeyVaultSheet() {
        _uiState.update {
            it.copy(
                showKeyVaultSheet = false,
                keyVaultError = null,
                pendingEraseAfterKeySave = false,
                pendingEraseSinglePageIndex = null,
            )
        }
    }

    fun selectModel(modelId: String) {
        val status = keyStore.saveSelectedModelId(modelId)
        _uiState.update { it.copy(vaultStatus = status) }
    }

    fun saveApiKey(rawKey: String, modelId: String) {
        try {
            keyStore.saveSelectedModelId(modelId)
            val status = keyStore.saveApiKey(rawKey)
            val shouldEraseAll = _uiState.value.pendingEraseAfterKeySave
            val singlePageIdx = _uiState.value.pendingEraseSinglePageIndex
            _uiState.update {
                it.copy(
                    vaultStatus = status,
                    showKeyVaultSheet = false,
                    keyVaultError = null,
                    pendingEraseAfterKeySave = false,
                    pendingEraseSinglePageIndex = null,
                )
            }
            if (singlePageIdx != null) {
                eraseSinglePage(singlePageIdx)
            } else if (shouldEraseAll) {
                eraseAllHandwriting()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store key in enclave", e)
            _uiState.update {
                it.copy(
                    keyVaultError = e.localizedMessage ?: "Could not store key in device keystore.",
                )
            }
        }
    }

    fun clearApiKey() {
        val status = keyStore.clearApiKey()
        _uiState.update {
            it.copy(
                vaultStatus = status,
                keyVaultError = null,
            )
        }
    }

    fun eraseAllHandwriting() {
        val doc = _uiState.value.document ?: return
        val apiKey = runCatching { keyStore.getApiKey() }.getOrNull()
        if (apiKey.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    showKeyVaultSheet = true,
                    pendingEraseAfterKeySave = true,
                    pendingEraseSinglePageIndex = null,
                    keyVaultError = null,
                )
            }
            return
        }

        val modelId = keyStore.getSelectedModelId()
        val targetIndices = doc.pages.indices.filter { idx -> !doc.pages[idx].isCleaned }
            .ifEmpty { doc.pages.indices.toList() }

        eraseJob?.cancel()
        eraseJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isErasing = true,
                    exportState = ExportState.Idle,
                    erasingProgressText = "Preparing ${targetIndices.size} page(s)…",
                )
            }
            try {
                targetIndices.forEachIndexed { step, pageIndex ->
                    val currentDoc = _uiState.value.document ?: return@launch
                    val page = currentDoc.pages.getOrNull(pageIndex) ?: return@forEachIndexed
                    updatePageStatus(pageIndex, PageEraseStatus.PROCESSING, null, null)
                    _uiState.update {
                        it.copy(
                            selectedPageIndex = pageIndex,
                            erasingProgressText = "Erasing page ${step + 1} of ${targetIndices.size}…",
                        )
                    }

                    try {
                        val cleanedPath = eraseService.eraseHandwritingFromPage(
                            originalImagePath = page.originalImagePath,
                            apiKey = apiKey,
                            modelId = modelId,
                        )
                        updatePageStatus(pageIndex, PageEraseStatus.CLEANED, cleanedPath, null)
                    } catch (e: CancellationException) {
                        updatePageStatus(pageIndex, PageEraseStatus.PENDING, page.cleanedImagePath, null)
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Page $pageIndex erase failed", e)
                        updatePageStatus(
                            pageIndex,
                            PageEraseStatus.ERROR,
                            page.cleanedImagePath,
                            e.localizedMessage ?: "Failed to process page.",
                        )
                    }
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isErasing = false,
                        erasingProgressText = null,
                    )
                }
            }
        }
    }

    fun eraseSinglePage(pageIndex: Int) {
        val doc = _uiState.value.document ?: return
        val page = doc.pages.getOrNull(pageIndex) ?: return
        val apiKey = runCatching { keyStore.getApiKey() }.getOrNull()
        if (apiKey.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    showKeyVaultSheet = true,
                    pendingEraseAfterKeySave = false,
                    pendingEraseSinglePageIndex = pageIndex,
                    keyVaultError = null,
                )
            }
            return
        }

        val modelId = keyStore.getSelectedModelId()
        eraseJob?.cancel()
        eraseJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isErasing = true,
                    selectedPageIndex = pageIndex,
                    erasingProgressText = "Erasing page ${pageIndex + 1}…",
                    exportState = ExportState.Idle,
                )
            }
            updatePageStatus(pageIndex, PageEraseStatus.PROCESSING, page.cleanedImagePath, null)
            try {
                val cleanedPath = eraseService.eraseHandwritingFromPage(
                    originalImagePath = page.originalImagePath,
                    apiKey = apiKey,
                    modelId = modelId,
                )
                updatePageStatus(pageIndex, PageEraseStatus.CLEANED, cleanedPath, null)
            } catch (e: CancellationException) {
                updatePageStatus(pageIndex, PageEraseStatus.PENDING, page.cleanedImagePath, null)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Single page erase failed", e)
                updatePageStatus(
                    pageIndex,
                    PageEraseStatus.ERROR,
                    page.cleanedImagePath,
                    e.localizedMessage ?: "Failed to process page.",
                )
            } finally {
                _uiState.update {
                    it.copy(
                        isErasing = false,
                        erasingProgressText = null,
                    )
                }
            }
        }
    }

    fun cancelErasing() {
        eraseJob?.cancel()
        _uiState.update {
            it.copy(
                isErasing = false,
                erasingProgressText = null,
            )
        }
    }

    fun exportPdf(destinationUri: Uri? = null) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Exporting) }
            try {
                val result = pdfExporter.exportToLocalAndOptionalUri(doc, destinationUri)
                _uiState.update { it.copy(exportState = result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF export failed", e)
                _uiState.update {
                    it.copy(
                        exportState = ExportState.Error(
                            e.localizedMessage ?: "Failed to export PDF.",
                        ),
                    )
                }
            }
        }
    }

    fun dismissExportState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun clearDocument() {
        eraseJob?.cancel()
        _uiState.update {
            it.copy(
                document = null,
                selectedPageIndex = 0,
                isErasing = false,
                erasingProgressText = null,
                exportState = ExportState.Idle,
            )
        }
    }

    private fun updatePageStatus(
        pageIndex: Int,
        status: PageEraseStatus,
        cleanedPath: String?,
        errorMessage: String?,
    ) {
        _uiState.update { state ->
            val currentDoc = state.document ?: return@update state
            val updatedPages = currentDoc.pages.map { p ->
                if (p.index == pageIndex) {
                    p.copy(
                        status = status,
                        cleanedImagePath = cleanedPath,
                        errorMessage = errorMessage,
                    )
                } else {
                    p
                }
            }
            state.copy(document = currentDoc.copy(pages = updatedPages))
        }
    }

    companion object {
        private const val TAG = "InkCleanViewModel"
    }
}
