package com.example.ui.explainer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.data.model.*
import com.example.data.preference.UserPreferencesManager
import android.content.Intent
import com.example.core.AiPrompts
import com.example.core.StudyNoteFormatter
import com.example.core.StudySection
import com.example.data.remote.MedicalAiResult
import com.example.data.remote.MedicalAiService
import com.example.data.repository.MedStudyRepository
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AiStatusBanner
import com.example.ui.components.MedicalDisclaimer
import com.example.ui.components.toAiConfig
import com.example.ui.diagram.MedicalDiagramCard
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplainerScreen(
    initialTopic: String = "",
    sourceDoc: String? = null,
    sourcePage: Int? = null,
    sourceTimestamp: Long? = null,
    repository: MedStudyRepository,
    aiService: MedicalAiService,
    preferencesManager: UserPreferencesManager,
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputTopic by remember { mutableStateOf(initialTopic) }
    var selectedMode by remember { mutableStateOf(ExplanationMode.FIRST_YEAR_MED) }
    var isGenerating by remember { mutableStateOf(false) }
    var aiResult by remember { mutableStateOf<MedicalAiResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaved by remember { mutableStateOf(false) }
    // Quiz interactive state: map of question index to selected option index
    val selectedQuizAnswers = remember { mutableStateMapOf<Int, Int>() }

    // One place that talks to the service, used by the auto-run and by the button.
    suspend fun generate(topic: String) {
        val settings = preferencesManager.userSettingsFlow.first()
        isGenerating = true
        isSaved = false
        errorMessage = null
        selectedQuizAnswers.clear()
        val res = aiService.explainMedicalConcept(
            textOrTopic = topic,
            mode = selectedMode,
            explanationLanguage = settings.explanationLanguage,
            sourceDoc = sourceDoc,
            sourcePage = sourcePage,
            config = settings.toAiConfig()
        )
        isGenerating = false
        aiResult = res.getOrNull()
        errorMessage = res.exceptionOrNull()?.message
    }

    var loadingSection by remember { mutableStateOf<StudySection?>(null) }

    // Diagram / quiz / flashcards are generated only when asked for: small request, cached, saves quota.
    suspend fun expand(section: StudySection) {
        val current = aiResult ?: return
        val settings = preferencesManager.userSettingsFlow.first()
        loadingSection = section
        errorMessage = null
        val res = aiService.expandSection(current, section, settings.explanationLanguage, settings.toAiConfig())
        loadingSection = null
        res.getOrNull()?.let {
            aiResult = it
            isSaved = false
            if (section == StudySection.QUIZ) selectedQuizAnswers.clear()
        }
        res.exceptionOrNull()?.let { errorMessage = it.message }
    }

    // Auto-explain if initial topic is passed
    LaunchedEffect(initialTopic) {
        if (initialTopic.isNotBlank()) generate(initialTopic)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "المساعد الطبي الذكي (AI Explainer)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = "شرح علمي مفاهيمي متكامل مع المخططات والامتحانات",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedMint
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Source Reference Banner
            if (!sourceDoc.isNullOrBlank() || sourceTimestamp != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MedCardDark),
                    border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Source", tint = MedMint, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المصدر: ${sourceDoc ?: "فيديو المحاضرة"} ${if (sourcePage != null) "• ص $sourcePage" else ""} ${if (sourceTimestamp != null) "• $sourceTimestamp ثانية" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedMint
                        )
                    }
                }
            }

            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "المفهوم أو الفقرة الطبية المراد فهمها:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MedTextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = inputTopic,
                        onValueChange = { inputTopic = it },
                        placeholder = {
                            Text(
                                "اكتب أو الصق موضوعاً طبياً (مثال: Action potential in neurons, Acute inflammation cascade, Mitosis vs Meiosis...)",
                                color = MedTextMutedDark,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 130.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MedMint,
                            unfocusedBorderColor = MedBorderDark,
                            focusedTextColor = MedTextPrimaryDark,
                            unfocusedTextColor = MedTextPrimaryDark,
                            focusedContainerColor = MedSurfaceDark,
                            unfocusedContainerColor = MedSurfaceDark
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Modes Chips Scrollable Row
                    Text(
                        text = "نمط الشرح الأكاديمي:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MedLavender
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExplanationMode.values().forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode.titleAr, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MedMintContainer,
                                    selectedLabelColor = MedMint,
                                    containerColor = MedSurfaceDark,
                                    labelColor = MedTextSecondaryDark
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Generate Button
                    Button(
                        onClick = {
                            if (inputTopic.isBlank()) return@Button
                            if (AiPrompts.wasTruncated(inputTopic)) {
                                Toast.makeText(context, "النص طويل: سيُرسل أول ${AiPrompts.MAX_INPUT_CHARS} حرف فقط", Toast.LENGTH_LONG).show()
                            }
                            scope.launch { generate(inputTopic) }
                        },
                        enabled = inputTopic.isNotBlank() && !isGenerating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color(0xFF00382B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(color = Color(0xFF00382B), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري استخلاص الآليات والمخططات الطبية...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Explain")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("توليد الشرح الطبي والمخطط والأسئلة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let {
                AiErrorCard(it, onOpenSettings)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // AI Result Content
            aiResult?.let { result ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AiStatusBanner(result, onOpenSettings)
                    MedicalDisclaimer()

                    // 1. Header Card with Save & Copy
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MedCardDark),
                        border = BorderStroke(1.dp, MedMint.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(containerColor = MedMintContainer) {
                                    Text(selectedMode.titleAr, color = MedMint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Medical Explanation", "${result.title}\n\n${result.detailedExplanation}")
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "تم نسخ الشرح للحافظة", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MedTextSecondaryDark, modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                repository.saveAIResult(
                                                    result = result,
                                                    sourceDoc = sourceDoc,
                                                    sourcePage = sourcePage,
                                                    sourceTimestamp = sourceTimestamp
                                                )
                                                isSaved = true
                                                Toast.makeText(context, "تم حفظ الشرح والمخطط والبطاقات في مكتبتك!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isSaved) Icons.Default.BookmarkAdded else Icons.Default.BookmarkAdd,
                                            contentDescription = "Save",
                                            tint = if (isSaved) MedMint else MedLavender,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = result.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MedTextPrimaryDark
                            )

                            if (result.translation.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = result.translation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MedMint
                                )
                            }
                        }
                    }

                    // 2. Simple Definition & Teaching Analogy
                    if (result.simpleExplanation.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedLavender.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lightbulb, contentDescription = "Analogy", tint = MedLavender, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("المفهوم المبسط وتشبيه الفهم (Analogy):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedLavender)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result.simpleExplanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MedTextPrimaryDark,
                                    lineHeight = 24.sp
                                )
                            }
                        }
                    }

                    // 3. Detailed Scientific Explanation
                    if (result.detailedExplanation.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MenuBook, contentDescription = "Detailed", tint = MedMint, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("الشرح العلمي المنهجي (Scientific Context):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedMint)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = result.detailedExplanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MedTextPrimaryDark,
                                    lineHeight = 25.sp
                                )
                            }
                        }
                    }

                    // 4. Mechanism Steps (Pathophysiology / Normal Mechanism)
                    if (result.mechanismSteps.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedCyan.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.LinearScale, contentDescription = "Mechanism", tint = MedCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("خطوات الآلية الفسيولوجية (Step-by-Step Mechanism):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedCyan)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                result.mechanismSteps.forEachIndexed { i, step ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(MedCyanContainer, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("${i + 1}", color = MedCyan, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(step, style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // 5. High-Yield Examination Points (Amber Badge)
                    if (result.keyPoints.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedAmberContainer.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MedAmber.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Grade, contentDescription = "Exam", tint = MedAmber, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("نقاط الامتحان عالية الأهمية (High-Yield Exam Pearls):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedAmber)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                result.keyPoints.forEach { point ->
                                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Text("★ ", color = MedAmber, fontWeight = FontWeight.Bold)
                                        Text(point, style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                                    }
                                }
                            }
                        }
                    }

                    // 6. Common Misconceptions (Coral Badge)
                    if (result.misconceptions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCoralContainer.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MedCoral.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.WarningAmber, contentDescription = "Misconceptions", tint = MedCoral, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("أخطاء شائعة يجب الحذر منها (Common Pitfalls):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedCoral)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                result.misconceptions.forEach { misc ->
                                    Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                        Text("⚠ ", color = MedCoral)
                                        Text(misc, style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                                    }
                                }
                            }
                        }
                    }

                    // 6a. Share a clean, formatted study note (Markdown)
                    TextButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, result.title)
                                putExtra(Intent.EXTRA_TEXT, StudyNoteFormatter.toMarkdown(result))
                            }
                            context.startActivity(Intent.createChooser(send, "مشاركة الملاحظة"))
                        }
                    ) {
                        Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مشاركة كملاحظة منسّقة")
                    }

                    // 6b. Parts that are generated on demand
                    CompleteGuideCard(
                        result = result,
                        loading = loadingSection,
                        onGenerate = { section -> scope.launch { expand(section) } }
                    )

                    // 7. Interactive Colored Diagram (Core Feature!)
                    if (result.diagramNodes.isNotEmpty() || result.diagramCode.isNotBlank()) MedicalDiagramCard(
                        title = "المخطط المفاهيمي: ${result.title}",
                        mermaidCode = result.diagramCode,
                        nodes = result.diagramNodes,
                        edges = result.diagramEdges,
                        onSaveDiagram = {
                            scope.launch {
                                repository.saveAIResult(result = result)
                                Toast.makeText(context, "تم حفظ المخطط في مكتبتك!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // 8. Comparison Table (if available)
                    result.comparisonTable?.let { table ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedBorderDark)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = table.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MedLavender
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, MedBorderDark, RoundedCornerShape(8.dp))
                                        .background(MedSurfaceDark)
                                ) {
                                    // Table Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MedSurfaceVariantDark)
                                            .padding(8.dp)
                                    ) {
                                        table.columns.forEach { col ->
                                            Text(col, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedMint, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    table.rows.forEach { row ->
                                        HorizontalDivider(color = MedBorderDark)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            row.forEach { cell ->
                                                Text(cell, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark, modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 9. Interactive Self-Assessment Quiz (Section 20)
                    if (result.questions.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedGreen.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Quiz, contentDescription = "Quiz", tint = MedGreen, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("اختبر فهمك للموضوع (Self-Assessment Quiz):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedGreen)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                result.questions.forEachIndexed { qIdx, q ->
                                    val selectedOpt = selectedQuizAnswers[qIdx]
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = MedSurfaceDark)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "${qIdx + 1}. ${q.question}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MedTextPrimaryDark
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            q.options.forEachIndexed { optIdx, opt ->
                                                val isSelected = selectedOpt == optIdx
                                                val isCorrect = optIdx == q.correctIndex
                                                val optionColor = when {
                                                    selectedOpt == null -> MedTextPrimaryDark
                                                    isSelected && isCorrect -> MedGreen
                                                    isSelected && !isCorrect -> MedCoral
                                                    !isSelected && isCorrect -> MedGreen
                                                    else -> MedTextSecondaryDark
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isSelected) {
                                                                if (isCorrect) MedGreenContainer else MedCoralContainer
                                                            } else MedSurfaceVariantDark
                                                        )
                                                        .clickable { selectedQuizAnswers[qIdx] = optIdx }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { selectedQuizAnswers[qIdx] = optIdx },
                                                        colors = RadioButtonDefaults.colors(selectedColor = optionColor)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(opt, style = MaterialTheme.typography.bodySmall, color = optionColor)
                                                }
                                            }

                                            // Feedback & Explanation
                                            if (selectedOpt != null) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = if (selectedOpt == q.correctIndex) "✓ إجابة صحيحة: ${q.explanation}" else "✗ إجابة خاطئة: ${q.explanation}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (selectedOpt == q.correctIndex) MedGreen else MedCoral
                                                )
                                                // A missed question is exactly what spaced repetition is for.
                                                if (selectedOpt != q.correctIndex && q.correctIndex in q.options.indices) {
                                                    TextButton(
                                                        onClick = {
                                                            scope.launch {
                                                                val added = repository.addFlashcard(q.question, q.options[q.correctIndex], q.explanation)
                                                                Toast.makeText(
                                                                    context,
                                                                    if (added) "أُضيف السؤال لبطاقات الحفظ" else "السؤال موجود مسبقاً في البطاقات",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    ) {
                                                        Text("أضف هذا السؤال لبطاقات الحفظ", color = MedMint, fontSize = 12.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 10. Flashcards Ready to Review / Save
                    if (result.flashcards.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Style, contentDescription = "Flashcards", tint = MedMint, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("بطاقات التكرار المتباعد (Flashcards):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedMint)
                                    }
                                    Badge(containerColor = MedMintContainer) {
                                        Text("${result.flashcards.size} بطاقة", color = MedMint, modifier = Modifier.padding(2.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                result.flashcards.forEach { fc ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MedSurfaceDark)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(fc.front, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MedMint)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(fc.back, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Offers the parts of the guide that were not generated yet (each one is a separate, small request). */
@Composable
private fun CompleteGuideCard(result: MedicalAiResult, loading: StudySection?, onGenerate: (StudySection) -> Unit) {
    if (result.isOfflineGenerated) return
    val missing = buildList {
        if (result.diagramNodes.isEmpty() && result.diagramCode.isBlank()) add(StudySection.DIAGRAM)
        if (result.questions.isEmpty()) add(StudySection.QUIZ)
        if (result.flashcards.isEmpty()) add(StudySection.FLASHCARDS)
    }
    if (missing.isEmpty()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MedCardDark),
        border = BorderStroke(1.dp, MedBorderDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("أكمل الدرس عند الحاجة:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
            Text(
                "كل جزء طلب صغير مستقل، فلا تستهلك من حصتك إلا ما تحتاجه.",
                style = MaterialTheme.typography.labelSmall,
                color = MedTextSecondaryDark
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                missing.forEach { section ->
                    val (label, icon) = when (section) {
                        StudySection.DIAGRAM -> "المخطط" to Icons.Default.AccountTree
                        StudySection.QUIZ -> "الأسئلة" to Icons.Default.Quiz
                        StudySection.FLASHCARDS -> "البطاقات" to Icons.Default.Style
                    }
                    FilledTonalButton(
                        onClick = { onGenerate(section) },
                        enabled = loading == null,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        if (loading == section) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
