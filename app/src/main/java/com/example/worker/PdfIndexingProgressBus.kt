package com.example.worker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Live state of background PDF indexing tasks.
 * Enables immediate UI reactivity across any screen (SubjectHub, Courses, Notification banner).
 */
data class IndexingProgress(
    val resourceId: Long,
    val lectureId: Long,
    val title: String,
    val progressPercent: Int = 0,
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val statusText: String = "",
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
    val flashcardsGenerated: Int = 0
)

object PdfIndexingProgressBus {
    private val _tasks = MutableStateFlow<Map<Long, IndexingProgress>>(emptyMap())
    val tasks: StateFlow<Map<Long, IndexingProgress>> = _tasks.asStateFlow()

    fun updateProgress(
        resourceId: Long,
        lectureId: Long,
        title: String,
        progressPercent: Int,
        currentPage: Int,
        totalPages: Int,
        statusText: String,
        flashcardsGenerated: Int = 0
    ) {
        _tasks.update { current ->
            current + (resourceId to IndexingProgress(
                resourceId = resourceId,
                lectureId = lectureId,
                title = title,
                progressPercent = progressPercent.coerceIn(0, 100),
                currentPage = currentPage,
                totalPages = totalPages,
                statusText = statusText,
                isCompleted = false,
                isFailed = false,
                flashcardsGenerated = flashcardsGenerated
            ))
        }
    }

    fun markCompleted(resourceId: Long, flashcardsGenerated: Int = 0) {
        _tasks.update { current ->
            val prev = current[resourceId]
            if (prev != null) {
                current + (resourceId to prev.copy(
                    progressPercent = 100,
                    statusText = "اكتملت الفهرسة واستخراج البطاقات بنجاح",
                    isCompleted = true,
                    isFailed = false,
                    flashcardsGenerated = flashcardsGenerated
                ))
            } else {
                current
            }
        }
    }

    fun markFailed(resourceId: Long, error: String) {
        _tasks.update { current ->
            val prev = current[resourceId]
            if (prev != null) {
                current + (resourceId to prev.copy(
                    statusText = error,
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = error
                ))
            } else {
                current
            }
        }
    }

    fun clearTask(resourceId: Long) {
        _tasks.update { current ->
            current - resourceId
        }
    }
}
