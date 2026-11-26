package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.security.KeyVaultStatus
import com.example.security.SecurityBacking

@Composable
fun EmptyStudioView(
    vaultStatus: KeyVaultStatus,
    onImportPdf: () -> Unit,
    onImportPhotos: () -> Unit,
    onOpenKeyVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            HeroRestorationPreviewCard()

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Restore marked exam papers to clean print.",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Import a PDF or exam photos. Each page is rendered to an image, processed by Gemini Nano Banana to strip handwriting while preserving printed text, compared side-by-side, and exported to PDF.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Primary Import Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onImportPdf,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("import_pdf_button"),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_import_pdf),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                OutlinedButton(
                    onClick = onImportPhotos,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .testTag("import_photos_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_import_photos),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            // 3-Step Pipeline Strip
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                shape = MaterialTheme.shapes.large,
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "PIPELINE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    PipelineStepRow(
                        step = "01",
                        title = "Page-to-Image Rendering",
                        subtitle = "Multi-page PDFs and photos become crisp page bitmaps.",
                        icon = Icons.Filled.PictureAsPdf,
                    )
                    PipelineStepRow(
                        step = "02",
                        title = "Nano Banana Ink Erasure",
                        subtitle = "Erases pen, pencil & scribbles while keeping printed questions.",
                        icon = Icons.Filled.AutoFixHigh,
                    )
                    PipelineStepRow(
                        step = "03",
                        title = "Side-by-Side Verify & PDF Export",
                        subtitle = "Compare original vs. cleaned pages and export a local PDF.",
                        icon = Icons.Outlined.Compare,
                    )
                }
            }

            // Hardware Enclave Readiness Card
            Surface(
                onClick = onOpenKeyVault,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("empty_state_enclave_card"),
                shape = MaterialTheme.shapes.medium,
                color = if (vaultStatus.hasKey) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (vaultStatus.hasKey) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (vaultStatus.hasKey) {
                                Icons.Outlined.VerifiedUser
                            } else {
                                Icons.Outlined.Lock
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (vaultStatus.hasKey) {
                                val label = when (vaultStatus.backing) {
                                    SecurityBacking.STRONGBOX -> "StrongBox Secure Enclave Ready"
                                    SecurityBacking.TEE_HARDWARE -> "Hardware TEE Enclave Ready"
                                    else -> "Keystore Enclave Ready"
                                }
                                "$label (${vaultStatus.maskedPreview})"
                            } else {
                                "Configure Gemini Nano Banana Key"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (vaultStatus.hasKey) {
                                "Model: ${vaultStatus.selectedModelId} · Tap to manage"
                            } else {
                                "Encrypted with AES-256-GCM inside Android Keystore. Never hardcoded."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineStepRow(
    step: String,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = step,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun HeroRestorationPreviewCard() {
    val transition = rememberInfiniteTransition(label = "wipe_beam")
    val sweepFraction by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sweep_fraction",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val emeraldColor = MaterialTheme.colorScheme.secondary
    val inkColor = MaterialTheme.colorScheme.onSurface
    val cardBg = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        primaryColor.copy(alpha = 0.08f),
                        emeraldColor.copy(alpha = 0.08f),
                    ),
                ),
            )
            .border(1.dp, borderColor, MaterialTheme.shapes.large),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            val docLeft = size.width * 0.08f
            val docWidth = size.width * 0.84f
            val docTop = size.height * 0.05f
            val docHeight = size.height * 0.90f
            val splitX = docLeft + docWidth * sweepFraction

            // Paper shadow & sheet
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.06f),
                topLeft = Offset(docLeft + 4f, docTop + 6f),
                size = Size(docWidth, docHeight),
                cornerRadius = CornerRadius(18f, 18f),
            )
            drawRoundRect(
                color = cardBg,
                topLeft = Offset(docLeft, docTop),
                size = Size(docWidth, docHeight),
                cornerRadius = CornerRadius(18f, 18f),
            )

            // Printed exam lines across the entire sheet
            val linePositions = listOf(0.24f, 0.42f, 0.60f, 0.78f)
            linePositions.forEachIndexed { idx, yFrac ->
                val y = docTop + docHeight * yFrac
                val lineW = if (idx % 2 == 0) docWidth * 0.62f else docWidth * 0.48f
                drawLine(
                    color = inkColor.copy(alpha = 0.72f),
                    start = Offset(docLeft + docWidth * 0.10f, y),
                    end = Offset(docLeft + docWidth * 0.10f + lineW, y),
                    strokeWidth = 5f,
                    cap = StrokeCap.Round,
                )
            }

            // Left side (before beam): blue/red handwritten scribbles overlaid on the printed sheet
            clipRect(left = docLeft, top = docTop, right = splitX, bottom = docTop + docHeight) {
                val scribblePath = Path().apply {
                    moveTo(docLeft + docWidth * 0.14f, docTop + docHeight * 0.34f)
                    cubicTo(
                        docLeft + docWidth * 0.25f, docTop + docHeight * 0.22f,
                        docLeft + docWidth * 0.32f, docTop + docHeight * 0.48f,
                        docLeft + docWidth * 0.46f, docTop + docHeight * 0.31f,
                    )
                    moveTo(docLeft + docWidth * 0.18f, docTop + docHeight * 0.70f)
                    cubicTo(
                        docLeft + docWidth * 0.30f, docTop + docHeight * 0.56f,
                        docLeft + docWidth * 0.44f, docTop + docHeight * 0.84f,
                        docLeft + docWidth * 0.65f, docTop + docHeight * 0.68f,
                    )
                }
                drawPath(
                    path = scribblePath,
                    color = primaryColor.copy(alpha = 0.85f),
                    style = Stroke(width = 6f, cap = StrokeCap.Round),
                )
                // Handwritten circle mark
                drawCircle(
                    color = Color(0xFFDC2626).copy(alpha = 0.75f),
                    radius = docHeight * 0.16f,
                    center = Offset(docLeft + docWidth * 0.72f, docTop + docHeight * 0.40f),
                    style = Stroke(width = 4.5f),
                )
            }

            // Active eraser scanner beam
            drawLine(
                color = emeraldColor,
                start = Offset(splitX, docTop - 8f),
                end = Offset(splitX, docTop + docHeight + 8f),
                strokeWidth = 5f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = emeraldColor,
                radius = 10f,
                center = Offset(splitX, docTop + docHeight * 0.5f),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                color = primaryColor.copy(alpha = 0.12f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "HANDWRITTEN INPUT",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Surface(
                color = emeraldColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    text = "CLEANED PRINT",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = emeraldColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
