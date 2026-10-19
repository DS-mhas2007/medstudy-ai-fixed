package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.R
import com.example.core.SmartCardParser
import com.example.data.files.PdfTextSource
import com.example.data.local.FlashcardEntity
import com.example.data.local.MedStudyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfIndexingWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val resourceId = inputData.getLong(KEY_RESOURCE_ID, -1L)
        val lectureId = inputData.getLong(KEY_LECTURE_ID, -1L)
        val courseId = inputData.getLong(KEY_COURSE_ID, 0L)
        val filePath = inputData.getString(KEY_FILE_PATH).orEmpty()
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "محاضرة PDF" }

        if (resourceId == -1L || filePath.isBlank()) {
            return@withContext Result.failure(workDataOf("error" to "معرّف المحاضرة أو المسار غير صالح"))
        }

        val file = File(filePath)
        if (!file.exists()) {
            PdfIndexingProgressBus.markFailed(resourceId, "لم يتم العثور على ملف المحاضرة")
            return@withContext Result.failure(workDataOf("error" to "الملف غير موجود"))
        }

        ensureNotificationChannel()

        val database = MedStudyDatabase.getDatabase(appContext)
        val textSource = PdfTextSource(appContext, file)

        var totalGeneratedCards = 0

        try {
            // Count pages via PdfRenderer or estimate
            val pageCount = runCatching {
                android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
                }
            }.getOrDefault(1).coerceAtLeast(1)

            val maxPagesToIndex = pageCount.coerceAtMost(60)

            PdfIndexingProgressBus.updateProgress(
                resourceId = resourceId,
                lectureId = lectureId,
                title = title,
                progressPercent = 5,
                currentPage = 0,
                totalPages = pageCount,
                statusText = "بدء قراءة وفهرسة صفحات المحاضرة..."
            )
            updateNotification(resourceId.toInt(), title, 5, "بدء القراءة والتحليل...")

            val newFlashcards = mutableListOf<FlashcardEntity>()

            for (pageIndex in 0 until maxPagesToIndex) {
                if (isStopped) {
                    textSource.close()
                    PdfIndexingProgressBus.markFailed(resourceId, "تم إلغاء عملية الفهرسة")
                    return@withContext Result.failure()
                }

                val currentProgress = ((pageIndex + 1) * 90) / maxPagesToIndex
                val statusText = "فهرسة الصفحة ${pageIndex + 1} من $pageCount..."

                PdfIndexingProgressBus.updateProgress(
                    resourceId = resourceId,
                    lectureId = lectureId,
                    title = title,
                    progressPercent = currentProgress,
                    currentPage = pageIndex + 1,
                    totalPages = pageCount,
                    statusText = statusText,
                    flashcardsGenerated = totalGeneratedCards
                )

                setProgress(
                    workDataOf(
                        KEY_PROGRESS to currentProgress,
                        KEY_CURRENT_PAGE to pageIndex + 1,
                        KEY_TOTAL_PAGES to pageCount,
                        KEY_STATUS to statusText
                    )
                )

                updateNotification(resourceId.toInt(), title, currentProgress, statusText)

                // Extract text from page
                val pageText = textSource.pageText(pageIndex)
                if (pageText.length > 40) {
                    val extracted = generateCardsFromText(pageText, lectureId, courseId, pageIndex + 1)
                    if (extracted.isNotEmpty()) {
                        newFlashcards.addAll(extracted)
                        totalGeneratedCards += extracted.size
                    }
                }
            }

            textSource.close()

            // Save cards to database in batch
            if (newFlashcards.isNotEmpty()) {
                database.flashcardDao().insertAllFlashcards(newFlashcards)
            }

            PdfIndexingProgressBus.markCompleted(resourceId, totalGeneratedCards)
            showCompletionNotification(resourceId.toInt(), title, totalGeneratedCards)

            Result.success(
                workDataOf(
                    KEY_PROGRESS to 100,
                    KEY_CARDS_COUNT to totalGeneratedCards,
                    KEY_STATUS to "اكتملت الفهرسة بنجاح"
                )
            )
        } catch (e: Exception) {
            textSource.close()
            val errorMsg = e.message ?: "خطأ أثناء فهرسة المحاضرة"
            PdfIndexingProgressBus.markFailed(resourceId, errorMsg)
            Result.failure(workDataOf("error" to errorMsg))
        }
    }

    private fun generateCardsFromText(
        text: String,
        lectureId: Long,
        courseId: Long,
        page: Int
    ): List<FlashcardEntity> {
        val cards = mutableListOf<FlashcardEntity>()
        val sentences = text.split(Regex("""(?<=[.!?\n])\s+"""))
            .map { it.trim() }
            .filter { it.length in 35..250 }

        for (sentence in sentences.take(4)) {
            val lower = sentence.lowercase()
            val isDefinitional = lower.contains("is defined as") ||
                    lower.contains("refers to") ||
                    lower.contains("consists of") ||
                    lower.contains("characterized by") ||
                    lower.contains("is caused by") ||
                    lower.contains("responsible for") ||
                    lower.contains("treatment of")

            if (isDefinitional) {
                val keywords = SmartCardParser.suggestClozeKeywords(sentence)
                if (keywords.isNotEmpty()) {
                    val targetWord = keywords.first()
                    val clozeText = SmartCardParser.wrapWordAsCloze(sentence, targetWord)

                    cards.add(
                        FlashcardEntity(
                            lectureId = lectureId,
                            courseId = if (courseId > 0) courseId else null,
                            frontText = clozeText,
                            backText = targetWord,
                            frontLanguage = "en",
                            backLanguage = "en",
                            explanation = "مستخرجة تلقائياً من صفحة $page في المحاضرة.",
                            cardType = "CLOZE",
                            clozeAnswer = targetWord,
                            dueDate = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return cards
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "معالجة وفهرسة المحاضرات"
            val descriptionText = "إشعارات تقدم استخراج النصوص وفهرسة بطاقات الاستذكار"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(notificationId: Int, title: String, progress: Int, message: String) {
        try {
            val manager = NotificationManagerCompat.from(appContext)
            if (!manager.areNotificationsEnabled()) return

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("جارٍ فهرسة: $title")
                .setContentText(message)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted, safe fallback
        }
    }

    private fun showCompletionNotification(notificationId: Int, title: String, cardsCount: Int) {
        try {
            val manager = NotificationManagerCompat.from(appContext)
            if (!manager.areNotificationsEnabled()) return

            val contentText = if (cardsCount > 0) {
                "اكتملت الفهرسة واستخراج $cardsCount بطاقة ذكية بنجاح."
            } else {
                "اكتملت فهرسة نص المحاضرة وأصبحت جاهزة للبحث السريع."
            }

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("اكتملت معالجة $title")
                .setContentText(contentText)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Notifications permission not granted, safe fallback
        }
    }

    companion object {
        const val CHANNEL_ID = "pdf_indexing_channel"
        const val KEY_RESOURCE_ID = "resource_id"
        const val KEY_LECTURE_ID = "lecture_id"
        const val KEY_COURSE_ID = "course_id"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_CURRENT_PAGE = "current_page"
        const val KEY_TOTAL_PAGES = "total_pages"
        const val KEY_STATUS = "status"
        const val KEY_CARDS_COUNT = "cards_count"
    }
}
