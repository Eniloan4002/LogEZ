package com.enil.logez.feature.workout.finish.share

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Share flow for the post-workout summary: live preview, Square/Story selection, and the system
 * ACTION_SEND chooser. Export goes through [WorkoutShareController]; nothing here (or in the
 * ViewModel) performs the platform side-effects itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSummarySheet(
    data: ShareCardData,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Fetched through a Hilt entry point rather than the ViewModel: the controller consumes an
    // android.graphics.Bitmap, which the spine testing rule keeps out of ViewModel signatures.
    val shareController = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, WorkoutShareEntryPoint::class.java)
            .workoutShareController()
    }
    var format by rememberSaveable { mutableStateOf(ShareCardFormat.SQUARE) }
    var isExporting by remember { mutableStateOf(false) }
    var exportFailed by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // Scrollable: in landscape the sheet is shorter than title+preview+chips+button, and the
        // share button must stay reachable.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.md)
                .padding(bottom = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.share_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            ScaledCardPreview(data, format, graphicsLayer, Modifier.padding(top = Spacing.md))

            Row(
                modifier = Modifier.padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                FormatChip(R.string.share_format_square, format == ShareCardFormat.SQUARE) { format = ShareCardFormat.SQUARE }
                FormatChip(R.string.share_format_story, format == ShareCardFormat.STORY) { format = ShareCardFormat.STORY }
            }

            if (exportFailed) {
                Text(
                    stringResource(R.string.share_export_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                )
            }

            Button(
                onClick = {
                    scope.launch {
                        isExporting = true
                        exportFailed = false
                        try {
                            val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                            val uri = shareController.exportPng(bitmap, format)
                            shareController.launchShareChooser(context, uri)
                            onDismiss()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            exportFailed = true
                        } finally {
                            isExporting = false
                        }
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.share_action))
                }
            }
        }
    }
}

/**
 * The preview IS the export. One [ShareCard] composition at full capture resolution (fixed
 * [ShareCardExportDensity], so its node measures 1080px wide), recorded into [graphicsLayer] on
 * every draw, then visually shrunk with a graphicsLayer scale — never re-laid-out at a smaller
 * size, which is what guarantees the shared PNG matches the preview pixel-for-pixel.
 */
@Composable
private fun ScaledCardPreview(
    data: ShareCardData,
    format: ShareCardFormat,
    graphicsLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardWidthPx = with(ShareCardExportDensity) { format.width.toPx() }
    val cardHeightPx = with(ShareCardExportDensity) { format.height.toPx() }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(PREVIEW_AREA_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        val scale = with(density) {
            minOf(maxWidth.toPx() / cardWidthPx, maxHeight.toPx() / cardHeightPx).coerceAtMost(1f)
        }
        val scaledSize = with(density) { DpSize((cardWidthPx * scale).toDp(), (cardHeightPx * scale).toDp()) }

        Box(
            modifier = Modifier.size(scaledSize.width, scaledSize.height),
            contentAlignment = Alignment.Center,
        ) {
            // Center-origin scale on a box the card overflows symmetrically -> the scaled card
            // lands exactly inside the sized wrapper above. wrapContentSize(unbounded = true)
            // measures the card without the preview slot's max constraints, so its requiredSize
            // reports the full capture resolution and graphicsLayer.record{} (which defaults to
            // the draw scope's size) captures the whole 1080px-wide card instead of a crop.
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .wrapContentSize(unbounded = true),
            ) {
                CompositionLocalProvider(LocalDensity provides ShareCardExportDensity) {
                    ShareCard(
                        data = data,
                        format = format,
                        modifier = Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                    )
                }
            }
        }
    }
}

private val PREVIEW_AREA_HEIGHT = 300.dp

/** Pill/mono-caps selection chip in the v4.0 chip vocabulary (AnalyticsScreen's MetricChip). */
@Composable
private fun FormatChip(@StringRes labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) primary else Color.Transparent)
            .let { if (selected) it else it.border(1.dp, MaterialTheme.colorScheme.outline, shape) }
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(
            stringResource(labelRes).uppercase(Locale.getDefault()),
            style = LogEzMono.dataSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.08.em,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}
