package com.example.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow

object PdfIndexingScheduler {

    fun scheduleIndexing(
        context: Context,
        resourceId: Long,
        lectureId: Long,
        courseId: Long,
        filePath: String,
        title: String
    ) {
        val inputData = workDataOf(
            PdfIndexingWorker.KEY_RESOURCE_ID to resourceId,
            PdfIndexingWorker.KEY_LECTURE_ID to lectureId,
            PdfIndexingWorker.KEY_COURSE_ID to courseId,
            PdfIndexingWorker.KEY_FILE_PATH to filePath,
            PdfIndexingWorker.KEY_TITLE to title
        )

        val workRequest = OneTimeWorkRequestBuilder<PdfIndexingWorker>()
            .setInputData(inputData)
            .addTag("pdf_indexing")
            .addTag("resource_$resourceId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "pdf_index_$resourceId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun observeWorkByResourceId(context: Context, resourceId: Long): Flow<List<WorkInfo>> {
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("pdf_index_$resourceId")
    }

    fun cancelIndexing(context: Context, resourceId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("pdf_index_$resourceId")
        PdfIndexingProgressBus.clearTask(resourceId)
    }
}
