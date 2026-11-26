package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ComparisonMode
import com.example.data.DocumentPage
import com.example.data.PageEraseStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun ComparisonStage(
    page: DocumentPage,
    comparisonMode: ComparisonMode,
    onModeChange: (ComparisonMode) -> Unit,
    onEraseSinglePage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val originalBitmap by rememberDecodedBitmap(page.originalImagePath)
    val cleanedBitmap by rememberDecodedBitmap(page.cleanedImagePath)

    var zoomScale by remember(page.index) { mutableFloatStateOf(1f) }
    var panOffset by remember(page.index) { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Comparison Mode Switcher & Zoom Reset Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.weight(1f),
            ) {
                val modes = listOf(
                    ComparisonMode.SIDE_BY_SIDE to stringResource(R.string.mode_side_by_side),
                    ComparisonMode.SLIDER_WIPE to stringResource(R.string.mode_slider),
                    ComparisonMode.CLEANED_ONLY to stringResource(R.string.mode_cleaned_only),
                )
                modes.forEachIndexed { idx, (mode, label) ->
                    SegmentedButton(
                        selected = comparisonMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                        modifier = Modifier.testTag("mode_${mode.name.lowercase()}"),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }

            if (zoomScale > 1.05f) {
                Surface(
                    onClick = {
                        zoomScale = 1f
                        panOffset = Offset.Zero
                    },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("reset_zoom_button"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ZoomOutMap,
                            contentDescription = stringResource(R.string.cd_reset_zoom),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "${(zoomScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        // Main Comparison Viewport
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (comparisonMode) {
                ComparisonMode.SIDE_BY_SIDE -> {
                    SideBySideComparisonPane(
                        page = page,
                        originalBitmap = originalBitmap,
                        cleanedBitmap = cleanedBitmap,
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        onTransform = { zoomDelta, panDelta ->
                            val nextScale = (zoomScale * zoomDelta).coerceIn(1f, 4f)
                            zoomScale = nextScale
                            panOffset = if (nextScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                panOffset + panDelta
                            }
                        },
                        onEraseSinglePage = onEraseSinglePage,
                    )
                }

                ComparisonMode.SLIDER_WIPE -> {
                    SliderWipeComparisonPane(
                        page = page,
                        originalBitmap = originalBitmap,
                        cleanedBitmap = cleanedBitmap,
                        onEraseSinglePage = onEraseSinglePage,
                    )
                }

                ComparisonMode.CLEANED_ONLY -> {
                    CleanedOnlyPane(
                        page = page,
                        originalBitmap = originalBitmap,
                        cleanedBitmap = cleanedBitmap,
                        zoomScale = zoomScale,
                        panOffset = panOffset,
                        onTransform = { zoomDelta, panDelta ->
                            val nextScale = (zoomScale * zoomDelta).coerceIn(1f, 4f)
                            zoomScale = nextScale
                            panOffset = if (nextScale <= 1.01f) {
                                Offset.Zero
                            } else {
                                panOffset + panDelta
                            }
                        },
                        onEraseSinglePage = onEraseSinglePage,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBySideComparisonPane(
    page: DocumentPage,
    originalBitmap: ImageBitmap?,
    cleanedBitmap: ImageBitmap?,
    zoomScale: Float,
    panOffset: Offset,
    onTransform: (zoomDelta: Float, panDelta: Offset) -> Unit,
    onEraseSinglePage: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(page.index) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(zoom, pan)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Left: Original Exam Paper
        DocumentPaneCard(
            badgeText = stringResource(R.string.label_original),
            badgeColor = MaterialTheme.colorScheme.primary,
            badgeBg = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("original_pane"),
        ) {
            if (originalBitmap != null) {
                Image(
                    bitmap = originalBitmap,
                    contentDescription = stringResource(R.string.cd_original_page),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        },
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }

        // Right: Cleaned Exam Paper
        DocumentPaneCard(
            badgeText = stringResource(R.string.label_cleaned),
            badgeColor = MaterialTheme.colorScheme.secondary,
            badgeBg = MaterialTheme.colorScheme.secondaryContainer,
            isActiveClean = page.isCleaned,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag("cleaned_pane"),
        ) {
            CleanedPageSlot(
                page = page,
                cleanedBitmap = cleanedBitmap,
                zoomScale = zoomScale,
                panOffset = panOffset,
                onEraseSinglePage = onEraseSinglePage,
            )
        }
    }
}

@Composable
private fun SliderWipeComparisonPane(
    page: DocumentPage,
    originalBitmap: ImageBitmap?,
    cleanedBitmap: ImageBitmap?,
    onEraseSinglePage: (Int) -> Unit,
) {
    var splitFraction by remember(page.index) { mutableFloatStateOf(0.5f) }

    if (cleanedBitmap == null || page.status != PageEraseStatus.CLEANED) {
        DocumentPaneCard(
            badgeText = stringResource(R.string.label_cleaned),
            badgeColor = MaterialTheme.colorScheme.secondary,
            badgeBg = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxSize(),
        ) {
            CleanedPageSlot(
                page = page,
                cleanedBitmap = cleanedBitmap,
                zoomScale = 1f,
                panOffset = Offset.Zero,
                onEraseSinglePage = onEraseSinglePage,
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .pointerInput(page.index) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    if (size.width > 0) {
                        splitFraction = (splitFraction + dragAmount / size.width.toFloat())
                            .coerceIn(0.05f, 0.95f)
                    }
                }
            }
            .testTag("slider_wipe_canvas"),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val dividerX = widthPx * splitFraction

        // Base layer: Cleaned (Right side)
        Image(
            bitmap = cleanedBitmap,
            contentDescription = stringResource(R.string.cd_cleaned_page),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )

        // Clipped top layer: Original (Left side up to dividerX)
        if (originalBitmap != null) {
            val leftClipShape = remember(splitFraction) {
                object : Shape {
                    override fun createOutline(
                        size: androidx.compose.ui.geometry.Size,
                        layoutDirection: LayoutDirection,
                        density: Density,
                    ): androidx.compose.ui.graphics.Outline {
                        return androidx.compose.ui.graphics.Outline.Rectangle(
                            androidx.compose.ui.geometry.Rect(
                                0f,
                                0f,
                                size.width * splitFraction,
                                size.height,
                            ),
                        )
                    }
                }
            }
            Image(
                bitmap = originalBitmap,
                contentDescription = stringResource(R.string.cd_original_page),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(leftClipShape)
                    .padding(8.dp),
            )
        }

        // Top badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_original),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_cleaned),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        // Vertical Divider Line + Drag Knob
        Box(
            modifier = Modifier
                .offset { IntOffset(dividerX.roundToInt() - 1, 0) }
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((dividerX - with(density) { 20.dp.toPx() }).roundToInt(), 0) }
                .size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.CompareArrows,
                    contentDescription = "Drag to compare original and cleaned",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CleanedOnlyPane(
    page: DocumentPage,
    originalBitmap: ImageBitmap?,
    cleanedBitmap: ImageBitmap?,
    zoomScale: Float,
    panOffset: Offset,
    onTransform: (zoomDelta: Float, panDelta: Offset) -> Unit,
    onEraseSinglePage: (Int) -> Unit,
) {
    DocumentPaneCard(
        badgeText = if (page.isCleaned) {
            stringResource(R.string.label_cleaned)
        } else {
            stringResource(R.string.label_original)
        },
        badgeColor = if (page.isCleaned) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        badgeBg = if (page.isCleaned) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        isActiveClean = page.isCleaned,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(page.index) {
                detectTransformGestures { _, pan, zoom, _ ->
                    onTransform(zoom, pan)
                }
            },
    ) {
        if (page.isCleaned && cleanedBitmap != null) {
            Image(
                bitmap = cleanedBitmap,
                contentDescription = stringResource(R.string.cd_cleaned_page),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoomScale
                        scaleY = zoomScale
                        translationX = panOffset.x
                        translationY = panOffset.y
                    },
            )
        } else if (page.status == PageEraseStatus.PENDING && originalBitmap != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = originalBitmap,
                    contentDescription = stringResource(R.string.cd_original_page),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.45f },
                )
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Not erased yet",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Button(onClick = { onEraseSinglePage(page.index) }) {
                            Icon(Icons.Filled.AutoFixHigh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.action_erase_page))
                        }
                    }
                }
            }
        } else {
            CleanedPageSlot(
                page = page,
                cleanedBitmap = cleanedBitmap,
                zoomScale = zoomScale,
                panOffset = panOffset,
                onEraseSinglePage = onEraseSinglePage,
            )
        }
    }
}

@Composable
private fun DocumentPaneCard(
    badgeText: String,
    badgeColor: Color,
    badgeBg: Color,
    isActiveClean: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isActiveClean) 1.5.dp else 1.dp,
            color = if (isActiveClean) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        text = badgeText.uppercase(),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeColor,
                    )
                }
                if (isActiveClean) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CleanedPageSlot(
    page: DocumentPage,
    cleanedBitmap: ImageBitmap?,
    zoomScale: Float,
    panOffset: Offset,
    onEraseSinglePage: (Int) -> Unit,
) {
    when (page.status) {
        PageEraseStatus.PENDING -> {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "Ready to Erase",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Tap Erase below to remove handwritten ink and preserve printed text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                OutlinedButton(
                    onClick = { onEraseSinglePage(page.index) },
                    modifier = Modifier.testTag("erase_single_page_button"),
                ) {
                    Text(stringResource(R.string.action_erase_page))
                }
            }
        }

        PageEraseStatus.PROCESSING -> {
            val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
            val progress by shimmerTransition.animateFloat(
                initialValue = -0.3f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmer_pos",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                (progress - 0.25f).coerceIn(0f, 1f) to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                progress.coerceIn(0f, 1f) to MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                (progress + 0.25f).coerceIn(0f, 1f) to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        text = "Erasing ink…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Gemini Nano Banana is preserving printed characters (15–45s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        PageEraseStatus.CLEANED -> {
            if (cleanedBitmap != null) {
                Image(
                    bitmap = cleanedBitmap,
                    contentDescription = stringResource(R.string.cd_cleaned_page),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        },
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }

        PageEraseStatus.ERROR -> {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = page.errorMessage ?: "Processing failed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { onEraseSinglePage(page.index) },
                    modifier = Modifier.testTag("retry_page_button"),
                ) {
                    Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.action_retry_page))
                }
            }
        }
    }
}

@Composable
fun rememberDecodedBitmap(path: String?): androidx.compose.runtime.State<ImageBitmap?> {
    return produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(path)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
}
