package com.example.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.DocumentPage
import com.example.data.ExportState
import com.example.data.ImportedDocument
import com.example.data.PageEraseStatus
import com.example.security.KeyVaultStatus
import com.example.security.SecurityBacking
import com.example.ui.components.ComparisonStage
import com.example.ui.components.EmptyStudioView
import com.example.ui.components.KeyVaultSheet
import com.example.ui.components.rememberDecodedBitmap
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkCleanScreen(
    viewModel: InkCleanViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.importPdf(uri)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

    val savePdfAsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportPdf(uri)
        }
    }

    var showImportMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = uiState.document?.let {
                                "${it.title} · ${it.cleanedCount}/${it.pages.size} cleaned"
                            } ?: stringResource(R.string.tagline_studio),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    EnclaveStatusChip(
                        vaultStatus = uiState.vaultStatus,
                        onClick = viewModel::openKeyVaultSheet,
                    )

                    if (uiState.document != null) {
                        Box {
                            IconButton(
                                onClick = { showImportMenu = true },
                                modifier = Modifier.testTag("import_more_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(R.string.cd_import_new),
                                )
                            }
                            DropdownMenu(
                                expanded = showImportMenu,
                                onDismissRequest = { showImportMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import_pdf)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                                    },
                                    onClick = {
                                        showImportMenu = false
                                        pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import_photos)) },
                                    leadingIcon = {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                    },
                                    onClick = {
                                        showImportMenu = false
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                uiState.isImporting -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Rendering document pages into images…",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }

                uiState.document == null -> {
                    EmptyStudioView(
                        vaultStatus = uiState.vaultStatus,
                        onImportPdf = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        onImportPhotos = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        onOpenKeyVault = viewModel::openKeyVaultSheet,
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }

                else -> {
                    val doc = uiState.document!!
                    val activePage = uiState.selectedPage ?: doc.pages.first()

                    DocumentWorkspace(
                        document = doc,
                        activePage = activePage,
                        uiState = uiState,
                        onSelectPage = viewModel::selectPage,
                        onModeChange = viewModel::setComparisonMode,
                        onEraseAll = viewModel::eraseAllHandwriting,
                        onEraseSinglePage = viewModel::eraseSinglePage,
                        onCancelErase = viewModel::cancelErasing,
                        onExportLocal = { viewModel.exportPdf(null) },
                        onExportSaveAs = {
                            val defaultName = doc.title.substringBeforeLast('.') + "_cleaned.pdf"
                            savePdfAsLauncher.launch(defaultName)
                        },
                    )
                }
            }
        }
    }

    // Import Error Dialog
    if (!uiState.importError.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = viewModel::dismissImportError,
            title = { Text("Import Failed") },
            text = { Text(uiState.importError.orEmpty()) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissImportError) {
                    Text("OK")
                }
            },
        )
    }

    // Export Result Dialog (Success or Error)
    when (val exportState = uiState.exportState) {
        is ExportState.Success -> {
            ExportSuccessDialog(
                exportState = exportState,
                onOpenPdf = { openLocalPdf(context, exportState.localFile) },
                onSharePdf = { shareLocalPdf(context, exportState.localFile) },
                onSaveCopyAs = {
                    viewModel.dismissExportState()
                    savePdfAsLauncher.launch(exportState.fileName)
                },
                onDismiss = viewModel::dismissExportState,
            )
        }

        is ExportState.Error -> {
            AlertDialog(
                onDismissRequest = viewModel::dismissExportState,
                title = { Text("PDF Export Failed") },
                text = { Text(exportState.message) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissExportState) {
                        Text("OK")
                    }
                },
            )
        }

        else -> Unit
    }

    // Hardware Enclave Key Vault Sheet
    if (uiState.showKeyVaultSheet) {
        KeyVaultSheet(
            vaultStatus = uiState.vaultStatus,
            errorMessage = uiState.keyVaultError,
            onDismiss = viewModel::closeKeyVaultSheet,
            onSaveKey = viewModel::saveApiKey,
            onSelectModel = viewModel::selectModel,
            onWipeKey = viewModel::clearApiKey,
        )
    }
}

@Composable
private fun DocumentWorkspace(
    document: ImportedDocument,
    activePage: DocumentPage,
    uiState: InkCleanUiState,
    onSelectPage: (Int) -> Unit,
    onModeChange: (com.example.data.ComparisonMode) -> Unit,
    onEraseAll: () -> Unit,
    onEraseSinglePage: (Int) -> Unit,
    onCancelErase: () -> Unit,
    onExportLocal: () -> Unit,
    onExportSaveAs: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 720.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left Control & Filmstrip Sidebar
                Surface(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "PAGES (${document.pages.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(document.pages, key = { it.index }) { page ->
                                PageThumbnailTile(
                                    page = page,
                                    isSelected = page.index == activePage.index,
                                    onClick = { onSelectPage(page.index) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        HorizontalDivider()

                        WorkspaceActionButtons(
                            document = document,
                            isErasing = uiState.isErasing,
                            erasingProgressText = uiState.erasingProgressText,
                            isExporting = uiState.exportState is ExportState.Exporting,
                            onEraseAll = onEraseAll,
                            onCancelErase = onCancelErase,
                            onExportLocal = onExportLocal,
                            onExportSaveAs = onExportSaveAs,
                        )
                    }
                }

                // Right Comparison Stage
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    ComparisonStage(
                        page = activePage,
                        comparisonMode = uiState.comparisonMode,
                        onModeChange = onModeChange,
                        onEraseSinglePage = onEraseSinglePage,
                    )
                }
            }
        } else {
            // Compact Handheld Vertical Studio
            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = uiState.isErasing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = uiState.erasingProgressText ?: "Processing with Gemini Nano Banana…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    ComparisonStage(
                        page = activePage,
                        comparisonMode = uiState.comparisonMode,
                        onModeChange = onModeChange,
                        onEraseSinglePage = onEraseSinglePage,
                    )
                }

                // Horizontal Page Filmstrip
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(document.pages, key = { it.index }) { page ->
                        PageThumbnailTile(
                            page = page,
                            isSelected = page.index == activePage.index,
                            onClick = { onSelectPage(page.index) },
                            modifier = Modifier.width(86.dp),
                        )
                    }
                }

                // Bottom Action Dock with safe navigation bar padding
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (uiState.isErasing) {
                            OutlinedButton(
                                onClick = onCancelErase,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("cancel_erase_button"),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.action_cancel_erase))
                            }
                        } else {
                            Button(
                                onClick = onEraseAll,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("erase_all_button"),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (document.allCleaned) {
                                        "Re-Erase"
                                    } else {
                                        stringResource(R.string.action_erase_all)
                                    },
                                    maxLines = 1,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onExportSaveAs,
                            enabled = document.hasAnyCleaned && uiState.exportState !is ExportState.Exporting,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("export_pdf_button"),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            if (uiState.exportState is ExportState.Exporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Filled.PictureAsPdf, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.action_export_pdf),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceActionButtons(
    document: ImportedDocument,
    isErasing: Boolean,
    erasingProgressText: String?,
    isExporting: Boolean,
    onEraseAll: () -> Unit,
    onCancelErase: () -> Unit,
    onExportLocal: () -> Unit,
    onExportSaveAs: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isErasing) {
            Text(
                text = erasingProgressText ?: "Erasing…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedButton(
                onClick = onCancelErase,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_erase_button"),
            ) {
                Icon(Icons.Filled.Close, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_cancel_erase))
            }
        } else {
            Button(
                onClick = onEraseAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("erase_all_button"),
            ) {
                Icon(Icons.Filled.AutoFixHigh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_erase_all))
            }
        }

        OutlinedButton(
            onClick = onExportSaveAs,
            enabled = document.hasAnyCleaned && !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("export_pdf_button"),
        ) {
            Icon(Icons.Filled.SaveAlt, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.action_save_pdf_as))
        }

        TextButton(
            onClick = onExportLocal,
            enabled = document.hasAnyCleaned && !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("quick_export_pdf_button"),
        ) {
            Text("Quick Export & Share")
        }
    }
}

@Composable
private fun PageThumbnailTile(
    page: DocumentPage,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbPath = page.cleanedImagePath ?: page.originalImagePath
    val bitmap by rememberDecodedBitmap(thumbPath)

    Surface(
        onClick = onClick,
        modifier = modifier.testTag("page_thumb_${page.index}"),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 44.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!,
                        contentDescription = "Page ${page.index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "P${page.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                when (page.status) {
                    PageEraseStatus.CLEANED -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Cleaned",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(14.dp),
                    )

                    PageEraseStatus.PROCESSING -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                    )

                    PageEraseStatus.ERROR -> Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "Error",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )

                    PageEraseStatus.PENDING -> Text(
                        text = "Raw",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EnclaveStatusChip(
    vaultStatus: KeyVaultStatus,
    onClick: () -> Unit,
) {
    val label = if (vaultStatus.hasKey) {
        when (vaultStatus.backing) {
            SecurityBacking.STRONGBOX -> stringResource(R.string.vault_status_strongbox)
            SecurityBacking.TEE_HARDWARE -> stringResource(R.string.vault_status_tee)
            else -> stringResource(R.string.vault_status_software)
        }
    } else {
        stringResource(R.string.vault_status_missing)
    }

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (vaultStatus.hasKey) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
        modifier = Modifier
            .padding(end = 4.dp)
            .testTag("enclave_status_chip"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = if (vaultStatus.hasKey) {
                    Icons.Outlined.VerifiedUser
                } else {
                    Icons.Outlined.Lock
                },
                contentDescription = stringResource(R.string.cd_enclave_button),
                tint = if (vaultStatus.hasKey) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ExportSuccessDialog(
    exportState: ExportState.Success,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveCopyAs: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(32.dp),
            )
        },
        title = { Text("PDF Exported") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${exportState.pageCount} cleaned page(s) compiled into a local PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = exportState.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenPdf,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("open_exported_pdf_button"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_open_pdf))
                    }
                    OutlinedButton(
                        onClick = onSharePdf,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_exported_pdf_button"),
                    ) {
                        Icon(Icons.Filled.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.action_share_pdf))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            if (exportState.savedUriString == null) {
                TextButton(onClick = onSaveCopyAs) {
                    Text(stringResource(R.string.action_save_pdf_as))
                }
            }
        },
    )
}

private fun openLocalPdf(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open Cleaned PDF"))
    }
}

private fun shareLocalPdf(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Cleaned PDF"))
    }
}
