package com.example.data.backup

import com.example.data.local.CourseEntity
import com.example.data.local.FlashcardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveBackupTest {

    @Test
    fun testBackupPayloadSerializationAndChecksum() {
        val course = CourseEntity(
            id = 1L,
            title = "Internal Medicine",
            titleAr = "الباطنة العامة",
            code = "IMED301",
            colorHex = "#60F5D2"
        )
        val card = FlashcardEntity(
            id = 101L,
            courseId = 1L,
            frontText = "What is the primary cause of Addison's disease?",
            backText = "Autoimmune destruction of the adrenal cortex",
            explanation = "90% of cases in developed nations"
        )

        val backup = MedStudyBackup(
            version = 1,
            appName = "MedStudy AI",
            createdAtMillis = 1700000000000L,
            courses = listOf(course),
            flashcards = listOf(card)
        )

        val json = DriveBackupManager.serializeBackup(backup)
        assertTrue(json.contains("Internal Medicine"))
        assertTrue(json.contains("Addison's disease"))

        val checksum = DriveBackupManager.calculateChecksum(json)
        assertNotNull(checksum)
        assertEquals(64, checksum.length) // SHA-256 is 64 hex chars

        // Deserialize
        val deserialized = DriveBackupManager.deserializeBackup(json)
        assertEquals(1, deserialized.version)
        assertEquals("MedStudy AI", deserialized.appName)
        assertEquals(1, deserialized.courses.size)
        assertEquals("Internal Medicine", deserialized.courses[0].title)
        assertEquals(1, deserialized.flashcards.size)
        assertEquals("Autoimmune destruction of the adrenal cortex", deserialized.flashcards[0].backText)

        // Metadata extraction
        val metadata = DriveBackupManager.extractMetadata(json)
        assertNotNull(metadata)
        assertEquals(1, metadata.coursesCount)
        assertEquals(1, metadata.flashcardsCount)
        assertEquals(checksum, metadata.checksum)
    }
}
