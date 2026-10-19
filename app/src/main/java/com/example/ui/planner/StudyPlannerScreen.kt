package com.example.ui.planner

import android.widget.Toast
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.core.DateUtils
import com.example.core.ReviewGrade
import com.example.core.Sm2Scheduler
import com.example.core.SrsState
import com.example.data.local.FlashcardEntity
import com.example.data.local.StudyTaskEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyPlannerScreen(
    repository: MedStudyRepository,
    onNavigateToCourse: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Study Tasks & Goals, 1: Flashcards Spaced Repetition
    var showAddTaskDialog by remember { mutableStateOf(false) }

    val tasks by repository.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val flashcards by repository.allFlashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    val difficultLectures by repository.difficultLectures.collectAsStateWithLifecycle(initialValue = emptyList())

    val dueCards by repository.dueFlashcards.collectAsStateWithLifecycle(initialValue = emptyList())
    var isCardFlipped by remember { mutableStateOf(false) }
    var showAddCardDialog by remember { mutableStateOf(false) }

    fun exportAnki() {
        scope.launch {
            val (text, count) = repository.exportFlashcardsForAnki()
            if (count == 0) {
                Toast.makeText(context, "لا توجد بطاقات للتصدير.", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val uri = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "exports").apply { mkdirs() }
                val file = File(dir, "medstudy-flashcards.txt")
                file.writeText(text, Charsets.UTF_8)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "بطاقات MedStudy لـ Anki")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "تصدير إلى Anki ($count بطاقة)"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "مخطط الدراسة والتكرار المتباعد",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = "إدارة المهام اليومية وجلسات الحفظ الذكية (Anki Mode)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedMint
                        )
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { showAddTaskDialog = true }) {
                            Icon(Icons.Default.AddTask, contentDescription = "New Task", tint = MedMint)
                        }
                    } else if (selectedTab == 1) {
                        IconButton(onClick = { showAddCardDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "بطاقة جديدة", tint = MedMint)
                        }
                        IconButton(onClick = { exportAnki() }) {
                            Icon(Icons.Default.IosShare, contentDescription = "تصدير إلى Anki", tint = MedLavender)
                        }
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
            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MedSurfaceDark,
                contentColor = MedMint
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("مهام اليوم والتقدم (${tasks.count { !it.isCompleted }})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        isCardFlipped = false
                    },
                    text = { Text("بطاقات اليوم (${dueCards.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("تركيز", fontWeight = FontWeight.Bold) }
                )
            }

            if (selectedTab == 0) {
                // Tasks & Study Plan View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    // Study Summary Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "ملخص الخطة الدراسية لهذا الأسبوع",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MedMint
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceAround
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${tasks.count { it.isCompleted }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MedGreen)
                                        Text("مهام منجزة", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${tasks.count { !it.isCompleted }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MedAmber)
                                        Text("مهام متبقية", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${difficultLectures.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MedCoral)
                                        Text("موضوعات صعبة", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                                    }
                                }
                            }
                        }
                    }

                    // Difficult Topics Alert
                    if (difficultLectures.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MedCoralContainer.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, MedCoral.copy(alpha = 0.5f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Flag, contentDescription = "Flag", tint = MedCoral, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("موضوعات حددتها كصعبة تحتاج مراجعة:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedCoral)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    difficultLectures.forEach { lec ->
                                        Text("• ${lec.titleAr} (${lec.title})", style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark)
                                    }
                                }
                            }
                        }
                    }

                    // Task List Items
                    item {
                        Text(
                            text = "قائمة المهام المجدولة:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                    }

                    items(tasks, key = { it.id }) { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedBorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { checked ->
                                        scope.launch {
                                            repository.updateTask(task.copy(isCompleted = checked, completedAt = if (checked) System.currentTimeMillis() else null))
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MedGreen,
                                        uncheckedColor = MedTextSecondaryDark
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = task.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (task.isCompleted) MedTextSecondaryDark else MedTextPrimaryDark,
                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (task.description.isNotBlank()) {
                                        Text(
                                            text = task.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MedTextSecondaryDark,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Badge(containerColor = MedSurfaceDark) {
                                    Text("${task.estimatedMinutes} د", color = MedMint, modifier = Modifier.padding(2.dp))
                                }

                                IconButton(
                                    onClick = {
                                        scope.launch { repository.deleteTask(task) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MedCoral, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                FlashcardReview(
                    dueCards = dueCards,
                    flipped = isCardFlipped,
                    onFlip = { isCardFlipped = !isCardFlipped },
                    onAnswer = { card, grade ->
                        isCardFlipped = false
                        scope.launch { repository.answerFlashcard(card, grade.value) }
                    },
                    onDelete = { card -> isCardFlipped = false; scope.launch { repository.deleteFlashcard(card) } }
                )
            } else {
                FocusTimer(onSessionFinished = { minutes ->
                    scope.launch {
                        repository.recordStudySession(minutes)
                        Toast.makeText(context, "تم تسجيل جلسة $minutes دقيقة", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            if (showAddCardDialog) {
                var front by remember { mutableStateOf("") }
                var back by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAddCardDialog = false },
                    title = { Text("بطاقة جديدة", color = MedMint, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = front, onValueChange = { front = it }, label = { Text("السؤال / المصطلح") })
                            OutlinedTextField(value = back, onValueChange = { back = it }, label = { Text("الإجابة") })
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            if (front.isNotBlank() && back.isNotBlank()) {
                                scope.launch {
                                    val added = repository.addFlashcard(front.trim(), back.trim())
                                    Toast.makeText(context, if (added) "تمت إضافة البطاقة" else "هذه البطاقة موجودة مسبقاً", Toast.LENGTH_SHORT).show()
                                }
                                showAddCardDialog = false
                            }
                        }) { Text("إضافة") }
                    },
                    dismissButton = { TextButton(onClick = { showAddCardDialog = false }) { Text("إلغاء") } },
                    containerColor = MedSurfaceDark
                )
            }

            // Dialog: Add Task
            if (showAddTaskDialog) {
                var taskTitle by remember { mutableStateOf("") }
                var taskDesc by remember { mutableStateOf("") }
                var taskEst by remember { mutableStateOf("30") }
                var taskTomorrow by remember { mutableStateOf(false) }

                AlertDialog(
                    onDismissRequest = { showAddTaskDialog = false },
                    title = { Text("إضافة مهمة دراسية جديدة", color = MedMint, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = taskTitle,
                                onValueChange = { taskTitle = it },
                                label = { Text("عنوان المهمة (مثال: مراجعة الدورة الدموية)") }
                            )
                            OutlinedTextField(
                                value = taskDesc,
                                onValueChange = { taskDesc = it },
                                label = { Text("تفاصيل وملاحظات المهمة") }
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = !taskTomorrow, onClick = { taskTomorrow = false }, label = { Text("اليوم") })
                                FilterChip(selected = taskTomorrow, onClick = { taskTomorrow = true }, label = { Text("غداً") })
                            }
                            OutlinedTextField(
                                value = taskEst,
                                onValueChange = { taskEst = it },
                                label = { Text("الوقت التقديري (بالدقائق)") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (taskTitle.isNotBlank()) {
                                    scope.launch {
                                        repository.insertTask(
                                            StudyTaskEntity(
                                                title = taskTitle,
                                                description = taskDesc,
                                                targetDate = DateUtils.isoDate(System.currentTimeMillis(), if (taskTomorrow) 1 else 0),
                                                estimatedMinutes = (taskEst.toIntOrNull() ?: 30).coerceIn(5, 600)
                                            )
                                        )
                                        showAddTaskDialog = false
                                        Toast.makeText(context, "تمت إضافة المهمة", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color(0xFF00382B))
                        ) {
                            Text("حفظ")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTaskDialog = false }) { Text("إلغاء", color = MedTextSecondaryDark) }
                    },
                    containerColor = MedSurfaceDark
                )
            }
        }
    }
}

@Composable
private fun FlashcardReview(
    dueCards: List<FlashcardEntity>,
    flipped: Boolean,
    onFlip: () -> Unit,
    onAnswer: (FlashcardEntity, ReviewGrade) -> Unit,
    onDelete: (FlashcardEntity) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        if (dueCards.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = MedGreen, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("أحسنت! لا توجد بطاقات مستحقة الآن", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "تُضاف بطاقات جديدة تلقائياً عند حفظ الشروح، أو أضف بطاقتك بزر +.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MedTextSecondaryDark,
                    textAlign = TextAlign.Center
                )
            }
            return@Box
        }
        val card = dueCards.first()
        val state = SrsState(card.intervalDays, card.repetitionCount, card.easeFactor, card.lapses, card.dueDate)
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("المتبقي اليوم: ${dueCards.size}", style = MaterialTheme.typography.labelMedium, color = MedMint)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth().height(280.dp).clickable { onFlip() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = if (flipped) MedCardDark else MedSurfaceDark),
                border = BorderStroke(1.5.dp, if (flipped) MedLavender else MedMint)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Badge(containerColor = if (flipped) MedLavenderContainer else MedMintContainer) {
                            Text(
                                text = if (flipped) "الإجابة" else "السؤال (انقر للكشف)",
                                color = if (flipped) MedLavender else MedMint,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (flipped) card.backText else card.frontText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark,
                            textAlign = TextAlign.Center
                        )
                        if (flipped && card.explanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(card.explanation, style = MaterialTheme.typography.bodyMedium, color = MedMint, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (flipped) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradeButton("أعد", ReviewGrade.AGAIN, state, MedCoralContainer, MedCoral, Modifier.weight(1f), onAnswer, card)
                    GradeButton("صعب", ReviewGrade.HARD, state, MedAmberContainer, MedAmber, Modifier.weight(1f), onAnswer, card)
                    GradeButton("جيد", ReviewGrade.GOOD, state, MedMintContainer, MedMint, Modifier.weight(1f), onAnswer, card)
                    GradeButton("سهل", ReviewGrade.EASY, state, MedGreenContainer, MedGreen, Modifier.weight(1f), onAnswer, card)
                }
            } else {
                TextButton(onClick = { onDelete(card) }) {
                    Text("حذف هذه البطاقة", color = MedTextSecondaryDark, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: ReviewGrade,
    state: SrsState,
    container: Color,
    content: Color,
    modifier: Modifier,
    onAnswer: (FlashcardEntity, ReviewGrade) -> Unit,
    card: FlashcardEntity
) {
    val days = Sm2Scheduler.previewDays(state, grade)
    val hint = if (grade == ReviewGrade.AGAIN) "10 د" else if (days <= 1) "يوم" else "$days يوم"
    Button(
        onClick = { onAnswer(card, grade) },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }
    }
}

/** Pomodoro timer. Only finished focus time is logged, so the streak and weekly minutes stay honest. */
@Composable
private fun FocusTimer(onSessionFinished: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    var remaining by rememberSaveable { mutableIntStateOf(25 * 60) }
    var running by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(running) {
        while (running && remaining > 0) {
            delay(1000)
            remaining--
        }
        if (running && remaining <= 0) {
            running = false
            onSessionFinished(minutes)
            remaining = minutes * 60
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("جلسة تركيز", style = MaterialTheme.typography.titleMedium, color = MedMint, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "%02d:%02d".format(remaining / 60, remaining % 60),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MedTextPrimaryDark
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 25, 45, 60).forEach { m ->
                FilterChip(
                    selected = minutes == m,
                    enabled = !running,
                    onClick = { minutes = m; remaining = m * 60 },
                    label = { Text("$m د") }
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { running = !running },
                colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color(0xFF00382B))
            ) { Text(if (running) "إيقاف مؤقت" else "ابدأ") }
            OutlinedButton(onClick = { running = false; remaining = minutes * 60 }) { Text("إعادة") }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "يعمل المؤقت أثناء بقاء هذه الشاشة مفتوحة. تُسجَّل الجلسة عند اكتمالها فقط.",
            style = MaterialTheme.typography.labelSmall,
            color = MedTextSecondaryDark,
            textAlign = TextAlign.Center
        )
    }
}
