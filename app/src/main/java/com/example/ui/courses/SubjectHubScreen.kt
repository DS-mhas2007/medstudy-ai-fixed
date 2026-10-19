package com.example.ui.courses

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ReviewGrade
import com.example.data.files.ImportResult
import com.example.data.files.ResourceImporter
import com.example.data.local.CourseEntity
import com.example.data.local.FlashcardEntity
import com.example.data.local.LectureEntity
import com.example.data.local.LectureResourceEntity
import com.example.data.local.ModuleEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.components.OcrScannerSheet
import com.example.ui.components.PdfIndexingBanner
import com.example.ui.components.SmartFlashcardView
import com.example.ui.theme.*
import com.example.worker.PdfIndexingScheduler
import kotlinx.coroutines.launch

/**
 * Dedicated Subject Workspace (المساحة المخصصة للمادة)
 * Unites PDFs, local video player lectures, AI summaries, FSRS smart flashcards,
 * and topic weakness tracking in a single course hub.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectHubScreen(
    course: CourseEntity,
    repository: MedStudyRepository,
    importer: ResourceImporter,
    onOpenPdfReader: (Long) -> Unit,
    onOpenVideoPlayer: (Long) -> Unit,
    onLaunchQuiz: (lectureId: Long, lectureTitle: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("المستندات", "الفيديوهات", "الملخصات", "البطاقات الذكية", "الاختبارات ونقاط الضعف")

    val modules by repository.getModulesForCourse(course.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val lectures by repository.getLecturesForCourse(course.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val allCards by repository.allFlashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val courseCards = remember(allCards, course.id) { allCards.filter { it.courseId == course.id } }
    val weaknesses by repository.getWeaknessesForCourse(course.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val quizAttempts by repository.getQuizAttemptsForCourse(course.id).collectAsStateWithLifecycle(initialValue = emptyList())

    var showOcrSheet by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }
    var showAddLectureDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = course.titleAr.ifBlank { course.title },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = "${course.code} • ${course.semester}",
                            fontSize = 11.sp,
                            color = MedMint
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MedTextPrimaryDark
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOcrSheet = true }) {
                        Icon(Icons.Default.DocumentScanner, contentDescription = "مسح OCR", tint = MedMint)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedSurfaceDark)
            )
        },
        containerColor = MedBackgroundDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live background indexing progress banner
            PdfIndexingBanner()

            // Segmented Scrollable Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MedSurfaceDark,
                contentColor = MedMint,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTabIndex == index) MedMint else MedTextSecondaryDark
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Workspace Content by Tab
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                when (selectedTabIndex) {
                    0 -> DocumentsWorkspaceTab(
                        course = course,
                        lectures = lectures,
                        repository = repository,
                        importer = importer,
                        onOpenPdf = onOpenPdfReader,
                        onLaunchQuiz = onLaunchQuiz,
                        onAddLecture = { showAddLectureDialog = true }
                    )
                    1 -> VideoWorkspaceTab(
                        course = course,
                        lectures = lectures,
                        repository = repository,
                        importer = importer,
                        onOpenVideoPlayer = onOpenVideoPlayer
                    )
                    2 -> SummariesWorkspaceTab(
                        courseId = course.id,
                        repository = repository
                    )
                    3 -> FlashcardsWorkspaceTab(
                        courseId = course.id,
                        cards = courseCards,
                        repository = repository,
                        onOpenOcr = { showOcrSheet = true },
                        onAddCard = { showAddCardDialog = true }
                    )
                    4 -> QuizzesAndWeaknessTab(
                        courseId = course.id,
                        lectures = lectures,
                        weaknesses = weaknesses,
                        attempts = quizAttempts,
                        onLaunchQuiz = onLaunchQuiz
                    )
                }
            }
        }

        // OCR Sheet
        if (showOcrSheet) {
            OcrScannerSheet(
                courseId = course.id,
                lectureId = lectures.firstOrNull()?.id,
                onDismiss = { showOcrSheet = false },
                onCardCreated = { front, back, isCloze ->
                    scope.launch {
                        if (isCloze) {
                            repository.addClozeFlashcard(text = front, courseId = course.id)
                        } else {
                            repository.addFlashcard(front = front, back = back)
                        }
                    }
                }
            )
        }

        // Add Flashcard Dialog
        if (showAddCardDialog) {
            AddSmartCardDialog(
                courseId = course.id,
                onDismiss = { showAddCardDialog = false },
                onConfirm = { front, back, isCloze, isAnatomy ->
                    scope.launch {
                        if (isCloze) {
                            repository.addClozeFlashcard(text = front, explanation = back, courseId = course.id)
                        } else if (isAnatomy) {
                            repository.addAnatomyFlashcard(frontLabel = front, backLabel = back, imageUri = "", courseId = course.id)
                        } else {
                            repository.addFlashcard(front = front, back = back)
                        }
                        showAddCardDialog = false
                    }
                }
            )
        }

        // Add Lecture Dialog
        if (showAddLectureDialog) {
            AddLectureQuickDialog(
                courseId = course.id,
                modules = modules,
                onDismiss = { showAddLectureDialog = false },
                onConfirm = { title, moduleId ->
                    scope.launch {
                        repository.insertLecture(
                            LectureEntity(
                                moduleId = moduleId,
                                courseId = course.id,
                                title = title,
                                titleAr = title
                            )
                        )
                        showAddLectureDialog = false
                    }
                }
            )
        }
    }
}

// -------------------------------------------------------------
// TAB 1: Documents Workspace (PDFs + Lecture Notes + Quiz Launcher)
// -------------------------------------------------------------
@Composable
private fun DocumentsWorkspaceTab(
    course: CourseEntity,
    lectures: List<LectureEntity>,
    repository: MedStudyRepository,
    importer: ResourceImporter,
    onOpenPdf: (Long) -> Unit,
    onLaunchQuiz: (lectureId: Long, lectureTitle: String) -> Unit,
    onAddLecture: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLectureForImport by remember { mutableStateOf<LectureEntity?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val lecture = selectedLectureForImport
        if (uri != null && lecture != null) {
            scope.launch {
                when (val result = importer.importPdf(uri)) {
                    is ImportResult.Success -> {
                        val insertedId = repository.insertResource(
                            LectureResourceEntity(
                                lectureId = lecture.id,
                                type = "PDF",
                                title = result.pdf.displayName,
                                uriString = result.pdf.file.absolutePath,
                                localPath = result.pdf.file.absolutePath,
                                pageCount = result.pdf.pageCount
                            )
                        )
                        PdfIndexingScheduler.scheduleIndexing(
                            context = context,
                            resourceId = insertedId,
                            lectureId = lecture.id,
                            courseId = course.id,
                            filePath = result.pdf.file.absolutePath,
                            title = result.pdf.displayName
                        )
                        Toast.makeText(context, "تم استيراد ${result.pdf.displayName} وبدأت الفهرسة بالخلفية.", Toast.LENGTH_SHORT).show()
                    }
                    is ImportResult.Failure -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "محاضرات ومستندات المقرر (${lectures.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedMint
                )
                Button(
                    onClick = onAddLecture,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MedMint, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة محاضرة", fontSize = 12.sp, color = MedMint)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (lectures.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MedSurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.MenuBook, contentDescription = null, tint = MedTextSecondaryDark, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لا توجد محاضرات مضافة بعد في هذه المادة", color = MedTextSecondaryDark, fontSize = 13.sp)
                    }
                }
            }
        }

        items(lectures) { lecture ->
            val resources by repository.getResourcesForLecture(lecture.id).collectAsStateWithLifecycle(initialValue = emptyList())
            val pdfResources = resources.filter { it.type == "PDF" }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lecture.titleAr.ifBlank { lecture.title },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark,
                            modifier = Modifier.weight(1f)
                        )
                        // Quiz from Lecture Button
                        Surface(
                            onClick = { onLaunchQuiz(lecture.id, lecture.titleAr.ifBlank { lecture.title }) },
                            shape = RoundedCornerShape(8.dp),
                            color = MedMint.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Quiz, contentDescription = null, tint = MedMint, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("توليد اختبار", fontSize = 11.sp, color = MedMint, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (pdfResources.isNotEmpty()) {
                        pdfResources.forEach { pdf ->
                            Surface(
                                onClick = { onOpenPdf(pdf.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = MedBackgroundDark,
                                border = BorderStroke(1.dp, MedBorderDark),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MedCoral, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pdf.title, fontSize = 13.sp, color = MedTextPrimaryDark, maxLines = 1)
                                        Text(
                                            text = if (pdf.pageCount > 0) "صفحة ${pdf.lastReadPage} من ${pdf.pageCount}" else "جاهز للقراءة",
                                            fontSize = 11.sp,
                                            color = MedTextSecondaryDark
                                        )
                                    }
                                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MedMint, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedLectureForImport = lecture
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MedBorderDark)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = MedTextSecondaryDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إرفاق ملف PDF للمحاضرة", fontSize = 12.sp, color = MedTextSecondaryDark)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 2: Video Workspace (Local Player Dedicated Hub)
// -------------------------------------------------------------
@Composable
private fun VideoWorkspaceTab(
    course: CourseEntity,
    lectures: List<LectureEntity>,
    repository: MedStudyRepository,
    importer: ResourceImporter,
    onOpenVideoPlayer: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedLectureForVideo by remember { mutableStateOf<LectureEntity?>(null) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val lecture = selectedLectureForVideo
        if (uri != null && lecture != null) {
            scope.launch {
                importer.keepVideoAccess(uri)
                val title = importer.displayName(uri).ifBlank { "فيديو المحاضرة" }
                repository.insertResource(
                    LectureResourceEntity(
                        lectureId = lecture.id,
                        type = "VIDEO",
                        title = title,
                        uriString = uri.toString()
                    )
                )
                Toast.makeText(context, "تم حفظ فيديو المحاضرة: $title", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedMint.copy(alpha = 0.3f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MedMint, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("مشغل المحاضرات المرئية المدمج", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                        Text("تشغيل الفيديوهات المحفوظة محلياً مع تدوين الملاحظات التزامنية عند الثواني.", fontSize = 11.sp, color = MedTextSecondaryDark)
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        items(lectures) { lecture ->
            val resources by repository.getResourcesForLecture(lecture.id).collectAsStateWithLifecycle(initialValue = emptyList())
            val videoResources = resources.filter { it.type == "VIDEO" }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = lecture.titleAr.ifBlank { lecture.title },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedTextPrimaryDark
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    if (videoResources.isNotEmpty()) {
                        videoResources.forEach { vid ->
                            Surface(
                                onClick = { onOpenVideoPlayer(vid.id) },
                                shape = RoundedCornerShape(10.dp),
                                color = MedMintContainer.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MedMint.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MedMint, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(vid.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                                        Text(
                                            text = if (vid.lastPlaybackPosition > 0) "آخر موضع: ${vid.lastPlaybackPosition} ثانية" else "جاهز للمشاهدة في المشغل",
                                            fontSize = 11.sp,
                                            color = MedMint
                                        )
                                    }
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, tint = MedMint, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedLectureForVideo = lecture
                                videoPickerLauncher.launch(arrayOf("video/*"))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MedBorderDark)
                        ) {
                            Icon(Icons.Default.VideoCall, contentDescription = null, tint = MedTextSecondaryDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استيراد فيديو المحاضرة للمشغل", fontSize = 12.sp, color = MedTextSecondaryDark)
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 3: Summaries Workspace (AI Explanations & Clinical pearls)
// -------------------------------------------------------------
@Composable
private fun SummariesWorkspaceTab(
    courseId: Long,
    repository: MedStudyRepository
) {
    val results by repository.allSavedResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val courseResults = remember(results, courseId) {
        results.filter { it.courseId == courseId || it.type == "EXPLANATION" }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Text("الملخصات والشروحات السريرية (${courseResults.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedMint)
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (courseResults.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MedSurfaceDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Psychology, contentDescription = null, tint = MedTextSecondaryDark, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("لا توجد ملخصات محفوظة بعد لهذه المادة", color = MedTextSecondaryDark, fontSize = 13.sp)
                    }
                }
            }
        }

        items(courseResults) { res ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(res.title.ifBlank { res.topic }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MedMint.copy(alpha = 0.15f)
                        ) {
                            Text(res.type, fontSize = 10.sp, color = MedMint, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                    if (res.simpleExplanation.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(res.simpleExplanation, fontSize = 12.sp, color = MedTextSecondaryDark, maxLines = 3)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 4: Flashcards Workspace (FSRS & Smart Cards)
// -------------------------------------------------------------
@Composable
private fun FlashcardsWorkspaceTab(
    courseId: Long,
    cards: List<FlashcardEntity>,
    repository: MedStudyRepository,
    onOpenOcr: () -> Unit,
    onAddCard: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var reviewIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val activeCard = cards.getOrNull(reviewIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        // Quick Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("البطاقات الذكية (${cards.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedMint)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // OCR Button
                Button(
                    onClick = onOpenOcr,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer)
                ) {
                    Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = MedMint, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("مسح OCR", fontSize = 11.sp, color = MedMint)
                }

                // Add Card Button
                Button(
                    onClick = onAddCard,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("إضافة بطاقة", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (cards.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MedSurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Style, contentDescription = null, tint = MedTextSecondaryDark, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("لا توجد بطاقات مضافة في هذه المادة بعد.", color = MedTextSecondaryDark, fontSize = 13.sp)
                    Text("اضغط على «مسح OCR» لقراءة نص المحاضرة وتحويله لبطاقة فراغات فوراً.", color = MedMint, fontSize = 11.sp)
                }
            }
        } else if (activeCard != null) {
            // Interactive Smart Flashcard Card View
            Text(
                text = "بطاقة ${reviewIndex + 1} من ${cards.size}",
                fontSize = 11.sp,
                color = MedTextSecondaryDark,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            SmartFlashcardView(
                flashcard = activeCard,
                isFlipped = isFlipped,
                onFlip = { isFlipped = !isFlipped },
                onAnswerGrade = { grade ->
                    scope.launch {
                        repository.answerFlashcard(activeCard, grade.value)
                        isFlipped = false
                        if (reviewIndex < cards.size - 1) {
                            reviewIndex++
                        } else {
                            reviewIndex = 0
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (reviewIndex > 0) {
                            reviewIndex--
                            isFlipped = false
                        }
                    },
                    enabled = reviewIndex > 0
                ) {
                    Text("البطاقة السابقة", color = if (reviewIndex > 0) MedMint else MedTextSecondaryDark)
                }

                TextButton(
                    onClick = {
                        if (reviewIndex < cards.size - 1) {
                            reviewIndex++
                            isFlipped = false
                        }
                    },
                    enabled = reviewIndex < cards.size - 1
                ) {
                    Text("البطاقة التالية", color = if (reviewIndex < cards.size - 1) MedMint else MedTextSecondaryDark)
                }
            }
        }
    }
}

// -------------------------------------------------------------
// TAB 5: Quizzes & Weakness Radar Tab
// -------------------------------------------------------------
@Composable
private fun QuizzesAndWeaknessTab(
    courseId: Long,
    lectures: List<LectureEntity>,
    weaknesses: List<com.example.data.local.TopicWeaknessEntity>,
    attempts: List<com.example.data.local.QuizAttemptEntity>,
    onLaunchQuiz: (lectureId: Long, lectureTitle: String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Topic Weakness Radar Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedMint.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Radar, contentDescription = null, tint = MedMint, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("رادار نقاط الضعف في هذا المقرر", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MedMint)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (weaknesses.isEmpty()) {
                        Text(
                            text = "لم يتم تسجيل اختبارات كافية بعد. اختبر نفسك في محاضرات المقرر لتوليد تحليل نقاط الضعف.",
                            fontSize = 12.sp,
                            color = MedTextSecondaryDark
                        )
                    } else {
                        weaknesses.forEach { w ->
                            val isWeak = w.accuracyPercent < 70
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(w.topic, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                                    Text(w.specialty, fontSize = 11.sp, color = MedTextSecondaryDark)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isWeak) MedCoral.copy(alpha = 0.2f) else MedMint.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isWeak) "${w.accuracyPercent}% (ضعيف)" else "${w.accuracyPercent}% (متقن)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWeak) MedCoral else MedMint,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Launch Quiz Header
        item {
            Text("توليد اختبار سريع من محاضرات المقرر:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(lectures) { lec ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lec.titleAr.ifBlank { lec.title }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                        Text("5 أسئلة سريرية متعددة الخيارات", fontSize = 11.sp, color = MedTextSecondaryDark)
                    }

                    Button(
                        onClick = { onLaunchQuiz(lec.id, lec.titleAr.ifBlank { lec.title }) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer)
                    ) {
                        Text("بدء الاختبار", fontSize = 11.sp, color = MedMint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Past Quiz Attempts History
        if (attempts.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("سجل الاختبارات السابقة (${attempts.size}):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedMint)
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(attempts) { att ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                    border = BorderStroke(1.dp, MedBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(att.title, fontSize = 13.sp, color = MedTextPrimaryDark)
                            Text(att.specialty, fontSize = 10.sp, color = MedTextSecondaryDark)
                        }
                        Text(
                            text = "${att.scorePercent}% (${att.correctCount}/${att.totalQuestions})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (att.scorePercent >= 70) MedMint else MedCoral
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Helper Dialogs
// -------------------------------------------------------------
@Composable
private fun AddSmartCardDialog(
    courseId: Long,
    onDismiss: () -> Unit,
    onConfirm: (front: String, back: String, isCloze: Boolean, isAnatomy: Boolean) -> Unit
) {
    var cardType by remember { mutableIntStateOf(0) } // 0: Standard, 1: Cloze, 2: Anatomy
    var frontText by remember { mutableStateOf("") }
    var backText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة بطاقة ذكية جديدة", color = MedTextPrimaryDark) },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = cardType == 0,
                        onClick = { cardType = 0 },
                        label = { Text("سؤال وجواب", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MedMint, selectedLabelColor = Color.Black)
                    )
                    FilterChip(
                        selected = cardType == 1,
                        onClick = { cardType = 1 },
                        label = { Text("فراغات (Cloze)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MedMint, selectedLabelColor = Color.Black)
                    )
                    FilterChip(
                        selected = cardType == 2,
                        onClick = { cardType = 2 },
                        label = { Text("تشريح وصور", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MedMint, selectedLabelColor = Color.Black)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = frontText,
                    onValueChange = { frontText = it },
                    label = {
                        Text(
                            when (cardType) {
                                1 -> "النص مع الفراغ: ضع الكلمة بين {{c1::...}}"
                                2 -> "اسم الهيكل أو العضو التشريحي"
                                else -> "السؤال / المفهوم الطبي"
                            },
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = backText,
                    onValueChange = { backText = it },
                    label = {
                        Text(
                            when (cardType) {
                                1 -> "إيضاح سريري للفراغ (اختياري)"
                                2 -> "الوصف التشريحي / الوظيفة"
                                else -> "الإجابة النموذجية"
                            },
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (frontText.isNotBlank()) {
                        onConfirm(frontText, backText, cardType == 1, cardType == 2)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MedMint)
            ) {
                Text("حفظ البطاقة", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MedTextSecondaryDark)
            }
        },
        containerColor = MedSurfaceDark
    )
}

@Composable
private fun AddLectureQuickDialog(
    courseId: Long,
    modules: List<ModuleEntity>,
    onDismiss: () -> Unit,
    onConfirm: (title: String, moduleId: Long) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedModuleId by remember { mutableLongStateOf(modules.firstOrNull()?.id ?: 0L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة محاضرة جديدة للمقرر", color = MedTextPrimaryDark) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("عنوان المحاضرة الطبية", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(title, selectedModuleId)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MedMint)
            ) {
                Text("إضافة", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = MedTextSecondaryDark)
            }
        },
        containerColor = MedSurfaceDark
    )
}
