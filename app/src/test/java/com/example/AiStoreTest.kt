package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MedStudyDatabase
import com.example.data.model.MedicalTerm
import com.example.data.remote.AiStore
import com.example.data.remote.MedicalAiResult
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
class AiStoreTest {
    private lateinit var db: MedStudyDatabase
    private var now = 1_000_000_000L

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MedStudyDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun store(max: Int = 300, ttl: Long = 1_000L) =
        AiStore(db.aiCacheDao(), db.aiUsageDao(), clock = { now }, ttlMillis = ttl, maxEntries = max)

    private fun result(title: String, offline: Boolean = false) = MedicalAiResult(
        title = title, topic = title, mode = "x", originalText = "o", translation = "t",
        simpleExplanation = "s", detailedExplanation = "d",
        keyTerms = listOf(MedicalTerm("a", "b", "c")), mechanismSteps = emptyList(), keyPoints = emptyList(),
        misconceptions = emptyList(), comparisonTable = null, diagramType = "mermaid", diagramCode = "",
        questions = emptyList(), flashcards = emptyList(), isOfflineGenerated = offline
    )

    @Test
    fun storedAnswerComesBackAndExpires() = runBlocking {
        val s = store()
        s.cache.put("k", result("A"))
        assertEquals("A", s.cache.get("k")?.title)
        now += 2_000L
        assertNull(s.cache.get("k"))
    }

    @Test
    fun offlineContentIsNeverCached() = runBlocking {
        val s = store()
        s.cache.put("k", result("local", offline = true))
        assertNull(s.cache.get("k"))
        assertEquals(0, s.cacheSize())
    }

    @Test
    fun leastRecentlyUsedEntriesAreEvicted() = runBlocking {
        val s = store(max = 2, ttl = 1_000_000L)
        s.cache.put("a", result("A")); now += 10
        s.cache.put("b", result("B")); now += 10
        s.cache.get("a"); now += 10            // "a" is now more recent than "b"
        s.cache.put("c", result("C"))
        assertNotNull(s.cache.get("a"))
        assertNull(s.cache.get("b"))
        assertNotNull(s.cache.get("c"))
        assertEquals(2, s.cacheSize())
    }

    @Test
    fun usageIsCountedAndOldRowsAreDropped() = runBlocking {
        val s = store()
        s.usage.record(now)
        s.usage.record(now + 1)
        assertEquals(2, s.usage.requestTimes(now - 1).size)
        assertEquals(2, s.requestsToday())
        now += 3L * 24 * 60 * 60 * 1000
        s.usage.record(now)
        assertEquals(1, s.usage.requestTimes(0).size)
    }

    @Test
    fun clearEmptiesTheCache() = runBlocking {
        val s = store()
        s.cache.put("k", result("A"))
        s.clearCache()
        assertEquals(0, s.cacheSize())
    }
}
