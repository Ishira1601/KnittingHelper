package com.example.knittinghelper.drive

import android.content.Context
import com.example.knittinghelper.data.repository.KnittingRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GoogleDriveBackupManager(private val context: Context) {

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope(DriveScopes.DRIVE_APPDATA),
                Scope(DriveScopes.DRIVE_FILE)
            )
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun getSignedInAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    private fun getDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("Knitting Companion")
            .build()
    }

    suspend fun backupToDrive(repository: KnittingRepository): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(Exception("Not signed in to Google"))

            val driveService = getDriveService(account)

            // Step 1: Create local temp zip backup file
            val tempZip = File(context.cacheDir, "knitting_companion_backup.zip")
            if (tempZip.exists()) tempZip.delete()

            val exportSuccess = repository.exportBackupZip(tempZip)
            if (!exportSuccess || !tempZip.exists()) {
                return@withContext Result.failure(Exception("Failed to generate local backup file"))
            }

            // Step 2: Check if backup already exists in Google Drive App Data Folder
            val query = "name = 'knitting_companion_backup.zip' and trashed = false"
            val fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, modifiedTime)")
                .execute()

            val mediaContent = FileContent("application/zip", tempZip)

            if (fileList.files.isNotEmpty()) {
                val fileId = fileList.files.first().id
                driveService.files().update(fileId, null, mediaContent)
                    .setFields("id, modifiedTime")
                    .execute()
            } else {
                val metadata = com.google.api.services.drive.model.File().apply {
                    name = "knitting_companion_backup.zip"
                    parents = listOf("appDataFolder")
                }
                driveService.files().create(metadata, mediaContent)
                    .setFields("id, modifiedTime")
                    .execute()
            }

            tempZip.delete()
            Result.success("Backup successfully uploaded to Google Drive!")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Backup failed: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }

    suspend fun restoreFromDrive(repository: KnittingRepository): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = getSignedInAccount()
                ?: return@withContext Result.failure(Exception("Not signed in to Google"))

            val driveService = getDriveService(account)

            // Step 1: Search AppDataFolder first
            var query = "name = 'knitting_companion_backup.zip' and trashed = false"
            var fileList = driveService.files().list()
                .setSpaces("appDataFolder")
                .setQ(query)
                .setFields("files(id, name, modifiedTime)")
                .execute()

            // Fallback: search root drive space if not in appDataFolder
            if (fileList.files.isEmpty()) {
                fileList = driveService.files().list()
                    .setQ(query)
                    .setFields("files(id, name, modifiedTime)")
                    .execute()
            }

            if (fileList.files.isEmpty()) {
                return@withContext Result.failure(Exception("No backup file found in Google Drive"))
            }

            val fileId = fileList.files.first().id

            // Step 2: Download backup zip file
            val tempZip = File(context.cacheDir, "downloaded_backup.zip")
            if (tempZip.exists()) tempZip.delete()

            FileOutputStream(tempZip).use { output ->
                driveService.files().get(fileId).executeMediaAndDownloadTo(output)
            }

            // Step 3: Import into repository
            val importSuccess = repository.importBackupZip(tempZip)
            tempZip.delete()

            if (importSuccess) {
                Result.success("Backup successfully restored from Google Drive!")
            } else {
                Result.failure(Exception("Failed to extract or parse backup data"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Restore failed: ${e.localizedMessage ?: "Unknown error"}"))
        }
    }
}
