package com.example.worker

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PdfIndexingProgressBusTest {

    @Before
    fun setup() {
        PdfIndexingProgressBus.clearAll()
    }

    @Test
    fun testProgressBusFlowUpdates() = runBlocking {
        val resourceId = 42L
        val title = "Cardiovascular Pathology.pdf"

        PdfIndexingProgressBus.updateProgress(resourceId, title, 25, "فهرسة الصفحة 5 من 20...")
        var tasks = PdfIndexingProgressBus.tasks.value
        assertEquals(1, tasks.size)
        val task = tasks[resourceId]
        assertEquals(25, task?.progressPercent)
        assertEquals("فهرسة الصفحة 5 من 20...", task?.statusText)
        assertFalse(task?.isCompleted ?: true)

        PdfIndexingProgressBus.markCompleted(resourceId, 18)
        tasks = PdfIndexingProgressBus.tasks.value
        val completedTask = tasks[resourceId]
        assertTrue(completedTask?.isCompleted ?: false)
        assertEquals(100, completedTask?.progressPercent)
        assertEquals(18, completedTask?.generatedCardsCount)

        PdfIndexingProgressBus.clearTask(resourceId)
        tasks = PdfIndexingProgressBus.tasks.value
        assertNull(tasks[resourceId])
        assertTrue(tasks.isEmpty())
    }
}
