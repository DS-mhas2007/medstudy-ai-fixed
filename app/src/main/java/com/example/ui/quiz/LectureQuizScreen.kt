package com.example.ui.quiz

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.ExamQuestion
import com.example.core.MedicalExamEngine
import com.example.data.local.LectureEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

import com.example.ui.viewmodel.LectureQuizViewModel

/**
 * Interactive quiz screen generated directly from the lecture material.
 * Includes automated clinical grading and topic weakness tracking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LectureQuizScreen(
    lectureId: Long,
    courseId: Long,
    lectureTitle: String,
    courseTitle: String,
    repository: MedStudyRepository,
    viewModel: LectureQuizViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vmState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }

    LaunchedEffect(lectureId, courseId, lectureTitle, courseTitle, viewModel) {
        viewModel?.initializeQuiz(lectureId, courseId, lectureTitle, courseTitle)
    }

    var localQuestions by remember {
        mutableStateOf(
            MedicalExamEngine.generateLectureQuiz(
                lectureTitle = lectureTitle,
                lectureText = lectureTitle,
                courseId = courseId,
                courseName = courseTitle,
                count = 5
            )
        )
    }

    var localCurrentIndex by remember { mutableIntStateOf(0) }
    val localUserAnswers = remember { mutableStateMapOf<Int, Int>() }
    var localSubmittedCurrent by remember { mutableStateOf(false) }
    var localIsQuizCompleted by remember { mutableStateOf(false) }

    val questions = vmState?.questions ?: localQuestions
    val currentIndex = vmState?.currentIndex ?: localCurrentIndex
    val userAnswers = vmState?.userAnswers ?: localUserAnswers
    val submittedCurrent = vmState?.submittedCurrent ?: localSubmittedCurrent
    val isQuizCompleted = vmState?.isQuizCompleted ?: localIsQuizCompleted

    val currentQuestion = questions.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "اختبار من المحاضرة مباشرة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = lectureTitle,
                            fontSize = 12.sp,
                            color = MedMint,
                            maxLines = 1
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedSurfaceDark)
            )
        },
        containerColor = MedBackgroundDark
    ) { innerPadding ->
        if (isQuizCompleted) {
            QuizSummaryView(
                lectureId = lectureId,
                courseId = courseId,
                lectureTitle = lectureTitle,
                questions = questions,
                userAnswers = userAnswers,
                repository = repository,
                modifier = Modifier.padding(innerPadding),
                onRestart = {
                    if (viewModel != null) {
                        viewModel.retakeQuiz()
                    } else {
                        localUserAnswers.clear()
                        localCurrentIndex = 0
                        localSubmittedCurrent = false
                        localIsQuizCompleted = false
                        localQuestions = MedicalExamEngine.generateLectureQuiz(
                            lectureTitle = lectureTitle,
                            lectureText = lectureTitle,
                            courseId = courseId,
                            courseName = courseTitle,
                            count = 5
                        )
                    }
                },
                onBack = onBack
            )
        } else if (currentQuestion != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                // Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "السؤال ${currentIndex + 1} من ${questions.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedMint
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MedMint.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = currentQuestion.specialty,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedMint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { (currentIndex + 1).toFloat() / questions.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MedMint,
                    trackColor = MedSurfaceDark
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Question Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                    border = BorderStroke(1.dp, MedBorderDark),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = currentQuestion.questionAr,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark,
                            lineHeight = 24.sp
                        )

                        if (currentQuestion.questionEn.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = currentQuestion.questionEn,
                                fontSize = 12.sp,
                                color = MedTextSecondaryDark,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Options List
                val selectedOption = userAnswers[currentIndex]
                currentQuestion.options.forEachIndexed { optIndex, optionText ->
                    val isSelected = selectedOption == optIndex
                    val isCorrect = optIndex == currentQuestion.correctIndex

                    val borderColor = when {
                        !submittedCurrent && isSelected -> MedMint
                        submittedCurrent && isCorrect -> MedMint
                        submittedCurrent && isSelected && !isCorrect -> MedCoral
                        else -> MedBorderDark
                    }

                    val containerColor = when {
                        !submittedCurrent && isSelected -> MedMintContainer.copy(alpha = 0.4f)
                        submittedCurrent && isCorrect -> MedMint.copy(alpha = 0.2f)
                        submittedCurrent && isSelected && !isCorrect -> MedCoral.copy(alpha = 0.2f)
                        else -> MedSurfaceDark
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !submittedCurrent) {
                                if (viewModel != null) viewModel.selectOption(optIndex)
                                else localUserAnswers[currentIndex] = optIndex
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = containerColor),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            submittedCurrent && isCorrect -> MedMint
                                            submittedCurrent && isSelected -> MedCoral
                                            isSelected -> MedMint
                                            else -> MedSurfaceDark
                                        }
                                    )
                                    .border(1.dp, borderColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (submittedCurrent && isCorrect) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                } else if (submittedCurrent && isSelected) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                } else if (isSelected) {
                                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Black))
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = optionText,
                                fontSize = 14.sp,
                                color = MedTextPrimaryDark,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Clinical Rationale Box after submit
                AnimatedVisibility(visible = submittedCurrent) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MedBackgroundDark,
                            border = BorderStroke(1.dp, MedMint.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = MedMint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "التعليل واللؤلؤة السريرية:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MedMint
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentQuestion.explanationAr,
                                    fontSize = 13.sp,
                                    color = MedTextPrimaryDark,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Navigation Button
                if (!submittedCurrent) {
                    Button(
                        onClick = {
                            if (userAnswers[currentIndex] != null) {
                                if (viewModel != null) {
                                    viewModel.submitCurrentAnswer()
                                } else {
                                    localSubmittedCurrent = true
                                    val ans = localUserAnswers[currentIndex] ?: -1
                                    val isCorrect = ans == currentQuestion.correctIndex
                                    scope.launch {
                                        repository.recordTopicWeakness(
                                            topic = currentQuestion.topic,
                                            specialty = currentQuestion.specialty,
                                            courseId = if (courseId > 0) courseId else null,
                                            isCorrect = isCorrect
                                        )
                                    }
                                }
                            } else {
                                Toast.makeText(context, "الرجاء اختيار إجابة أولاً", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                    ) {
                        Text("تحقق من الإجابة", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            if (viewModel != null) {
                                viewModel.nextQuestion()
                            } else {
                                if (localCurrentIndex < questions.size - 1) {
                                    localCurrentIndex++
                                    localSubmittedCurrent = false
                                } else {
                                    localIsQuizCompleted = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer)
                    ) {
                        Text(
                            text = if (currentIndex < questions.size - 1) "السؤال التالي" else "عرض تقرير الاختبار ونقاط الضعف",
                            color = MedMint,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MedMint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizSummaryView(
    lectureId: Long,
    courseId: Long,
    lectureTitle: String,
    questions: List<ExamQuestion>,
    userAnswers: Map<Int, Int>,
    repository: MedStudyRepository,
    modifier: Modifier = Modifier,
    onRestart: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val correctCount = remember(questions, userAnswers) {
        questions.filterIndexed { index, q -> userAnswers[index] == q.correctIndex }.size
    }
    val scorePercent = remember(questions, correctCount) {
        if (questions.isNotEmpty()) ((correctCount.toFloat() / questions.size.toFloat()) * 100f).toInt() else 0
    }

    // Auto save quiz attempt & update topic weaknesses on load
    LaunchedEffect(Unit) {
        repository.recordQuizAttempt(
            lectureId = lectureId,
            courseId = courseId,
            title = "اختبار: $lectureTitle",
            topic = questions.firstOrNull()?.topic ?: "عام",
            specialty = questions.firstOrNull()?.specialty ?: "طب عام",
            questions = questions,
            answers = userAnswers
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Trophy / Score Banner
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        if (scorePercent >= 70) MedMint.copy(alpha = 0.2f) else MedCoral.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (scorePercent >= 70) Icons.Default.EmojiEvents else Icons.Default.Analytics,
                    contentDescription = null,
                    tint = if (scorePercent >= 70) MedMint else MedCoral,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (scorePercent >= 70) "أداء متميز في هذه المحاضرة!" else "تحتاج لتعزيز بعض المفاهيم",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MedTextPrimaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "الدرجة النهائية: $scorePercent% ($correctCount من ${questions.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (scorePercent >= 70) MedMint else MedCoral
            )

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Weakness Analysis Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Radar, contentDescription = null, tint = MedMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "رادار تتبع نقاط الضعف حسب التخصص:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedMint
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val specialtyStats = questions.groupBy { it.specialty }
                    specialtyStats.forEach { (specialty, qList) ->
                        val correct = qList.count { userAnswers[questions.indexOf(it)] == it.correctIndex }
                        val pct = ((correct.toFloat() / qList.size.toFloat()) * 100f).toInt()
                        val isWeak = pct < 70

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isWeak) MedCoral else MedMint)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = specialty,
                                    fontSize = 13.sp,
                                    color = MedTextPrimaryDark
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isWeak) MedCoral.copy(alpha = 0.2f) else MedMint.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (isWeak) "$pct% (يحتاج مراجعة)" else "$pct% (متقن)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWeak) MedCoral else MedMint,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Action Buttons
        item {
            // Remedial Flashcard Generator for wrong questions
            val wrongQuestions = questions.filterIndexed { index, q -> userAnswers[index] != q.correctIndex }
            if (wrongQuestions.isNotEmpty()) {
                Button(
                    onClick = {
                        scope.launch {
                            var added = 0
                            wrongQuestions.forEach { wq ->
                                val res = repository.addFlashcard(
                                    front = wq.questionAr,
                                    back = wq.options[wq.correctIndex],
                                    explanation = wq.explanationAr
                                )
                                if (res) added++
                            }
                            Toast.makeText(context, "تمت إضافة $added بطاقة مراجعة لمعالجة نقاط الضعف!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedCoral.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MedCoral)
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = null, tint = MedCoral)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إضافة الأسئلة الخاطئة (${wrongQuestions.size}) إلى بطاقات المراجعة",
                        color = MedCoral,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MedBorderDark)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = MedTextPrimaryDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("إعادة الاختبار", color = MedTextPrimaryDark)
                }

                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                ) {
                    Text("العودة للمحاضرة", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
