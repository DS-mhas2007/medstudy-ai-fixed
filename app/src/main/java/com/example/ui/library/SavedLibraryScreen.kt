package com.example.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextAlign
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.AIResultEntity
import com.example.data.model.DiagramEdge
import com.example.data.model.DiagramNode
import com.example.data.repository.MedStudyRepository
import com.example.ui.diagram.MedicalDiagramCard
import com.example.ui.diagram.fallbackNodesFromMermaid
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedLibraryScreen(
    repository: MedStudyRepository,
    onOpenSource: (String, Int?, Long?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "EXPLANATION", "TRANSLATION", "FAVORITE"
    var selectedDetailItem by remember { mutableStateOf<AIResultEntity?>(null) }

    val allResults by repository.allSavedResults.collectAsStateWithLifecycle(initialValue = emptyList())

    val filteredList = remember(allResults, searchQuery, selectedTypeFilter) {
        allResults.filter { item ->
            val matchesType = when (selectedTypeFilter) {
                "ALL" -> true
                "FAVORITE" -> item.isFavorite
                else -> item.type == selectedTypeFilter
            }
            val matchesQuery = searchQuery.isBlank() ||
                    item.title.contains(searchQuery, ignoreCase = true) ||
                    item.topic.contains(searchQuery, ignoreCase = true) ||
                    item.originalText.contains(searchQuery, ignoreCase = true) ||
                    item.translation.contains(searchQuery, ignoreCase = true) ||
                    item.tags.contains(searchQuery, ignoreCase = true)

            matchesType && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "المكتبة الطبية المحفوظة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = "وصول كامل بدون إنترنت للمخططات والشروح والترجمات",
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("بحث شامل في العناوين والمصطلحات والشروح...", color = MedTextSecondaryDark, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = MedMint) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MedTextSecondaryDark)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MedMint,
                    unfocusedBorderColor = MedBorderDark,
                    focusedTextColor = MedTextPrimaryDark,
                    unfocusedTextColor = MedTextPrimaryDark,
                    focusedContainerColor = MedCardDark,
                    unfocusedContainerColor = MedCardDark
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "ALL",
                        onClick = { selectedTypeFilter = "ALL" },
                        label = { Text("الكل (${allResults.size})", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedMintContainer,
                            selectedLabelColor = MedMint,
                            containerColor = MedSurfaceDark,
                            labelColor = MedTextSecondaryDark
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "EXPLANATION",
                        onClick = { selectedTypeFilter = "EXPLANATION" },
                        label = { Text("الشروح والمخططات", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedMintContainer,
                            selectedLabelColor = MedMint,
                            containerColor = MedSurfaceDark,
                            labelColor = MedTextSecondaryDark
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "TRANSLATION",
                        onClick = { selectedTypeFilter = "TRANSLATION" },
                        label = { Text("الترجمات والمصطلحات", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedMintContainer,
                            selectedLabelColor = MedMint,
                            containerColor = MedSurfaceDark,
                            labelColor = MedTextSecondaryDark
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = selectedTypeFilter == "FAVORITE",
                        onClick = { selectedTypeFilter = "FAVORITE" },
                        label = { Text("المفضلة ★", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MedAmberContainer,
                            selectedLabelColor = MedAmber,
                            containerColor = MedSurfaceDark,
                            labelColor = MedTextSecondaryDark
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Results List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BookmarkBorder, contentDescription = "Empty", tint = MedTextSecondaryDark, modifier = Modifier.size(54.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "لا توجد نتائج مطابقة لبحثك" else "مكتبتك المحفوظة فارغة حالياً",
                            style = MaterialTheme.typography.titleMedium,
                            color = MedTextPrimaryDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "احفظ أي ترجمة أو شرح من المساعد الطبي للرجوع إليها بدون إنترنت",
                            style = MaterialTheme.typography.bodySmall,
                            color = MedTextSecondaryDark,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDetailItem = item },
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
                                    Badge(
                                        containerColor = if (item.type == "TRANSLATION") MedMintContainer else MedLavenderContainer
                                    ) {
                                        Text(
                                            text = if (item.type == "TRANSLATION") "ترجمة طبية" else "شرح ومخطط",
                                            color = if (item.type == "TRANSLATION") MedMint else MedLavender,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.updateAIResult(item.copy(isFavorite = !item.isFavorite))
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                                contentDescription = "Favorite",
                                                tint = if (item.isFavorite) MedAmber else MedTextSecondaryDark,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    repository.deleteAIResult(item)
                                                    Toast.makeText(context, "تم حذف العنصر من المكتبة", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MedCoral, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MedTextPrimaryDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (item.simpleExplanation.isNotBlank() || item.translation.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.simpleExplanation.ifBlank { item.translation },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MedTextSecondaryDark,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Footer: Source link or diagram indicator
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.diagramCode.isNotBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountTree, contentDescription = "Diagram", tint = MedMint, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("يتضمن مخططاً تفاعلياً", style = MaterialTheme.typography.labelSmall, color = MedMint, fontSize = 10.sp)
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    if (item.isOfflineGenerated) {
                                        Text("محتوى محلي جاهز", style = MaterialTheme.typography.labelSmall, color = MedCoral, fontSize = 10.sp)
                                    }
                                    if (item.sourceDocument != null) {
                                        Text(
                                            text = if (item.sourceTimestamp != null) "${item.sourceDocument} • فيديو" else "${item.sourceDocument} • ص ${item.sourcePage ?: 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MedLavender,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Detail BottomSheet Modal
            selectedDetailItem?.let { detail ->
                ModalBottomSheet(
                    onDismissRequest = { selectedDetailItem = null },
                    containerColor = MedSurfaceDark,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = MedMint) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                            .navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MedTextPrimaryDark,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { selectedDetailItem = null }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = MedTextSecondaryDark)
                            }
                        }

                        if (detail.translation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("الترجمة الطبية:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedMint)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(detail.translation, style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                        }

                        if (detail.detailedExplanation.isNotBlank()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("الشرح العلمي المعتمد:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MedLavender)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(detail.detailedExplanation, style = MaterialTheme.typography.bodyMedium, color = MedTextPrimaryDark)
                        }

                        // Render Diagram if present!
                        if (detail.diagramCode.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            MedicalDiagramCard(
                                title = "المخطط البصري: ${detail.title}",
                                mermaidCode = detail.diagramCode,
                                nodes = fallbackNodesFromMermaid(detail.diagramCode)
                            )
                        }

                        // Reopen Source Action Button
                        if (detail.sourceDocument != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val doc = detail.sourceDocument
                                    val pg = detail.sourcePage
                                    val ts = detail.sourceTimestamp
                                    selectedDetailItem = null
                                    onOpenSource(doc, pg, ts)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer, contentColor = MedMint)
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = "Open")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (detail.sourceTimestamp != null) "إعادة فتح المصدر عند اللحظة المحفوظة" else "إعادة فتح المصدر في صفحة ${detail.sourcePage ?: 1}")
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }
            }
        }
    }
}
