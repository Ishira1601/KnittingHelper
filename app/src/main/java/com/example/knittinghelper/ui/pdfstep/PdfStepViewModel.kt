package com.example.knittinghelper.ui.pdfstep

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.knittinghelper.data.model.PatternStep
import com.example.knittinghelper.data.model.Project
import com.example.knittinghelper.data.repository.KnittingRepository
import com.example.knittinghelper.pdf.PdfRendererManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfStepViewModel(
    private val repository: KnittingRepository,
    private val pdfManager: PdfRendererManager,
    private val projectId: String
) : ViewModel() {

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _steps = MutableStateFlow<List<PatternStep>>(emptyList())
    val steps: StateFlow<List<PatternStep>> = _steps.asStateFlow()

    private val _activeStepIndex = MutableStateFlow(0)
    val activeStepIndex: StateFlow<Int> = _activeStepIndex.asStateFlow()

    private val _currentPageBitmap = MutableStateFlow<Bitmap?>(null)
    val currentPageBitmap: StateFlow<Bitmap?> = _currentPageBitmap.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _pdfPageCount = MutableStateFlow(0)
    val pdfPageCount: StateFlow<Int> = _pdfPageCount.asStateFlow()

    private val _isLoadingPdf = MutableStateFlow(true)
    val isLoadingPdf: StateFlow<Boolean> = _isLoadingPdf.asStateFlow()

    init {
        loadProjectAndSteps()
    }

    private fun loadProjectAndSteps() {
        val proj = repository.getProjectById(projectId)
        _project.value = proj

        val projectSteps = repository.getStepsForProject(projectId)
        _steps.value = projectSteps

        proj?.pdfUriString?.let { pdfUri ->
            viewModelScope.launch {
                _isLoadingPdf.value = true
                val opened = pdfManager.openPdf(pdfUri)
                if (opened) {
                    val count = pdfManager.getPageCount()
                    _pdfPageCount.value = count
                    
                    val initialPage = projectSteps.firstOrNull()?.pdfPageIndex ?: 0
                    loadPdfPage(initialPage)
                }
                _isLoadingPdf.value = false
            }
        } ?: run {
            _isLoadingPdf.value = false
        }
    }

    fun loadPdfPage(pageIndex: Int) {
        if (pageIndex in 0 until _pdfPageCount.value) {
            _currentPageIndex.value = pageIndex
            viewModelScope.launch {
                val bitmap = pdfManager.renderPage(pageIndex)
                _currentPageBitmap.value = bitmap
            }
        }
    }

    fun selectStep(index: Int) {
        if (index in _steps.value.indices) {
            _activeStepIndex.value = index
            val step = _steps.value[index]
            loadPdfPage(step.pdfPageIndex)
        }
    }

    fun incrementStepRow() {
        val stepList = _steps.value
        val index = _activeStepIndex.value
        if (index in stepList.indices) {
            val currentStep = stepList[index]
            val newRow = currentStep.currentRow + 1
            val updated = currentStep.copy(
                currentRow = newRow,
                isCompleted = if (currentStep.targetRows in 1..newRow) true else currentStep.isCompleted
            )
            repository.updateStep(updated)
            _steps.value = repository.getStepsForProject(projectId)
        }
    }

    fun decrementStepRow() {
        val stepList = _steps.value
        val index = _activeStepIndex.value
        if (index in stepList.indices) {
            val currentStep = stepList[index]
            if (currentStep.currentRow > 0) {
                val newRow = currentStep.currentRow - 1
                val updated = currentStep.copy(currentRow = newRow)
                repository.updateStep(updated)
                _steps.value = repository.getStepsForProject(projectId)
            }
        }
    }

    fun resetStepRow() {
        val stepList = _steps.value
        val index = _activeStepIndex.value
        if (index in stepList.indices) {
            val currentStep = stepList[index]
            val updated = currentStep.copy(currentRow = 0, isCompleted = false)
            repository.updateStep(updated)
            _steps.value = repository.getStepsForProject(projectId)
        }
    }

    fun toggleStepCompleted() {
        val stepList = _steps.value
        val index = _activeStepIndex.value
        if (index in stepList.indices) {
            val currentStep = stepList[index]
            val updated = currentStep.copy(isCompleted = !currentStep.isCompleted)
            repository.updateStep(updated)
            _steps.value = repository.getStepsForProject(projectId)
        }
    }

    fun saveStep(title: String, targetRows: Int, pdfPageIndex: Int, notes: String, existingStepId: String? = null) {
        val currentList = _steps.value
        val step = PatternStep(
            id = existingStepId ?: java.util.UUID.randomUUID().toString(),
            projectId = projectId,
            title = title,
            targetRows = targetRows,
            pdfPageIndex = pdfPageIndex,
            notes = notes,
            stepOrder = if (existingStepId == null) currentList.size else (currentList.find { it.id == existingStepId }?.stepOrder ?: 0)
        )
        repository.updateStep(step)
        _steps.value = repository.getStepsForProject(projectId)
        if (existingStepId == null) {
            _activeStepIndex.value = _steps.value.size - 1
        }
        loadPdfPage(pdfPageIndex)
    }

    fun deleteStep(stepId: String) {
        repository.deleteStep(stepId, projectId)
        _steps.value = repository.getStepsForProject(projectId)
        if (_activeStepIndex.value >= _steps.value.size) {
            _activeStepIndex.value = (_steps.value.size - 1).coerceAtLeast(0)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pdfManager.close()
    }
}

class PdfStepViewModelFactory(
    private val context: Context,
    private val projectId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = KnittingRepository(context)
        val pdfManager = PdfRendererManager(context)
        return PdfStepViewModel(repository, pdfManager, projectId) as T
    }
}
