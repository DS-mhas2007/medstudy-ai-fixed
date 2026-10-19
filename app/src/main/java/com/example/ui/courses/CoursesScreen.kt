package com.example.ui.courses

import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.files.ImportResult
import com.example.data.files.ResourceImporter
import com.example.data.local.LectureResourceEntity
import com.example.data.local.CourseEntity
import com.example.data.local.LectureEntity
import com.example.data.local.ModuleEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.launch

import com.example.ui.components.PdfIndexingBanner
import com.example.ui.viewmodel.CoursesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    repository: MedStudyRepository,
    importer: ResourceImporter,
    viewModel: CoursesViewModel? = null,
    onOpenPdfReader: (Long) -> Unit,
    onOpenVideoPlayer: (Long) -> Unit,
    onLaunchQuiz: (lectureId: Long, lectureTitle: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vmState by viewModel?.uiState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    var localSelectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    var localShowCreateCourseDialog by remember { mutableStateOf(false) }
    var localCourseToDelete by remember { mutableStateOf<CourseEntity?>(null) }

    val selectedCourse = vmState?.selectedCourse ?: localSelectedCourse
    val showCreateCourseDialog = vmState?.isCreatingCourse ?: localShowCreateCourseDialog
    val courseToDelete = vmState?.courseToDelete ?: localCourseToDelete

    val coursesList by (viewModel?.courses ?: repository.allCourses).collectAsStateWithLifecycle(initialValue = emptyList())

    if (selectedCourse != null) {
        SubjectHubScreen(
            course = selectedCourse,
            repository = repository,
            importer = importer,
            onOpenPdfReader = onOpenPdfReader,
            onOpenVideoPlayer = onOpenVideoPlayer,
            onLaunchQuiz = onLaunchQuiz,
            onBack = {
                if (viewModel != null) viewModel.selectCourse(null)
                else localSelectedCourse = null
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "مقرراتي الأكاديمية",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedTextPrimaryDark
                            )
                            Text(
                                text = "المساحات المخصصة والمقررات الطبية",
                                style = MaterialTheme.typography.labelSmall,
                                color = MedMint
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (viewModel != null) viewModel.setCreatingCourse(true)
                            else localShowCreateCourseDialog = true
                        }) {
                            Icon(Icons.Default.AddCircleOutline, contentDescription = "New Course", tint = MedMint)
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
                // Live WorkManager PDF Indexing progress banner
                PdfIndexingBanner()

                // Course List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                ) {
                    items(coursesList, key = { it.id }) { course ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (viewModel != null) viewModel.selectCourse(course)
                                    else localSelectedCourse = course
                                },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MedCardDark),
                            border = BorderStroke(1.dp, MedBorderDark)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val courseColor = try {
                                    Color(android.graphics.Color.parseColor(course.colorHex))
                                } catch (e: Exception) {
                                    MedMint
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(courseColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (course.code) {
                                            "MED101" -> Icons.Default.Translate
                                            "PHY102" -> Icons.Default.Biotech
                                            else -> Icons.Default.Psychology
                                        },
                                        contentDescription = "Course Icon",
                                        tint = courseColor,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Badge(containerColor = courseColor.copy(alpha = 0.25f)) {
                                            Text(course.code, color = courseColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(course.semester, style = MaterialTheme.typography.labelSmall, color = MedTextSecondaryDark)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = course.titleAr,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MedTextPrimaryDark
                                    )
                                    Text(
                                        text = course.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MedTextSecondaryDark
                                    )
                                }

                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open", tint = MedTextSecondaryDark)
                            }
                        }
                    }
                }
            }

            courseToDelete?.let { course ->
                AlertDialog(
                    onDismissRequest = {
                        if (viewModel != null) viewModel.setCourseToDelete(null)
                        else localCourseToDelete = null
                    },
                    title = { Text("حذف المقرر «${course.titleAr}»؟", color = MedCoral) },
                    text = { Text("ستُحذف كل وحداته ومحاضراته وملفاته وملاحظاته نهائياً ولا يمكن التراجع.") },
                    confirmButton = {
                        TextButton(onClick = {
                            if (viewModel != null) {
                                viewModel.confirmDeleteCourse()
                            } else {
                                scope.launch { repository.deleteCourse(course) }
                                localCourseToDelete = null
                                localSelectedCourse = null
                            }
                        }) { Text("حذف", color = MedCoral) }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            if (viewModel != null) viewModel.setCourseToDelete(null)
                            else localCourseToDelete = null
                        }) { Text("إلغاء") }
                    },
                    containerColor = MedSurfaceDark
                )
            }

            // Dialog: Create Course
            if (showCreateCourseDialog) {
                var newTitleEn by remember { mutableStateOf("") }
                var newTitleAr by remember { mutableStateOf("") }
                var newCode by remember { mutableStateOf("") }

                AlertDialog(
                    onDismissRequest = {
                        if (viewModel != null) viewModel.setCreatingCourse(false)
                        else localShowCreateCourseDialog = false
                    },
                    title = { Text("إنشاء مقرر أكاديمي جديد", color = MedMint, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newTitleAr,
                                onValueChange = { newTitleAr = it },
                                label = { Text("اسم المقرر بالعربية") }
                            )
                            OutlinedTextField(
                                value = newTitleEn,
                                onValueChange = { newTitleEn = it },
                                label = { Text("Course Title (English)") }
                            )
                            OutlinedTextField(
                                value = newCode,
                                onValueChange = { newCode = it },
                                label = { Text("رمز المقرر (مثال: PHA104)") }
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTitleAr.isNotBlank()) {
                                    if (viewModel != null) {
                                        viewModel.createCourse(
                                            title = newTitleEn.ifBlank { newTitleAr },
                                            titleAr = newTitleAr,
                                            code = newCode.ifBlank { "MED" },
                                            semester = "الفصل الحالي",
                                            colorHex = "#60F5D2"
                                        )
                                    } else {
                                        scope.launch {
                                            repository.insertCourse(
                                                CourseEntity(
                                                    title = newTitleEn.ifBlank { newTitleAr },
                                                    titleAr = newTitleAr,
                                                    code = newCode.ifBlank { "MED" },
                                                    semester = "الفصل الحالي",
                                                    colorHex = "#60F5D2"
                                                )
                                            )
                                            localShowCreateCourseDialog = false
                                        }
                                    }
                                    Toast.makeText(context, "تمت إضافة المقرر الأكاديمي بنجاح", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedMint, contentColor = Color(0xFF00382B))
                        ) {
                            Text("حفظ المقرر")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            if (viewModel != null) viewModel.setCreatingCourse(false)
                            else localShowCreateCourseDialog = false
                        }) {
                            Text("إلغاء", color = MedTextSecondaryDark)
                        }
                    },
                    containerColor = MedSurfaceDark
                )
            }
        }
    }
}
