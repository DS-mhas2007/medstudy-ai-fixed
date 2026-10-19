package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.FsrsScheduler
import com.example.data.local.FlashcardEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.repository.MedStudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlannerUiState(
    val selectedTab: Int = 0,
    val showAddTaskDialog: Boolean = false,
    val showAddCardDialog: Boolean = false,
    val isCardFlipped: Boolean = false,
    val currentCardIndex: Int = 0
)

class StudyPlannerViewModel(
    private val repository: MedStudyRepository
) : ViewModel() {

    val tasks: StateFlow<List<StudyTaskEntity>> = repository.allTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dueCards: StateFlow<List<FlashcardEntity>> = repository.dueFlashcards
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(PlannerUiState())
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setShowAddTaskDialog(show: Boolean) {
        _uiState.update { it.copy(showAddTaskDialog = show) }
    }

    fun setShowAddCardDialog(show: Boolean) {
        _uiState.update { it.copy(showAddCardDialog = show) }
    }

    fun toggleCardFlipped() {
        _uiState.update { it.copy(isCardFlipped = !it.isCardFlipped) }
    }

    fun addTask(title: String, description: String, date: String, minutes: Int, courseId: Long? = null) {
        viewModelScope.launch {
            val task = StudyTaskEntity(
                title = title.ifBlank { "جلسة مراجعة" },
                description = description,
                targetDate = date,
                estimatedMinutes = minutes,
                courseId = courseId
            )
            repository.insertTask(task)
            _uiState.update { it.copy(showAddTaskDialog = false) }
        }
    }

    fun toggleTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            repository.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

    fun deleteTask(task: StudyTaskEntity) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun rateDueCard(card: FlashcardEntity, grade: Int) {
        viewModelScope.launch {
            repository.answerFlashcard(card, grade)
            _uiState.update { it.copy(isCardFlipped = false) }
        }
    }
}
