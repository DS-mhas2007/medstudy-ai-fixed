package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.*
import com.example.data.model.ExplanationMode
import com.example.data.model.TranslationDirection
import com.example.data.model.MedicalSpecialty
import com.example.data.remote.MedicalAiService
import com.example.data.repository.MedStudyRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedStudyRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: MedStudyDatabase
    private lateinit var aiService: MedicalAiService
    private lateinit var repository: MedStudyRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MedStudyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        aiService = MedicalAiService()
        repository = MedStudyRepository(context, db, aiService)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testAppNameMatchesMedStudyAI() {
        val appName = context.getString(R.string.app_name)
        assertEquals("MedStudy AI", appName)
    }

    @Test
    fun testCourseAndModulePersistence() = runBlocking {
        val courseId = repository.insertCourse(
            CourseEntity(
                title = "Medical Terminology",
                titleAr = "المصطلحات الطبية",
                code = "MED101",
                semester = "الفصل الأول",
                colorHex = "#60F5D2"
            )
        )
        assertTrue(courseId > 0)

        val courses = repository.allCourses.first()
        assertEquals(1, courses.size)
        assertEquals("MED101", courses[0].code)

        val moduleId = repository.insertModule(
            ModuleEntity(
                courseId = courseId,
                title = "Word Elements",
                titleAr = "عناصر الكلمة الطبية",
                orderIndex = 1
            )
        )
        assertTrue(moduleId > 0)

        val modules = repository.getModulesForCourse(courseId).first()
        assertEquals(1, modules.size)
        assertEquals("Word Elements", modules[0].title)
    }

    @Test
    fun testAIResultSaveAndSearch() = runBlocking {
        val localExplanation = aiService.generateLocalExplanation(
            topic = "Action Potential",
            mode = ExplanationMode.FIRST_YEAR_MED,
            lang = "ar",
            sourceDoc = "Lecture1.pdf",
            sourcePage = 2
        )
        assertNotNull(localExplanation)
        assertTrue(localExplanation.keyTerms.isNotEmpty())
        assertTrue(localExplanation.mechanismSteps.isNotEmpty())

        val savedId = repository.saveAIResult(localExplanation, sourceDoc = "Lecture1.pdf", sourcePage = 2)
        assertTrue(savedId > 0)

        val savedList = repository.allSavedResults.first()
        assertEquals(1, savedList.size)
        assertEquals(localExplanation.title, savedList[0].title)

        // Search test
        val searchResults = repository.searchSavedResults("Action").first()
        assertEquals(1, searchResults.size)
    }

    @Test
    fun testFlashcardSpacedRepetition() = runBlocking {
        val fc = FlashcardEntity(
            frontText = "Threshold Potential",
            backText = "-55 mV",
            explanation = "Voltage needed to trigger voltage-gated Na+ channels"
        )
        val id = db.flashcardDao().insertFlashcard(fc)
        val saved = db.flashcardDao().getAllFlashcards().first().first()

        // Rate Easy (3)
        repository.answerFlashcard(saved, 3)
        val updated = db.flashcardDao().getAllFlashcards().first().first()
        assertTrue(updated.intervalDays >= 1)
        assertEquals(1, updated.repetitionCount)
        assertTrue(updated.easeFactor > 2.5f)
    }

    @Test
    fun testMedicalTranslationEngine() {
        val trans = aiService.generateLocalTranslation(
            text = "Acetylcholine release at neuromuscular junction",
            direction = TranslationDirection.EN_TO_AR,
            specialty = MedicalSpecialty.PHYSIOLOGY
        )
        assertNotNull(trans)
        assertTrue(trans.keyTerms.any { it.term.contains("Acetylcholine", ignoreCase = true) })
        assertTrue(trans.mechanismSteps.isNotEmpty())
    }
}
