package com.example.knittinghelper.ui.components

import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun ZoomableBox(
    modifier: Modifier = Modifier,
    minScale: Float = 1f,
    maxScale: Float = 5f,
    content: @Composable BoxScope.(scale: Float, offsetX: Float, offsetY: Float) -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.size

                        if (pointerCount >= 2) {
                            // Multi-touch: Pinch to zoom and 2-finger pan
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            scale = (scale * zoom).coerceIn(minScale, maxScale)

                            val extraWidth = (scale - 1) * size.width
                            val extraHeight = (scale - 1) * size.height
                            val maxX = extraWidth / 2
                            val maxY = extraHeight / 2

                            if (scale > 1f) {
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        } else if (pointerCount == 1 && scale > 1f) {
                            // Single touch when zoomed: vertical pan only, allowing horizontal drag for stitch marker
                            val pan = event.calculatePan()
                            val extraHeight = (scale - 1) * size.height
                            val maxY = extraHeight / 2
                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                        } else if (scale <= 1f) {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offsetX
                translationY = offsetY
            }
    ) {
        content(scale, offsetX, offsetY)
    }
}
