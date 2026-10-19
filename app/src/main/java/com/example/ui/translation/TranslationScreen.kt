package com.example.ui.translation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.example.core.AiPrompts
import com.example.data.remote.MedicalAiResult
import com.example.ui.components.AiErrorCard
import com.example.ui.components.AiStatusBanner
import com.example.ui.components.MedicalDisclaimer
import com.example.ui.components.toAiConfig
import com.example.data.remote.MedicalAiService
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    initialText: String = "",
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

    var inputText by remember { mutableStateOf(initialText) }
    var direction by remember { mutableStateOf(TranslationDirection.EN_TO_AR) }
    var selectedSpecialty by remember { mutableStateOf(MedicalSpecialty.GENERAL) }
    var isTranslating by remember { mutableStateOf(false) }
    var translationResult by remember { mutableStateOf<MedicalAiResult?>(null) }
    var isSaved by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun translate(text: String) {
        val settings = preferencesManager.userSettingsFlow.first()
        isTranslating = true
        isSaved = false
        errorMessage = null
        val res = aiService.translateMedicalText(
            text = text,
            direction = direction,
            specialty = selectedSpecialty,
            config = settings.toAiConfig()
        )
        isTranslating = false
        translationResult = res.getOrNull()
        errorMessage = res.exceptionOrNull()?.message
    }

    // If initial text is provided, auto-translate
    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) translate(initialText)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "المترجم الطبي الأكاديمي",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = "ترجمة اصطلاحية مع جدول المصطلحات والجذور اللاتينية",
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
            // Source Citation Banner if coming from PDF/Video
            if (!sourceDoc.isNullOrBlank() || sourceTimestamp != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MedCardDark),
                    border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Source", tint = MedMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المصدر: ${sourceDoc ?: "مرئي المحاضرة"} ${if (sourcePage != null) "• صفحة $sourcePage" else ""} ${if (sourceTimestamp != null) "• التوقيت $sourceTimestamp ث" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedMint
                        )
                    }
                }
            }

            // Direction & Specialty Pickers Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction Toggle
                Button(
                    onClick = {
                        direction = if (direction == TranslationDirection.EN_TO_AR)
                            TranslationDirection.AR_TO_EN
                        else TranslationDirection.EN_TO_AR
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MedSurfaceDark, contentColor = MedMint),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MedBorderDark)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(direction.labelAr, style = MaterialTheme.typography.labelMedium)
                }

                // Specialty Dropdown Menu
                var specialtyExpanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { specialtyExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MedBorderDark),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MedLavender)
                    ) {
                        Text(selectedSpecialty.labelAr, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select")
                    }
                    DropdownMenu(
                        expanded = specialtyExpanded,
                        onDismissRequest = { specialtyExpanded = false },
                        modifier = Modifier.background(MedSurfaceDark)
                    ) {
                        MedicalSpecialty.values().forEach { spec ->
                            DropdownMenuItem(
                                text = { Text(spec.labelAr, color = MedTextPrimaryDark) },
                                onClick = {
                                    selectedSpecialty = spec
                                    specialtyExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Input Text Field
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (direction == TranslationDirection.EN_TO_AR) "النص الطبي بالإنجليزية:" else "النص الطبي بالعربية:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextSecondaryDark
                        )
                        Row {
                            // Paste Button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = clipboard.primaryClip
                                    if (clip != null && clip.itemCount > 0) {
                                        inputText = clip.getItemAt(0).text.toString()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = MedMint, modifier = Modifier.size(16.dp))
                            }
                            // Clear Button
                            if (inputText.isNotBlank()) {
                                IconButton(onClick = { inputText = "" }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MedTextSecondaryDark, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    TextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "الصق مصطلحاً أو فقرة طبية (مثال: Acetylcholine release at neuromuscular junction...)",
                                color = MedTextMutedDark,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp, max = 150.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = MedTextPrimaryDark,
                            unfocusedTextColor = MedTextPrimaryDark,
                            cursorColor = MedMint,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Translate Action Button
                    Button(
                        onClick = {
                            if (inputText.isBlank()) return@Button
                            if (AiPrompts.wasTruncated(inputText)) {
                                Toast.makeText(context, "النص طويل: سيُترجم أول ${AiPrompts.MAX_INPUT_CHARS} حرف فقط", Toast.LENGTH_LONG).show()
                            }
                            scope.launch { translate(inputText) }
                        },
                        enabled = inputText.isNotBlank() && !isTranslating,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color(0xFF00382B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTranslating) {
                            CircularProgressIndicator(color = Color(0xFF00382B), modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("جاري استخلاص المعنى الطبي الموحد...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(Icons.Default.Translate, contentDescription = "Translate")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ترجمة طبية دقيقة وتفكيك المصطلحات", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            errorMessage?.let {
                AiErrorCard(it, onOpenSettings)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Translation Result Display
            translationResult?.let { result ->
                AiStatusBanner(result, onOpenSettings)
                if (result.isOfflineGenerated) Spacer(modifier = Modifier.height(10.dp))
                MedicalDisclaimer()
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MedCardDark),
                    border = BorderStroke(1.dp, MedMint.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Title & Actions Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Badge(containerColor = MedMintContainer) {
                                Text(if (result.isOfflineGenerated) "ترجمة محلية جاهزة" else "ترجمة طبية بالذكاء الاصطناعي", color = MedMint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                            }

                            Row {
                                // Copy
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Medical Translation", "${result.translation}\n\n${result.simpleExplanation}")
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "تم نسخ الترجمة للحافظة", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MedTextSecondaryDark, modifier = Modifier.size(18.dp))
                                }

                                // Save to library
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
                                            Toast.makeText(context, "تم حفظ الترجمة في مكتبتك الطبية!", Toast.LENGTH_SHORT).show()
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Medical Translation Text
                        Text(
                            text = result.translation,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MedTextPrimaryDark,
                            lineHeight = 26.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Contextual / Simple Explanation
                        if (result.simpleExplanation.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "السياق الفسيولوجي والسريري:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MedLavender
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = result.simpleExplanation,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MedTextSecondaryDark
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Medical Terminology Table (Mandatory Section C)
                        if (result.keyTerms.isNotEmpty()) {
                            Text(
                                text = "جدول تفكيك المصطلحات الطبية (Terminology Table):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MedMint
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, MedBorderDark, RoundedCornerShape(10.dp))
                                    .background(MedSurfaceDark)
                            ) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MedSurfaceVariantDark)
                                        .padding(vertical = 8.dp, horizontal = 10.dp)
                                ) {
                                    Text("المصطلح (English)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedCyan, modifier = Modifier.weight(1.2f))
                                    Text("المعادل الطبي", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedMint, modifier = Modifier.weight(1.2f))
                                    Text("الدلالة السريرية", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedLavender, modifier = Modifier.weight(1.6f))
                                }

                                HorizontalDivider(color = MedBorderDark)

                                result.keyTerms.forEachIndexed { idx, term ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(term.term, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark, modifier = Modifier.weight(1.2f))
                                        Text(term.arabicEquivalent, style = MaterialTheme.typography.bodySmall, color = MedMint, modifier = Modifier.weight(1.2f))
                                        Text(term.definition, style = MaterialTheme.typography.bodySmall, color = MedTextSecondaryDark, modifier = Modifier.weight(1.6f))
                                    }
                                    if (idx < result.keyTerms.size - 1) {
                                        HorizontalDivider(color = MedBorderDark.copy(alpha = 0.5f))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // Translation Notes & Greek/Latin roots (Section D)
                        if (result.translationNotes.isNotBlank()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MedAmberContainer.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MedAmber.copy(alpha = 0.4f))
                            ) {
                                Row(modifier = Modifier.padding(12.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = "Notes", tint = MedAmber, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text("ملاحظات الترجمة والأصول اللاتينية:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedAmber)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(result.translationNotes, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Alternative Translation (Section E)
                        if (result.alternativeTranslation.isNotBlank()) {
                            Text(
                                text = "صياغة بديلة: ${result.alternativeTranslation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MedTextSecondaryDark
                            )
                        }
                    }
                }
            }
        }
    }
}
