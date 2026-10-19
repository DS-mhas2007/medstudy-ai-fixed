package com.example.ui.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.files.PdfTextSource
import com.example.data.local.LectureResourceEntity
import com.example.data.repository.MedStudyRepository
import com.example.data.sample.SamplePdfGenerator
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    resourceId: Long,
    initialPage: Int,
    repository: MedStudyRepository,
    onSendToTranslator: (String, String, Int) -> Unit,
    onSendToExplainer: (String, String, Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var resource by remember { mutableStateOf<LectureResourceEntity?>(null) }
    var currentFile by remember { mutableStateOf<File?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var fileDescriptor by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var textSource by remember { mutableStateOf<PdfTextSource?>(null) }
    var pageCount by remember { mutableIntStateOf(1) }
    var currentPageIndex by remember { mutableIntStateOf(initialPage.coerceAtLeast(0)) }
    var currentPageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val renderLock = remember { Mutex() }

    val displayTitle = resource?.title?.ifBlank { null } ?: currentFile?.name ?: "PDF"

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.8f, 4f)
        offset += panChange
    }

    var showActionMenu by remember { mutableStateOf(false) }
    var selectedPassage by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }

    // Open the resource (or the sample document when there is none).
    LaunchedEffect(resourceId) {
        val res = if (resourceId > 0) repository.getResourceById(resourceId) else null
        resource = res
        val file = withContext(Dispatchers.IO) {
            if (res != null && res.localPath.isNotBlank()) File(res.localPath)
            else SamplePdfGenerator.generateSampleLecturePdf(context)
        }
        if (!file.exists()) {
            loadError = "ملف المحاضرة لم يعد موجوداً على الجهاز. أعد إرفاقه من صفحة المقررات."
            isLoading = false
            return@LaunchedEffect
        }
        try {
            withContext(Dispatchers.IO) {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                fileDescriptor = pfd
                pdfRenderer = renderer
                pageCount = renderer.pageCount
            }
            textSource = PdfTextSource(context, file)
            currentFile = file
            currentPageIndex = currentPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
            res?.let { repository.touchLecture(it.lectureId) }
        } catch (e: Exception) {
            loadError = "تعذر فتح الملف. قد يكون تالفاً أو محمياً بكلمة مرور."
            isLoading = false
        }
    }

    // Release native resources when leaving the reader.
    DisposableEffect(Unit) {
        onDispose {
            textSource?.close()
            try { pdfRenderer?.close() } catch (_: Exception) {}
            try { fileDescriptor?.close() } catch (_: Exception) {}
            currentPageBitmap = null
        }
    }

    // Render current page (one page open at a time; PdfRenderer is not thread safe).
    LaunchedEffect(pdfRenderer, currentPageIndex) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        isLoading = true
        scale = 1f
        offset = Offset.Zero
        val bitmap = withContext(Dispatchers.IO) {
            renderLock.withLock {
                try {
                    renderer.openPage(currentPageIndex).use { page ->
                        val factor = minOf(2f, 2200f / maxOf(page.width, page.height))
                        val bmp = Bitmap.createBitmap((page.width * factor).toInt(), (page.height * factor).toInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
        currentPageBitmap = bitmap
        isLoading = false
    }

    // Remember where the student stopped.
    LaunchedEffect(resource, currentPageIndex, pageCount) {
        val res = resource ?: return@LaunchedEffect
        if (pageCount <= 0) return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        repository.updateResource(res.copy(lastReadPage = currentPageIndex + 1, pageCount = pageCount))
    }

    // Extract the real text of the page when the AI sheet is opened or the page changes while it is open.
    LaunchedEffect(showActionMenu, currentPageIndex, textSource) {
        val source = textSource ?: return@LaunchedEffect
        if (!showActionMenu) return@LaunchedEffect
        isExtracting = true
        selectedPassage = source.pageText(currentPageIndex)
        isExtracting = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark,
                            maxLines = 1
                        )
                        Text(
                            text = "صفحة ${currentPageIndex + 1} من $pageCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedMint
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MedTextPrimaryDark)
                    }
                },
                actions = {
                    IconButton(onClick = { showActionMenu = !showActionMenu }) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = "AI Actions",
                            tint = if (showActionMenu) MedLavender else MedTextPrimaryDark
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MedSurfaceDark)
            )
        },
        bottomBar = {
            // Reader Control Bar
            Surface(
                color = MedSurfaceDark,
                border = BorderStroke(1.dp, MedBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    // Page navigation slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${currentPageIndex + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MedTextSecondaryDark,
                            modifier = Modifier.width(28.dp)
                        )
                        Slider(
                            value = currentPageIndex.toFloat(),
                            onValueChange = { currentPageIndex = it.toInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0)) },
                            valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MedMint,
                                activeTrackColor = MedMint,
                                inactiveTrackColor = MedBorderDark
                            )
                        )
                        Text(
                            text = "$pageCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MedTextSecondaryDark,
                            modifier = Modifier.width(28.dp)
                        )
                    }

                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                            enabled = currentPageIndex > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = MedSurfaceVariantDark, contentColor = MedTextPrimaryDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("السابق", style = MaterialTheme.typography.labelMedium)
                        }

                        // Quick AI Action trigger
                        FilledTonalButton(
                            onClick = { showActionMenu = true },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MedMintContainer, contentColor = MedMint),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Icon(Icons.Default.Psychology, contentDescription = "Explain", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استخراج وشرح النص", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { if (currentPageIndex < pageCount - 1) currentPageIndex++ },
                            enabled = currentPageIndex < pageCount - 1,
                            colors = ButtonDefaults.buttonColors(containerColor = MedSurfaceVariantDark, contentColor = MedTextPrimaryDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("التالي", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        containerColor = MedBackgroundDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // PDF Page Viewer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformState),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MedMint)
                } else if (currentPageBitmap != null) {
                    Image(
                        bitmap = currentPageBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                } else {
                    Text(loadError ?: "تعذر عرض الصفحة", color = MedTextSecondaryDark, modifier = Modifier.padding(24.dp))
                }
            }

            // Zoom Reset Floating Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scale = (scale + 0.3f).coerceAtMost(4f) },
                    containerColor = MedSurfaceDark,
                    contentColor = MedMint
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }
                SmallFloatingActionButton(
                    onClick = { scale = (scale - 0.3f).coerceAtLeast(0.8f) },
                    containerColor = MedSurfaceDark,
                    contentColor = MedMint
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }
                SmallFloatingActionButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    containerColor = MedSurfaceDark,
                    contentColor = MedLavender
                ) {
                    Icon(Icons.Default.FitScreen, contentDescription = "Fit")
                }
            }

            // Contextual AI Bottom Sheet / Menu
            if (showActionMenu) {
                ModalBottomSheet(
                    onDismissRequest = { showActionMenu = false },
                    containerColor = MedSurfaceDark,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = MedMint) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "مستخرج النصوص والذكاء الاصطناعي",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedMint
                            )
                            Badge(containerColor = MedLavenderContainer) {
                                Text("صفحة ${currentPageIndex + 1}", color = MedLavender, modifier = Modifier.padding(2.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isExtracting) "جارٍ استخراج النص (قد يستغرق OCR بضع ثوانٍ للصفحات الممسوحة)..." else "النص المستخرج من هذه الصفحة. عدّله أو قصّه لتحديد الفقرة المطلوبة:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedTextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = selectedPassage,
                            onValueChange = { selectedPassage = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 100.dp, max = 150.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MedMint,
                                unfocusedBorderColor = MedBorderDark,
                                focusedTextColor = MedTextPrimaryDark,
                                unfocusedTextColor = MedTextPrimaryDark,
                                focusedContainerColor = MedCardDark,
                                unfocusedContainerColor = MedCardDark
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "اختر الإجراء الأكاديمي المطلوب:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Grid
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 1. Translate
                            Button(
                                enabled = selectedPassage.isNotBlank(),
                                onClick = {
                                    showActionMenu = false
                                    onSendToTranslator(selectedPassage, displayTitle, currentPageIndex + 1)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MedCardDark, contentColor = MedMint),
                                border = BorderStroke(1.dp, MedMint.copy(alpha = 0.5f))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Translate, contentDescription = "Translate")
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("ترجمة طبية", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            // 2. Explain
                            Button(
                                enabled = selectedPassage.isNotBlank(),
                                onClick = {
                                    showActionMenu = false
                                    onSendToExplainer(selectedPassage, displayTitle, currentPageIndex + 1)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MedCardDark, contentColor = MedLavender),
                                border = BorderStroke(1.dp, MedLavender.copy(alpha = 0.5f))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.School, contentDescription = "Explain")
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("شرح مفصل", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}
