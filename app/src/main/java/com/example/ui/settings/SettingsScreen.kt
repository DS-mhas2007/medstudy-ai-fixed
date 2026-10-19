package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preference.UserPreferencesManager
import com.example.data.preference.DEFAULT_AI_MODEL
import com.example.data.remote.AiRequestConfig
import com.example.data.remote.MedicalAiService
import com.example.ui.theme.*
import kotlinx.coroutines.launch

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.viewmodel.BackupSyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: UserPreferencesManager,
    aiService: MedicalAiService,
    backupViewModel: BackupSyncViewModel? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by preferencesManager.userSettingsFlow.collectAsStateWithLifecycle(initialValue = null)

    var currentApiKey by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf(DEFAULT_AI_MODEL) }
    var consent by remember { mutableStateOf(false) }
    var dailyGoal by remember { mutableStateOf(20f) }
    var requestsToday by remember { mutableStateOf(0) }
    var cachedAnswers by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        requestsToday = aiService.requestsToday()
        cachedAnswers = aiService.cacheSize()
    }
    var appLanguage by remember { mutableStateOf("ar") }
    var explanationLanguage by remember { mutableStateOf("ar") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var isTestingConnection by remember { mutableStateOf(false) }
    var testResultText by remember { mutableStateOf<String?>(null) }
    var testSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(settings) {
        settings?.let {
            currentApiKey = it.apiKey
            selectedModel = it.aiModel
            appLanguage = it.appLanguage
            explanationLanguage = it.explanationLanguage
            consent = it.hasAcceptedAiConsent
            dailyGoal = it.dailyCardGoal.toFloat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("الإعدادات وتكوين الذكاء الاصطناعي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                        Text("مفتاح API ومزود الذكاء الاصطناعي والخصوصية", style = MaterialTheme.typography.labelSmall, color = MedMint)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MedTextPrimaryDark)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Provider & Key Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VpnKey, contentDescription = "Key", tint = MedMint, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إعدادات مفتاح API (Google Gemini):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedMint)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "يُحفظ المفتاح مشفراً على جهازك (Android Keystore) ولا يُرسل إلا إلى واجهة Gemini الرسمية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MedTextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = currentApiKey,
                        onValueChange = { currentApiKey = it },
                        placeholder = { Text("أدخل مفتاح Gemini API هنا...") },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = MedTextSecondaryDark
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model name: editable, because Google renames and retires models regularly.
                    Text("اسم النموذج (AI Model):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedLavender)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = selectedModel,
                        onValueChange = { selectedModel = it.trim() },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
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
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(DEFAULT_AI_MODEL, "gemini-3.1-pro-preview").forEach { model ->
                            FilterChip(
                                selected = selectedModel == model,
                                onClick = { selectedModel = model },
                                label = { Text(model, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MedMintContainer,
                                    selectedLabelColor = MedMint,
                                    containerColor = MedSurfaceDark,
                                    labelColor = MedTextSecondaryDark
                                )
                            )
                        }
                    }
                    Text(
                        "إذا ظهر خطأ «النموذج غير موجود»، اكتب اسماً حالياً من Google AI Studio.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MedTextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Save Key Button
                        Button(
                            onClick = {
                                scope.launch {
                                    preferencesManager.updateApiKey(currentApiKey)
                                    preferencesManager.updateAiModel(selectedModel)
                                    Toast.makeText(context, "تم حفظ إعدادات API بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color(0xFF00382B))
                        ) {
                            Text("حفظ المفتاح")
                        }

                        // Test Connection Button
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    isTestingConnection = true
                                    testResultText = null
                                    val res = aiService.testConnection(AiRequestConfig(currentApiKey, selectedModel, consent))
                                    isTestingConnection = false
                                    if (res.isSuccess) {
                                        testSuccess = true
                                        testResultText = res.getOrNull()
                                    } else {
                                        testSuccess = false
                                        testResultText = res.exceptionOrNull()?.message ?: "فشل الاتصال"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MedLavender),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MedLavender)
                        ) {
                            if (isTestingConnection) {
                                CircularProgressIndicator(color = MedLavender, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("اختبار الاتصال")
                            }
                        }
                    }

                    // Test Result Feedback Banner
                    testResultText?.let { text ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (testSuccess) MedGreenContainer else MedCoralContainer
                            )
                        ) {
                            Text(
                                text = text,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testSuccess) MedGreen else MedCoral,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // AI consent
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, if (consent) MedMint.copy(alpha = 0.4f) else MedCoral.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("السماح بإرسال النص إلى Gemini", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                            Text(
                                "عند التفعيل يُرسل إلى Google النص الذي تطلب ترجمته أو شرحه فقط (حتى 6000 حرف)، وليس الملفات كاملة. ملاحظة: في الخطة المجانية قد تستخدم Google المحتوى لتحسين خدماتها، فلا ترسل بيانات مرضى أو معلومات شخصية.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MedTextSecondaryDark,
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = consent,
                            onCheckedChange = {
                                consent = it
                                scope.launch { preferencesManager.updateConsent(it) }
                            }
                        )
                    }
                }
            }

            // Daily goal
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("هدف المراجعة اليومي: ${dailyGoal.toInt()} بطاقة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedLavender)
                    Slider(
                        value = dailyGoal,
                        onValueChange = { dailyGoal = it },
                        onValueChangeFinished = { scope.launch { preferencesManager.updateDailyCardGoal(dailyGoal.toInt()) } },
                        valueRange = 5f..100f,
                        steps = 18
                    )
                }
            }

            // Usage and cache
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الاستهلاك والذاكرة المحلية", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedLavender)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("طلبات أُرسلت اليوم: $requestsToday", style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                    Text("أجوبة محفوظة: $cachedAnswers", style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "الطلب المتكرر لنفس النص يُجاب من الذاكرة بدون استهلاك. العداد يتصفّر عند منتصف الليل بتوقيت المحيط الهادئ، وحدودك الفعلية تراها في Google AI Studio. التطبيق يُبطئ الإرسال تلقائياً إذا تجاوزت 10 طلبات بالدقيقة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MedTextSecondaryDark,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                aiService.clearCache()
                                cachedAnswers = aiService.cacheSize()
                                Toast.makeText(context, "تم مسح الأجوبة المحفوظة", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = cachedAnswers > 0
                    ) { Text("مسح الأجوبة المحفوظة") }
                }
            }

            // Language & Localization Preferences Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedCardDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("اللغة والتوطين (Language & Localization):", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MedLavender)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("لغة واجهة التطبيق:", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = appLanguage == "ar",
                            onClick = {
                                appLanguage = "ar"
                                scope.launch { preferencesManager.updateAppLanguage("ar") }
                            },
                            label = { Text("العربية (افتراضي)") }
                        )
                        FilterChip(
                            selected = appLanguage == "en",
                            onClick = {
                                appLanguage = "en"
                                scope.launch { preferencesManager.updateAppLanguage("en") }
                            },
                            label = { Text("English") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text("لغة الشرح الطبي الافتراضية:", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = explanationLanguage == "ar",
                            onClick = {
                                explanationLanguage = "ar"
                                scope.launch { preferencesManager.updateExplanationLanguage("ar") }
                            },
                            label = { Text("العربية مع المصطلحات الإنجليزية") }
                        )
                        FilterChip(
                            selected = explanationLanguage == "en",
                            onClick = {
                                explanationLanguage = "en"
                                scope.launch { preferencesManager.updateExplanationLanguage("en") }
                            },
                            label = { Text("English Only") }
                        )
                    }
                }
            }

            // Google Drive Cloud Backup & Restore Section
            if (backupViewModel != null) {
                val backupState by backupViewModel.uiState.collectAsStateWithLifecycle()

                val createBackupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) {
                        backupViewModel.exportToDrive(uri)
                    }
                }

                val openBackupLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) {
                        backupViewModel.inspectBackup(uri)
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                    border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = "Cloud Backup", tint = MedMint, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "المزامنة والنسخ الاحتياطي (Google Drive Cloud Backup):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MedMint
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "احفظ مقرراتك وبطاقاتك السريرية وملاحظاتك ونتائج اختباراتك على Google Drive لنقلها بين أجهزتك واستعادتها بأمان تام.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MedTextSecondaryDark,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val last = backupState.lastBackup
                        if (last != null) {
                            Surface(
                                color = MedSurfaceVariantDark,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MedLavender, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "آخر نسخة محفوظة: ${last.formattedDate}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MedTextPrimaryDark
                                        )
                                        Text(
                                            text = "${last.coursesCount} مقررات • ${last.flashcardsCount} بطاقات • ${last.notesCount} ملاحظات",
                                            fontSize = 11.sp,
                                            color = MedTextSecondaryDark
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (backupState.isBackingUp || backupState.isRestoring) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MedMint, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (backupState.isBackingUp) "جارٍ تجهيز ورفع النسخة الاحتياطية..." else "جارٍ فحص واستعادة البيانات...",
                                    fontSize = 12.sp,
                                    color = MedMint
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        createBackupLauncher.launch("medstudy_backup_${System.currentTimeMillis()}.json")
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("نسخ إلى Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        openBackupLauncher.launch(arrayOf("application/json", "*/*"))
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MedTextPrimaryDark),
                                    border = BorderStroke(1.dp, MedBorderDark)
                                ) {
                                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp), tint = MedMint)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("استعادة النسخة", fontSize = 12.sp)
                                }
                            }
                        }

                        // Success / Error Notice
                        backupState.successMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "✓ $msg", color = MedMint, fontSize = 12.sp)
                        }
                        backupState.errorMessage?.let { err ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "⚠ $err", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                // Confirmation dialog before overwriting/restoring data
                val preview = backupState.previewBackup
                if (preview != null) {
                    AlertDialog(
                        onDismissRequest = { backupViewModel.cancelRestore() },
                        title = {
                            Text("تأكيد استعادة النسخة الاحتياطية", fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "تم فحص ملف النسخة الاحتياطية بنجاح:",
                                    fontSize = 13.sp,
                                    color = MedTextSecondaryDark
                                )
                                Text("• تاريخ النسخة: ${preview.formattedDate}", fontSize = 12.sp, color = MedTextPrimaryDark)
                                Text("• عدد المقررات: ${preview.coursesCount}", fontSize = 12.sp, color = MedTextPrimaryDark)
                                Text("• عدد البطاقات الذكية: ${preview.flashcardsCount}", fontSize = 12.sp, color = MedTextPrimaryDark)
                                Text("• عدد الملاحظات والملخصات: ${preview.notesCount}", fontSize = 12.sp, color = MedTextPrimaryDark)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "سيتم دمج وتحديث البيانات الحالية بمحتويات هذه النسخة السحابية. هل ترغب بالاستمرار؟",
                                    fontSize = 12.sp,
                                    color = MedMint
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { backupViewModel.confirmRestore() },
                                colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color.Black)
                            ) {
                                Text("تأكيد الاستعادة", fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { backupViewModel.cancelRestore() }) {
                                Text("إلغاء", color = MedTextSecondaryDark)
                            }
                        },
                        containerColor = MedSurfaceDark
                    )
                }
            }

            // Privacy & Architecture Notice
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                border = BorderStroke(1.dp, MedBorderDark)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = "Privacy", tint = MedMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("الخصوصية وحفظ البيانات (Privacy & Architecture):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedMint)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• المحاضرات والملاحظات والبطاقات تُحفظ في قاعدة بيانات على جهازك، وملفات PDF تُنسخ إلى مساحة التطبيق الخاصة.\n" +
                               "• لا يُرسل أي ملف كاملاً؛ فقط النص الذي تطلب معالجته، وبعد موافقتك أعلاه.\n" +
                               "• بدون مفتاح أو إنترنت أو موافقة تعمل فقط الموضوعات الجاهزة المحدودة داخل التطبيق، وتظهر لك بعلامة واضحة. ما عداها يحتاج الذكاء الاصطناعي.\n" +
                               "• المحتوى المولّد للمراجعة الدراسية فقط وقد يحتوي أخطاء؛ راجع مراجعك الطبية المعتمدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MedTextSecondaryDark,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
