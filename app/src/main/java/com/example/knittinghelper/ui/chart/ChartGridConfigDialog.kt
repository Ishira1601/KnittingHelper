package com.example.knittinghelper.ui.chart

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.knittinghelper.data.model.ChartConfig
import kotlin.math.abs

@Composable
fun ChartGridConfigDialog(
    config: ChartConfig,
    onDismiss: () -> Unit,
    onSave: (ChartConfig) -> Unit
) {
    var gridRowsText by remember { mutableStateOf(config.gridRows.toString()) }
    var gridColsText by remember { mutableStateOf(config.gridCols.toString()) }
    var offsetX by remember { mutableFloatStateOf(config.offsetXRatio) }
    var offsetY by remember { mutableFloatStateOf(config.offsetYRatio) }
    var gridWidthScale by remember { mutableFloatStateOf(config.gridWidthRatio) }
    var gridHeightScale by remember { mutableFloatStateOf(config.gridHeightRatio) }
    var autoReverse by remember { mutableStateOf(config.autoReverseDirection) }
    var wsDecreases by remember { mutableStateOf(config.wsCountDecreases) }
    var showCursors by remember { mutableStateOf(config.showVerticalCursors) }
    var cursor1 by remember { mutableIntStateOf(config.cursorCol1) }
    var cursor2 by remember { mutableIntStateOf(config.cursorCol2) }
    var fillCells by remember { mutableStateOf(config.fillCellColors) }
    var fillThreshold by remember { mutableFloatStateOf(config.cellColorThreshold) }

    val totalCols = gridColsText.toIntOrNull()?.coerceAtLeast(1) ?: config.gridCols

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grid Alignment & Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Grid Size
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = gridRowsText,
                        onValueChange = { gridRowsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Grid Rows") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = gridColsText,
                        onValueChange = { gridColsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Grid Columns") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                Text("Grid Offset & Alignment", style = MaterialTheme.typography.titleSmall)

                // Offset X Slider
                Column {
                    Text("Position X Offset: ${(offsetX * 100).toInt()}%")
                    Slider(
                        value = offsetX,
                        onValueChange = { offsetX = it },
                        valueRange = 0f..0.8f
                    )
                }

                // Offset Y Slider
                Column {
                    Text("Position Y Offset: ${(offsetY * 100).toInt()}%")
                    Slider(
                        value = offsetY,
                        onValueChange = { offsetY = it },
                        valueRange = 0f..0.8f
                    )
                }

                // Width Scale
                Column {
                    Text("Grid Width Scale: ${(gridWidthScale * 100).toInt()}%")
                    Slider(
                        value = gridWidthScale,
                        onValueChange = { gridWidthScale = it },
                        valueRange = 0.2f..1f
                    )
                }

                // Height Scale
                Column {
                    Text("Grid Height Scale: ${(gridHeightScale * 100).toInt()}%")
                    Slider(
                        value = gridHeightScale,
                        onValueChange = { gridHeightScale = it },
                        valueRange = 0.2f..1f
                    )
                }

                HorizontalDivider()

                Text("Vertical Cursors & Repeat Measurement", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Dual Vertical Cursors")
                    Switch(
                        checked = showCursors,
                        onCheckedChange = { showCursors = it }
                    )
                }

                if (showCursors) {
                    Column {
                        Text("Cursor 1 (Grid Line $cursor1)")
                        Slider(
                            value = cursor1.toFloat(),
                            onValueChange = { cursor1 = it.toInt().coerceIn(0, totalCols) },
                            valueRange = 0f..totalCols.toFloat()
                        )
                    }

                    Column {
                        Text("Cursor 2 (Grid Line $cursor2)")
                        Slider(
                            value = cursor2.toFloat(),
                            onValueChange = { cursor2 = it.toInt().coerceIn(0, totalCols) },
                            valueRange = 0f..totalCols.toFloat()
                        )
                    }

                    val diff = abs(cursor1 - cursor2)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Cursor Difference:", style = MaterialTheme.typography.bodyMedium)
                            Text("$diff stitches", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                HorizontalDivider()

                Text("Cell Color Filling", style = MaterialTheme.typography.titleSmall)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Solid Fill Coloured Cells", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Fill cell with solid colour if >50% coloured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = fillCells,
                        onCheckedChange = { fillCells = it }
                    )
                }

                if (fillCells) {
                    Column {
                        Text("Colour Fill Threshold: ${(fillThreshold * 100).toInt()}%")
                        Slider(
                            value = fillThreshold,
                            onValueChange = { fillThreshold = it },
                            valueRange = 0.2f..0.8f
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-reverse RS/WS Direction")
                    Switch(
                        checked = autoReverse,
                        onCheckedChange = { autoReverse = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("WS Stitch Countdown", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Stitch count decreases on Left → Right (WS) rows",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = wsDecreases,
                        onCheckedChange = { wsDecreases = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val rows = gridRowsText.toIntOrNull()?.coerceAtLeast(1) ?: 20
                    val cols = gridColsText.toIntOrNull()?.coerceAtLeast(1) ?: 20
                    onSave(
                        config.copy(
                            gridRows = rows,
                            gridCols = cols,
                            offsetXRatio = offsetX,
                            offsetYRatio = offsetY,
                            gridWidthRatio = gridWidthScale,
                            gridHeightRatio = gridHeightScale,
                            autoReverseDirection = autoReverse,
                            wsCountDecreases = wsDecreases,
                            showVerticalCursors = showCursors,
                            cursorCol1 = cursor1.coerceIn(1, cols),
                            cursorCol2 = cursor2.coerceIn(1, cols),
                            fillCellColors = fillCells,
                            cellColorThreshold = fillThreshold
                        )
                    )
                }
            ) {
                Text("Apply & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
