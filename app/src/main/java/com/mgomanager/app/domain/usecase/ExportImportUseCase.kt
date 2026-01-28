package com.mgomanager.app.domain.usecase

import android.content.Context
import com.mgomanager.app.data.local.database.AppDatabase
import com.mgomanager.app.data.local.preferences.SettingsDataStore
import com.mgomanager.app.data.model.ExportProgress
import com.mgomanager.app.data.model.ImportProgress
import com.mgomanager.app.data.model.OperationProgress
import com.mgomanager.app.data.repository.AccountRepository
import com.mgomanager.app.data.repository.LogRepository
import com.mgomanager.app.domain.util.SSHSyncService
import com.mgomanager.app.domain.util.SSHOperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class ExportImportUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val settingsDataStore: SettingsDataStore,
    private val logRepository: LogRepository,
    private val sshSyncService: SSHSyncService
) {

    companion object {
        const val EXPORT_DIR = "/storage/emulated/0/mgo/exports/"
        const val DB_FOLDER = "database"
        const val BACKUPS_FOLDER = "backups"
    }

    /**
     * Export database and all backup files to a zip file with progress updates.
     * @return Flow of ExportProgress states
     */
    fun exportData(context: Context): Flow<ExportProgress> = flow {
        try {
            val totalSteps = 7
            var currentStep = 0

            logRepository.logInfo("EXPORT", "Starting data export")

            // Step 1: Prepare export
            emit(ExportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Bereite Export vor...",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            // Create export directory
            val exportDir = File(EXPORT_DIR)
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }

            // Generate filename with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val zipFile = File(exportDir, "mgo_export_$timestamp.zip")

            // Get backup path from settings
            val backupPath = settingsDataStore.backupRootPath.first()

            // Get database path
            val dbPath = context.getDatabasePath(AppDatabase.DATABASE_NAME).absolutePath

            // Step 2: Prepare file list
            emit(ExportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Sammle Dateien...",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            // Collect all files to export
            val backupsDir = File(backupPath)
            val allBackupFiles = mutableListOf<Pair<File, String>>()

            if (backupsDir.exists() && backupsDir.isDirectory) {
                collectFilesRecursively(backupsDir, BACKUPS_FOLDER, allBackupFiles)
            }

            val totalFiles = allBackupFiles.size + 1 // +1 for database

            // Step 3: Export database
            emit(ExportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Exportiere Datenbank...",
                    percentComplete = currentStep.toFloat() / totalSteps,
                    currentFile = AppDatabase.DATABASE_NAME,
                    filesProcessed = 0,
                    totalFiles = totalFiles
                )
            ))

            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                // Add database file and related files (WAL, SHM)
                val dbFile = File(dbPath)
                if (dbFile.exists()) {
                    addFileToZip(zos, dbFile, "$DB_FOLDER/${dbFile.name}")

                    // Also export WAL and SHM files if they exist
                    val walFile = File("$dbPath-wal")
                    val shmFile = File("$dbPath-shm")
                    if (walFile.exists()) {
                        addFileToZip(zos, walFile, "$DB_FOLDER/${walFile.name}")
                    }
                    if (shmFile.exists()) {
                        addFileToZip(zos, shmFile, "$DB_FOLDER/${shmFile.name}")
                    }

                    logRepository.logInfo("EXPORT", "Database added to export")
                }

                // Step 4: Export backup files with progress
                emit(ExportProgress.InProgress(
                    OperationProgress(
                        currentStep = ++currentStep,
                        totalSteps = totalSteps,
                        stepDescription = "Kopiere Backup-Dateien...",
                        percentComplete = currentStep.toFloat() / totalSteps,
                        filesProcessed = 1,
                        totalFiles = totalFiles
                    )
                ))

                allBackupFiles.forEachIndexed { index, (file, entryPath) ->
                    addFileToZip(zos, file, entryPath)

                    // Emit progress every 10 files or on last file
                    if (index % 10 == 0 || index == allBackupFiles.size - 1) {
                        emit(ExportProgress.InProgress(
                            OperationProgress(
                                currentStep = currentStep,
                                totalSteps = totalSteps,
                                stepDescription = "Kopiere Backup-Dateien...",
                                percentComplete = currentStep.toFloat() / totalSteps,
                                currentFile = file.name,
                                filesProcessed = index + 2, // +1 for database, +1 for 0-index
                                totalFiles = totalFiles
                            )
                        ))
                    }
                }

                logRepository.logInfo("EXPORT", "Backup files added to export")
            }

            // Step 5: Create ZIP (finalize)
            emit(ExportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Finalisiere ZIP-Archiv...",
                    percentComplete = currentStep.toFloat() / totalSteps,
                    isIndeterminate = true
                )
            ))

            logRepository.logInfo("EXPORT", "Export completed: ${zipFile.absolutePath}")

            // Step 6: Auto-upload to SSH server if enabled
            emit(ExportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Pruefe SSH-Upload...",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            val autoUpload = settingsDataStore.sshAutoUploadOnExport.first()
            val sshServer = settingsDataStore.sshServer.first()
            var uploadMessage = ""

            if (autoUpload && sshServer.isNotBlank()) {
                emit(ExportProgress.InProgress(
                    OperationProgress(
                        currentStep = currentStep,
                        totalSteps = totalSteps,
                        stepDescription = "Lade auf SSH-Server hoch...",
                        percentComplete = currentStep.toFloat() / totalSteps,
                        isIndeterminate = true
                    )
                ))

                logRepository.logInfo("EXPORT", "Auto-uploading to SSH server...")
                val uploadResult = sshSyncService.uploadZip(zipFile.absolutePath)

                uploadMessage = when (uploadResult) {
                    is SSHOperationResult.Success -> {
                        logRepository.logInfo("EXPORT", "Auto-upload successful")
                        "\n(Upload erfolgreich)"
                    }
                    is SSHOperationResult.Error -> {
                        logRepository.logError("EXPORT", "Auto-upload failed: ${uploadResult.message}")
                        "\n(Upload fehlgeschlagen: ${uploadResult.message})"
                    }
                }
            }

            // Step 7: Cleanup
            emit(ExportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Raeume auf...",
                    percentComplete = 1f
                )
            ))

            // Brief pause to show completion
            delay(500)

            emit(ExportProgress.Success("Export gespeichert unter:\n${zipFile.absolutePath}$uploadMessage"))

        } catch (e: Exception) {
            logRepository.logError("EXPORT", "Export failed: ${e.message}", exception = e)
            emit(ExportProgress.Error("Export fehlgeschlagen: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Import database and backup files from a zip file with progress updates.
     * Note: This looks for the most recent export file in the exports directory
     * @return Flow of ImportProgress states
     */
    fun importData(context: Context): Flow<ImportProgress> = flow {
        try {
            val totalSteps = 7
            var currentStep = 0

            logRepository.logInfo("IMPORT", "Starting data import")

            // Step 1: Find export file
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Suche Export-Datei...",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            // Find the most recent export file
            val exportDir = File(EXPORT_DIR)
            val zipFiles = exportDir.listFiles { file ->
                file.name.startsWith("mgo_export_") && file.name.endsWith(".zip")
            }?.sortedByDescending { it.lastModified() }

            if (zipFiles.isNullOrEmpty()) {
                emit(ImportProgress.Error("Keine Export-Datei gefunden in $EXPORT_DIR"))
                return@flow
            }

            val zipFile = zipFiles.first()
            logRepository.logInfo("IMPORT", "Importing from: ${zipFile.name}")

            // Step 2: Validate ZIP
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Validiere Datei: ${zipFile.name}",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            // Get backup path from settings
            val backupPath = settingsDataStore.backupRootPath.first()

            // Get database path
            val dbPath = context.getDatabasePath(AppDatabase.DATABASE_NAME).parentFile?.absolutePath
                ?: run {
                    emit(ImportProgress.Error("Database path not found"))
                    return@flow
                }

            // Step 3: Count entries for progress
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Zaehle Eintraege...",
                    percentComplete = currentStep.toFloat() / totalSteps,
                    isIndeterminate = true
                )
            ))

            var totalEntries = 0
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        totalEntries++
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Step 4: Extract files
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Extrahiere Archiv...",
                    percentComplete = currentStep.toFloat() / totalSteps,
                    filesProcessed = 0,
                    totalFiles = totalEntries
                )
            ))

            var filesExtracted = 0
            var dbRestored = false

            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val currentEntry = entry!!
                    val entryName = currentEntry.name

                    when {
                        entryName.startsWith("$DB_FOLDER/") -> {
                            // Extract database file
                            val fileName = entryName.removePrefix("$DB_FOLDER/")
                            val destFile = File(dbPath, fileName)
                            extractFile(zis, destFile)
                            dbRestored = true
                            logRepository.logInfo("IMPORT", "Database restored: $fileName")
                            filesExtracted++
                        }
                        entryName.startsWith("$BACKUPS_FOLDER/") -> {
                            // Extract backup files
                            val relativePath = entryName.removePrefix("$BACKUPS_FOLDER/")
                            val destFile = File(backupPath, relativePath)
                            if (currentEntry.isDirectory) {
                                destFile.mkdirs()
                            } else {
                                destFile.parentFile?.mkdirs()
                                extractFile(zis, destFile)
                                filesExtracted++
                            }
                        }
                    }

                    // Emit progress every 10 files or on last file
                    if (!currentEntry.isDirectory && (filesExtracted % 10 == 0 || filesExtracted == totalEntries)) {
                        emit(ImportProgress.InProgress(
                            OperationProgress(
                                currentStep = currentStep,
                                totalSteps = totalSteps,
                                stepDescription = "Extrahiere Archiv...",
                                percentComplete = currentStep.toFloat() / totalSteps,
                                currentFile = currentEntry.name.substringAfterLast("/"),
                                filesProcessed = filesExtracted,
                                totalFiles = totalEntries
                            )
                        ))
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Step 5: Verify database
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Verifiziere Datenbank...",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            if (!dbRestored) {
                logRepository.logWarning("IMPORT", "No database found in export file")
            }

            // Step 6: Update paths
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Aktualisiere Pfade...",
                    percentComplete = currentStep.toFloat() / totalSteps
                )
            ))

            // Note: Path updates happen automatically when Room re-initializes

            // Step 7: Cleanup
            emit(ImportProgress.InProgress(
                OperationProgress(
                    currentStep = ++currentStep,
                    totalSteps = totalSteps,
                    stepDescription = "Raeume auf...",
                    percentComplete = 1f
                )
            ))

            // Brief pause to show completion
            delay(500)

            logRepository.logInfo("IMPORT", "Import completed successfully")
            emit(ImportProgress.Success("Import erfolgreich abgeschlossen!\n$filesExtracted Dateien importiert.\n\nBitte starte die App neu, um die importierten Daten zu laden."))

        } catch (e: Exception) {
            logRepository.logError("IMPORT", "Import failed: ${e.message}", exception = e)
            emit(ImportProgress.Error("Import fehlgeschlagen: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Recursively collects all files from a directory.
     */
    private fun collectFilesRecursively(
        dir: File,
        basePath: String,
        result: MutableList<Pair<File, String>>
    ) {
        dir.listFiles()?.forEach { file ->
            val entryPath = "$basePath/${file.name}"
            if (file.isDirectory) {
                collectFilesRecursively(file, entryPath, result)
            } else {
                result.add(file to entryPath)
            }
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        FileInputStream(file).use { fis ->
            zos.putNextEntry(ZipEntry(entryName))
            fis.copyTo(zos)
            zos.closeEntry()
        }
    }

    private fun extractFile(zis: ZipInputStream, destFile: File) {
        FileOutputStream(destFile).use { fos ->
            zis.copyTo(fos)
        }
    }
}
