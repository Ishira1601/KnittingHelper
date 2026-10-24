package com.example.knittinghelper.data.model

data class ChartConfig(
    val projectId: String,
    val imageUriString: String,
    val originalImageUriString: String? = null,
    val gridRows: Int = 20,
    val gridCols: Int = 20,
    val offsetXRatio: Float = 0f,
    val offsetYRatio: Float = 0f,
    val gridWidthRatio: Float = 1f,
    val gridHeightRatio: Float = 1f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 1f,
    val cropBottom: Float = 1f,
    val currentRow: Int = 1,
    val currentCol: Int = 1,
    val isRightToLeft: Boolean = true,
    val autoReverseDirection: Boolean = true,
    val wsCountDecreases: Boolean = true,
    val showVerticalCursors: Boolean = false,
    val cursorCol1: Int = 0,
    val cursorCol2: Int = 10,
    val fillCellColors: Boolean = true,
    val cellColorThreshold: Float = 0.5f
) {
    fun getEffectiveOriginalImageUri(): String {
        return originalImageUriString?.takeIf { it.isNotBlank() } ?: imageUriString
    }
}
