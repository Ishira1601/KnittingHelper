package com.example.knittinghelper.ui.pdfstep

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.knittinghelper.ui.components.RowCounterWidget
import com.example.knittinghelper.ui.components.ZoomableBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfStepScreen(
    projectId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PdfStepViewModel = viewModel(
        factory = PdfStepViewModelFactory(context, projectId)
    )

    val project by viewModel.project.collectAsState()
    val steps by viewModel.steps.collectAsState()
    val activeStepIndex by viewModel.activeStepIndex.collectAsState()
    val currentPageBitmap by viewModel.currentPageBitmap.collectAsState()
    val currentPageIndex by viewModel.currentPageIndex.collectAsState()
    val pdfPageCount by viewModel.pdfPageCount.collectAsState()
    val isLoadingPdf by viewModel.isLoadingPdf.collectAsState()

    var showStepDialog by remember { mutableStateOf(false) }
    var isEditingExisting by remember { mutableStateOf(false) }

    val activeStep = steps.getOrNull(activeStepIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = project?.title ?: "Pattern Steps",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isEditingExisting = false
                        showStepDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Step")
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
            // Step Selection Carousel / Chips
            if (steps.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(steps) { index, step ->
                        FilterChip(
                            selected = index == activeStepIndex,
                            onClick = { viewModel.selectStep(index) },
                            label = {
                                Text(
                                    text = "${index + 1}. ${step.title}",
                                    fontWeight = if (index == activeStepIndex) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            trailingIcon = {
                                if (step.isCompleted) {
                                    Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "No steps created yet. Tap '+' to add a step!")
                        Button(onClick = {
                            isEditingExisting = false
                            showStepDialog = true
                        }) {
                            Text("Add Step")
                        }
                    }
                }
            }

            // PDF Viewer Area (Weight 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isLoadingPdf) {
                    CircularProgressIndicator()
                } else if (currentPageBitmap != null) {
                    ZoomableBox(modifier = Modifier.fillMaxSize()) { _, _, _ ->
                        Image(
                            bitmap = currentPageBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPageIndex + 1}",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Text("No PDF pattern available or failed to render page.")
                }

                // PDF Page Navigation Bar overlay at bottom of PDF preview
                if (pdfPageCount > 1) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.loadPdfPage(currentPageIndex - 1) },
                                enabled = currentPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Page")
                            }

                            Text(
                                text = "Page ${currentPageIndex + 1} of $pdfPageCount",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { viewModel.loadPdfPage(currentPageIndex + 1) },
                                enabled = currentPageIndex < pdfPageCount - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Page")
                            }
                        }
                    }
                }
            }

            // Bottom Section: Step Active Details & Row Counter
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeStep != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeStep.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (activeStep.notes.isNotBlank()) {
                                Text(
                                    text = activeStep.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = {
                                isEditingExisting = true
                                showStepDialog = true
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Step")
                            }
                            IconButton(onClick = {
                                viewModel.deleteStep(activeStep.id)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Step", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    RowCounterWidget(
                        currentRow = activeStep.currentRow,
                        targetRows = activeStep.targetRows,
                        isCompleted = activeStep.isCompleted,
                        onIncrement = { viewModel.incrementStepRow() },
                        onDecrement = { viewModel.decrementStepRow() },
                        onReset = { viewModel.resetStepRow() },
                        onToggleCompleted = { viewModel.toggleStepCompleted() }
                    )
                }
            }
        }

        if (showStepDialog) {
            StepEditorDialog(
                existingStep = if (isEditingExisting) activeStep else null,
                pdfPageCount = pdfPageCount,
                initialPageIndex = currentPageIndex,
                onDismiss = { showStepDialog = false },
                onSave = { title, targetRows, pageIndex, notes ->
                    viewModel.saveStep(
                        title = title,
                        targetRows = targetRows,
                        pdfPageIndex = pageIndex,
                        notes = notes,
                        existingStepId = if (isEditingExisting) activeStep?.id else null
                    )
                    showStepDialog = false
                }
            )
        }
    }
}
