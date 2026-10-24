package com.example.knittinghelper.data.model

import java.util.UUID

data class PatternStep(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val notes: String = "",
    val pdfPageIndex: Int = 0,
    val targetRows: Int = 1,
    val currentRow: Int = 0,
    val isCompleted: Boolean = false,
    val stepOrder: Int = 0
)
