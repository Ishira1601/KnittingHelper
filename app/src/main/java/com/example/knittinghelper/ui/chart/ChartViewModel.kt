package com.example.knittinghelper.ui.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.knittinghelper.data.model.ChartConfig
import com.example.knittinghelper.data.model.Project
import com.example.knittinghelper.data.repository.KnittingRepository
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class ChartViewModel(
    private val repository: KnittingRepository,
    private val projectId: String
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _chartConfig = MutableStateFlow<ChartConfig?>(null)
    val chartConfig: StateFlow<ChartConfig?> = _chartConfig.asStateFlow()

    private val _activeBitmap = MutableStateFlow<Bitmap?>(null)
    val activeBitmap: StateFlow<Bitmap?> = _activeBitmap.asStateFlow()

    private val _cellColors = MutableStateFlow<Array<Array<Color?>>?>(null)
    val cellColors: StateFlow<Array<Array<Color?>>?> = _cellColors.asStateFlow()

    private var colorCalculationJob: Job? = null

    init {
        loadData()
    }

    private fun loadData() {
        val proj = repository.getProjectById(projectId)
        _project.value = proj

        val config = repository.getChartConfigForProject(projectId) ?: proj?.chartImageUriString?.let { uri ->
            ChartConfig(
                projectId = projectId,
                imageUriString = uri,
                originalImageUriString = uri
            )
        }
        _chartConfig.value = config
        if (config != null) {
            repository.saveChartConfig(config)
        }
        recalculateCellColors()
    }

    fun loadBitmap(context: Context) {
        val config = _chartConfig.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = Uri.parse(config.imageUriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                _activeBitmap.value = bmp
                recalculateCellColors()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateConfig(config: ChartConfig) {
        _chartConfig.value = config
        recalculateCellColors()
        viewModelScope.launch {
            repository.saveChartConfig(config)
        }
    }

    fun recalculateCellColors() {
        colorCalculationJob?.cancel()
        val bitmap = _activeBitmap.value
        val config = _chartConfig.value

        if (bitmap == null || bitmap.isRecycled || config == null || !config.fillCellColors) {
            _cellColors.value = null
            return
        }

        colorCalculationJob = viewModelScope.launch(Dispatchers.Default) {
            val rows = config.gridRows.coerceAtLeast(1)
            val cols = config.gridCols.coerceAtLeast(1)
            val threshold = config.cellColorThreshold.coerceIn(0.1f, 0.9f)

            val bmpW = bitmap.width
            val bmpH = bitmap.height

            val gridLeftPx = (config.offsetXRatio * bmpW).roundToInt().coerceIn(0, bmpW - 1)
            val gridTopPx = (config.offsetYRatio * bmpH).roundToInt().coerceIn(0, bmpH - 1)
            val gridWidthPx = (config.gridWidthRatio * bmpW).roundToInt().coerceIn(1, bmpW - gridLeftPx)
            val gridHeightPx = (config.gridHeightRatio * bmpH).roundToInt().coerceIn(1, bmpH - gridTopPx)

            val cellWidthPx = gridWidthPx.toFloat() / cols
            val cellHeightPx = gridHeightPx.toFloat() / rows

            val pixels = IntArray(bmpW * bmpH)
            try {
                bitmap.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
            } catch (e: Exception) {
                _cellColors.value = null
                return@launch
            }

            val matrix = Array(rows) { Array<Color?>(cols) { null } }

            for (r in 0 until rows) {
                val cellTop = (gridTopPx + r * cellHeightPx).toInt().coerceIn(0, bmpH - 1)
                val cellBottom = (gridTopPx + (r + 1) * cellHeightPx).toInt().coerceIn(cellTop + 1, bmpH)

                for (c in 0 until cols) {
                    val cellLeft = (gridLeftPx + c * cellWidthPx).toInt().coerceIn(0, bmpW - 1)
                    val cellRight = (gridLeftPx + (c + 1) * cellWidthPx).toInt().coerceIn(cellLeft + 1, bmpW)

                    var totalSampled = 0
                    var coloredCount = 0
                    var sumR = 0L
                    var sumG = 0L
                    var sumB = 0L

                    val stepX = ((cellRight - cellLeft) / 15).coerceAtLeast(1)
                    val stepY = ((cellBottom - cellTop) / 15).coerceAtLeast(1)

                    for (y in cellTop until cellBottom step stepY) {
                        val rowOffset = y * bmpW
                        for (x in cellLeft until cellRight step stepX) {
                            val pixel = pixels[rowOffset + x]
                            val red = (pixel shr 16) and 0xFF
                            val green = (pixel shr 8) and 0xFF
                            val blue = pixel and 0xFF

                            totalSampled++

                            val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
                            val maxC = maxOf(red, maxOf(green, blue))
                            val minC = minOf(red, minOf(green, blue))
                            val saturation = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f

                            val isPaperBackground = (luminance > 210f && saturation < 0.22f)

                            if (!isPaperBackground) {
                                coloredCount++
                                sumR += red
                                sumG += green
                                sumB += blue
                            }
                        }
                    }

                    if (totalSampled > 0) {
                        val coloredRatio = coloredCount.toFloat() / totalSampled
                        if (coloredRatio >= threshold && coloredCount > 0) {
                            val avgR = (sumR / coloredCount).toInt().coerceIn(0, 255)
                            val avgG = (sumG / coloredCount).toInt().coerceIn(0, 255)
                            val avgB = (sumB / coloredCount).toInt().coerceIn(0, 255)
                            matrix[r][c] = Color(avgR, avgG, avgB)
                        }
                    }
                }
            }

            _cellColors.value = matrix
        }
    }

    fun toggleVerticalCursors() {
        val config = _chartConfig.value ?: return
        updateConfig(config.copy(showVerticalCursors = !config.showVerticalCursors))
    }

    fun updateCursors(col1: Int, col2: Int) {
        val config = _chartConfig.value ?: return
        val c1 = col1.coerceIn(0, config.gridCols)
        val c2 = col2.coerceIn(0, config.gridCols)
        updateConfig(config.copy(cursorCol1 = c1, cursorCol2 = c2))
    }

    fun setStitch(col: Int) {
        val config = _chartConfig.value ?: return
        val clamped = col.coerceIn(1, config.gridCols)
        if (config.currentCol != clamped) {
            updateConfig(config.copy(currentCol = clamped))
        }
    }

    fun setRow(row: Int) {
        val config = _chartConfig.value ?: return
        val clamped = row.coerceIn(1, config.gridRows)
        if (config.currentRow != clamped) {
            updateConfig(config.copy(currentRow = clamped))
        }
    }

    fun applyBitmapCrop(context: Context, cropLeft: Float, cropTop: Float, cropRight: Float, cropBottom: Float) {
        val config = _chartConfig.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val originalUriStr = config.getEffectiveOriginalImageUri()
                val originalUri = Uri.parse(originalUriStr)
                val inputStream = context.contentResolver.openInputStream(originalUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val origWidth = bitmap.width
                    val origHeight = bitmap.height

                    val leftX = (origWidth * cropLeft).toInt().coerceIn(0, origWidth - 1)
                    val topY = (origHeight * cropTop).toInt().coerceIn(0, origHeight - 1)
                    val cropW = (origWidth * (cropRight - cropLeft)).toInt().coerceIn(1, origWidth - leftX)
                    val cropH = (origHeight * (cropBottom - cropTop)).toInt().coerceIn(1, origHeight - topY)

                    val croppedBitmap = Bitmap.createBitmap(bitmap, leftX, topY, cropW, cropH)
                    val newUriString = repository.saveBitmapToInternalStorage(croppedBitmap, config.projectId)

                    val updated = config.copy(
                        imageUriString = newUriString ?: config.imageUriString,
                        originalImageUriString = originalUriStr,
                        cropLeft = cropLeft,
                        cropTop = cropTop,
                        cropRight = cropRight,
                        cropBottom = cropBottom,
                        offsetXRatio = 0f,
                        offsetYRatio = 0f,
                        gridWidthRatio = 1f,
                        gridHeightRatio = 1f
                    )
                    updateConfig(updated)
                    _activeBitmap.value = croppedBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetCrop(context: Context) {
        val config = _chartConfig.value ?: return
        val origUriStr = config.getEffectiveOriginalImageUri()
        val updated = config.copy(
            imageUriString = origUriStr,
            originalImageUriString = origUriStr,
            cropLeft = 0f,
            cropTop = 0f,
            cropRight = 1f,
            cropBottom = 1f,
            offsetXRatio = 0f,
            offsetYRatio = 0f,
            gridWidthRatio = 1f,
            gridHeightRatio = 1f
        )
        updateConfig(updated)
        loadBitmap(context)
    }

    fun nextStitch() {
        val config = _chartConfig.value ?: return
        var col = config.currentCol
        var row = config.currentRow
        var isRtl = config.isRightToLeft

        if (isRtl) {
            if (col < config.gridCols) {
                col++
            } else {
                if (row < config.gridRows) {
                    row++
                    if (config.autoReverseDirection) {
                        isRtl = false
                        col = if (config.wsCountDecreases) config.gridCols else 1
                    } else {
                        col = 1
                    }
                }
            }
        } else {
            if (config.wsCountDecreases) {
                if (col > 1) {
                    col--
                } else {
                    if (row < config.gridRows) {
                        row++
                        if (config.autoReverseDirection) {
                            isRtl = true
                            col = 1
                        } else {
                            col = config.gridCols
                        }
                    }
                }
            } else {
                if (col < config.gridCols) {
                    col++
                } else {
                    if (row < config.gridRows) {
                        row++
                        if (config.autoReverseDirection) {
                            isRtl = true
                            col = 1
                        } else {
                            col = 1
                        }
                    }
                }
            }
        }

        updateConfig(config.copy(currentRow = row, currentCol = col, isRightToLeft = isRtl))
    }

    fun prevStitch() {
        val config = _chartConfig.value ?: return
        var col = config.currentCol
        var row = config.currentRow
        var isRtl = config.isRightToLeft

        if (isRtl) {
            if (col > 1) {
                col--
            } else {
                if (row > 1) {
                    row--
                    if (config.autoReverseDirection) {
                        isRtl = false
                        col = if (config.wsCountDecreases) 1 else config.gridCols
                    } else {
                        col = config.gridCols
                    }
                }
            }
        } else {
            if (config.wsCountDecreases) {
                if (col < config.gridCols) {
                    col++
                } else {
                    if (row > 1) {
                        row--
                        if (config.autoReverseDirection) {
                            isRtl = true
                            col = config.gridCols
                        } else {
                            col = 1
                        }
                    }
                }
            } else {
                if (col > 1) {
                    col--
                } else {
                    if (row > 1) {
                        row--
                        if (config.autoReverseDirection) {
                            isRtl = true
                            col = config.gridCols
                        } else {
                            col = config.gridCols
                        }
                    }
                }
            }
        }

        updateConfig(config.copy(currentRow = row, currentCol = col, isRightToLeft = isRtl))
    }

    fun nextRow() {
        val config = _chartConfig.value ?: return
        if (config.currentRow < config.gridRows) {
            val newRow = config.currentRow + 1
            val newRtl = if (config.autoReverseDirection) !config.isRightToLeft else config.isRightToLeft
            val newCol = if (newRtl) {
                1
            } else {
                if (config.wsCountDecreases) config.gridCols else 1
            }
            updateConfig(config.copy(currentRow = newRow, currentCol = newCol, isRightToLeft = newRtl))
        }
    }

    fun prevRow() {
        val config = _chartConfig.value ?: return
        if (config.currentRow > 1) {
            val newRow = config.currentRow - 1
            val newRtl = if (config.autoReverseDirection) !config.isRightToLeft else config.isRightToLeft
            val newCol = if (newRtl) {
                1
            } else {
                if (config.wsCountDecreases) config.gridCols else 1
            }
            updateConfig(config.copy(currentRow = newRow, currentCol = newCol, isRightToLeft = newRtl))
        }
    }

    fun resetProgress() {
        val config = _chartConfig.value ?: return
        updateConfig(config.copy(currentRow = 1, currentCol = 1, isRightToLeft = true))
    }

    fun toggleDirection() {
        val config = _chartConfig.value ?: return
        updateConfig(config.copy(isRightToLeft = !config.isRightToLeft))
    }
}

class ChartViewModelFactory(
    private val context: Context,
    private val projectId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = KnittingRepository(context)
        return ChartViewModel(repository, projectId) as T
    }
}
