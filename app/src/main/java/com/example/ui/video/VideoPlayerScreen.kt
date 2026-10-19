package com.example.ui.video

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.annotation.OptIn
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.core.SubtitleParser
import com.example.core.TranscriptSegment
import com.example.data.files.ResourceImporter
import com.example.data.local.LectureNoteEntity
import com.example.data.local.LectureResourceEntity
import com.example.data.repository.MedStudyRepository
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(UnstableApi::class)
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun VideoPlayerScreen(
    resourceId: Long,
    startPositionSeconds: Long,
    repository: MedStudyRepository,
    importer: ResourceImporter,
    onSendToTranslator: (String, String, Long) -> Unit,
    onSendToExplainer: (String, String, Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var resource by remember { mutableStateOf<LectureResourceEntity?>(null) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentPositionSeconds by remember { mutableLongStateOf(0L) }
    var transcriptSearch by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf<List<TranscriptSegment>>(emptyList()) }
    var hasVideo by remember { mutableStateOf(false) }
    var noteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    val videoTitle = resource?.title?.ifBlank { null } ?: "فيديو المحاضرة"
    val lectureIdForNotes = resource?.lectureId
    val notesFlow = remember(lectureIdForNotes) {
        lectureIdForNotes?.let { repository.getTimestampNotes(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList<LectureNoteEntity>())
    }
    val notes by notesFlow.collectAsState(initial = emptyList())

    val exoPlayer = remember { ExoPlayer.Builder(context).build().apply { playWhenReady = false } }

    fun subtitleFile(id: Long) = File(File(context.filesDir, "subtitles").apply { mkdirs() }, "$id.txt")

    fun loadVideo(res: LectureResourceEntity, seekToSeconds: Long) {
        if (res.uriString.isBlank()) { hasVideo = false; return }
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(res.uriString)))
        exoPlayer.prepare()
        val start = if (seekToSeconds >= 0) seekToSeconds * 1000 else res.lastPlaybackPosition
        if (start > 0) exoPlayer.seekTo(start)
        hasVideo = true
    }

    LaunchedEffect(resourceId) {
        val res = if (resourceId > 0) repository.getResourceById(resourceId) else null
        resource = res
        if (res != null) {
            loadVideo(res, startPositionSeconds)
            repository.touchLecture(res.lectureId)
            val cached = withContext(Dispatchers.IO) { subtitleFile(res.id).takeIf { it.exists() }?.readText() }
            if (!cached.isNullOrBlank()) transcript = SubtitleParser.group(SubtitleParser.parse(cached))
        }
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val res = resource
        if (uri != null && res != null) {
            importer.keepVideoAccess(uri)
            val updated = res.copy(uriString = uri.toString())
            resource = updated
            scope.launch {
                repository.updateResource(updated)
                loadVideo(updated, -1L)
            }
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val res = resource
        if (uri != null && res != null) {
            scope.launch {
                val raw = importer.readText(uri)
                val segments = raw?.let { SubtitleParser.group(SubtitleParser.parse(it)) }.orEmpty()
                if (segments.isEmpty()) {
                    Toast.makeText(context, "لم أجد ترجمة صالحة. استخدم ملف SRT أو VTT.", Toast.LENGTH_LONG).show()
                } else {
                    transcript = segments
                    withContext(Dispatchers.IO) { subtitleFile(res.id).writeText(raw!!) }
                    Toast.makeText(context, "تم استيراد ${segments.size} مقطعاً", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Track playback position and save it so the lecture resumes where the student stopped.
    LaunchedEffect(exoPlayer) {
        var ticks = 0
        while (true) {
            currentPositionSeconds = exoPlayer.currentPosition / 1000
            ticks++
            val res = resource
            if (ticks % 5 == 0 && res != null && hasVideo && exoPlayer.isPlaying) {
                repository.updateResource(res.copy(lastPlaybackPosition = exoPlayer.currentPosition, durationSeconds = maxOf(0L, exoPlayer.duration / 1000)))
            }
            delay(1000)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val res = resource
            val position = exoPlayer.currentPosition
            exoPlayer.release()
            if (res != null && position > 0) {
                @Suppress("OPT_IN_USAGE")
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    repository.updateResource(res.copy(lastPlaybackPosition = position))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = videoTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark,
                            maxLines = 1
                        )
                        Text(
                            text = "مشغل المحاضرات والمرئيات الطبية",
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
                    IconButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Default.VideoFile, contentDescription = "اختيار فيديو", tint = MedMint)
                    }
                    IconButton(onClick = { subtitlePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Subtitles, contentDescription = "استيراد ترجمة SRT/VTT", tint = MedLavender)
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
            // Video Player Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(Color.Black)
            ) {
                if (hasVideo) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("لا يوجد فيديو مرتبط بهذه المحاضرة بعد.", color = MedTextSecondaryDark, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(onClick = { videoPicker.launch(arrayOf("video/*")) }) { Text("اختر فيديو من جهازك") }
                    }
                }
            }

            // Playback Controls Row (Speed, Seek +/- 10s)
            Surface(
                color = MedSurfaceDark,
                border = BorderStroke(1.dp, MedBorderDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { noteDialog = true }, enabled = hasVideo) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "ملاحظة عند هذه اللحظة", tint = MedLavender)
                        }
                        IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0)) }) {
                            Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = MedTextPrimaryDark)
                        }
                        IconButton(onClick = { exoPlayer.seekTo(exoPlayer.currentPosition + 10000) }) {
                            Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = MedTextPrimaryDark)
                        }
                    }

                    // Speed Selector Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            FilterChip(
                                selected = currentSpeed == speed,
                                onClick = {
                                    currentSpeed = speed
                                    exoPlayer.setPlaybackSpeed(speed)
                                },
                                label = { Text("${speed}x", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MedMintContainer,
                                    selectedLabelColor = MedMint,
                                    containerColor = MedSurfaceVariantDark,
                                    labelColor = MedTextSecondaryDark
                                )
                            )
                        }
                    }
                }
            }

            // Transcript Section Header & Search
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Transcript", tint = MedMint, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "النص المتزامن للمحاضرة (Transcript)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                    }
                    Text(
                        text = "انقر على مقطع للانتقال",
                        style = MaterialTheme.typography.labelSmall,
                        color = MedTextSecondaryDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = transcriptSearch,
                    onValueChange = { transcriptSearch = it },
                    placeholder = { Text("ابحث في مصطلحات النص...", color = MedTextSecondaryDark, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MedMint) },
                    trailingIcon = {
                        if (transcriptSearch.isNotBlank()) {
                            IconButton(onClick = { transcriptSearch = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MedTextSecondaryDark)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
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
            }

            // Filtered Transcript List
            val filteredList = remember(transcriptSearch, transcript) {
                if (transcriptSearch.isBlank()) transcript
                else transcript.filter { it.text.contains(transcriptSearch, ignoreCase = true) }
            }
            val currentIndex = remember(currentPositionSeconds, transcript) {
                transcript.indexOfLast { it.startSeconds <= currentPositionSeconds }
            }

            if (transcript.isEmpty()) {
                Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    Text(
                        "لا توجد ترجمة نصية لهذا الفيديو. استورد ملف ترجمة SRT أو VTT (أيقونة الترجمة بالأعلى) لتحصل على نص متزامن قابل للبحث والترجمة والشرح.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MedTextSecondaryDark
                    )
                    if (notes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("ملاحظاتك على الفيديو:", style = MaterialTheme.typography.titleSmall, color = MedTextPrimaryDark)
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(notes) { n -> NoteRow(n, exoPlayer::seekTo) }
                        }
                    }
                }
            } else LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (notes.isNotEmpty()) {
                    item {
                        Text("ملاحظاتك:", style = MaterialTheme.typography.labelMedium, color = MedLavender)
                    }
                    items(notes) { n -> NoteRow(n, exoPlayer::seekTo) }
                }
                items(filteredList) { segment ->
                    val isCurrent = transcript.indexOf(segment) == currentIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { exoPlayer.seekTo(segment.startMs) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MedMintContainer.copy(alpha = 0.5f) else MedCardDark
                        ),
                        border = BorderStroke(1.dp, if (isCurrent) MedMint else MedBorderDark)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Badge(
                                    containerColor = if (isCurrent) MedMint else MedSurfaceVariantDark,
                                    contentColor = if (isCurrent) Color.Black else MedMint
                                ) {
                                    Text(segment.timeLabel, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                }
                                Row {
                                    IconButton(
                                        onClick = { onSendToTranslator(segment.text, videoTitle, segment.startSeconds) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.Translate, contentDescription = "Translate", tint = MedMint, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onSendToExplainer(segment.text, videoTitle, segment.startSeconds) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = "Explain", tint = MedLavender, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = segment.text, style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                        }
                    }
                }
            }
        }
    }

    if (noteDialog) {
        AlertDialog(
            onDismissRequest = { noteDialog = false },
            title = { Text("ملاحظة عند ${SubtitleParser.formatTime(exoPlayer.currentPosition)}") },
            text = {
                OutlinedTextField(value = noteText, onValueChange = { noteText = it }, placeholder = { Text("اكتب ملاحظتك...") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val res = resource
                    val ms = exoPlayer.currentPosition
                    if (res != null && noteText.isNotBlank()) {
                        scope.launch {
                            repository.insertNote(
                                LectureNoteEntity(
                                    lectureId = res.lectureId,
                                    title = "عند ${SubtitleParser.formatTime(ms)}",
                                    content = noteText.trim(),
                                    videoTimestamp = ms
                                )
                            )
                        }
                    }
                    noteText = ""
                    noteDialog = false
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { noteDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun NoteRow(note: LectureNoteEntity, seekTo: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { note.videoTimestamp?.let(seekTo) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MedLavenderContainer.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(note.title, style = MaterialTheme.typography.labelSmall, color = MedLavender)
            Text(note.content, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark)
        }
    }
}
