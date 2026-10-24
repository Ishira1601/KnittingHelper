package com.example.knittinghelper.ui.chart

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.knittinghelper.ui.components.ZoomableBox
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartReaderScreen(
    projectId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChartViewModel = viewModel(
        factory = ChartViewModelFactory(context, projectId)
    )

    val project by viewModel.project.collectAsState()
    val chartConfig by viewModel.chartConfig.collectAsState()
    val activeBitmap by viewModel.activeBitmap.collectAsState()
    val cellColors by viewModel.cellColors.collectAsState()

    var showAlignPanel by remember { mutableStateOf(false) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }

    LaunchedEffect(chartConfig?.imageUriString) {
        viewModel.loadBitmap(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = project?.title ?: "Chart Reader") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleVerticalCursors() }) {
                        Icon(
                            imageVector = Icons.Default.ViewColumn,
                            contentDescription = "Toggle Cursors",
                            tint = if (chartConfig?.showVerticalCursors == true) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { showAlignPanel = !showAlignPanel }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Align Grid Live",
                            tint = if (showAlignPanel) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                    IconButton(onClick = { showCropDialog = true }) {
                        Icon(Icons.Default.Crop, contentDescription = "Crop Image")
                    }
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Chart Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val config = chartConfig

            if (config != null) {
                // Chart Display Canvas Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    ZoomableBox(modifier = Modifier.fillMaxSize()) { scale, _, _ ->
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(config, activeBitmap) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        val touchX = change.position.x

                                        val bitmap = activeBitmap ?: return@detectDragGestures
                                        val canvasW = size.width.toFloat()
                                        val canvasH = size.height.toFloat()

                                        val imgW = bitmap.width.toFloat()
                                        val imgH = bitmap.height.toFloat()
                                        val imgAspect = imgW / imgH
                                        val canvasAspect = canvasW / canvasH

                                        val (drawW, _) = if (imgAspect > canvasAspect) {
                                            canvasW to (canvasW / imgAspect)
                                        } else {
                                            (canvasH * imgAspect) to canvasH
                                        }

                                        val drawLeft = (canvasW - drawW) / 2f
                                        val gridLeft = drawLeft + drawW * config.offsetXRatio
                                        val gridWidth = drawW * config.gridWidthRatio
                                        val cellWidth = gridWidth / config.gridCols.coerceAtLeast(1)

                                        if (gridWidth > 0) {
                                            val relX = touchX - gridLeft

                                            val rawCol = if (config.isRightToLeft) {
                                                ((gridWidth - relX) / cellWidth).toInt() + 1
                                            } else {
                                                if (config.wsCountDecreases) {
                                                    config.gridCols - (relX / cellWidth).toInt()
                                                } else {
                                                    (relX / cellWidth).toInt() + 1
                                                }
                                            }

                                            viewModel.setStitch(rawCol)
                                        }
                                    }
                                }
                        ) {
                            val canvasW = size.width
                            val canvasH = size.height
                            val bitmap = activeBitmap

                            if (bitmap != null && !bitmap.isRecycled) {
                                val imgW = bitmap.width.toFloat()
                                val imgH = bitmap.height.toFloat()
                                val imgAspect = imgW / imgH
                                val canvasAspect = canvasW / canvasH

                                val (drawW, drawH) = if (imgAspect > canvasAspect) {
                                    canvasW to (canvasW / imgAspect)
                                } else {
                                    (canvasH * imgAspect) to canvasH
                                }

                                val drawLeft = (canvasW - drawW) / 2f
                                val drawTop = (canvasH - drawH) / 2f

                                // 1. Render High Quality Original Chart Image (Clean & Untouched)
                                drawImage(
                                    image = bitmap.asImageBitmap(),
                                    dstOffset = IntOffset(drawLeft.roundToInt(), drawTop.roundToInt()),
                                    dstSize = IntSize(drawW.roundToInt(), drawH.roundToInt()),
                                    filterQuality = FilterQuality.High
                                )

                                // 1.5. Render Solid Fill for >50% Coloured Grid Cells
                                val matrix = cellColors
                                if (config.fillCellColors && matrix != null) {
                                    val fillGridLeft = drawLeft + drawW * config.offsetXRatio
                                    val fillGridTop = drawTop + drawH * config.offsetYRatio
                                    val fillGridWidth = drawW * config.gridWidthRatio
                                    val fillGridHeight = drawH * config.gridHeightRatio

                                    val fillCellW = fillGridWidth / config.gridCols.coerceAtLeast(1)
                                    val fillCellH = fillGridHeight / config.gridRows.coerceAtLeast(1)

                                    val rCount = minOf(matrix.size, config.gridRows)
                                    for (r in 0 until rCount) {
                                        val rowArray = matrix[r]
                                        val cCount = minOf(rowArray.size, config.gridCols)
                                        for (c in 0 until cCount) {
                                            val cellColor = rowArray[c]
                                            if (cellColor != null) {
                                                val x = fillGridLeft + c * fillCellW
                                                val y = fillGridTop + r * fillCellH
                                                drawRect(
                                                    color = cellColor,
                                                    topLeft = Offset(x, y),
                                                    size = Size(fillCellW, fillCellH)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 2. Crisp Grid Lines
                                val gridLeft = drawLeft + drawW * config.offsetXRatio
                                val gridTop = drawTop + drawH * config.offsetYRatio
                                val gridWidth = drawW * config.gridWidthRatio
                                val gridHeight = drawH * config.gridHeightRatio

                                val cellWidth = gridWidth / config.gridCols.coerceAtLeast(1)
                                val cellHeight = gridHeight / config.gridRows.coerceAtLeast(1)

                                val baseLineWidth = (1.2f / scale).coerceIn(0.5f, 1.5f)
                                val majorLineWidth = (baseLineWidth * 1.8f).coerceAtLeast(1f)

                                // Draw Column Lines
                                for (col in 0..config.gridCols) {
                                    val x = (gridLeft + col * cellWidth).roundToInt().toFloat()
                                    val isMajor = (col > 0 && col % 5 == 0) || col == config.gridCols
                                    drawLine(
                                        color = if (isMajor) Color.Cyan.copy(alpha = 0.85f) else Color.Cyan.copy(alpha = 0.45f),
                                        start = Offset(x, gridTop.roundToInt().toFloat()),
                                        end = Offset(x, (gridTop + gridHeight).roundToInt().toFloat()),
                                        strokeWidth = if (isMajor) majorLineWidth else baseLineWidth
                                    )
                                }

                                // Draw Row Lines
                                for (row in 0..config.gridRows) {
                                    val y = (gridTop + row * cellHeight).roundToInt().toFloat()
                                    val isMajor = (row > 0 && row % 5 == 0) || row == config.gridRows
                                    drawLine(
                                        color = if (isMajor) Color.Cyan.copy(alpha = 0.85f) else Color.Cyan.copy(alpha = 0.45f),
                                        start = Offset(gridLeft.roundToInt().toFloat(), y),
                                        end = Offset((gridLeft + gridWidth).roundToInt().toFloat(), y),
                                        strokeWidth = if (isMajor) majorLineWidth else baseLineWidth
                                    )
                                }

                                // Render Vertical Cursors directly aligned to grid lines
                                if (config.showVerticalCursors) {
                                    val c1Col = config.cursorCol1.coerceIn(0, config.gridCols)
                                    val c2Col = config.cursorCol2.coerceIn(0, config.gridCols)

                                    val c1X = if (config.isRightToLeft) {
                                        gridLeft + gridWidth - c1Col * cellWidth
                                    } else {
                                        gridLeft + c1Col * cellWidth
                                    }

                                    val c2X = if (config.isRightToLeft) {
                                        gridLeft + gridWidth - c2Col * cellWidth
                                    } else {
                                        gridLeft + c2Col * cellWidth
                                    }

                                    val minX = minOf(c1X, c2X)
                                    val maxX = maxOf(c1X, c2X)

                                    // Span highlight between C1 and C2
                                    drawRect(
                                        color = Color.Green.copy(alpha = 0.2f),
                                        topLeft = Offset(minX, gridTop),
                                        size = Size(maxX - minX, gridHeight)
                                    )

                                    val cursorStroke = (4f / scale).coerceIn(2f, 6f)
                                    val handleRadius = (8.dp.toPx() / scale).coerceIn(5.dp.toPx(), 12.dp.toPx())

                                    // Cursor 1 (Green) - Aligned to grid line
                                    drawLine(
                                        color = Color.Green,
                                        start = Offset(c1X, gridTop),
                                        end = Offset(c1X, gridTop + gridHeight),
                                        strokeWidth = cursorStroke
                                    )
                                    drawCircle(
                                        color = Color.Green,
                                        radius = handleRadius,
                                        center = Offset(c1X, gridTop)
                                    )
                                    drawCircle(
                                        color = Color.Green,
                                        radius = handleRadius,
                                        center = Offset(c1X, gridTop + gridHeight)
                                    )

                                    // Cursor 2 (Magenta) - Aligned to grid line
                                    drawLine(
                                        color = Color.Magenta,
                                        start = Offset(c2X, gridTop),
                                        end = Offset(c2X, gridTop + gridHeight),
                                        strokeWidth = cursorStroke
                                    )
                                    drawCircle(
                                        color = Color.Magenta,
                                        radius = handleRadius,
                                        center = Offset(c2X, gridTop)
                                    )
                                    drawCircle(
                                        color = Color.Magenta,
                                        radius = handleRadius,
                                        center = Offset(c2X, gridTop + gridHeight)
                                    )
                                }

                                // Highlight Active Row
                                val activeRowIndexFromBottom = (config.currentRow - 1).coerceIn(0, config.gridRows - 1)
                                val activeRowY = gridTop + gridHeight - (activeRowIndexFromBottom + 1) * cellHeight

                                // Dim non-active rows
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    topLeft = Offset(gridLeft, gridTop),
                                    size = Size(gridWidth, (activeRowY - gridTop).coerceAtLeast(0f))
                                )
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.45f),
                                    topLeft = Offset(gridLeft, activeRowY + cellHeight),
                                    size = Size(gridWidth, ((gridTop + gridHeight) - (activeRowY + cellHeight)).coerceAtLeast(0f))
                                )

                                // Highlight Active Row Band
                                drawRect(
                                    color = Color.Yellow.copy(alpha = 0.35f),
                                    topLeft = Offset(gridLeft, activeRowY),
                                    size = Size(gridWidth, cellHeight)
                                )

                                // Highlight Active Stitch Cell (Draggable Marker)
                                val activeColIndex = (config.currentCol - 1).coerceIn(0, config.gridCols - 1)
                                val activeCellX = if (config.isRightToLeft) {
                                    gridLeft + gridWidth - (activeColIndex + 1) * cellWidth
                                } else {
                                    if (config.wsCountDecreases) {
                                        gridLeft + (config.gridCols - activeColIndex) * cellWidth - cellWidth
                                    } else {
                                        gridLeft + activeColIndex * cellWidth
                                    }
                                }

                                drawRect(
                                    color = Color.Red.copy(alpha = 0.75f),
                                    topLeft = Offset(activeCellX, activeRowY),
                                    size = Size(cellWidth, cellHeight)
                                )

                                drawRect(
                                    color = Color.Yellow,
                                    topLeft = Offset(activeCellX, activeRowY),
                                    size = Size(cellWidth, cellHeight),
                                    style = Stroke(width = (3f / scale).coerceIn(1.5f, 4f))
                                )
                            }
                        }
                    }
                }

                // Live On-Screen Grid Alignment Panel
                AnimatedVisibility(
                    visible = showAlignPanel,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📐 Live Grid Alignment Sliders", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { showAlignPanel = false }) {
                                    Text("Done")
                                }
                            }

                            // Offset X Slider
                            Column {
                                Text("Position X Offset: ${(config.offsetXRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = config.offsetXRatio,
                                    onValueChange = { viewModel.updateConfig(config.copy(offsetXRatio = it)) },
                                    valueRange = 0f..0.8f
                                )
                            }

                            // Offset Y Slider
                            Column {
                                Text("Position Y Offset: ${(config.offsetYRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = config.offsetYRatio,
                                    onValueChange = { viewModel.updateConfig(config.copy(offsetYRatio = it)) },
                                    valueRange = 0f..0.8f
                                )
                            }

                            // Width Scale Slider
                            Column {
                                Text("Grid Width Scale: ${(config.gridWidthRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = config.gridWidthRatio,
                                    onValueChange = { viewModel.updateConfig(config.copy(gridWidthRatio = it)) },
                                    valueRange = 0.2f..1f
                                )
                            }

                            // Height Scale Slider
                            Column {
                                Text("Grid Height Scale: ${(config.gridHeightRatio * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                Slider(
                                    value = config.gridHeightRatio,
                                    onValueChange = { viewModel.updateConfig(config.copy(gridHeightRatio = it)) },
                                    valueRange = 0.2f..1f
                                )
                            }

                            // Quick Row & Col Steppers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Rows: ${config.gridRows}  ", style = MaterialTheme.typography.bodySmall)
                                    IconButton(
                                        onClick = { if (config.gridRows > 1) viewModel.updateConfig(config.copy(gridRows = config.gridRows - 1)) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "- Row")
                                    }
                                    IconButton(
                                        onClick = { viewModel.updateConfig(config.copy(gridRows = config.gridRows + 1)) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+ Row")
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Cols: ${config.gridCols}  ", style = MaterialTheme.typography.bodySmall)
                                    IconButton(
                                        onClick = { if (config.gridCols > 1) viewModel.updateConfig(config.copy(gridCols = config.gridCols - 1)) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "- Col")
                                    }
                                    IconButton(
                                        onClick = { viewModel.updateConfig(config.copy(gridCols = config.gridCols + 1)) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "+ Col")
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Control Navigation Bar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 6.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Current Row & Stitch Status Display
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Row ${config.currentRow} of ${config.gridRows}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Stitch ${config.currentCol} of ${config.gridCols}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Direction Toggle Chip
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleDirection() },
                                label = {
                                    Text(
                                        text = if (config.isRightToLeft) "RS: Right → Left" else "WS: Left → Right",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (config.isRightToLeft) Icons.AutoMirrored.Filled.ArrowBack else Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Direction"
                                    )
                                }
                            )
                        }

                        // Vertical Cursor Control Sliders & Arrow Steppers Card on Main Page
                        if (config.showVerticalCursors) {
                            val cursorDiff = abs(config.cursorCol1 - config.cursorCol2)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Vertical Cursors (0-${config.gridCols})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(
                                                text = "Diff: $cursorDiff st",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }

                                    // Cursor 1 Row: Label + Stepper Arrows
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cursor 1 (Green): Grid Line ${config.cursorCol1}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.updateCursors(config.cursorCol1 - 1, config.cursorCol2) },
                                                enabled = config.cursorCol1 > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "- Cursor 1")
                                            }
                                            IconButton(
                                                onClick = { viewModel.updateCursors(config.cursorCol1 + 1, config.cursorCol2) },
                                                enabled = config.cursorCol1 < config.gridCols,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "+ Cursor 1")
                                            }
                                        }
                                    }
                                    Slider(
                                        value = config.cursorCol1.toFloat(),
                                        onValueChange = { viewModel.updateCursors(it.roundToInt(), config.cursorCol2) },
                                        valueRange = 0f..config.gridCols.toFloat().coerceAtLeast(1f)
                                    )

                                    // Cursor 2 Row: Label + Stepper Arrows
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Cursor 2 (Magenta): Grid Line ${config.cursorCol2}",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = { viewModel.updateCursors(config.cursorCol1, config.cursorCol2 - 1) },
                                                enabled = config.cursorCol2 > 0,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "- Cursor 2")
                                            }
                                            IconButton(
                                                onClick = { viewModel.updateCursors(config.cursorCol1, config.cursorCol2 + 1) },
                                                enabled = config.cursorCol2 < config.gridCols,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "+ Cursor 2")
                                            }
                                        }
                                    }
                                    Slider(
                                        value = config.cursorCol2.toFloat(),
                                        onValueChange = { viewModel.updateCursors(config.cursorCol1, it.roundToInt()) },
                                        valueRange = 0f..config.gridCols.toFloat().coerceAtLeast(1f)
                                    )
                                }
                            }
                        }

                        // Navigation Steppers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Prev Row
                            OutlinedIconButton(onClick = { viewModel.prevRow() }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Previous Row")
                            }

                            // Prev Stitch
                            FilledTonalIconButton(
                                onClick = { viewModel.prevStitch() },
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous Stitch",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Reset
                            IconButton(onClick = { viewModel.resetProgress() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Progress")
                            }

                            // Next Stitch
                            FilledIconButton(
                                onClick = { viewModel.nextStitch() },
                                modifier = Modifier.size(60.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next Stitch",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Next Row
                            OutlinedIconButton(onClick = { viewModel.nextRow() }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Next Row")
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No chart image assigned to this project.")
                }
            }
        }

        if (showConfigDialog && chartConfig != null) {
            ChartGridConfigDialog(
                config = chartConfig!!,
                onDismiss = { showConfigDialog = false },
                onSave = { updatedConfig ->
                    viewModel.updateConfig(updatedConfig)
                    showConfigDialog = false
                }
            )
        }

        if (showCropDialog && chartConfig != null) {
            ChartCropDialog(
                config = chartConfig!!,
                onDismiss = { showCropDialog = false },
                onResetCrop = {
                    viewModel.resetCrop(context)
                    showCropDialog = false
                },
                onSaveCrop = { cLeft, cTop, cRight, cBottom ->
                    viewModel.applyBitmapCrop(context, cLeft, cTop, cRight, cBottom)
                    showCropDialog = false
                }
            )
        }
    }
}
