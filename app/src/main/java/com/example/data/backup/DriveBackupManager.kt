package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.AIResultEntity
import com.example.data.local.CourseEntity
import com.example.data.local.ExamAttemptEntity
import com.example.data.local.FlashcardEntity
import com.example.data.local.LectureEntity
import com.example.data.local.LectureNoteEntity
import com.example.data.local.LectureResourceEntity
import com.example.data.local.MedStudyDatabase
import com.example.data.local.ModuleEntity
import com.example.data.local.QuizAttemptEntity
import com.example.data.local.StudySessionEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.local.TopicWeaknessEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class MedStudyBackup(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val appIdentifier: String = "MedStudyAI",
    val courses: List<CourseBackupDto> = emptyList(),
    val modules: List<ModuleBackupDto> = emptyList(),
    val lectures: List<LectureBackupDto> = emptyList(),
    val resources: List<ResourceBackupDto> = emptyList(),
    val notes: List<NoteBackupDto> = emptyList(),
    val aiResults: List<AiResultBackupDto> = emptyList(),
    val flashcards: List<FlashcardBackupDto> = emptyList(),
    val tasks: List<TaskBackupDto> = emptyList(),
    val sessions: List<SessionBackupDto> = emptyList(),
    val quizAttempts: List<QuizAttemptBackupDto> = emptyList(),
    val weaknesses: List<WeaknessBackupDto> = emptyList(),
    val examAttempts: List<ExamAttemptBackupDto> = emptyList()
)

@Serializable
data class CourseBackupDto(
    val id: Long,
    val title: String,
    val titleAr: String,
    val code: String,
    val semester: String,
    val colorHex: String,
    val iconName: String = "menu_book",
    val isArchived: Boolean = false,
    val createdAt: Long
)

@Serializable
data class ModuleBackupDto(
    val id: Long,
    val courseId: Long,
    val title: String,
    val titleAr: String,
    val orderIndex: Int = 0,
    val description: String = ""
)

@Serializable
data class LectureBackupDto(
    val id: Long,
    val moduleId: Long,
    val courseId: Long,
    val title: String,
    val titleAr: String,
    val orderIndex: Int = 0,
    val status: String = "NOT_STARTED",
    val priority: String = "MEDIUM",
    val isDifficult: Boolean = false,
    val deadlineDate: String? = null,
    val lastStudiedAt: Long = 0L,
    val createdAt: Long
)

@Serializable
data class ResourceBackupDto(
    val id: Long,
    val lectureId: Long,
    val type: String,
    val title: String,
    val uriString: String = "",
    val pageCount: Int = 0,
    val lastReadPage: Int = 1,
    val durationSeconds: Long = 0L,
    val lastPlaybackPosition: Long = 0L,
    val notes: String = ""
)

@Serializable
data class NoteBackupDto(
    val id: Long,
    val lectureId: Long,
    val title: String,
    val content: String,
    val pageNumber: Int? = null,
    val videoTimestamp: Long? = null,
    val createdAt: Long
)

@Serializable
data class AiResultBackupDto(
    val id: Long,
    val lectureId: Long? = null,
    val courseId: Long? = null,
    val type: String,
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
    val createdAt: Long
)

@Serializable
data class FlashcardBackupDto(
    val id: Long,
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
    val dueDate: Long,
    val difficulty: String = "MEDIUM",
    val isLearned: Boolean = false,
    val lapses: Int = 0,
    val lastReviewedAt: Long = 0L,
    val cardType: String = "STANDARD",
    val clozeAnswer: String = "",
    val imageUrl: String = "",
    val fsrsStability: Float = 1.0f,
    val fsrsDifficulty: Float = 5.0f,
    val fsrsState: String = "NEW"
)

@Serializable
data class TaskBackupDto(
    val id: Long,
    val courseId: Long? = null,
    val lectureId: Long? = null,
    val title: String,
    val description: String = "",
    val targetDate: String = "",
    val estimatedMinutes: Int = 30,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null
)

@Serializable
data class SessionBackupDto(
    val id: Long,
    val lectureId: Long,
    val durationMinutes: Int,
    val sessionDate: Long,
    val notes: String = ""
)

@Serializable
data class QuizAttemptBackupDto(
    val id: Long,
    val lectureId: Long? = null,
    val courseId: Long? = null,
    val title: String,
    val topic: String,
    val specialty: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val detailsJson: String = "[]",
    val createdAt: Long
)

@Serializable
data class WeaknessBackupDto(
    val id: Long,
    val courseId: Long? = null,
    val topic: String,
    val specialty: String,
    val totalAttempts: Int = 0,
    val correctAttempts: Int = 0,
    val accuracyPercent: Int = 0,
    val lastAssessedAt: Long
)

@Serializable
data class ExamAttemptBackupDto(
    val id: Long,
    val title: String,
    val durationSeconds: Long,
    val timeSpentSeconds: Long,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val courseIdsJson: String = "[]",
    val topicBreakdownJson: String = "{}",
    val createdAt: Long
)

data class BackupMetadata(
    val timestamp: Long,
    val version: Int,
    val coursesCount: Int,
    val flashcardsCount: Int,
    val notesCount: Int,
    val quizCount: Int,
    val examsCount: Int,
    val formattedDate: String
)

class DriveBackupManager(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    suspend fun createBackupPayload(database: MedStudyDatabase): MedStudyBackup = withContext(Dispatchers.IO) {
        val courses = database.courseDao().getAllCoursesList().map {
            CourseBackupDto(it.id, it.title, it.titleAr, it.code, it.semester, it.colorHex, it.iconName, it.isArchived, it.createdAt)
        }
        val modules = database.courseDao().getAllModulesList().map {
            ModuleBackupDto(it.id, it.courseId, it.title, it.titleAr, it.orderIndex, it.description)
        }
        val lectures = database.courseDao().getAllLecturesList().map {
            LectureBackupDto(it.id, it.moduleId, it.courseId, it.title, it.titleAr, it.orderIndex, it.status, it.priority, it.isDifficult, it.deadlineDate, it.lastStudiedAt, it.createdAt)
        }
        val resources = database.resourceDao().getAllResourcesList().map {
            ResourceBackupDto(it.id, it.lectureId, it.type, it.title, it.uriString, it.pageCount, it.lastReadPage, it.durationSeconds, it.lastPlaybackPosition, it.notes)
        }
        val notes = database.resourceDao().getAllNotesList().map {
            NoteBackupDto(it.id, it.lectureId, it.title, it.content, it.pageNumber, it.videoTimestamp, it.createdAt)
        }
        val aiResults = database.aiResultDao().getAllAIResultsList().map {
            AiResultBackupDto(it.id, it.lectureId, it.courseId, it.type, it.title, it.topic, it.mode, it.originalText, it.translation, it.simpleExplanation, it.detailedExplanation, it.keyTermsJson, it.mechanismStepsJson, it.keyPointsJson, it.misconceptionsJson, it.comparisonTableJson, it.diagramType, it.diagramCode, it.questionsJson, it.flashcardsJson, it.sourceDocument, it.sourcePage, it.sourceTimestamp, it.isFavorite, it.tags, it.isOfflineGenerated, it.createdAt)
        }
        val flashcards = database.flashcardDao().getAllFlashcardsList().map {
            FlashcardBackupDto(it.id, it.lectureId, it.courseId, it.frontText, it.backText, it.frontLanguage, it.backLanguage, it.explanation, it.intervalDays, it.repetitionCount, it.easeFactor, it.dueDate, it.difficulty, it.isLearned, it.lapses, it.lastReviewedAt, it.cardType, it.clozeAnswer, it.imageUrl, it.fsrsStability, it.fsrsDifficulty, it.fsrsState)
        }
        val tasks = database.studyPlannerDao().getAllTasksList().map {
            TaskBackupDto(it.id, it.courseId, it.lectureId, it.title, it.description, it.targetDate, it.estimatedMinutes, it.isCompleted, it.completedAt)
        }
        val sessions = database.studyPlannerDao().getAllSessionsList().map {
            SessionBackupDto(it.id, it.lectureId, it.durationMinutes, it.sessionDate, it.notes)
        }
        val quizAttempts = database.quizAttemptDao().getAllAttemptsList().map {
            QuizAttemptBackupDto(it.id, it.lectureId, it.courseId, it.title, it.topic, it.specialty, it.totalQuestions, it.correctCount, it.scorePercent, it.detailsJson, it.createdAt)
        }
        val weaknesses = database.topicWeaknessDao().getAllWeaknessesList().map {
            WeaknessBackupDto(it.id, it.courseId, it.topic, it.specialty, it.totalAttempts, it.correctAttempts, it.accuracyPercent, it.lastAssessedAt)
        }
        val examAttempts = database.examAttemptDao().getAllExamAttemptsList().map {
            ExamAttemptBackupDto(it.id, it.title, it.durationSeconds, it.timeSpentSeconds, it.totalQuestions, it.correctCount, it.scorePercent, it.courseIdsJson, it.topicBreakdownJson, it.createdAt)
        }

        MedStudyBackup(
            courses = courses,
            modules = modules,
            lectures = lectures,
            resources = resources,
            notes = notes,
            aiResults = aiResults,
            flashcards = flashcards,
            tasks = tasks,
            sessions = sessions,
            quizAttempts = quizAttempts,
            weaknesses = weaknesses,
            examAttempts = examAttempts
        )
    }

    suspend fun exportBackupToUri(database: MedStudyDatabase, targetUri: Uri): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val payload = createBackupPayload(database)
            val jsonString = json.encodeToString(payload)

            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                out.write(jsonString.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return@withContext Result.failure(Exception("تعذر فتح وجهة الحفظ المحددة في Google Drive."))

            // Save local snapshot cache as well
            saveLocalSnapshot(jsonString)

            val metadata = toMetadata(payload)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectBackupFromUri(sourceUri: Uri): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("تعذر فتح ملف النسخة الاحتياطية."))

            val backup = json.decodeFromString<MedStudyBackup>(jsonString)
            Result.success(toMetadata(backup))
        } catch (e: Exception) {
            Result.failure(Exception("ملف النسخة الاحتياطية غير صالح أو تالف."))
        }
    }

    suspend fun restoreBackupFromUri(database: MedStudyDatabase, sourceUri: Uri): Result<BackupMetadata> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: return@withContext Result.failure(Exception("تعذر فتح ملف النسخة الاحتياطية."))

            val backup = json.decodeFromString<MedStudyBackup>(jsonString)

            // Perform restoration into database
            if (backup.courses.isNotEmpty()) {
                database.courseDao().insertCourses(backup.courses.map {
                    CourseEntity(it.id, it.title, it.titleAr, it.code, it.semester, it.colorHex, it.iconName, it.isArchived, it.createdAt)
                })
            }
            if (backup.modules.isNotEmpty()) {
                database.courseDao().insertModules(backup.modules.map {
                    ModuleEntity(it.id, it.courseId, it.title, it.titleAr, it.orderIndex, it.description)
                })
            }
            if (backup.lectures.isNotEmpty()) {
                database.courseDao().insertLectures(backup.lectures.map {
                    LectureEntity(it.id, it.moduleId, it.courseId, it.title, it.titleAr, it.orderIndex, it.status, it.priority, it.isDifficult, it.deadlineDate, it.lastStudiedAt, it.createdAt)
                })
            }
            if (backup.resources.isNotEmpty()) {
                database.resourceDao().insertResources(backup.resources.map {
                    LectureResourceEntity(it.id, it.lectureId, it.type, it.title, it.uriString, "", it.pageCount, it.lastReadPage, it.durationSeconds, it.lastPlaybackPosition, it.notes)
                })
            }
            if (backup.notes.isNotEmpty()) {
                database.resourceDao().insertNotes(backup.notes.map {
                    LectureNoteEntity(it.id, it.lectureId, it.title, it.content, it.pageNumber, it.videoTimestamp, it.createdAt)
                })
            }
            if (backup.aiResults.isNotEmpty()) {
                database.aiResultDao().insertAIResults(backup.aiResults.map {
                    AIResultEntity(it.id, it.lectureId, it.courseId, it.type, it.title, it.topic, it.mode, it.originalText, it.translation, it.simpleExplanation, it.detailedExplanation, it.keyTermsJson, it.mechanismStepsJson, it.keyPointsJson, it.misconceptionsJson, it.comparisonTableJson, it.diagramType, it.diagramCode, it.questionsJson, it.flashcardsJson, it.sourceDocument, it.sourcePage, it.sourceTimestamp, it.isFavorite, it.tags, it.isOfflineGenerated, it.createdAt)
                })
            }
            if (backup.flashcards.isNotEmpty()) {
                database.flashcardDao().insertAllFlashcards(backup.flashcards.map {
                    FlashcardEntity(it.id, it.lectureId, it.courseId, it.frontText, it.backText, it.frontLanguage, it.backLanguage, it.explanation, it.intervalDays, it.repetitionCount, it.easeFactor, it.dueDate, it.difficulty, it.isLearned, it.lapses, it.lastReviewedAt, it.cardType, it.clozeAnswer, it.imageUrl, it.fsrsStability, it.fsrsDifficulty, it.fsrsState)
                })
            }
            if (backup.tasks.isNotEmpty()) {
                database.studyPlannerDao().insertTasks(backup.tasks.map {
                    StudyTaskEntity(it.id, it.courseId, it.lectureId, it.title, it.description, it.targetDate, it.estimatedMinutes, it.isCompleted, it.completedAt)
                })
            }
            if (backup.sessions.isNotEmpty()) {
                database.studyPlannerDao().insertSessions(backup.sessions.map {
                    StudySessionEntity(it.id, it.lectureId, it.durationMinutes, it.sessionDate, it.notes)
                })
            }
            if (backup.quizAttempts.isNotEmpty()) {
                database.quizAttemptDao().insertAttempts(backup.quizAttempts.map {
                    QuizAttemptEntity(it.id, it.lectureId, it.courseId, it.title, it.topic, it.specialty, it.totalQuestions, it.correctCount, it.scorePercent, it.detailsJson, it.createdAt)
                })
            }
            if (backup.weaknesses.isNotEmpty()) {
                database.topicWeaknessDao().insertWeaknesses(backup.weaknesses.map {
                    TopicWeaknessEntity(it.id, it.courseId, it.topic, it.specialty, it.totalAttempts, it.correctAttempts, it.accuracyPercent, it.lastAssessedAt)
                })
            }
            if (backup.examAttempts.isNotEmpty()) {
                database.examAttemptDao().insertExamAttempts(backup.examAttempts.map {
                    ExamAttemptEntity(it.id, it.title, it.durationSeconds, it.timeSpentSeconds, it.totalQuestions, it.correctCount, it.scorePercent, it.courseIdsJson, it.topicBreakdownJson, it.createdAt)
                })
            }

            saveLocalSnapshot(jsonString)
            Result.success(toMetadata(backup))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveLocalSnapshot(jsonString: String) {
        try {
            val dir = File(context.filesDir, "backups").apply { mkdirs() }
            val file = File(dir, "latest_backup.json")
            file.writeText(jsonString, Charsets.UTF_8)
        } catch (_: Exception) {
            // non-fatal
        }
    }

    fun getLocalSnapshotMetadata(): BackupMetadata? {
        val file = File(context.filesDir, "backups/latest_backup.json")
        if (!file.exists()) return null
        return try {
            val backup = json.decodeFromString<MedStudyBackup>(file.readText(Charsets.UTF_8))
            toMetadata(backup)
        } catch (_: Exception) {
            null
        }
    }

    private fun toMetadata(backup: MedStudyBackup): BackupMetadata {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        return BackupMetadata(
            timestamp = backup.timestamp,
            version = backup.version,
            coursesCount = backup.courses.size,
            flashcardsCount = backup.flashcards.size,
            notesCount = backup.notes.size + backup.aiResults.size,
            quizCount = backup.quizAttempts.size,
            examsCount = backup.examAttempts.size,
            formattedDate = sdf.format(Date(backup.timestamp))
        )
    }
}
