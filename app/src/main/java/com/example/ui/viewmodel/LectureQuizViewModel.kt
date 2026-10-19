package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ExamQuestion
import com.example.core.MedicalExamEngine
import com.example.data.repository.MedStudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LectureQuizUiState(
    val lectureId: Long = 0L,
    val courseId: Long = 0L,
    val lectureTitle: String = "",
    val courseTitle: String = "",
    val questions: List<ExamQuestion> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswers: Map<Int, Int> = emptyMap(),
    val submittedCurrent: Boolean = false,
    val isQuizCompleted: Boolean = false,
    val scorePercent: Int = 0,
    val correctCount: Int = 0
)

class LectureQuizViewModel(
    private val repository: MedStudyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LectureQuizUiState())
    val uiState: StateFlow<LectureQuizUiState> = _uiState.asStateFlow()

    fun initializeQuiz(
        lectureId: Long,
        courseId: Long,
        lectureTitle: String,
        courseTitle: String
    ) {
        if (_uiState.value.questions.isNotEmpty() && _uiState.value.lectureId == lectureId) {
            return // Keep state intact on rotation!
        }

        val generated = MedicalExamEngine.generateLectureQuiz(
            lectureTitle = lectureTitle,
            lectureText = lectureTitle,
            courseId = courseId,
            courseName = courseTitle,
            count = 5
        )

        _uiState.update {
            it.copy(
                lectureId = lectureId,
                courseId = courseId,
                lectureTitle = lectureTitle,
                courseTitle = courseTitle,
                questions = generated,
                currentIndex = 0,
                userAnswers = emptyMap(),
                submittedCurrent = false,
                isQuizCompleted = false,
                scorePercent = 0,
                correctCount = 0
            )
        }
    }

    fun selectOption(optionIndex: Int) {
        if (_uiState.value.submittedCurrent) return
        val currentIdx = _uiState.value.currentIndex
        _uiState.update {
            it.copy(userAnswers = it.userAnswers + (currentIdx to optionIndex))
        }
    }

    fun submitCurrentAnswer() {
        val state = _uiState.value
        val currentQ = state.questions.getOrNull(state.currentIndex) ?: return
        val answer = state.userAnswers[state.currentIndex] ?: return

        _uiState.update { it.copy(submittedCurrent = true) }

        viewModelScope.launch {
            val isCorrect = answer == currentQ.correctIndex
            repository.recordTopicWeakness(
                topic = currentQ.topic,
                specialty = currentQ.specialty,
                courseId = if (state.courseId > 0) state.courseId else null,
                isCorrect = isCorrect
            )
        }
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (state.currentIndex + 1 < state.questions.size) {
            _uiState.update {
                it.copy(
                    currentIndex = it.currentIndex + 1,
                    submittedCurrent = false
                )
            }
        } else {
            finishQuiz()
        }
    }

    fun finishQuiz() {
        val state = _uiState.value
        var correct = 0
        state.questions.forEachIndexed { idx, q ->
            if (state.userAnswers[idx] == q.correctIndex) correct++
        }
        val pct = if (state.questions.isNotEmpty()) (correct * 100) / state.questions.size else 0

        _uiState.update {
            it.copy(
                isQuizCompleted = true,
                correctCount = correct,
                scorePercent = pct
            )
        }

        viewModelScope.launch {
            repository.recordQuizAttempt(
                lectureId = state.lectureId,
                courseId = if (state.courseId > 0) state.courseId else null,
                title = "اختبار: ${state.lectureTitle}",
                topic = state.lectureTitle,
                specialty = state.courseTitle.ifBlank { "General Medicine" },
                questions = state.questions,
                answers = state.userAnswers
            )
        }
    }

    fun retakeQuiz() {
        val state = _uiState.value
        val regenerated = MedicalExamEngine.generateLectureQuiz(
            lectureTitle = state.lectureTitle,
            lectureText = state.lectureTitle,
            courseId = state.courseId,
            courseName = state.courseTitle,
            count = 5
        )
        _uiState.update {
            it.copy(
                questions = regenerated,
                currentIndex = 0,
                userAnswers = emptyMap(),
                submittedCurrent = false,
                isQuizCompleted = false,
                scorePercent = 0,
                correctCount = 0
            )
        }
    }
}
