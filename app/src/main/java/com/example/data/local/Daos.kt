package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE isArchived = 0 ORDER BY id ASC")
    fun getAllActiveCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses ORDER BY id ASC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getCourseById(id: Long): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity): Long

    @Update
    suspend fun updateCourse(course: CourseEntity)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    // Modules
    @Query("SELECT * FROM modules WHERE courseId = :courseId ORDER BY orderIndex ASC")
    fun getModulesForCourse(courseId: Long): Flow<List<ModuleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModule(module: ModuleEntity): Long

    @Update
    suspend fun updateModule(module: ModuleEntity)

    @Delete
    suspend fun deleteModule(module: ModuleEntity)

    // Lectures
    @Query("SELECT * FROM lectures WHERE moduleId = :moduleId ORDER BY orderIndex ASC")
    fun getLecturesForModule(moduleId: Long): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE courseId = :courseId ORDER BY moduleId ASC, orderIndex ASC")
    fun getLecturesForCourse(courseId: Long): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE id = :id LIMIT 1")
    suspend fun getLectureById(id: Long): LectureEntity?

    @Query("SELECT * FROM lectures ORDER BY lastStudiedAt DESC LIMIT :limit")
    fun getRecentLectures(limit: Int = 5): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE isDifficult = 1")
    fun getDifficultLectures(): Flow<List<LectureEntity>>

    @Query("SELECT * FROM lectures WHERE lastStudiedAt > 0 ORDER BY lastStudiedAt DESC LIMIT 1")
    fun getLastStudiedLecture(): Flow<LectureEntity?>

    @Query("SELECT COUNT(*) FROM lectures")
    suspend fun countLectures(): Int

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun countCourses(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLecture(lecture: LectureEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModules(modules: List<ModuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLectures(lectures: List<LectureEntity>)

    @Query("SELECT * FROM courses")
    suspend fun getAllCoursesList(): List<CourseEntity>

    @Query("SELECT * FROM modules")
    suspend fun getAllModulesList(): List<ModuleEntity>

    @Query("SELECT * FROM lectures")
    suspend fun getAllLecturesList(): List<LectureEntity>

    @Update
    suspend fun updateLecture(lecture: LectureEntity)

    @Delete
    suspend fun deleteLecture(lecture: LectureEntity)
}

@Dao
interface ResourceDao {
    @Query("SELECT * FROM lecture_resources WHERE lectureId = :lectureId")
    fun getResourcesForLecture(lectureId: Long): Flow<List<LectureResourceEntity>>

    @Query("SELECT * FROM lecture_resources WHERE id = :id LIMIT 1")
    suspend fun getResourceById(id: Long): LectureResourceEntity?

    @Query("SELECT * FROM lecture_resources WHERE lectureId = :lectureId AND type = :type ORDER BY id DESC")
    suspend fun getResourcesOfType(lectureId: Long, type: String): List<LectureResourceEntity>

    @Query("SELECT * FROM lecture_resources WHERE title = :title AND type = :type ORDER BY id DESC LIMIT 1")
    suspend fun findResourceByTitle(title: String, type: String): LectureResourceEntity?

    @Query("SELECT * FROM lecture_notes WHERE lectureId = :lectureId AND videoTimestamp IS NOT NULL ORDER BY videoTimestamp ASC")
    fun getTimestampNotes(lectureId: Long): Flow<List<LectureNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: LectureResourceEntity): Long

    @Update
    suspend fun updateResource(resource: LectureResourceEntity)

    @Delete
    suspend fun deleteResource(resource: LectureResourceEntity)

    // Notes
    @Query("SELECT * FROM lecture_notes WHERE lectureId = :lectureId ORDER BY createdAt DESC")
    fun getNotesForLecture(lectureId: Long): Flow<List<LectureNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: LectureNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResources(resources: List<LectureResourceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<LectureNoteEntity>)

    @Query("SELECT * FROM lecture_resources")
    suspend fun getAllResourcesList(): List<LectureResourceEntity>

    @Query("SELECT * FROM lecture_notes")
    suspend fun getAllNotesList(): List<LectureNoteEntity>

    @Delete
    suspend fun deleteNote(note: LectureNoteEntity)
}

@Dao
interface AIResultDao {
    @Query("SELECT * FROM ai_results ORDER BY createdAt DESC")
    fun getAllAIResults(): Flow<List<AIResultEntity>>

    @Query("SELECT * FROM ai_results WHERE type = :type ORDER BY createdAt DESC")
    fun getAIResultsByType(type: String): Flow<List<AIResultEntity>>

    @Query("SELECT * FROM ai_results WHERE isFavorite = 1 ORDER BY createdAt DESC")
    fun getFavoriteAIResults(): Flow<List<AIResultEntity>>

    @Query("SELECT * FROM ai_results WHERE lectureId = :lectureId ORDER BY createdAt DESC")
    fun getAIResultsForLecture(lectureId: Long): Flow<List<AIResultEntity>>

    @Query("SELECT * FROM ai_results WHERE id = :id LIMIT 1")
    suspend fun getAIResultById(id: Long): AIResultEntity?

    // The caller escapes % and _ (see MedStudyRepository.escapeLike) so a search for "50%" is literal.
    @Query("""
        SELECT * FROM ai_results 
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
           OR topic LIKE '%' || :query || '%' ESCAPE '\'
           OR originalText LIKE '%' || :query || '%' ESCAPE '\'
           OR translation LIKE '%' || :query || '%' ESCAPE '\'
           OR simpleExplanation LIKE '%' || :query || '%' ESCAPE '\'
           OR detailedExplanation LIKE '%' || :query || '%' ESCAPE '\'
           OR keyTermsJson LIKE '%' || :query || '%' ESCAPE '\'
           OR tags LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY createdAt DESC
    """)
    fun searchAIResults(query: String): Flow<List<AIResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAIResult(result: AIResultEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAIResults(results: List<AIResultEntity>)

    @Query("SELECT * FROM ai_results")
    suspend fun getAllAIResultsList(): List<AIResultEntity>

    @Update
    suspend fun updateAIResult(result: AIResultEntity)

    @Delete
    suspend fun deleteAIResult(result: AIResultEntity)
}

@Dao
interface FlashcardDao {
    @Query("SELECT * FROM flashcards ORDER BY dueDate ASC")
    fun getAllFlashcards(): Flow<List<FlashcardEntity>>

    @Query("SELECT * FROM flashcards")
    suspend fun getAllFlashcardsList(): List<FlashcardEntity>

    @Query("SELECT * FROM flashcards WHERE lectureId = :lectureId")
    fun getFlashcardsForLecture(lectureId: Long): Flow<List<FlashcardEntity>>

    /** Cards whose due time is before [before] (use the start of tomorrow for "due today"). */
    @Query("SELECT * FROM flashcards WHERE dueDate < :before ORDER BY dueDate ASC, id ASC")
    fun getDueFlashcards(before: Long): Flow<List<FlashcardEntity>>

    @Query("SELECT COUNT(*) FROM flashcards WHERE dueDate < :before")
    fun countDue(before: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards")
    fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM flashcards WHERE isLearned = 1")
    fun countMature(): Flow<Int>

    @Query("SELECT * FROM flashcards WHERE id = :id LIMIT 1")
    suspend fun getFlashcardById(id: Long): FlashcardEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFlashcard(flashcard: FlashcardEntity): Long

    /** Duplicates (same front and back) are skipped by the unique index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllFlashcards(flashcards: List<FlashcardEntity>): List<Long>

    @Update
    suspend fun updateFlashcard(flashcard: FlashcardEntity)

    @Delete
    suspend fun deleteFlashcard(flashcard: FlashcardEntity)

    @Insert
    suspend fun insertReviewLog(log: ReviewLogEntity): Long

    @Query("SELECT reviewedAt FROM review_logs WHERE reviewedAt >= :since")
    fun getReviewTimestampsSince(since: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM review_logs WHERE reviewedAt >= :since")
    fun countReviewsSince(since: Long): Flow<Int>
}

@Dao
interface StudyPlannerDao {
    @Query("SELECT * FROM study_tasks ORDER BY isCompleted ASC, targetDate ASC, id ASC")
    fun getAllTasks(): Flow<List<StudyTaskEntity>>

    @Query("SELECT * FROM study_tasks WHERE targetDate = :date ORDER BY isCompleted ASC")
    fun getTasksForDate(date: String): Flow<List<StudyTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: StudyTaskEntity): Long

    @Update
    suspend fun updateTask(task: StudyTaskEntity)

    @Delete
    suspend fun deleteTask(task: StudyTaskEntity)

    @Query("SELECT * FROM study_sessions ORDER BY sessionDate DESC LIMIT :limit")
    fun getRecentSessions(limit: Int = 10): Flow<List<StudySessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<StudyTaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySessionEntity>)

    @Query("SELECT * FROM study_tasks")
    suspend fun getAllTasksList(): List<StudyTaskEntity>

    @Query("SELECT * FROM study_sessions")
    suspend fun getAllSessionsList(): List<StudySessionEntity>

    @Query("SELECT SUM(durationMinutes) FROM study_sessions WHERE sessionDate >= :sinceTime")
    fun getTotalStudyMinutesSince(sinceTime: Long): Flow<Int?>

    @Query("SELECT sessionDate FROM study_sessions WHERE sessionDate >= :since")
    fun getSessionTimestampsSince(since: Long): Flow<List<Long>>
}

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE cacheKey = :key AND createdAt >= :notBefore LIMIT 1")
    suspend fun find(key: String, notBefore: Long): AiCacheEntity?

    @Query("UPDATE ai_cache SET lastUsedAt = :now WHERE cacheKey = :key")
    suspend fun touch(key: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AiCacheEntity)

    @Query("SELECT COUNT(*) FROM ai_cache")
    suspend fun count(): Int

    @Query("DELETE FROM ai_cache WHERE cacheKey IN (SELECT cacheKey FROM ai_cache ORDER BY lastUsedAt ASC LIMIT :n)")
    suspend fun deleteOldest(n: Int)

    @Query("DELETE FROM ai_cache WHERE cacheKey = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM ai_cache WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM ai_cache")
    suspend fun clear()
}

@Dao
interface AiUsageDao {
    @Insert
    suspend fun insert(entry: AiUsageEntity): Long

    @Query("SELECT requestedAt FROM ai_usage WHERE requestedAt >= :since ORDER BY requestedAt ASC")
    suspend fun since(since: Long): List<Long>

    @Query("SELECT COUNT(*) FROM ai_usage WHERE requestedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("DELETE FROM ai_usage WHERE requestedAt < :before")
    suspend fun deleteBefore(before: Long)
}

@Dao
interface QuizAttemptDao {
    @Query("SELECT * FROM quiz_attempts ORDER BY createdAt DESC")
    fun getAllAttempts(): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE lectureId = :lectureId ORDER BY createdAt DESC")
    fun getAttemptsForLecture(lectureId: Long): Flow<List<QuizAttemptEntity>>

    @Query("SELECT * FROM quiz_attempts WHERE courseId = :courseId ORDER BY createdAt DESC")
    fun getAttemptsForCourse(courseId: Long): Flow<List<QuizAttemptEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: QuizAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<QuizAttemptEntity>)

    @Query("SELECT * FROM quiz_attempts")
    suspend fun getAllAttemptsList(): List<QuizAttemptEntity>

    @Query("SELECT AVG(scorePercent) FROM quiz_attempts")
    fun getAverageScore(): Flow<Double?>
}

@Dao
interface TopicWeaknessDao {
    @Query("SELECT * FROM topic_weaknesses ORDER BY accuracyPercent ASC")
    fun getAllWeaknesses(): Flow<List<TopicWeaknessEntity>>

    @Query("SELECT * FROM topic_weaknesses")
    suspend fun getAllWeaknessesList(): List<TopicWeaknessEntity>

    @Query("SELECT * FROM topic_weaknesses WHERE courseId = :courseId ORDER BY accuracyPercent ASC")
    fun getWeaknessesForCourse(courseId: Long): Flow<List<TopicWeaknessEntity>>

    @Query("SELECT * FROM topic_weaknesses WHERE accuracyPercent < 70 ORDER BY accuracyPercent ASC")
    fun getCriticalWeaknesses(): Flow<List<TopicWeaknessEntity>>

    @Query("SELECT * FROM topic_weaknesses WHERE topic = :topic AND specialty = :specialty LIMIT 1")
    suspend fun findByTopicAndSpecialty(topic: String, specialty: String): TopicWeaknessEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeakness(weakness: TopicWeaknessEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeaknesses(weaknesses: List<TopicWeaknessEntity>)
}

@Dao
interface ExamAttemptDao {
    @Query("SELECT * FROM exam_attempts ORDER BY createdAt DESC")
    fun getAllExamAttempts(): Flow<List<ExamAttemptEntity>>

    @Query("SELECT * FROM exam_attempts")
    suspend fun getAllExamAttemptsList(): List<ExamAttemptEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamAttempt(attempt: ExamAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamAttempts(attempts: List<ExamAttemptEntity>)

    @Query("SELECT COUNT(*) FROM exam_attempts")
    fun countExams(): Flow<Int>

    @Query("SELECT AVG(scorePercent) FROM exam_attempts")
    fun getAverageExamScore(): Flow<Double?>
}
