package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.data.files.ResourceImporter
import com.example.data.local.MedStudyDatabase
import com.example.data.preference.UserPreferencesManager
import com.example.data.remote.AiStore
import com.example.data.remote.MedicalAiService
import com.example.data.repository.MedStudyRepository
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.MedBackgroundDark
import com.example.ui.theme.MedStudyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = MedStudyDatabase.getDatabase(applicationContext)
        val aiService = MedicalAiService(store = AiStore(database))
        val repository = MedStudyRepository(applicationContext, database, aiService)
        val preferencesManager = UserPreferencesManager(applicationContext)
        val importer = ResourceImporter(applicationContext)

        // Seed the sample curriculum on first start (idempotent).
        lifecycleScope.launch(Dispatchers.IO) {
            repository.ensureInitialDataSeeded()
        }

        setContent {
            MedStudyTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MedBackgroundDark) {
                    MainAppNavigation(
                        repository = repository,
                        aiService = aiService,
                        preferencesManager = preferencesManager,
                        importer = importer
                    )
                }
            }
        }
    }
}
