package com.example.knittinghelper.ui.chart

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.knittinghelper.data.model.ChartConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ChartCropDialog(
    config: ChartConfig,
    onDismiss: () -> Unit,
    onResetCrop: () -> Unit,
    onSaveCrop: (cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float) -> Unit
) {
    val context = LocalContext.current

    var cropLeft by remember { mutableFloatStateOf(config.cropLeft) }
    var cropTop by remember { mutableFloatStateOf(config.cropTop) }
    var cropRight by remember { mutableFloatStateOf(config.cropRight) }
    var cropBottom by remember { mutableFloatStateOf(config.cropBottom) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(config.getEffectiveOriginalImageUri()) {
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(config.getEffectiveOriginalImageUri())
                context.contentResolver.openInputStream(uri)?.use { input ->
                    originalBitmap = BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Crop Chart Boundaries", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Interactive Preview with Crop Bounding Box matching image aspect ratio exactly
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val bitmap = originalBitmap
                        if (bitmap != null && !bitmap.isRecycled) {
                            val imgW = bitmap.width.toFloat()
                            val imgH = bitmap.height.toFloat()
                            val imgAspect = imgW / imgH
                            val canvasAspect = size.width / size.height

                            val (drawW, drawH) = if (imgAspect > canvasAspect) {
                                size.width to (size.width / imgAspect)
                            } else {
                                (size.height * imgAspect) to size.height
                            }

                            val drawLeft = (size.width - drawW) / 2f
                            val drawTop = (size.height - drawH) / 2f

                            // Draw original image
                            drawImage(
                                image = bitmap.asImageBitmap(),
                                dstOffset = IntOffset(drawLeft.toInt(), drawTop.toInt()),
                                dstSize = IntSize(drawW.toInt(), drawH.toInt())
                            )

                            // Calculate Crop Bounding Box relative to the drawn image
                            val cropX = drawLeft + drawW * cropLeft
                            val cropY = drawTop + drawH * cropTop
                            val cropW = (drawW * (cropRight - cropLeft)).coerceAtLeast(10f)
                            val cropH = (drawH * (cropBottom - cropTop)).coerceAtLeast(10f)

                            // Darken area outside crop rectangle
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(drawLeft, drawTop),
                                size = Size(drawW, (cropY - drawTop).coerceAtLeast(0f))
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(drawLeft, cropY + cropH),
                                size = Size(drawW, ((drawTop + drawH) - (cropY + cropH)).coerceAtLeast(0f))
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(drawLeft, cropY),
                                size = Size((cropX - drawLeft).coerceAtLeast(0f), cropH)
                            )
                            drawRect(
                                color = Color.Black.copy(alpha = 0.65f),
                                topLeft = Offset(cropX + cropW, cropY),
                                size = Size(((drawLeft + drawW) - (cropX + cropW)).coerceAtLeast(0f), cropH)
                            )

                            // Magenta crop border box
                            drawRect(
                                color = Color.Magenta,
                                topLeft = Offset(cropX, cropY),
                                size = Size(cropW, cropH),
                                style = Stroke(width = 4f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Crop Margins", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = {
                        cropLeft = 0f
                        cropTop = 0f
                        cropRight = 1f
                        cropBottom = 1f
                        onResetCrop()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset to Full")
                    }
                }

                // Left Crop Slider
                Column {
                    Text("Crop Left Edge: ${(cropLeft * 100).toInt()}%")
                    Slider(
                        value = cropLeft,
                        onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                        valueRange = 0f..0.8f
                    )
                }

                // Right Crop Slider
                Column {
                    Text("Crop Right Edge: ${(cropRight * 100).toInt()}%")
                    Slider(
                        value = cropRight,
                        onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                        valueRange = 0.2f..1f
                    )
                }

                // Top Crop Slider
                Column {
                    Text("Crop Top Edge: ${(cropTop * 100).toInt()}%")
                    Slider(
                        value = cropTop,
                        onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                        valueRange = 0f..0.8f
                    )
                }

                // Bottom Crop Slider
                Column {
                    Text("Crop Bottom Edge: ${(cropBottom * 100).toInt()}%")
                    Slider(
                        value = cropBottom,
                        onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                        valueRange = 0.2f..1f
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveCrop(cropLeft, cropTop, cropRight, cropBottom)
                }
            ) {
                Text("Crop & Align Grid")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
