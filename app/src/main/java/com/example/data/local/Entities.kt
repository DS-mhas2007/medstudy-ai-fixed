package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val titleAr: String,
    val code: String,
    val semester: String,
    val colorHex: String,
    val iconName: String = "menu_book",
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "modules",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["courseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("courseId")]
)
data class ModuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val title: String,
    val titleAr: String,
    val orderIndex: Int = 0,
    val description: String = ""
)

@Entity(
    tableName = "lectures",
    foreignKeys = [
        ForeignKey(
            entity = ModuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moduleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("moduleId"), Index("courseId")]
)
data class LectureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moduleId: Long,
    val courseId: Long,
    val title: String,
    val titleAr: String,
    val orderIndex: Int = 0,
    val status: String = "NOT_STARTED", // NOT_STARTED, IN_PROGRESS, COMPLETED
    val priority: String = "MEDIUM", // LOW, MEDIUM, HIGH
    val isDifficult: Boolean = false,
    val deadlineDate: String? = null,
    val lastStudiedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "lecture_resources",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lectureId")]
)
data class LectureResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long,
    val type: String, // PDF, VIDEO
    val title: String,
    val uriString: String = "",
    val localPath: String = "",
    val pageCount: Int = 0,
    val lastReadPage: Int = 1,
    val durationSeconds: Long = 0L,
    val lastPlaybackPosition: Long = 0L,
    val notes: String = ""
)

@Entity(
    tableName = "lecture_notes",
    foreignKeys = [
        ForeignKey(
            entity = LectureEntity::class,
            parentColumns = ["id"],
            childColumns = ["lectureId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("lectureId")]
)
data class LectureNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long,
    val title: String,
    val content: String,
    val pageNumber: Int? = null,
    val videoTimestamp: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_results",
    indices = [Index("lectureId"), Index("courseId"), Index("type"), Index("isFavorite")]
)
data class AIResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long? = null,
    val courseId: Long? = null,
    val type: String, // EXPLANATION, TRANSLATION, DIAGRAM, COMPARISON, QUIZ, FLASHCARDS
    val title: String,
    val topic: String,
    val mode: String = "first_year",
    val originalText: String = "",
    val translation: String = "",
    val simpleExplanation: String = "",
    val detailedExplanation: String = "",
    val keyTermsJson: String = "[]",
    val mechanismStepsJson: String = "[]",
    val keyPointsJson: String = "[]",
    val misconceptionsJson: String = "[]",
    val comparisonTableJson: String = "",
    val diagramType: String = "mermaid",
    val diagramCode: String = "",
    val questionsJson: String = "[]",
    val flashcardsJson: String = "[]",
    val sourceDocument: String? = null,
    val sourcePage: Int? = null,
    val sourceTimestamp: Long? = null,
    val isFavorite: Boolean = false,
    val tags: String = "",
    val isOfflineGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "flashcards",
    indices = [
        Index("lectureId"),
        Index("courseId"),
        Index("dueDate"),
        // The same card can never be added twice (re-saving an explanation used to duplicate its deck).
        Index(value = ["frontText", "backText"], unique = true)
    ]
)
data class FlashcardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long? = null,
    val courseId: Long? = null,
    val frontText: String,
    val backText: String,
    val frontLanguage: String = "en",
    val backLanguage: String = "ar",
    val explanation: String = "",
    val intervalDays: Int = 1,
    val repetitionCount: Int = 0,
    val easeFactor: Float = 2.5f,
    val dueDate: Long = System.currentTimeMillis(),
    val difficulty: String = "MEDIUM",
    /** True once the card reached a mature interval (see Sm2Scheduler.MATURE_INTERVAL_DAYS). */
    val isLearned: Boolean = false,
    val lapses: Int = 0,
    val lastReviewedAt: Long = 0L,
    val cardType: String = "STANDARD", // STANDARD, CLOZE, ANATOMY
    val clozeAnswer: String = "",
    val imageUrl: String = "",
    val fsrsStability: Float = 1.0f,
    val fsrsDifficulty: Float = 5.0f,
    val fsrsState: String = "NEW" // NEW, LEARNING, REVIEW, RELEARNING
)

@Entity(
    tableName = "quiz_attempts",
    indices = [Index("lectureId"), Index("courseId"), Index("specialty"), Index("createdAt")]
)
data class QuizAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long? = null,
    val courseId: Long? = null,
    val title: String,
    val topic: String,
    val specialty: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val detailsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topic_weaknesses",
    indices = [
        Index("courseId"),
        Index("specialty"),
        Index(value = ["topic", "specialty"], unique = true)
    ]
)
data class TopicWeaknessEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long? = null,
    val topic: String,
    val specialty: String,
    val totalAttempts: Int = 0,
    val correctAttempts: Int = 0,
    val accuracyPercent: Int = 0,
    val lastAssessedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "exam_attempts",
    indices = [Index("createdAt")]
)
data class ExamAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val durationSeconds: Long,
    val timeSpentSeconds: Long,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val courseIdsJson: String = "[]",
    val topicBreakdownJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis()
)

/** One answered flashcard review. Feeds the streak and the daily statistics. */
@Entity(
    tableName = "review_logs",
    indices = [Index("reviewedAt"), Index("flashcardId")]
)
data class ReviewLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val flashcardId: Long,
    val grade: Int,
    val reviewedAt: Long = System.currentTimeMillis(),
    val intervalAfterDays: Int = 0
)

/** targetDate is an ISO date (yyyy-MM-dd) so tasks can be sorted, filtered and flagged as overdue. */
@Entity(
    tableName = "study_tasks",
    indices = [Index("courseId"), Index("lectureId"), Index("isCompleted")]
)
data class StudyTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long? = null,
    val lectureId: Long? = null,
    val title: String,
    val description: String = "",
    val targetDate: String = "",
    val estimatedMinutes: Int = 30,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

@Entity(
    tableName = "study_sessions",
    indices = [Index("lectureId")]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 means a general session that is not tied to a lecture. */
    val lectureId: Long,
    val durationMinutes: Int,
    val sessionDate: Long = System.currentTimeMillis(),
    val notes: String = ""
)

/** One cached model answer (see core.RequestPipeline). Only successful, parsed answers are stored. */
@Entity(tableName = "ai_cache", indices = [Index("lastUsedAt")])
data class AiCacheEntity(
    @PrimaryKey val cacheKey: String,
    val payload: String,
    val createdAt: Long,
    val lastUsedAt: Long
)

/** When a model request was sent: pacing per minute and the "requests today" counter. */
@Entity(tableName = "ai_usage", indices = [Index("requestedAt")])
data class AiUsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestedAt: Long
)
