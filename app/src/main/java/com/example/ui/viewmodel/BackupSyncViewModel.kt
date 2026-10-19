package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupMetadata
import com.example.data.backup.DriveBackupManager
import com.example.data.local.MedStudyDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    val lastBackup: BackupMetadata? = null,
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val previewBackup: BackupMetadata? = null,
    val pendingRestoreUri: Uri? = null,
    val successMessage: String? = null,
    val errorMessage: String? = null
)

class BackupSyncViewModel(
    private val database: MedStudyDatabase,
    private val backupManager: DriveBackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    init {
        refreshLastBackup()
    }

    fun refreshLastBackup() {
        val local = backupManager.getLocalSnapshotMetadata()
        _uiState.update { it.copy(lastBackup = local) }
    }

    fun exportToDrive(targetUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUp = true, successMessage = null, errorMessage = null) }
            val result = backupManager.exportBackupToUri(database, targetUri)
            result.fold(
                onSuccess = { metadata ->
                    _uiState.update {
                        it.copy(
                            isBackingUp = false,
                            lastBackup = metadata,
                            successMessage = "تم حفظ النسخة الاحتياطية بنجاح على Google Drive (${metadata.coursesCount} مقررات، ${metadata.flashcardsCount} بطاقة)."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isBackingUp = false,
                            errorMessage = error.message ?: "تعذر تصدير النسخة الاحتياطية."
                        )
                    }
                }
            )
        }
    }

    fun inspectBackup(sourceUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, successMessage = null, errorMessage = null) }
            val result = backupManager.inspectBackupFromUri(sourceUri)
            result.fold(
                onSuccess = { metadata ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            previewBackup = metadata,
                            pendingRestoreUri = sourceUri
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            errorMessage = error.message ?: "الملف المحدد غير صالح كنسخة احتياطية."
                        )
                    }
                }
            )
        }
    }

    fun confirmRestore() {
        val uri = _uiState.value.pendingRestoreUri ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoring = true, errorMessage = null) }
            val result = backupManager.restoreBackupFromUri(database, uri)
            result.fold(
                onSuccess = { metadata ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            previewBackup = null,
                            pendingRestoreUri = null,
                            lastBackup = metadata,
                            successMessage = "تمت استعادة البيانات بنجاح (${metadata.coursesCount} مقررات، ${metadata.flashcardsCount} بطاقة، ${metadata.notesCount} ملاحظة)."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRestoring = false,
                            previewBackup = null,
                            pendingRestoreUri = null,
                            errorMessage = error.message ?: "فشلت استعادة البيانات من النسخة المحددة."
                        )
                    }
                }
            )
        }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(previewBackup = null, pendingRestoreUri = null) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(successMessage = null, errorMessage = null) }
    }
}
