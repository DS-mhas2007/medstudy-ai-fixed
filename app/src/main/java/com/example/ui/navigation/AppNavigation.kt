package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.example.data.files.ResourceImporter
import com.example.data.preference.UserPreferencesManager
import com.example.data.remote.MedicalAiService
import com.example.data.repository.MedStudyRepository
import com.example.ui.courses.CoursesScreen
import com.example.ui.explainer.ExplainerScreen
import com.example.ui.home.HomeScreen
import com.example.ui.library.SavedLibraryScreen
import com.example.ui.pdf.PdfReaderScreen
import com.example.ui.planner.StudyPlannerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.*
import com.example.ui.exam.ExamModeScreen
import com.example.ui.quiz.LectureQuizScreen
import com.example.ui.translation.TranslationScreen
import com.example.ui.video.VideoPlayerScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.backup.DriveBackupManager
import com.example.data.local.MedStudyDatabase
import com.example.ui.viewmodel.AppViewModelFactory
import com.example.ui.viewmodel.BackupSyncViewModel
import com.example.ui.viewmodel.CoursesViewModel
import com.example.ui.viewmodel.ExamViewModel
import com.example.ui.viewmodel.LectureQuizViewModel
import com.example.ui.viewmodel.StudyPlannerViewModel

sealed class Screen(val route: String, val titleAr: String, val titleEn: String, val icon: ImageVector) {
    object Home : Screen("home", "الرئيسية", "Home", Icons.Default.Home)
    object Courses : Screen("courses", "مقرراتي", "My Courses", Icons.Default.School)
    object Explainer : Screen("explainer", "المساعد الذكي", "AI Assistant", Icons.Default.Psychology)
    object Translator : Screen("translator", "المترجم", "Translator", Icons.Default.Translate)
    object Library : Screen("library", "المكتبة", "Library", Icons.Default.Bookmarks)
    object Planner : Screen("planner", "المخطط", "Planner", Icons.Default.EventNote)
    object Settings : Screen("settings", "الإعدادات", "Settings", Icons.Default.Settings)
    object PdfReader : Screen("pdf_reader", "قارئ المحاضرات", "PDF Reader", Icons.Default.MenuBook)
    object VideoPlayer : Screen("video_player", "مشغل المرئيات", "Video Player", Icons.Default.PlayCircle)
    object ExamMode : Screen("exam_mode", "وضع الامتحان", "Exam Mode", Icons.Default.Timer)
    object LectureQuiz : Screen("lecture_quiz", "اختبار المحاضرة", "Lecture Quiz", Icons.Default.Quiz)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    repository: MedStudyRepository,
    aiService: MedicalAiService,
    preferencesManager: UserPreferencesManager,
    importer: ResourceImporter
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val settings by preferencesManager.userSettingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val isArabic = (settings?.appLanguage ?: "ar") == "ar"
    val layoutDirection = if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr

    val appViewModelFactory = remember(repository, importer) {
        val database = MedStudyDatabase.getDatabase(context)
        val backupManager = DriveBackupManager(context)
        AppViewModelFactory(
            repository = repository,
            database = database,
            backupManager = backupManager,
            importer = importer,
            appContext = context.applicationContext
        )
    }

    // State holders for passing contextual data between screens
    // Hand-off between screens; rememberSaveable so a rotation or process restart does not lose it.
    var activeTranslatorInput by rememberSaveable { mutableStateOf("") }
    var activeExplainerInput by rememberSaveable { mutableStateOf("") }
    var activeSourceDoc by rememberSaveable { mutableStateOf<String?>(null) }
    var activeSourcePage by rememberSaveable { mutableStateOf<Int?>(null) }
    var activeSourceTimestamp by rememberSaveable { mutableStateOf<Long?>(null) }

    fun openPdf(resourceId: Long, page: Int = 0) =
        navController.navigate("pdf_reader/$resourceId?page=$page")
    fun openVideo(resourceId: Long, positionSeconds: Long = -1L) =
        navController.navigate("video_player/$resourceId?position=$positionSeconds")
    val openSettings = { navController.navigate(Screen.Settings.route) }
    val scope = rememberCoroutineScope()
    // Saved results remember the source by title (and page / timestamp); resolve it back to a resource.
    fun openSource(doc: String, page: Int?, timestamp: Long?) {
        scope.launch {
            if (timestamp != null) {
                val video = repository.findResourceByTitle(doc, "VIDEO")
                if (video != null) { openVideo(video.id, timestamp); return@launch }
            }
            val pdf = repository.findResourceByTitle(doc, "PDF")
            if (pdf != null) openPdf(pdf.id, ((page ?: 1) - 1).coerceAtLeast(0))
            else Toast.makeText(context, "لم يعد المصدر «$doc» موجوداً في مقرراتك.", Toast.LENGTH_SHORT).show()
        }
    }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Courses,
        Screen.Explainer,
        Screen.Translator,
        Screen.Library,
        Screen.Planner
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MedSurfaceDark,
                        contentColor = MedTextPrimaryDark,
                        tonalElevation = 8.dp,
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        bottomNavItems.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != screen.route) {
                                        navController.navigate(screen.route) {
                                            popUpTo(Screen.Home.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.titleAr,
                                        tint = if (selected) MedMint else MedTextSecondaryDark
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isArabic) screen.titleAr else screen.titleEn,
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MedMint else MedTextSecondaryDark,
                                        maxLines = 1
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MedMint,
                                    selectedTextColor = MedMint,
                                    indicatorColor = MedMintContainer
                                )
                            )
                        }
                    }
                }
            },
            containerColor = MedBackgroundDark
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        repository = repository,
                        onNavigateToCourses = { navController.navigate(Screen.Courses.route) },
                        onNavigateToExplainer = { navController.navigate(Screen.Explainer.route) },
                        onNavigateToTranslator = { navController.navigate(Screen.Translator.route) },
                        onNavigateToLibrary = { navController.navigate(Screen.Library.route) },
                        onNavigateToPlanner = { navController.navigate(Screen.Planner.route) },
                        onNavigateToExam = { navController.navigate(Screen.ExamMode.route) },
                        onOpenResource = { resource ->
                            if (resource.type == "VIDEO") openVideo(resource.id) else openPdf(resource.id, (resource.lastReadPage - 1).coerceAtLeast(0))
                        },
                        onOpenSettings = { openSettings() }
                    )
                }

                composable(Screen.Courses.route) {
                    val coursesVm: CoursesViewModel = viewModel(factory = appViewModelFactory)
                    CoursesScreen(
                        repository = repository,
                        importer = importer,
                        viewModel = coursesVm,
                        onOpenPdfReader = { resourceId -> openPdf(resourceId) },
                        onOpenVideoPlayer = { resourceId -> openVideo(resourceId) },
                        onLaunchQuiz = { lectureId, title ->
                            navController.navigate("lecture_quiz/$lectureId?title=$title&courseId=0")
                        }
                    )
                }

                composable(Screen.Explainer.route) {
                    ExplainerScreen(
                        initialTopic = activeExplainerInput,
                        sourceDoc = activeSourceDoc,
                        sourcePage = activeSourcePage,
                        sourceTimestamp = activeSourceTimestamp,
                        repository = repository,
                        aiService = aiService,
                        preferencesManager = preferencesManager,
                        onOpenSettings = { openSettings() }
                    )
                }

                composable(Screen.Translator.route) {
                    TranslationScreen(
                        initialText = activeTranslatorInput,
                        sourceDoc = activeSourceDoc,
                        sourcePage = activeSourcePage,
                        sourceTimestamp = activeSourceTimestamp,
                        repository = repository,
                        aiService = aiService,
                        preferencesManager = preferencesManager,
                        onOpenSettings = { openSettings() }
                    )
                }

                composable(Screen.Library.route) {
                    SavedLibraryScreen(
                        repository = repository,
                        onOpenSource = { doc, page, timestamp -> openSource(doc, page, timestamp) }
                    )
                }

                composable(Screen.Planner.route) {
                    StudyPlannerScreen(
                        repository = repository,
                        onNavigateToCourse = { navController.navigate(Screen.Courses.route) }
                    )
                }

                composable(Screen.Settings.route) {
                    val backupVm: BackupSyncViewModel = viewModel(factory = appViewModelFactory)
                    SettingsScreen(
                        preferencesManager = preferencesManager,
                        aiService = aiService,
                        backupViewModel = backupVm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "pdf_reader/{resourceId}?page={page}",
                    arguments = listOf(
                        navArgument("resourceId") { type = NavType.LongType },
                        navArgument("page") { type = NavType.IntType; defaultValue = 0 }
                    )
                ) { entry ->
                    PdfReaderScreen(
                        resourceId = entry.arguments?.getLong("resourceId") ?: 0L,
                        initialPage = entry.arguments?.getInt("page") ?: 0,
                        repository = repository,
                        onSendToTranslator = { text, doc, page ->
                            activeTranslatorInput = text
                            activeSourceDoc = doc
                            activeSourcePage = page
                            activeSourceTimestamp = null
                            navController.navigate(Screen.Translator.route)
                        },
                        onSendToExplainer = { text, doc, page ->
                            activeExplainerInput = text
                            activeSourceDoc = doc
                            activeSourcePage = page
                            activeSourceTimestamp = null
                            navController.navigate(Screen.Explainer.route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "video_player/{resourceId}?position={position}",
                    arguments = listOf(
                        navArgument("resourceId") { type = NavType.LongType },
                        navArgument("position") { type = NavType.LongType; defaultValue = -1L }
                    )
                ) { entry ->
                    VideoPlayerScreen(
                        resourceId = entry.arguments?.getLong("resourceId") ?: 0L,
                        startPositionSeconds = entry.arguments?.getLong("position") ?: -1L,
                        repository = repository,
                        importer = importer,
                        onSendToTranslator = { text, title, timestamp ->
                            activeTranslatorInput = text
                            activeSourceDoc = title
                            activeSourcePage = null
                            activeSourceTimestamp = timestamp
                            navController.navigate(Screen.Translator.route)
                        },
                        onSendToExplainer = { text, title, timestamp ->
                            activeExplainerInput = text
                            activeSourceDoc = title
                            activeSourcePage = null
                            activeSourceTimestamp = timestamp
                            navController.navigate(Screen.Explainer.route)
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Screen.ExamMode.route) {
                    val examVm: ExamViewModel = viewModel(factory = appViewModelFactory)
                    ExamModeScreen(
                        repository = repository,
                        viewModel = examVm,
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    route = "lecture_quiz/{lectureId}?title={title}&courseId={courseId}",
                    arguments = listOf(
                        navArgument("lectureId") { type = NavType.LongType },
                        navArgument("title") { type = NavType.StringType; defaultValue = "" },
                        navArgument("courseId") { type = NavType.LongType; defaultValue = 0L }
                    )
                ) { entry ->
                    val lectureId = entry.arguments?.getLong("lectureId") ?: 0L
                    val title = entry.arguments?.getString("title") ?: "المحاضرة"
                    val courseId = entry.arguments?.getLong("courseId") ?: 0L
                    val quizVm: LectureQuizViewModel = viewModel(key = "quiz_$lectureId", factory = appViewModelFactory)
                    LectureQuizScreen(
                        lectureId = lectureId,
                        courseId = courseId,
                        lectureTitle = title,
                        courseTitle = "",
                        repository = repository,
                        viewModel = quizVm,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
