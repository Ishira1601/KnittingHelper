package com.example.knittinghelper.ui.project

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.knittinghelper.data.model.Project
import com.example.knittinghelper.data.model.ProjectType
import com.example.knittinghelper.data.repository.KnittingRepository
import com.example.knittinghelper.drive.GoogleDriveBackupManager
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProjectListViewModel(
    @Suppress("StaticFieldLeak") private val context: Context,
    private val repository: KnittingRepository
) : ViewModel() {

    private val driveManager = GoogleDriveBackupManager(context.applicationContext)

    val projects: StateFlow<List<Project>> = repository.projects

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(driveManager.getSignedInAccount())
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(repository.getLastBackupTime())
    val lastBackupTime: StateFlow<Long> = _lastBackupTime.asStateFlow()

    fun getSignInIntent(): Intent {
        return driveManager.getGoogleSignInClient().signInIntent
    }

    fun handleSignInResult(data: Intent?) {
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(Exception::class.java)
            _signedInAccount.value = account
            _statusMessage.value = "Signed in as ${account?.email ?: "Google Account"}"
        } catch (e: Exception) {
            e.printStackTrace()
            _signedInAccount.value = null
            _statusMessage.value = "Sign in failed: ${e.localizedMessage ?: "Unknown error"}"
        }
    }

    fun signOut() {
        driveManager.getGoogleSignInClient().signOut().addOnCompleteListener {
            _signedInAccount.value = null
            _statusMessage.value = "Signed out of Google"
        }
    }

    fun backupToCloud() {
        if (_signedInAccount.value == null) {
            _statusMessage.value = "Please sign in to Google Drive first"
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _statusMessage.value = "Backing up to Google Drive..."
            val result = driveManager.backupToDrive(repository)
            _isSyncing.value = false
            result.onSuccess { msg ->
                _statusMessage.value = msg
                _lastBackupTime.value = repository.getLastBackupTime()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Backup failed"
            }
        }
    }

    fun restoreFromCloud() {
        if (_signedInAccount.value == null) {
            _statusMessage.value = "Please sign in to Google Drive first"
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            _statusMessage.value = "Restoring from Google Drive..."
            val result = driveManager.restoreFromDrive(repository)
            _isSyncing.value = false
            result.onSuccess { msg ->
                _statusMessage.value = msg
                _lastBackupTime.value = repository.getLastBackupTime()
            }.onFailure { err ->
                _statusMessage.value = err.message ?: "Restore failed"
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun createProject(
        title: String,
        description: String,
        pdfUri: Uri?,
        chartUri: Uri?
    ) {
        val internalPdfPath = pdfUri?.let { repository.copyFileToInternalStorage(it, "pdf") }
        val internalChartPath = chartUri?.let { repository.copyFileToInternalStorage(it, "chart") }

        val type = when {
            internalPdfPath != null && internalChartPath != null -> ProjectType.BOTH
            internalChartPath != null -> ProjectType.CHART_IMAGE
            else -> ProjectType.PDF_PATTERN
        }

        val newProject = Project(
            title = title,
            description = description,
            type = type,
            pdfUriString = internalPdfPath,
            chartImageUriString = internalChartPath
        )

        repository.saveProject(newProject)
    }

    fun deleteProject(projectId: String) {
        repository.deleteProject(projectId)
    }
}

class ProjectListViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = KnittingRepository(context)
        return ProjectListViewModel(context, repository) as T
    }
}
