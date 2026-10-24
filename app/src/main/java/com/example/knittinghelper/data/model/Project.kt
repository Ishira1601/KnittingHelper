package com.example.knittinghelper.data.model

import java.util.UUID

enum class ProjectType {
    PDF_PATTERN,
    CHART_IMAGE,
    BOTH
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val type: ProjectType = ProjectType.BOTH,
    val pdfUriString: String? = null,
    val chartImageUriString: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis()
)
