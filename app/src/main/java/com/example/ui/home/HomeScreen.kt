package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.StudyStats
import com.example.data.local.LectureResourceEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: MedStudyRepository,
    onNavigateToCourses: () -> Unit,
    onNavigateToExplainer: () -> Unit,
    onNavigateToTranslator: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlanner: () -> Unit,
    onNavigateToExam: () -> Unit = {},
    onOpenResource: (LectureResourceEntity) -> Unit,
    onOpenSettings: () -> Unit
) {
    val courses by repository.allCourses.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentLectures by repository.recentLectures.collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by repository.allTasks.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedResults by repository.allSavedResults.collectAsStateWithLifecycle(initialValue = emptyList())
    val stats by repository.studyStats.collectAsStateWithLifecycle(initialValue = StudyStats())
    val continueItem by repository.continueStudying.collectAsStateWithLifecycle(initialValue = null)

    val today = com.example.core.DateUtils.isoDate(System.currentTimeMillis())
    val pendingTasks = tasks.filter { !it.isCompleted && (it.targetDate.isBlank() || it.targetDate <= today) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MedMintContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.School, contentDescription = "Logo", tint = MedMint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("MedStudy AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                            Text("رفيق دراستك الطبية بالذكاء الاصطناعي", style = MaterialTheme.typography.labelSmall, color = MedMint, fontSize = 11.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MedTextSecondaryDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedSurfaceDark)
            )
        },
        containerColor = MedBackgroundDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp)
        ) {
            // 1. Academic Hero Welcome Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MedCardDark),
                    border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MedMintContainer.copy(alpha = 0.5f),
                                        MedCardDark,
                                        MedLavenderContainer.copy(alpha = 0.3f)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(containerColor = MedMintContainer) {
                                    Text("MedStudy AI", color = MedMint, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                                Text(if (stats.streakDays > 0) "🔥 ${stats.streakDays} يوم متتالي" else "ابدأ سلسلتك اليوم", style = MaterialTheme.typography.labelSmall, color = MedLavender)
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (stats.dueToday > 0) "عندك ${stats.dueToday} بطاقة للمراجعة اليوم" else "جاهز لمتابعة الدراسة اليوم؟",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedTextPrimaryDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "راجع ${stats.reviewsToday} بطاقة اليوم • ${stats.minutesThisWeek} دقيقة تركيز هذا الأسبوع • ${stats.totalCards} بطاقة في مكتبتك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MedTextSecondaryDark
                            )
                        }
                    }
                }
            }

            // 2. Quick Action Hub (Core Defining Workflow)
            item {
                Column {
                    Text(
                        text = "الوصول السريع للأدوات الطبية الذكية:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MedTextPrimaryDark
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Translator Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToTranslator() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedMint.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(MedMintContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = "Translator", tint = MedMint, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("المترجم الطبي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                                Text("جدول المصطلحات", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark, fontSize = 10.sp)
                            }
                        }

                        // Explainer Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToExplainer() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedLavender.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(MedLavenderContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Psychology, contentDescription = "Explainer", tint = MedLavender, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("المساعد الطبي", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                                Text("شرح وتشبيه علمي", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark, fontSize = 10.sp)
                            }
                        }

                        // PDF Reader Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToPlanner() },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedCyan.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(MedCyanContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Style, contentDescription = "Review", tint = MedCyan, modifier = Modifier.size(22.dp))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("مراجعة البطاقات", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                                Text(if (stats.dueToday > 0) "${stats.dueToday} مستحقة" else "لا شيء مستحق", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }

            // Exam Mode & Weakness Diagnosis Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToExam() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                    border = BorderStroke(1.dp, MedMint.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MedMint.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = MedMint, modifier = Modifier.size(26.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("محاكاة وضع الامتحان الشامل", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MedTextPrimaryDark)
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = MedMintContainer) {
                                    Text("مؤقت + تقرير", fontSize = 10.sp, color = MedMint, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("أسئلة مختلطة من عدة مقررات مع تقرير تشخيصي لنقاط الضعف حسب التخصص.", fontSize = 11.sp, color = MedTextSecondaryDark)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MedMint, modifier = Modifier.size(18.dp))
                    }
                }
            }

            // 3. Continue Studying (last lecture you actually opened)
            item {
                val item = continueItem
                if (item == null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onNavigateToCourses() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MedCardDark),
                        border = BorderStroke(1.dp, MedBorderDark)
                    ) {
                        Text(
                            "افتح أي محاضرة من مقرراتك لتظهر هنا ويمكنك المتابعة منها بضغطة واحدة.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MedTextSecondaryDark,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { item.resource?.let(onOpenResource) ?: onNavigateToCourses() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MedCardDark),
                        border = BorderStroke(1.dp, MedBorderDark)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(46.dp).background(MedMintContainer, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Continue", tint = MedMint, modifier = Modifier.size(28.dp))
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Badge(containerColor = MedMintContainer) {
                                        Text("تابع دراستك", color = MedMint, modifier = Modifier.padding(2.dp))
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = item.course?.let { "${it.code} • ${it.titleAr}" } ?: "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MedTextSecondaryDark
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.lecture.titleAr,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MedTextPrimaryDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.resource?.let { r ->
                                    Text(
                                        text = if (r.type == "PDF" && r.pageCount > 0) "صفحة ${r.lastReadPage} من ${r.pageCount}" else if (r.type == "VIDEO") "فيديو • ${r.lastPlaybackPosition / 60000} د" else r.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MedTextSecondaryDark
                                    )
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open", tint = MedTextSecondaryDark)
                        }
                    }
                }
            }

            // 4. Today's Study Tasks
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "مهام اليوم المتبقية (${pendingTasks.size}):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        TextButton(onClick = onNavigateToPlanner) {
                            Text("عرض الخطة كاملة", color = MedMint, fontSize = 12.sp)
                        }
                    }

                    if (pendingTasks.isEmpty()) {
                        Text("لا توجد مهام لليوم. أضف مهمة من المخطط.", style = MaterialTheme.typography.bodySmall, color = MedTextSecondaryDark)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pendingTasks.take(2).forEach { task ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                                    border = BorderStroke(1.dp, MedBorderDark)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Task", tint = MedMint, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(task.title, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark, modifier = Modifier.weight(1f))
                                        Text("${task.estimatedMinutes} د", style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Recently Saved AI Explanations & Diagrams
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "أحدث الشروح والمخططات المحفوظة:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        TextButton(onClick = onNavigateToLibrary) {
                            Text("المكتبة (${savedResults.size})", color = MedLavender, fontSize = 12.sp)
                        }
                    }

                    if (savedResults.isEmpty()) {
                        Text("لم تحفظ شروحاً بعد.", style = MaterialTheme.typography.bodySmall, color = MedTextSecondaryDark)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(savedResults.take(4)) { item ->
                                Card(
                                    modifier = Modifier
                                        .width(220.dp)
                                        .clickable { onNavigateToLibrary() },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MedCardDark),
                                    border = BorderStroke(1.dp, MedBorderDark)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Badge(containerColor = MedLavenderContainer) {
                                            Text(
                                                if (item.diagramCode.isNotBlank()) "مخطط ملون" else "شرح طبي",
                                                color = MedLavender,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MedTextPrimaryDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = item.simpleExplanation.ifBlank { item.translation },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MedTextSecondaryDark,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 11.sp
                                        )
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
