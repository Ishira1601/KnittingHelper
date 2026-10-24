package com.example.knittinghelper.data.repository

import android.content.Context
import android.net.Uri
import com.example.knittinghelper.data.model.ChartConfig
import com.example.knittinghelper.data.model.PatternStep
import com.example.knittinghelper.data.model.Project
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream

class KnittingRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("knitting_helper_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _steps = MutableStateFlow<Map<String, List<PatternStep>>>(emptyMap())
    val steps: StateFlow<Map<String, List<PatternStep>>> = _steps.asStateFlow()

    private val _chartConfigs = MutableStateFlow<Map<String, ChartConfig>>(emptyMap())
    val chartConfigs: StateFlow<Map<String, ChartConfig>> = _chartConfigs.asStateFlow()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val projectsJson = prefs.getString("key_projects", null)
        if (projectsJson != null) {
            val type = object : TypeToken<List<Project>>() {}.type
            val list: List<Project> = gson.fromJson(projectsJson, type) ?: emptyList()
            _projects.value = list
        }

        val stepsJson = prefs.getString("key_steps", null)
        if (stepsJson != null) {
            val type = object : TypeToken<Map<String, List<PatternStep>>>() {}.type
            val map: Map<String, List<PatternStep>> = gson.fromJson(stepsJson, type) ?: emptyMap()
            _steps.value = map
        }

        val chartsJson = prefs.getString("key_charts", null)
        if (chartsJson != null) {
            val type = object : TypeToken<Map<String, ChartConfig>>() {}.type
            val map: Map<String, ChartConfig> = gson.fromJson(chartsJson, type) ?: emptyMap()
            _chartConfigs.value = map
        }
    }

    private fun saveProjectsToPrefs() {
        prefs.edit().putString("key_projects", gson.toJson(_projects.value)).apply()
    }

    private fun saveStepsToPrefs() {
        prefs.edit().putString("key_steps", gson.toJson(_steps.value)).apply()
    }

    private fun saveChartsToPrefs() {
        prefs.edit().putString("key_charts", gson.toJson(_chartConfigs.value)).apply()
    }

    fun getProjectById(id: String): Project? {
        return _projects.value.find { it.id == id }
    }

    fun saveProject(project: Project) {
        val currentList = _projects.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == project.id }
        if (index >= 0) {
            currentList[index] = project
        } else {
            currentList.add(0, project)
        }
        _projects.value = currentList
        saveProjectsToPrefs()
    }

    fun deleteProject(projectId: String) {
        val currentList = _projects.value.filterNot { it.id == projectId }
        _projects.value = currentList
        saveProjectsToPrefs()

        val stepsMap = _steps.value.toMutableMap()
        stepsMap.remove(projectId)
        _steps.value = stepsMap
        saveStepsToPrefs()

        val chartsMap = _chartConfigs.value.toMutableMap()
        chartsMap.remove(projectId)
        _chartConfigs.value = chartsMap
        saveChartsToPrefs()
    }

    fun getStepsForProject(projectId: String): List<PatternStep> {
        return _steps.value[projectId] ?: emptyList()
    }

    fun saveStepsForProject(projectId: String, stepList: List<PatternStep>) {
        val map = _steps.value.toMutableMap()
        map[projectId] = stepList.sortedBy { it.stepOrder }
        _steps.value = map
        saveStepsToPrefs()
    }

    fun updateStep(step: PatternStep) {
        val currentSteps = getStepsForProject(step.projectId).toMutableList()
        val index = currentSteps.indexOfFirst { it.id == step.id }
        if (index >= 0) {
            currentSteps[index] = step
        } else {
            currentSteps.add(step)
        }
        saveStepsForProject(step.projectId, currentSteps)
    }

    fun deleteStep(stepId: String, projectId: String) {
        val currentSteps = getStepsForProject(projectId).filterNot { it.id == stepId }
        saveStepsForProject(projectId, currentSteps)
    }

    fun getChartConfigForProject(projectId: String): ChartConfig? {
        return _chartConfigs.value[projectId]
    }

    fun saveChartConfig(config: ChartConfig) {
        val map = _chartConfigs.value.toMutableMap()
        map[config.projectId] = config
        _chartConfigs.value = map
        saveChartsToPrefs()
    }

    /**
     * Copy imported file content from incoming URI into app internal storage
     * so URIs remain persistent and accessible across app launches.
     */
    fun copyFileToInternalStorage(uri: Uri, prefix: String): String? {
        return try {
            val extension = if (prefix == "pdf") ".pdf" else ".png"
            val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}$extension")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun saveBitmapToInternalStorage(bitmap: android.graphics.Bitmap, prefix: String): String? {
        return try {
            val file = File(context.filesDir, "${prefix}_cropped_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
            }
            Uri.fromFile(file).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getLastBackupTime(): Long {
        return prefs.getLong("key_last_backup_time", 0L)
    }

    fun setLastBackupTime(timestamp: Long) {
        prefs.edit().putLong("key_last_backup_time", timestamp).apply()
    }

    /**
     * Export all app data and referenced internal storage files into a single ZIP file.
     */
    fun exportBackupZip(destZipFile: File): Boolean {
        return try {
            val manifest = BackupManifest(
                version = 1,
                timestamp = System.currentTimeMillis(),
                projects = _projects.value,
                steps = _steps.value,
                chartConfigs = _chartConfigs.value
            )
            val manifestJson = gson.toJson(manifest)

            java.util.zip.ZipOutputStream(FileOutputStream(destZipFile)).use { zos ->
                // Write manifest
                val manifestEntry = java.util.zip.ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // Collect files in internal storage
                val filesDir = context.filesDir
                val files = filesDir.listFiles() ?: emptyArray()
                for (file in files) {
                    if (file.isFile) {
                        val fileEntry = java.util.zip.ZipEntry("files/${file.name}")
                        zos.putNextEntry(fileEntry)
                        file.inputStream().use { input ->
                            input.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }
            setLastBackupTime(System.currentTimeMillis())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Import app data and files from a backup ZIP file.
     */
    fun importBackupZip(srcZipFile: File): Boolean {
        return try {
            var manifestJson: String? = null
            val filesDir = context.filesDir

            java.util.zip.ZipInputStream(srcZipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.json") {
                        manifestJson = zis.bufferedReader(Charsets.UTF_8).readText()
                    } else if (entry.name.startsWith("files/") && !entry.isDirectory) {
                        val fileName = entry.name.removePrefix("files/")
                        if (fileName.isNotBlank()) {
                            val destFile = File(filesDir, fileName)
                            FileOutputStream(destFile).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (manifestJson == null) return false

            val manifest = gson.fromJson(manifestJson, BackupManifest::class.java) ?: return false

            // Remap URIs to current device filesDir
            val remappedProjects = manifest.projects.map { project ->
                project.copy(
                    pdfUriString = remapFileUri(project.pdfUriString, filesDir),
                    chartImageUriString = remapFileUri(project.chartImageUriString, filesDir)
                )
            }

            val remappedChartConfigs = manifest.chartConfigs.mapValues { (_, config) ->
                config.copy(
                    imageUriString = remapFileUri(config.imageUriString, filesDir) ?: config.imageUriString,
                    originalImageUriString = remapFileUri(config.originalImageUriString, filesDir) ?: config.originalImageUriString
                )
            }

            _projects.value = remappedProjects
            _steps.value = manifest.steps
            _chartConfigs.value = remappedChartConfigs

            saveProjectsToPrefs()
            saveStepsToPrefs()
            saveChartsToPrefs()

            setLastBackupTime(manifest.timestamp.takeIf { it > 0 } ?: System.currentTimeMillis())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun remapFileUri(uriString: String?, filesDir: File): String? {
        if (uriString == null) return null
        return try {
            val uri = Uri.parse(uriString)
            val fileName = uri.lastPathSegment ?: return uriString
            val localFile = File(filesDir, fileName)
            if (localFile.exists()) {
                Uri.fromFile(localFile).toString()
            } else {
                uriString
            }
        } catch (e: Exception) {
            uriString
        }
    }
}

data class BackupManifest(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val projects: List<Project> = emptyList(),
    val steps: Map<String, List<PatternStep>> = emptyMap(),
    val chartConfigs: Map<String, ChartConfig> = emptyMap()
)

