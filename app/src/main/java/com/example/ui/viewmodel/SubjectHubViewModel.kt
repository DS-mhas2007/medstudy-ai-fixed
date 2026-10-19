package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.FsrsScheduler
import com.example.data.files.ImportResult
import com.example.data.files.ResourceImporter
import com.example.data.local.FlashcardEntity
import com.example.data.local.LectureEntity
import com.example.data.local.LectureResourceEntity
import com.example.data.repository.MedStudyRepository
import com.example.worker.PdfIndexingScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SubjectHubUiState(
    val selectedTabIndex: Int = 0,
    val showOcrSheet: Boolean = false,
    val showAddCardDialog: Boolean = false,
    val showAddLectureDialog: Boolean = false,
    val userNotice: String? = null
)

class SubjectHubViewModel(
    private val repository: MedStudyRepository,
    private val importer: ResourceImporter,
    private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectHubUiState())
    val uiState: StateFlow<SubjectHubUiState> = _uiState.asStateFlow()

    fun setTabIndex(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }

    fun setShowOcrSheet(show: Boolean) {
        _uiState.update { it.copy(showOcrSheet = show) }
    }

    fun setShowAddCardDialog(show: Boolean) {
        _uiState.update { it.copy(showAddCardDialog = show) }
    }

    fun setShowAddLectureDialog(show: Boolean) {
        _uiState.update { it.copy(showAddLectureDialog = show) }
    }

    fun importPdfAndScheduleIndexing(
        uri: Uri,
        lecture: LectureEntity,
        courseId: Long
    ) {
        viewModelScope.launch {
            when (val result = importer.importPdf(uri)) {
                is ImportResult.Success -> {
                    val resource = LectureResourceEntity(
                        lectureId = lecture.id,
                        type = "PDF",
                        title = result.pdf.displayName,
                        localPath = result.pdf.file.absolutePath,
                        pageCount = result.pdf.pageCount,
                        lastReadPage = 1
                    )
                    val insertedId = repository.insertResource(resource)

                    // Kick off WorkManager in background!
                    PdfIndexingScheduler.scheduleIndexing(
                        context = appContext,
                        resourceId = insertedId,
                        lectureId = lecture.id,
                        courseId = courseId,
                        filePath = result.pdf.file.absolutePath,
                        title = result.pdf.displayName
                    )

                    _uiState.update {
                        it.copy(userNotice = "بدأت معالجة وفهرسة ${result.pdf.displayName} في الخلفية.")
                    }
                }
                is ImportResult.Failure -> {
                    _uiState.update { it.copy(userNotice = result.message) }
                }
            }
        }
    }

    fun rateCard(card: FlashcardEntity, grade: Int) {
        viewModelScope.launch {
            repository.answerFlashcard(card, grade)
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(userNotice = null) }
    }
}
