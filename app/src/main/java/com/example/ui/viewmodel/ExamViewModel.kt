package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ExamQuestion
import com.example.core.ExamReport
import com.example.core.MedicalExamEngine
import com.example.data.repository.MedStudyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExamUiState(
    val isExamStarted: Boolean = false,
    val isExamFinished: Boolean = false,
    val selectedDurationMinutes: Int = 15,
    val selectedQuestionCount: Int = 8,
    val selectedCourseIds: Set<Long> = emptySet(),
    val questions: List<ExamQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswers: Map<Int, Int> = emptyMap(),
    val flaggedQuestionIndices: Set<Int> = emptySet(),
    val secondsRemaining: Long = 0L,
    val examReport: ExamReport? = null,
    val showSubmitConfirmation: Boolean = false,
    val autoSubmittedNotice: Boolean = false
)

class ExamViewModel(
    private val repository: MedStudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExamUiState())
    val uiState: StateFlow<ExamUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun updateDuration(minutes: Int) {
        _uiState.update { it.copy(selectedDurationMinutes = minutes) }
    }

    fun updateQuestionCount(count: Int) {
        _uiState.update { it.copy(selectedQuestionCount = count) }
    }

    fun toggleCourseSelection(courseId: Long) {
        _uiState.update { state ->
            val updated = if (state.selectedCourseIds.contains(courseId)) {
                state.selectedCourseIds - courseId
            } else {
                state.selectedCourseIds + courseId
            }
            state.copy(selectedCourseIds = updated)
        }
    }

    fun setSelectedCourses(ids: Set<Long>) {
        _uiState.update { it.copy(selectedCourseIds = ids) }
    }

    fun startExam(generatedQuestions: List<ExamQuestion>) {
        timerJob?.cancel()
        val durationSec = _uiState.value.selectedDurationMinutes * 60L
        _uiState.update {
            it.copy(
                isExamStarted = true,
                isExamFinished = false,
                questions = generatedQuestions,
                currentIndex = 0,
                userAnswers = emptyMap(),
                flaggedQuestionIndices = emptySet(),
                secondsRemaining = durationSec,
                examReport = null,
                showSubmitConfirmation = false,
                autoSubmittedNotice = false
            )
        }

        startTimer()
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            while (_uiState.value.secondsRemaining > 0 && !_uiState.value.isExamFinished) {
                delay(1000L)
                _uiState.update { it.copy(secondsRemaining = (it.secondsRemaining - 1).coerceAtLeast(0L)) }
            }

            if (_uiState.value.secondsRemaining <= 0L && _uiState.value.isExamStarted && !_uiState.value.isExamFinished) {
                submitExam(autoSubmitted = true)
            }
        }
    }

    fun selectAnswer(questionIndex: Int, optionIndex: Int) {
        _uiState.update { state ->
            val updated = state.userAnswers + (questionIndex to optionIndex)
            state.copy(userAnswers = updated)
        }
    }

    fun toggleFlagQuestion(questionIndex: Int) {
        _uiState.update { state ->
            val flagged = if (state.flaggedQuestionIndices.contains(questionIndex)) {
                state.flaggedQuestionIndices - questionIndex
            } else {
                state.flaggedQuestionIndices + questionIndex
            }
            state.copy(flaggedQuestionIndices = flagged)
        }
    }

    fun navigateToQuestion(index: Int) {
        _uiState.update {
            it.copy(currentIndex = index.coerceIn(0, (it.questions.size - 1).coerceAtLeast(0)))
        }
    }

    fun setShowSubmitConfirmation(show: Boolean) {
        _uiState.update { it.copy(showSubmitConfirmation = show) }
    }

    fun submitExam(autoSubmitted: Boolean = false) {
        timerJob?.cancel()
        val state = _uiState.value
        val totalSec = state.selectedDurationMinutes * 60L
        val spent = totalSec - state.secondsRemaining

        val report = MedicalExamEngine.evaluateExam(
            examTitle = "محاكاة الامتحان الشامل",
            questions = state.questions,
            answers = state.userAnswers,
            timeSpentSeconds = spent,
            timeLimitSeconds = totalSec
        )

        _uiState.update {
            it.copy(
                isExamFinished = true,
                examReport = report,
                showSubmitConfirmation = false,
                autoSubmittedNotice = autoSubmitted
            )
        }

        viewModelScope.launch {
            repository.recordExamAttempt(report, state.selectedCourseIds.toList())
        }
    }

    fun resetExam() {
        timerJob?.cancel()
        _uiState.update {
            it.copy(
                isExamStarted = false,
                isExamFinished = false,
                questions = emptyList(),
                userAnswers = emptyMap(),
                flaggedQuestionIndices = emptySet(),
                examReport = null,
                showSubmitConfirmation = false,
                autoSubmittedNotice = false
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
