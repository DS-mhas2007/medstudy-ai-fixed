package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MedStudyDatabase
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

/** Study-flow behaviour that sits on top of Room: due queue, review logs, de-duplication, sessions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MedStudyStudyFlowTest {
    private lateinit var db: MedStudyDatabase
    private lateinit var repository: MedStudyRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MedStudyDatabase::class.java).allowMainThreadQueries().build()
        repository = MedStudyRepository(context, db, MedicalAiService())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun newCardIsDueAndLeavesTheQueueAfterGood() = runBlocking {
        assertTrue(repository.addFlashcard("Threshold potential", "-55 mV"))
        val due = repository.dueFlashcards.first()
        assertEquals(1, due.size)

        repository.answerFlashcard(due.first(), 2)

        assertTrue("A card answered Good must not stay due today", repository.dueFlashcards.first().isEmpty())
        assertEquals(1, db.flashcardDao().getAllFlashcards().first().first().repetitionCount)
    }

    @Test
    fun againKeepsTheCardInTheQueueAndCountsALapse() = runBlocking {
        repository.addFlashcard("Resting potential", "-70 mV")
        val card = repository.dueFlashcards.first().first()
        repository.answerFlashcard(card, 2)
        val reviewed = db.flashcardDao().getAllFlashcards().first().first()
        repository.answerFlashcard(reviewed, 0)

        val after = db.flashcardDao().getAllFlashcards().first().first()
        assertEquals(1, after.lapses)
        assertFalse(after.isLearned)
    }

    @Test
    fun duplicateCardsAreIgnored() = runBlocking {
        assertTrue(repository.addFlashcard("Synapse", "نقطة اتصال بين عصبونين"))
        assertFalse(repository.addFlashcard("Synapse", "نقطة اتصال بين عصبونين"))
        assertEquals(1, db.flashcardDao().getAllFlashcards().first().size)
    }

    @Test
    fun ankiExportContainsOnlyRealCards() = runBlocking {
        repository.addFlashcard("Axon", "ليف عصبي ناقل")
        val (text, count) = repository.exportFlashcardsForAnki()
        assertEquals(1, count)
        assertTrue(text.contains("Axon"))
    }

    @Test
    fun finishedFocusSessionCountsTowardWeeklyMinutes() = runBlocking {
        repository.recordStudySession(25)
        repository.recordStudySession(0) // ignored
        val stats = repository.studyStats.first()
        assertEquals(25, stats.minutesThisWeek)
    }
}
