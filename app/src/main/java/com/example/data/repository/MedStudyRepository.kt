package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.core.AnkiExporter
import com.example.core.DateUtils
import com.example.core.ExamQuestion
import com.example.core.ExamReport
import com.example.core.ExportCard
import com.example.core.FsrsScheduler
import com.example.core.ReviewGrade
import com.example.core.Sm2Scheduler
import com.example.core.SmartCardParser
import com.example.core.SrsState
import com.example.core.StreakCalculator
import com.example.core.StudyStats
import com.example.data.local.*
import com.example.data.model.*
import com.example.data.remote.MedicalAiResult
import com.example.data.remote.MedicalAiService
import com.example.data.sample.SamplePdfGenerator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The lecture the student studied last, with the resource to reopen. */
data class ContinueItem(
    val lecture: LectureEntity,
    val course: CourseEntity?,
    val resource: LectureResourceEntity?
)

class MedStudyRepository(
    private val context: Context,
    private val database: MedStudyDatabase,
    private val aiService: MedicalAiService
) {
    private val courseDao = database.courseDao()
    private val resourceDao = database.resourceDao()
    private val aiResultDao = database.aiResultDao()
    private val flashcardDao = database.flashcardDao()
    private val studyPlannerDao = database.studyPlannerDao()
    private val quizAttemptDao = database.quizAttemptDao()
    private val topicWeaknessDao = database.topicWeaknessDao()
    private val examAttemptDao = database.examAttemptDao()
    private val seedMutex = Mutex()

    // --- Flows ---
    val allCourses: Flow<List<CourseEntity>> = courseDao.getAllActiveCourses()
    val allSavedResults: Flow<List<AIResultEntity>> = aiResultDao.getAllAIResults()
    val favoriteResults: Flow<List<AIResultEntity>> = aiResultDao.getFavoriteAIResults()
    val allFlashcards: Flow<List<FlashcardEntity>> = flashcardDao.getAllFlashcards()
    val allTasks: Flow<List<StudyTaskEntity>> = studyPlannerDao.getAllTasks()
    val recentLectures: Flow<List<LectureEntity>> = courseDao.getRecentLectures(5)
    val difficultLectures: Flow<List<LectureEntity>> = courseDao.getDifficultLectures()
    val allQuizAttempts: Flow<List<QuizAttemptEntity>> = quizAttemptDao.getAllAttempts()
    val allTopicWeaknesses: Flow<List<TopicWeaknessEntity>> = topicWeaknessDao.getAllWeaknesses()
    val criticalWeaknesses: Flow<List<TopicWeaknessEntity>> = topicWeaknessDao.getCriticalWeaknesses()
    val allExamAttempts: Flow<List<ExamAttemptEntity>> = examAttemptDao.getAllExamAttempts()
    val averageQuizScore: Flow<Double?> = quizAttemptDao.getAverageScore()
    val averageExamScore: Flow<Double?> = examAttemptDao.getAverageExamScore()

    /** Cards due before the end of today (evaluated each time the flow is collected). */
    val dueFlashcards: Flow<List<FlashcardEntity>> = flow {
        emitAll(flashcardDao.getDueFlashcards(Sm2Scheduler.startOfTomorrow(System.currentTimeMillis())))
    }

    val studyStats: Flow<StudyStats> = flow { emitAll(studyStatsFlow(System.currentTimeMillis())) }

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueStudying: Flow<ContinueItem?> = courseDao.getLastStudiedLecture().flatMapLatest { lecture ->
        if (lecture == null) flowOf(null)
        else resourceDao.getResourcesForLecture(lecture.id).map { resources ->
            ContinueItem(
                lecture = lecture,
                course = courseDao.getCourseById(lecture.courseId),
                resource = resources.firstOrNull { it.type == "PDF" } ?: resources.firstOrNull()
            )
        }
    }

    private fun studyStatsFlow(now: Long): Flow<StudyStats> {
        val since = Sm2Scheduler.startOfDayPlusDays(now, -60)
        val weekStart = Sm2Scheduler.startOfDayPlusDays(now, -6)
        val todayStart = DateUtils.startOfDay(now)
        val activity = combine(
            flashcardDao.getReviewTimestampsSince(since),
            studyPlannerDao.getSessionTimestampsSince(since)
        ) { reviews, sessions -> reviews to sessions }
        val cards = combine(
            flashcardDao.countAll(),
            flashcardDao.countMature(),
            flashcardDao.countDue(Sm2Scheduler.startOfTomorrow(now))
        ) { total, mature, due -> Triple(total, mature, due) }
        return combine(activity, cards, studyPlannerDao.getTotalStudyMinutesSince(weekStart)) { act, c, minutes ->
            StudyStats(
                streakDays = StreakCalculator.currentStreak(act.first + act.second, now),
                reviewsToday = act.first.count { it >= todayStart },
                minutesThisWeek = minutes ?: 0,
                totalCards = c.first,
                matureCards = c.second,
                dueToday = c.third
            )
        }
    }

    fun getModulesForCourse(courseId: Long): Flow<List<ModuleEntity>> = courseDao.getModulesForCourse(courseId)
    fun getLecturesForModule(moduleId: Long): Flow<List<LectureEntity>> = courseDao.getLecturesForModule(moduleId)
    fun getLecturesForCourse(courseId: Long): Flow<List<LectureEntity>> = courseDao.getLecturesForCourse(courseId)
    fun getResourcesForLecture(lectureId: Long): Flow<List<LectureResourceEntity>> = resourceDao.getResourcesForLecture(lectureId)
    fun getTimestampNotes(lectureId: Long): Flow<List<LectureNoteEntity>> = resourceDao.getTimestampNotes(lectureId)
    fun getNotesForLecture(lectureId: Long): Flow<List<LectureNoteEntity>> = resourceDao.getNotesForLecture(lectureId)
    fun getAIResultsForLecture(lectureId: Long): Flow<List<AIResultEntity>> = aiResultDao.getAIResultsForLecture(lectureId)
    fun getResultsByType(type: String): Flow<List<AIResultEntity>> = aiResultDao.getAIResultsByType(type)
    fun searchSavedResults(query: String): Flow<List<AIResultEntity>> = aiResultDao.searchAIResults(escapeLike(query))

    suspend fun getCourseById(id: Long) = courseDao.getCourseById(id)
    suspend fun getLectureById(id: Long) = courseDao.getLectureById(id)
    suspend fun getAIResultById(id: Long) = aiResultDao.getAIResultById(id)
    suspend fun getResourceById(id: Long) = resourceDao.getResourceById(id)
    suspend fun findResourceByTitle(title: String, type: String) = resourceDao.findResourceByTitle(title, type)
    suspend fun getFirstResource(lectureId: Long, type: String) = resourceDao.getResourcesOfType(lectureId, type).firstOrNull()

    // --- DB Mutations ---
    suspend fun insertCourse(course: CourseEntity) = courseDao.insertCourse(course)
    suspend fun updateCourse(course: CourseEntity) = courseDao.updateCourse(course)

    /** Deletes the course (rows cascade) and the PDF copies that belong to its lectures. */
    suspend fun deleteCourse(course: CourseEntity) {
        val lectures = courseDao.getLecturesForCourse(course.id).first()
        lectures.forEach { deleteLectureFiles(it.id) }
        courseDao.deleteCourse(course)
    }

    suspend fun insertModule(module: ModuleEntity) = courseDao.insertModule(module)
    suspend fun updateModule(module: ModuleEntity) = courseDao.updateModule(module)

    suspend fun deleteModule(module: ModuleEntity) {
        courseDao.getLecturesForModule(module.id).first().forEach { deleteLectureFiles(it.id) }
        courseDao.deleteModule(module)
    }

    suspend fun insertLecture(lecture: LectureEntity) = courseDao.insertLecture(lecture)
    suspend fun updateLecture(lecture: LectureEntity) = courseDao.updateLecture(lecture)

    suspend fun deleteLecture(lecture: LectureEntity) {
        deleteLectureFiles(lecture.id)
        courseDao.deleteLecture(lecture)
    }

    suspend fun insertResource(resource: LectureResourceEntity) = resourceDao.insertResource(resource)
    suspend fun updateResource(resource: LectureResourceEntity) = resourceDao.updateResource(resource)

    suspend fun deleteResource(resource: LectureResourceEntity) {
        deleteOwnedFile(resource)
        resourceDao.deleteResource(resource)
    }

    suspend fun insertNote(note: LectureNoteEntity) = resourceDao.insertNote(note)
    suspend fun deleteNote(note: LectureNoteEntity) = resourceDao.deleteNote(note)

    suspend fun insertTask(task: StudyTaskEntity) = studyPlannerDao.insertTask(task)
    suspend fun updateTask(task: StudyTaskEntity) = studyPlannerDao.updateTask(task)
    suspend fun deleteTask(task: StudyTaskEntity) = studyPlannerDao.deleteTask(task)

    suspend fun insertSession(session: StudySessionEntity) = studyPlannerDao.insertSession(session)
    suspend fun deleteAIResult(result: AIResultEntity) = aiResultDao.deleteAIResult(result)
    suspend fun updateAIResult(result: AIResultEntity) = aiResultDao.updateAIResult(result)

    /** Marks the lecture as being studied now so it shows up under "continue studying". */
    suspend fun touchLecture(lectureId: Long) {
        val lecture = courseDao.getLectureById(lectureId) ?: return
        courseDao.updateLecture(
            lecture.copy(
                lastStudiedAt = System.currentTimeMillis(),
                status = if (lecture.status == "NOT_STARTED") "IN_PROGRESS" else lecture.status
            )
        )
    }

    /** Logs a finished focus session (Pomodoro). [lectureId] 0 means a general session. */
    suspend fun recordStudySession(minutes: Int, lectureId: Long = 0L, notes: String = "") {
        if (minutes <= 0) return
        studyPlannerDao.insertSession(StudySessionEntity(lectureId = lectureId, durationMinutes = minutes, notes = notes))
        if (lectureId > 0) touchLecture(lectureId)
    }

    // --- Save AI Result ---
    suspend fun saveAIResult(
        result: MedicalAiResult,
        lectureId: Long? = null,
        courseId: Long? = null,
        sourceDoc: String? = null,
        sourcePage: Int? = null,
        sourceTimestamp: Long? = null
    ): Long {
        val keyTermsJson = JSONArray().apply {
            result.keyTerms.forEach { t ->
                put(JSONObject().apply {
                    put("term", t.term)
                    put("arabic_equivalent", t.arabicEquivalent)
                    put("definition", t.definition)
                })
            }
        }.toString()

        val mechanismStepsJson = JSONArray(result.mechanismSteps).toString()
        val keyPointsJson = JSONArray(result.keyPoints).toString()
        val misconceptionsJson = JSONArray(result.misconceptions).toString()

        val comparisonTableJson = result.comparisonTable?.let { table ->
            JSONObject().apply {
                put("title", table.title)
                put("columns", JSONArray(table.columns))
                val rowsArr = JSONArray()
                table.rows.forEach { r -> rowsArr.put(JSONArray(r)) }
                put("rows", rowsArr)
            }
        }?.toString() ?: ""

        val questionsJson = JSONArray().apply {
            result.questions.forEach { q ->
                put(JSONObject().apply {
                    put("question", q.question)
                    put("options", JSONArray(q.options))
                    put("correct_index", q.correctIndex)
                    put("explanation", q.explanation)
                })
            }
        }.toString()

        val flashcardsJson = JSONArray().apply {
            result.flashcards.forEach { f ->
                put(JSONObject().apply {
                    put("front", f.front)
                    put("back", f.back)
                    put("explanation", f.explanation)
                })
            }
        }.toString()

        val entity = AIResultEntity(
            lectureId = lectureId,
            courseId = courseId,
            type = if (result.mode == "translation") "TRANSLATION" else "EXPLANATION",
            title = result.title,
            topic = result.topic,
            mode = result.mode,
            originalText = result.originalText,
            translation = result.translation,
            simpleExplanation = result.simpleExplanation,
            detailedExplanation = result.detailedExplanation,
            keyTermsJson = keyTermsJson,
            mechanismStepsJson = mechanismStepsJson,
            keyPointsJson = keyPointsJson,
            misconceptionsJson = misconceptionsJson,
            comparisonTableJson = comparisonTableJson,
            diagramType = result.diagramType,
            diagramCode = result.diagramCode,
            questionsJson = questionsJson,
            flashcardsJson = flashcardsJson,
            sourceDocument = sourceDoc,
            sourcePage = sourcePage,
            sourceTimestamp = sourceTimestamp,
            tags = "${result.topic}, ${result.mode}",
            isOfflineGenerated = result.isOfflineGenerated
        )
        val id = aiResultDao.insertAIResult(entity)
        addFlashcards(result.flashcards, lectureId, courseId)
        return id
    }

    /** Adds cards to the spaced-repetition deck. Cards that already exist (same front and back) are skipped. */
    suspend fun addFlashcards(cards: List<FlashcardItem>, lectureId: Long? = null, courseId: Long? = null): Int {
        val entities = cards
            .filter { it.front.isNotBlank() && it.back.isNotBlank() }
            .map {
                FlashcardEntity(
                    lectureId = lectureId,
                    courseId = courseId,
                    frontText = it.front.trim(),
                    backText = it.back.trim(),
                    explanation = it.explanation.trim()
                )
            }
        if (entities.isEmpty()) return 0
        return flashcardDao.insertAllFlashcards(entities).count { it != -1L }
    }

    suspend fun addFlashcard(front: String, back: String, explanation: String = ""): Boolean =
        addFlashcards(listOf(FlashcardItem(front, back, explanation))) > 0

    suspend fun deleteFlashcard(card: FlashcardEntity) = flashcardDao.deleteFlashcard(card)

    suspend fun addClozeFlashcard(
        text: String,
        explanation: String = "",
        lectureId: Long? = null,
        courseId: Long? = null
    ): Boolean {
        val answer = SmartCardParser.formatAnswerSide(text)
        val question = SmartCardParser.formatQuestionSide(text)
        val entity = FlashcardEntity(
            lectureId = lectureId,
            courseId = courseId,
            frontText = text,
            backText = answer,
            explanation = explanation,
            cardType = "CLOZE",
            clozeAnswer = answer
        )
        return flashcardDao.insertFlashcard(entity) != -1L
    }

    suspend fun addAnatomyFlashcard(
        frontLabel: String,
        backLabel: String,
        imageUri: String,
        explanation: String = "",
        lectureId: Long? = null,
        courseId: Long? = null
    ): Boolean {
        val entity = FlashcardEntity(
            lectureId = lectureId,
            courseId = courseId,
            frontText = frontLabel,
            backText = backLabel,
            explanation = explanation,
            cardType = "ANATOMY",
            imageUrl = imageUri
        )
        return flashcardDao.insertFlashcard(entity) != -1L
    }

    // --- Spaced repetition (SM-2 & FSRS-v4) ---
    /** grade: 0 = Again, 1 = Hard, 2 = Good, 3 = Easy */
    suspend fun answerFlashcard(flashcard: FlashcardEntity, grade: Int) {
        val g = ReviewGrade.fromValue(grade)
        val now = System.currentTimeMillis()

        // SM-2 calculation
        val nextSm2 = Sm2Scheduler.next(
            SrsState(
                intervalDays = flashcard.intervalDays,
                repetitions = flashcard.repetitionCount,
                easeFactor = flashcard.easeFactor,
                lapses = flashcard.lapses,
                dueAtMillis = flashcard.dueDate
            ),
            g,
            now
        )

        // FSRS DSR calculation
        val elapsedDays = if (flashcard.lastReviewedAt > 0) {
            ((now - flashcard.lastReviewedAt).toFloat() / (24f * 60f * 60f * 1000f)).coerceAtLeast(0.1f)
        } else {
            0f
        }
        val fsrsNext = FsrsScheduler.next(
            currentStability = flashcard.fsrsStability,
            currentDifficulty = flashcard.fsrsDifficulty,
            currentRepetitions = flashcard.repetitionCount,
            grade = g,
            elapsedDays = elapsedDays,
            nowMillis = now
        )

        database.withTransaction {
            flashcardDao.updateFlashcard(
                flashcard.copy(
                    intervalDays = nextSm2.intervalDays,
                    repetitionCount = nextSm2.repetitions,
                    easeFactor = nextSm2.easeFactor,
                    lapses = nextSm2.lapses,
                    dueDate = nextSm2.dueAtMillis,
                    lastReviewedAt = now,
                    isLearned = Sm2Scheduler.isMature(nextSm2.intervalDays),
                    fsrsStability = fsrsNext.stability,
                    fsrsDifficulty = fsrsNext.difficulty,
                    fsrsState = fsrsNext.state
                )
            )
            flashcardDao.insertReviewLog(
                ReviewLogEntity(
                    flashcardId = flashcard.id,
                    grade = g.value,
                    reviewedAt = now,
                    intervalAfterDays = nextSm2.intervalDays
                )
            )
        }
    }

    // --- Quizzes, Exams & Topic Weakness Tracking ---
    suspend fun recordQuizAttempt(
        lectureId: Long?,
        courseId: Long?,
        title: String,
        topic: String,
        specialty: String,
        questions: List<ExamQuestion>,
        answers: Map<Int, Int>
    ): Long {
        var correct = 0
        val detailsArray = JSONArray()

        questions.forEachIndexed { i, q ->
            val sel = answers[i] ?: -1
            val isCorrect = sel == q.correctIndex
            if (isCorrect) correct++

            val obj = JSONObject().apply {
                put("question", q.questionAr)
                put("selected", sel)
                put("correct", q.correctIndex)
                put("isCorrect", isCorrect)
                put("specialty", q.specialty)
                put("topic", q.topic)
            }
            detailsArray.put(obj)
        }

        val total = questions.size.coerceAtLeast(1)
        val scorePercent = ((correct.toFloat() / total.toFloat()) * 100f).toInt()

        val attemptId = quizAttemptDao.insertAttempt(
            QuizAttemptEntity(
                lectureId = lectureId,
                courseId = courseId,
                title = title,
                topic = topic,
                specialty = specialty,
                totalQuestions = total,
                correctCount = correct,
                scorePercent = scorePercent,
                detailsJson = detailsArray.toString()
            )
        )

        // Update topic weakness
        database.withTransaction {
            val existing = topicWeaknessDao.findByTopicAndSpecialty(topic, specialty)
            val newTotal = (existing?.totalAttempts ?: 0) + total
            val newCorrect = (existing?.correctAttempts ?: 0) + correct
            val accuracy = if (newTotal > 0) ((newCorrect.toFloat() / newTotal.toFloat()) * 100f).toInt() else 0

            topicWeaknessDao.upsertWeakness(
                TopicWeaknessEntity(
                    id = existing?.id ?: 0L,
                    courseId = courseId,
                    topic = topic,
                    specialty = specialty,
                    totalAttempts = newTotal,
                    correctAttempts = newCorrect,
                    accuracyPercent = accuracy,
                    lastAssessedAt = System.currentTimeMillis()
                )
            )
        }

        return attemptId
    }

    suspend fun recordTopicWeakness(
        topic: String,
        specialty: String,
        isCorrect: Boolean,
        courseId: Long? = null
    ) {
        database.withTransaction {
            val existing = topicWeaknessDao.findByTopicAndSpecialty(topic, specialty)
            val newTotal = (existing?.totalAttempts ?: 0) + 1
            val newCorrect = (existing?.correctAttempts ?: 0) + (if (isCorrect) 1 else 0)
            val accuracy = if (newTotal > 0) ((newCorrect.toFloat() / newTotal.toFloat()) * 100f).toInt() else 0

            topicWeaknessDao.upsertWeakness(
                TopicWeaknessEntity(
                    id = existing?.id ?: 0L,
                    courseId = courseId ?: existing?.courseId,
                    topic = topic,
                    specialty = specialty,
                    totalAttempts = newTotal,
                    correctAttempts = newCorrect,
                    accuracyPercent = accuracy,
                    lastAssessedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun recordExamAttempt(report: ExamReport, courseIds: List<Long>): Long {
        val courseIdsJson = JSONArray(courseIds).toString()
        val breakdownJson = JSONObject().apply {
            report.specialtyScores.forEach { (spec, score) ->
                put(spec, score)
            }
        }.toString()

        val id = examAttemptDao.insertExamAttempt(
            ExamAttemptEntity(
                title = report.examTitle,
                durationSeconds = report.timeLimitSeconds,
                timeSpentSeconds = report.timeSpentSeconds,
                totalQuestions = report.totalQuestions,
                correctCount = report.correctCount,
                scorePercent = report.scorePercent,
                courseIdsJson = courseIdsJson,
                topicBreakdownJson = breakdownJson
            )
        )

        // Update topic weaknesses from exam results
        database.withTransaction {
            val topicGroups = report.detailedResults.groupBy { it.question.topic to it.question.specialty }
            topicGroups.forEach { (key, results) ->
                val (topic, specialty) = key
                val totalQ = results.size
                val correctQ = results.count { it.isCorrect }
                val existing = topicWeaknessDao.findByTopicAndSpecialty(topic, specialty)
                val newTotal = (existing?.totalAttempts ?: 0) + totalQ
                val newCorrect = (existing?.correctAttempts ?: 0) + correctQ
                val accuracy = if (newTotal > 0) ((newCorrect.toFloat() / newTotal.toFloat()) * 100f).toInt() else 0

                topicWeaknessDao.upsertWeakness(
                    TopicWeaknessEntity(
                        id = existing?.id ?: 0L,
                        courseId = results.firstOrNull()?.question?.courseId,
                        topic = topic,
                        specialty = specialty,
                        totalAttempts = newTotal,
                        correctAttempts = newCorrect,
                        accuracyPercent = accuracy,
                        lastAssessedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        return id
    }

    fun getWeaknessesForCourse(courseId: Long): Flow<List<TopicWeaknessEntity>> =
        topicWeaknessDao.getWeaknessesForCourse(courseId)

    fun getQuizAttemptsForLecture(lectureId: Long): Flow<List<QuizAttemptEntity>> =
        quizAttemptDao.getAttemptsForLecture(lectureId)

    fun getQuizAttemptsForCourse(courseId: Long): Flow<List<QuizAttemptEntity>> =
        quizAttemptDao.getAttemptsForCourse(courseId)

    fun getFlashcardsForCourse(courseId: Long): Flow<List<FlashcardEntity>> =
        flashcardDao.getAllFlashcards().map { list -> list.filter { it.courseId == courseId } }

    /** Whole deck as an Anki-importable text file body. */
    suspend fun exportFlashcardsForAnki(): Pair<String, Int> {
        val cards = flashcardDao.getAllFlashcards().first().map { card ->
            val course = card.courseId?.let { courseDao.getCourseById(it) }
            ExportCard(
                front = card.frontText,
                back = card.backText,
                explanation = card.explanation,
                tags = listOfNotNull("MedStudy", course?.code)
            )
        }
        return AnkiExporter.toAnkiText(cards) to AnkiExporter.exportableCount(cards)
    }

    // --- Files owned by the app ---
    private suspend fun deleteLectureFiles(lectureId: Long) {
        resourceDao.getResourcesOfType(lectureId, "PDF").forEach { deleteOwnedFile(it) }
    }

    private fun deleteOwnedFile(resource: LectureResourceEntity) {
        if (resource.type != "PDF" || resource.localPath.isBlank()) return
        val pdfDir = File(context.filesDir, "pdfs").canonicalFile
        val file = File(resource.localPath).canonicalFile
        // Only ever delete files we imported ourselves, never an arbitrary path stored in the database.
        if (file.parentFile == pdfDir && file.exists()) file.delete()
    }

    // --- Seed Initial Data ---
    suspend fun ensureInitialDataSeeded() = seedMutex.withLock {
        if (courseDao.countCourses() > 0) return@withLock

        val samplePdf = SamplePdfGenerator.generateSampleLecturePdf(context)
        val now = System.currentTimeMillis()
        val today = DateUtils.isoDate(now)
        val tomorrow = DateUtils.isoDate(now, 1)

        database.withTransaction {
            // Course 1: Medical Terminology
            val c1Id = courseDao.insertCourse(
                CourseEntity(
                    title = "Medical Terminology",
                    titleAr = "المصطلحات الطبية",
                    code = "MED101",
                    semester = "الفصل الأول",
                    colorHex = "#60F5D2",
                    iconName = "translate"
                )
            )
            val m1Id = courseDao.insertModule(
                ModuleEntity(
                    courseId = c1Id,
                    title = "Basic Word Building",
                    titleAr = "أساسيات بناء المصطلح الطبي",
                    orderIndex = 1,
                    description = "دراسة البوادئ، الجذور، اللواحق وحروف الوصل"
                )
            )
            val l1Id = courseDao.insertLecture(
                LectureEntity(
                    moduleId = m1Id,
                    courseId = c1Id,
                    title = "Medical Word Components",
                    titleAr = "أجزاء الكلمة الطبية: الجذور واللواحق",
                    orderIndex = 1,
                    status = "IN_PROGRESS",
                    priority = "HIGH",
                    isDifficult = false,
                    lastStudiedAt = now
                )
            )
            resourceDao.insertResource(
                LectureResourceEntity(
                    lectureId = l1Id,
                    type = "PDF",
                    title = "Lecture 1: Medical Word Elements.pdf",
                    localPath = samplePdf.absolutePath,
                    pageCount = 3,
                    lastReadPage = 1,
                    notes = "ملف تجريبي مضمّن في التطبيق. أضف محاضراتك من زر الإضافة داخل المقرر."
                )
            )
            resourceDao.insertNote(
                LectureNoteEntity(
                    lectureId = l1Id,
                    title = "قاعدة قراءة المصطلح الطبي",
                    content = "نبدأ دائماً بقراءة اللاحقة (Suffix) في النهاية ثم البادئة ثم الجذر في المنتصف. مثال: Myocarditis = التهاب عضلة القلب.",
                    pageNumber = 1
                )
            )

            // Course 2: Cell Physiology
            val c2Id = courseDao.insertCourse(
                CourseEntity(
                    title = "Cell Biology & Physiology",
                    titleAr = "بيولوجيا الخلية والفيزيولوجيا",
                    code = "PHY102",
                    semester = "الفصل الأول",
                    colorHex = "#38BDF8",
                    iconName = "biotech"
                )
            )
            val m2Id = courseDao.insertModule(
                ModuleEntity(
                    courseId = c2Id,
                    title = "Membrane Potentials",
                    titleAr = "الجهود الغشائية والأنسجة المستثارة",
                    orderIndex = 1
                )
            )
            val l2Id = courseDao.insertLecture(
                LectureEntity(
                    moduleId = m2Id,
                    courseId = c2Id,
                    title = "Resting & Action Potential",
                    titleAr = "جهد الراحة وجهد الفعل العصبي",
                    orderIndex = 1,
                    status = "COMPLETED",
                    priority = "HIGH",
                    isDifficult = true,
                    lastStudiedAt = now - 86_400_000L
                )
            )

            // Course 3: Neuroanatomy
            val c3Id = courseDao.insertCourse(
                CourseEntity(
                    title = "Basic Neuroanatomy",
                    titleAr = "التشريح العصبي الأساسي",
                    code = "ANA103",
                    semester = "الفصل الأول",
                    colorHex = "#D6B2FC",
                    iconName = "psychology"
                )
            )
            val m3Id = courseDao.insertModule(
                ModuleEntity(
                    courseId = c3Id,
                    title = "Autonomic Nervous System",
                    titleAr = "الجهاز العصبي المستقل",
                    orderIndex = 1
                )
            )
            val l3Id = courseDao.insertLecture(
                LectureEntity(
                    moduleId = m3Id,
                    courseId = c3Id,
                    title = "Sympathetic & Parasympathetic Divisions",
                    titleAr = "الجهاز الودي ونظير الودي",
                    orderIndex = 1,
                    status = "NOT_STARTED",
                    priority = "MEDIUM",
                    isDifficult = false
                )
            )

            // Sample study material (clearly curated offline content, flagged as such)
            saveAIResult(
                aiService.generateLocalExplanation("Action Potential", ExplanationMode.FIRST_YEAR_MED, "ar", null, null),
                l2Id, c2Id
            )
            saveAIResult(
                aiService.generateLocalExplanation("Autonomic Nervous System", ExplanationMode.COMPARE, "ar", null, null),
                l3Id, c3Id
            )

            // Sample tasks with real dates
            studyPlannerDao.insertTask(
                StudyTaskEntity(
                    courseId = c1Id,
                    lectureId = l1Id,
                    title = "مراجعة المصطلحات الطبية لصفحة 1-3",
                    description = "حفظ الجذور اللاتينية الأكثر تكراراً في امتحانات الفصل الأول",
                    targetDate = today,
                    estimatedMinutes = 25
                )
            )
            studyPlannerDao.insertTask(
                StudyTaskEntity(
                    courseId = c2Id,
                    lectureId = l2Id,
                    title = "رسم مخطط جهد الفعل وفترات الحران",
                    description = "التركيز على الفروق بين فترتي الحران المطلق والنسبي",
                    targetDate = today,
                    estimatedMinutes = 35,
                    isCompleted = true,
                    completedAt = now - 3_600_000L
                )
            )
            studyPlannerDao.insertTask(
                StudyTaskEntity(
                    courseId = c3Id,
                    lectureId = l3Id,
                    title = "حفظ الأعصاب القحفية ذات الألياف نظيرة الودية",
                    description = "الأعصاب CN III, VII, IX, X وتأثيراتها الحشوية",
                    targetDate = tomorrow,
                    estimatedMinutes = 30
                )
            )
        }
    }

    companion object {
        /** Escapes % _ and \ so a student's search text is matched literally by the LIKE ... ESCAPE '\' queries. */
        fun escapeLike(query: String): String =
            query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
