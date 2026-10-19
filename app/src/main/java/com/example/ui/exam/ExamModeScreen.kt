package com.example.ui.exam

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ExamQuestion
import com.example.core.ExamReport
import com.example.core.MedicalExamEngine
import com.example.data.local.CourseEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.example.ui.viewmodel.ExamViewModel

/**
 * Multi-course timed medical exam simulation with countdown timer
 * and analytical post-session diagnostic report.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamModeScreen(
    repository: MedStudyRepository,
    viewModel: ExamViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val courses by repository.allCourses.collectAsStateWithLifecycle(initialValue = emptyList())

    val vmState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    // Fallback local state if viewModel is not supplied
    var localIsExamStarted by remember { mutableStateOf(false) }
    var localSelectedDurationMinutes by remember { mutableIntStateOf(15) }
    var localSelectedQuestionCount by remember { mutableIntStateOf(8) }
    val localSelectedCourseIds = remember { mutableStateListOf<Long>() }
    var localQuestions by remember { mutableStateOf<List<ExamQuestion>>(emptyList()) }
    var localCurrentIndex by remember { mutableIntStateOf(0) }
    val localUserAnswers = remember { mutableStateMapOf<Int, Int>() }
    val localFlaggedQuestions = remember { mutableStateListOf<Int>() }
    var localSecondsRemaining by remember { mutableLongStateOf(0L) }
    var localIsExamFinished by remember { mutableStateOf(false) }
    var localExamReport by remember { mutableStateOf<ExamReport?>(null) }
    var localShowSubmitConfirmation by remember { mutableStateOf(false) }

    // Initialize course selection when courses load
    LaunchedEffect(courses) {
        if (courses.isNotEmpty()) {
            if (viewModel != null && vmState?.selectedCourseIds?.isEmpty() == true) {
                viewModel.setSelectedCourses(courses.map { it.id }.toSet())
            } else if (localSelectedCourseIds.isEmpty()) {
                localSelectedCourseIds.addAll(courses.map { it.id })
            }
        }
    }

    val isExamStarted = vmState?.isExamStarted ?: localIsExamStarted
    val isExamFinished = vmState?.isExamFinished ?: localIsExamFinished
    val selectedDurationMinutes = vmState?.selectedDurationMinutes ?: localSelectedDurationMinutes
    val selectedQuestionCount = vmState?.selectedQuestionCount ?: localSelectedQuestionCount
    val questions = vmState?.questions ?: localQuestions
    val currentIndex = vmState?.currentIndex ?: localCurrentIndex
    val userAnswers = vmState?.userAnswers ?: localUserAnswers
    val secondsRemaining = vmState?.secondsRemaining ?: localSecondsRemaining
    val examReport = vmState?.examReport ?: localExamReport
    val showSubmitConfirmation = vmState?.showSubmitConfirmation ?: localShowSubmitConfirmation
    val flaggedQuestionIndices = vmState?.flaggedQuestionIndices ?: localFlaggedQuestions.toSet()

    // Fallback timer only when viewModel is null (ViewModel manages timer across rotation!)
    LaunchedEffect(isExamStarted, isExamFinished, viewModel) {
        if (viewModel == null && isExamStarted && !isExamFinished) {
            while (localSecondsRemaining > 0 && !localIsExamFinished) {
                delay(1000L)
                localSecondsRemaining--
            }
            if (localSecondsRemaining <= 0L && localIsExamStarted && !localIsExamFinished) {
                val spent = (localSelectedDurationMinutes * 60L) - localSecondsRemaining
                val report = MedicalExamEngine.evaluateExam(
                    examTitle = "محاكاة الامتحان الشامل",
                    questions = localQuestions,
                    answers = localUserAnswers,
                    timeSpentSeconds = spent,
                    timeLimitSeconds = localSelectedDurationMinutes * 60L
                )
                localExamReport = report
                localIsExamFinished = true
                repository.recordExamAttempt(report, localSelectedCourseIds)
                Toast.makeText(context, "انتهى وقت الامتحان! تم تسليم الإجابات تلقائياً.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (!isExamStarted) "وضع الامتحان الشامل" else if (!isExamFinished) "جلسة الاختبار جارية" else "تقرير تشخيص الأداء",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedTextPrimaryDark
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isExamStarted && !isExamFinished) {
                            if (viewModel != null) viewModel.setShowSubmitConfirmation(true)
                            else localShowSubmitConfirmation = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = MedTextPrimaryDark
                        )
                    }
                },
                actions = {
                    if (isExamStarted && !isExamFinished) {
                        TextButton(onClick = {
                            if (viewModel != null) viewModel.setShowSubmitConfirmation(true)
                            else localShowSubmitConfirmation = true
                        }) {
                            Text("إنهاء وتسليم", color = MedMint, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedSurfaceDark)
            )
        },
        containerColor = MedBackgroundDark
    ) { innerPadding ->
        when {
            // Screen 1: Exam Setup Modal
            !isExamStarted -> {
                val activeCourseIds = if (viewModel != null) vmState?.selectedCourseIds ?: emptySet() else localSelectedCourseIds.toSet()
                ExamSetupView(
                    courses = courses,
                    selectedDuration = selectedDurationMinutes,
                    onDurationChange = {
                        if (viewModel != null) viewModel.updateDuration(it)
                        else localSelectedDurationMinutes = it
                    },
                    selectedCount = selectedQuestionCount,
                    onCountChange = {
                        if (viewModel != null) viewModel.updateQuestionCount(it)
                        else localSelectedQuestionCount = it
                    },
                    selectedCourseIds = activeCourseIds.toList(),
                    onToggleCourse = { id ->
                        if (viewModel != null) {
                            viewModel.toggleCourseSelection(id)
                        } else {
                            if (localSelectedCourseIds.contains(id)) {
                                if (localSelectedCourseIds.size > 1) localSelectedCourseIds.remove(id)
                            } else {
                                localSelectedCourseIds.add(id)
                            }
                        }
                    },
                    modifier = Modifier.padding(innerPadding),
                    onStartExam = {
                        val activeCourses = courses.filter { activeCourseIds.contains(it.id) }
                        val coursePairs = activeCourses.map { it.id to (it.titleAr.ifBlank { it.title }) }
                        val generated = MedicalExamEngine.createMultiCourseExam(coursePairs, selectedQuestionCount)
                        if (viewModel != null) {
                            viewModel.startExam(generated)
                        } else {
                            localQuestions = generated
                            localSecondsRemaining = selectedDurationMinutes * 60L
                            localUserAnswers.clear()
                            localFlaggedQuestions.clear()
                            localCurrentIndex = 0
                            localIsExamStarted = true
                            localIsExamFinished = false
                        }
                    }
                )
            }

            // Screen 2: Active Exam in Progress
            !isExamFinished -> {
                val currentQ = questions.getOrNull(currentIndex)
                if (currentQ != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        // Timer & Status Bar
                        ExamTimerHeader(
                            secondsRemaining = secondsRemaining,
                            totalSeconds = selectedDurationMinutes * 60L,
                            answeredCount = userAnswers.size,
                            totalQuestions = questions.size,
                            isFlagged = flaggedQuestionIndices.contains(currentIndex),
                            onToggleFlag = {
                                if (viewModel != null) {
                                    viewModel.toggleFlagQuestion(currentIndex)
                                } else {
                                    if (localFlaggedQuestions.contains(currentIndex)) {
                                        localFlaggedQuestions.remove(currentIndex)
                                    } else {
                                        localFlaggedQuestions.add(currentIndex)
                                    }
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Question Index Bar
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(questions) { idx, _ ->
                                val isAnswered = userAnswers.containsKey(idx)
                                val isCurrent = idx == currentIndex
                                val isFlagged = flaggedQuestionIndices.contains(idx)

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCurrent -> MedMint
                                                isFlagged -> Color(0xFFF39C12)
                                                isAnswered -> MedMintContainer
                                                else -> MedSurfaceDark
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isCurrent) MedMint else MedBorderDark,
                                            CircleShape
                                        )
                                        .clickable {
                                            if (viewModel != null) viewModel.navigateToQuestion(idx)
                                            else localCurrentIndex = idx
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) Color.Black else MedTextPrimaryDark
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Question Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                            border = BorderStroke(1.dp, MedBorderDark),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MedMint.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = currentQ.courseName.ifBlank { "مقرر طبي" },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MedMint,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MedSurfaceLight.copy(alpha = 0.3f)
                                    ) {
                                        Text(
                                            text = currentQ.specialty,
                                            fontSize = 11.sp,
                                            color = MedTextSecondaryDark,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = currentQ.questionAr,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MedTextPrimaryDark,
                                    lineHeight = 24.sp
                                )

                                if (currentQ.questionEn.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currentQ.questionEn,
                                        fontSize = 12.sp,
                                        color = MedTextSecondaryDark,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Options
                        val selectedOpt = userAnswers[currentIndex]
                        currentQ.options.forEachIndexed { optIndex, text ->
                            val isSelected = selectedOpt == optIndex
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (viewModel != null) viewModel.selectAnswer(currentIndex, optIndex)
                                        else localUserAnswers[currentIndex] = optIndex
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MedMintContainer.copy(alpha = 0.5f) else MedSurfaceDark
                                ),
                                border = BorderStroke(1.dp, if (isSelected) MedMint else MedBorderDark)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(if (isSelected) MedMint else MedSurfaceDark)
                                            .border(1.dp, if (isSelected) MedMint else MedBorderDark, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color.Black))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = text,
                                        fontSize = 14.sp,
                                        color = MedTextPrimaryDark,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Next / Previous Buttons
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (viewModel != null) viewModel.navigateToQuestion(currentIndex - 1)
                                    else if (localCurrentIndex > 0) localCurrentIndex--
                                },
                                enabled = currentIndex > 0,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MedBorderDark)
                            ) {
                                Text("السابق", color = if (currentIndex > 0) MedTextPrimaryDark else MedTextSecondaryDark)
                            }

                            Button(
                                onClick = {
                                    if (currentIndex < questions.size - 1) {
                                        if (viewModel != null) viewModel.navigateToQuestion(currentIndex + 1)
                                        else localCurrentIndex++
                                    } else {
                                        if (viewModel != null) viewModel.setShowSubmitConfirmation(true)
                                        else localShowSubmitConfirmation = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                            ) {
                                Text(
                                    text = if (currentIndex < questions.size - 1) "التالي" else "إنهاء الاختبار",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Screen 3: Post-Exam Analytical Diagnostic Report
            else -> {
                examReport?.let { report ->
                    ExamReportView(
                        report = report,
                        repository = repository,
                        modifier = Modifier.padding(innerPadding),
                        onRetake = {
                            if (viewModel != null) {
                                viewModel.resetExam()
                            } else {
                                localIsExamStarted = false
                                localIsExamFinished = false
                                localExamReport = null
                            }
                        },
                        onClose = onBack
                    )
                }
            }
        }

        // Confirmation Dialog
        if (showSubmitConfirmation) {
            AlertDialog(
                onDismissRequest = {
                    if (viewModel != null) viewModel.setShowSubmitConfirmation(false)
                    else localShowSubmitConfirmation = false
                },
                title = { Text("تسليم الاختبار وإنهاء الجلسة؟", color = MedTextPrimaryDark) },
                text = {
                    val answered = userAnswers.size
                    val total = questions.size
                    Text(
                        "أجبت على $answered من أصل $total سؤالاً. هل تود تسليم الإجابات والحصول على التقرير التشخيصي؟",
                        color = MedTextSecondaryDark
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel != null) {
                                viewModel.submitExam()
                            } else {
                                localShowSubmitConfirmation = false
                                val spent = (localSelectedDurationMinutes * 60L) - localSecondsRemaining
                                val report = MedicalExamEngine.evaluateExam(
                                    examTitle = "محاكاة الامتحان الشامل",
                                    questions = localQuestions,
                                    answers = localUserAnswers,
                                    timeSpentSeconds = spent,
                                    timeLimitSeconds = localSelectedDurationMinutes * 60L
                                )
                                localExamReport = report
                                localIsExamFinished = true
                                scope.launch {
                                    repository.recordExamAttempt(report, localSelectedCourseIds)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                    ) {
                        Text("نعم، تسليم وعرض التقرير", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (viewModel != null) viewModel.setShowSubmitConfirmation(false)
                        else localShowSubmitConfirmation = false
                    }) {
                        Text("متابعة الاختبار", color = MedTextSecondaryDark)
                    }
                },
                containerColor = MedSurfaceDark
            )
        }
    }
}

@Composable
private fun ExamSetupView(
    courses: List<CourseEntity>,
    selectedDuration: Int,
    onDurationChange: (Int) -> Unit,
    selectedCount: Int,
    onCountChange: (Int) -> Unit,
    selectedCourseIds: List<Long>,
    onToggleCourse: (Long) -> Unit,
    onStartExam: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Hero Intro Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
            border = BorderStroke(1.dp, MedMint.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MedMint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MedMint, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("محاكاة الاختبارات السريرية", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                    Text("أسئلة مختلطة من عدة مقررات بمؤقت زمني مع تقرير تشخيصي فوري لنقاط الضعف.", fontSize = 12.sp, color = MedTextSecondaryDark)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Time Limit Selection
        Text("مدة الاختبار (المؤقت التنازلي):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MedMint)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(10, 15, 30, 45).forEach { mins ->
                FilterChip(
                    selected = selectedDuration == mins,
                    onClick = { onDurationChange(mins) },
                    label = { Text("$mins دقيقة", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MedMint,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Question Count Selection
        Text("عدد الأسئلة:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MedMint)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(6, 8, 12, 16).forEach { count ->
                FilterChip(
                    selected = selectedCount == count,
                    onClick = { onCountChange(count) },
                    label = { Text("$count أسئلة", fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MedMint,
                        selectedLabelColor = Color.Black
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Courses Selection
        Text("المقررات المشمولة في الامتحان:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MedMint)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(courses) { course ->
                val isSelected = selectedCourseIds.contains(course.id)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onToggleCourse(course.id) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MedMintContainer.copy(alpha = 0.4f) else MedSurfaceDark
                    ),
                    border = BorderStroke(1.dp, if (isSelected) MedMint else MedBorderDark)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleCourse(course.id) },
                            colors = CheckboxDefaults.colors(checkedColor = MedMint)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(course.titleAr.ifBlank { course.title }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                            Text(course.code, fontSize = 11.sp, color = MedTextSecondaryDark)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start Button
        Button(
            onClick = onStartExam,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MedMint)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text("بدء الامتحان الآن", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ExamTimerHeader(
    secondsRemaining: Long,
    totalSeconds: Long,
    answeredCount: Int,
    totalQuestions: Int,
    isFlagged: Boolean,
    onToggleFlag: () -> Unit
) {
    val mins = secondsRemaining / 60
    val secs = secondsRemaining % 60
    val timeFormatted = String.format("%02d:%02d", mins, secs)
    val isUrgent = secondsRemaining < 180 // < 3 minutes

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) MedCoral.copy(alpha = 0.15f) else MedSurfaceDark
        ),
        border = BorderStroke(1.dp, if (isUrgent) MedCoral else MedBorderDark)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Timer Display
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = if (isUrgent) MedCoral else MedMint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timeFormatted,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUrgent) MedCoral else MedMint
                )
            }

            // Answered counter
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MedBackgroundDark
            ) {
                Text(
                    text = "الإجابات: $answeredCount / $totalQuestions",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedTextPrimaryDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Flag for review button
            IconButton(onClick = onToggleFlag) {
                Icon(
                    imageVector = if (isFlagged) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "تمييز للمراجعة",
                    tint = if (isFlagged) Color(0xFFF39C12) else MedTextSecondaryDark
                )
            }
        }
    }
}

@Composable
private fun ExamReportView(
    report: ExamReport,
    repository: MedStudyRepository,
    modifier: Modifier = Modifier,
    onRetake: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val timeSpentMins = report.timeSpentSeconds / 60
    val timeSpentSecs = report.timeSpentSeconds % 60

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Overall Score Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (report.isPassed) MedMint.copy(alpha = 0.15f) else MedCoral.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, if (report.isPassed) MedMint else MedCoral)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = if (report.isPassed) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (report.isPassed) MedMint else MedCoral,
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (report.isPassed) "اجتزت الامتحان بنجاح!" else "لم تحقق درجة الاجتياز (60%)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedTextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "الدرجة: ${report.scorePercent}% (${report.correctCount} من ${report.totalQuestions})",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (report.isPassed) MedMint else MedCoral
                    )
                    Text(
                        text = "الوقت المستغرق: $timeSpentMins دقيقة و$timeSpentSecs ثانية",
                        fontSize = 12.sp,
                        color = MedTextSecondaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Specialty Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Analytics, contentDescription = null, tint = MedMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("التقرير التشخيصي لتخصصات الامتحان:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedMint)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    report.specialtyScores.forEach { (specialty, score) ->
                        val isWeak = score < 70
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(specialty, fontSize = 13.sp, color = MedTextPrimaryDark)
                                Text(
                                    text = if (isWeak) "$score% (نقطة ضعف)" else "$score% (ممتاز)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWeak) MedCoral else MedMint
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { score.toFloat() / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = if (isWeak) MedCoral else MedMint,
                                trackColor = MedBackgroundDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Action: Convert missed questions to flashcards
        val wrongResults = report.detailedResults.filter { !it.isCorrect }
        if (wrongResults.isNotEmpty()) {
            item {
                Button(
                    onClick = {
                        scope.launch {
                            var added = 0
                            wrongResults.forEach { r ->
                                val ok = repository.addFlashcard(
                                    front = r.question.questionAr,
                                    back = r.question.options[r.question.correctIndex],
                                    explanation = r.question.explanationAr
                                )
                                if (ok) added++
                            }
                            Toast.makeText(context, "تمت إضافة $added بطاقة لجدول تكرار FSRS!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedCoral.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MedCoral)
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = MedCoral)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إضافة الأسئلة الخاطئة (${wrongResults.size}) إلى بطاقات المراجعة", color = MedCoral, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Detailed Question Review Header
        item {
            Text("مراجعة الأسئلة والإجابات النموذجية:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MedMint)
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Questions List
        items(report.detailedResults) { res ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, if (res.isCorrect) MedMint.copy(alpha = 0.4f) else MedCoral.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = res.question.questionAr,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (res.isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (res.isCorrect) MedMint else MedCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (!res.isCorrect) {
                        val yourAns = if (res.selectedIndex >= 0) res.question.options[res.selectedIndex] else "لم تجب"
                        Text("إجابتك: $yourAns", fontSize = 11.sp, color = MedCoral)
                    }
                    Text("الإجابة الصحيحة: ${res.question.options[res.question.correctIndex]}", fontSize = 11.sp, color = MedMint, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(res.question.explanationAr, fontSize = 11.sp, color = MedTextSecondaryDark)
                }
            }
        }

        // Retake and Close Buttons
        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRetake,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MedBorderDark)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MedTextPrimaryDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("امتحان جديد", color = MedTextPrimaryDark)
                }

                Button(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                ) {
                    Text("إنهاء الجلسة", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
