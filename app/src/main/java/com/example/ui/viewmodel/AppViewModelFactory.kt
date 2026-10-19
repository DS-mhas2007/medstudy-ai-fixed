package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.backup.DriveBackupManager
import com.example.data.files.ResourceImporter
import com.example.data.local.MedStudyDatabase
import com.example.data.repository.MedStudyRepository

class AppViewModelFactory(
    private val repository: MedStudyRepository,
    private val database: MedStudyDatabase,
    private val backupManager: DriveBackupManager,
    private val importer: ResourceImporter,
    private val appContext: Context
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(CoursesViewModel::class.java) -> {
                CoursesViewModel(repository, appContext) as T
            }
            modelClass.isAssignableFrom(SubjectHubViewModel::class.java) -> {
                SubjectHubViewModel(repository, importer, appContext) as T
            }
            modelClass.isAssignableFrom(ExamViewModel::class.java) -> {
                ExamViewModel(repository) as T
            }
            modelClass.isAssignableFrom(LectureQuizViewModel::class.java) -> {
                LectureQuizViewModel(repository) as T
            }
            modelClass.isAssignableFrom(StudyPlannerViewModel::class.java) -> {
                StudyPlannerViewModel(repository) as T
            }
            modelClass.isAssignableFrom(BackupSyncViewModel::class.java) -> {
                BackupSyncViewModel(database, backupManager) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
