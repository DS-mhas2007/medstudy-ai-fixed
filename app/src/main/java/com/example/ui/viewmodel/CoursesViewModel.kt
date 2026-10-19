package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CourseEntity
import com.example.data.local.LectureEntity
import com.example.data.local.ModuleEntity
import com.example.data.repository.MedStudyRepository
import com.example.worker.PdfIndexingScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CoursesUiState(
    val selectedCourse: CourseEntity? = null,
    val isCreatingCourse: Boolean = false,
    val courseToDelete: CourseEntity? = null,
    val userFeedbackMessage: String? = null
)

class CoursesViewModel(
    private val repository: MedStudyRepository,
    private val appContext: Context
) : ViewModel() {

    val courses: StateFlow<List<CourseEntity>> = repository.allCourses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    fun selectCourse(course: CourseEntity?) {
        _uiState.update { it.copy(selectedCourse = course) }
    }

    fun setCreatingCourse(isCreating: Boolean) {
        _uiState.update { it.copy(isCreatingCourse = isCreating) }
    }

    fun setCourseToDelete(course: CourseEntity?) {
        _uiState.update { it.copy(courseToDelete = course) }
    }

    fun createCourse(
        title: String,
        titleAr: String,
        code: String,
        semester: String,
        colorHex: String
    ) {
        viewModelScope.launch {
            val newCourse = CourseEntity(
                title = title.ifBlank { "Untitled Course" },
                titleAr = titleAr.ifBlank { title },
                code = code.ifBlank { "MED101" },
                semester = semester.ifBlank { "الفصل الأول" },
                colorHex = colorHex.ifBlank { "#0D9488" }
            )
            repository.insertCourse(newCourse)
            _uiState.update {
                it.copy(
                    isCreatingCourse = false,
                    userFeedbackMessage = "تمت إضافة مقرر $title بنجاح"
                )
            }
        }
    }

    fun confirmDeleteCourse() {
        val course = _uiState.value.courseToDelete ?: return
        viewModelScope.launch {
            repository.deleteCourse(course)
            _uiState.update {
                it.copy(
                    courseToDelete = null,
                    selectedCourse = if (it.selectedCourse?.id == course.id) null else it.selectedCourse,
                    userFeedbackMessage = "تم حذف المقرر"
                )
            }
        }
    }

    fun schedulePdfIndexing(
        resourceId: Long,
        lectureId: Long,
        courseId: Long,
        filePath: String,
        title: String
    ) {
        PdfIndexingScheduler.scheduleIndexing(
            context = appContext,
            resourceId = resourceId,
            lectureId = lectureId,
            courseId = courseId,
            filePath = filePath,
            title = title
        )
    }

    fun clearFeedback() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }
}
