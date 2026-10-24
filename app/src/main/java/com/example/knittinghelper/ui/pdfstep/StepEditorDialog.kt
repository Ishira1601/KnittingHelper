package com.example.knittinghelper.ui.pdfstep

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.knittinghelper.data.model.PatternStep

@Composable
fun StepEditorDialog(
    existingStep: PatternStep?,
    pdfPageCount: Int,
    initialPageIndex: Int,
    onDismiss: () -> Unit,
    onSave: (title: String, targetRows: Int, pdfPageIndex: Int, notes: String) -> Unit
) {
    var title by remember { mutableStateOf(existingStep?.title ?: "") }
    var notes by remember { mutableStateOf(existingStep?.notes ?: "") }
    var targetRowsText by remember { mutableStateOf(existingStep?.targetRows?.toString() ?: "10") }
    var pageIndexText by remember { mutableStateOf(((existingStep?.pdfPageIndex ?: initialPageIndex) + 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = if (existingStep != null) "Edit Step" else "Add Pattern Step")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Step Title (e.g. Ribbing, Collar)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = targetRowsText,
                        onValueChange = { targetRowsText = it.filter { c -> c.isDigit() } },
                        label = { Text("Target Rows") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = pageIndexText,
                        onValueChange = { pageIndexText = it.filter { c -> c.isDigit() } },
                        label = { Text("PDF Page (1-${pdfPageCount.coerceAtLeast(1)})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Step Instructions / Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = {
                    val targetRows = targetRowsText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val page1Based = pageIndexText.toIntOrNull() ?: 1
                    val zeroBasedPage = (page1Based - 1).coerceIn(0, (pdfPageCount - 1).coerceAtLeast(0))
                    onSave(title, targetRows, zeroBasedPage, notes)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
