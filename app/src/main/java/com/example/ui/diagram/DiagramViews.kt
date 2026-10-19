package com.example.ui.diagram

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.MermaidHtml
import com.example.data.model.DiagramEdge
import com.example.data.model.DiagramNode
import com.example.ui.theme.*

@Composable
fun MedicalDiagramCard(
    title: String,
    mermaidCode: String,
    nodes: List<DiagramNode> = emptyList(),
    edges: List<DiagramEdge> = emptyList(),
    onSaveDiagram: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isFullScreen by remember { mutableStateOf(false) }
    var selectedNode by remember { mutableStateOf<DiagramNode?>(null) }
    var renderMode by remember { mutableStateOf(0) } // 0: Interactive Nodes, 1: Mermaid Engine

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MedCardDark),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MedBorderDark))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MedMintContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountTree,
                            contentDescription = "Diagram",
                            tint = MedMint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title.ifBlank { "المخطط الطبي البصري التفاعلي" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MedTextPrimaryDark
                        )
                        Text(
                            text = "مخطط مفاهيمي ملون وقابل للتكبير",
                            style = MaterialTheme.typography.labelSmall,
                            color = MedTextSecondaryDark
                        )
                    }
                }

                Row {
                    // Mode Switcher (Interactive Nodes vs Mermaid)
                    IconButton(
                        onClick = { renderMode = if (renderMode == 0) 1 else 0 },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (renderMode == 0) Icons.Default.Code else Icons.Default.ViewQuilt,
                            contentDescription = "Switch Engine",
                            tint = MedLavender,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (onSaveDiagram != null) {
                        IconButton(onClick = onSaveDiagram, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.BookmarkAdd,
                                contentDescription = "Save Diagram",
                                tint = MedMint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Semantic Color Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendPill("تعريف", MedLavender)
                LegendPill("آلية", MedMint)
                LegendPill("تشريح", MedCyan)
                LegendPill("امتحان", MedAmber)
                LegendPill("تحذير", MedCoral)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Render Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MedSurfaceDark)
                    .border(1.dp, MedBorderDark, RoundedCornerShape(12.dp))
            ) {
                if (renderMode == 0) {
                    InteractiveNodeGraph(
                        nodes = if (nodes.isNotEmpty()) nodes else fallbackNodesFromMermaid(mermaidCode),
                        edges = if (edges.isNotEmpty()) edges else fallbackEdgesFromMermaid(mermaidCode),
                        onNodeClick = { selectedNode = it }
                    )
                } else {
                    MermaidWebView(
                        mermaidCode = mermaidCode.ifBlank { "graph TD\n  A[بداية المنبه] --> B[تفعيل المستقبل]\n  B --> C[الاستجابة]" }
                    )
                }
            }

            // Node Inspector Dialog / Banner
            selectedNode?.let { node ->
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = getNodeCategoryColor(node.category).copy(alpha = 0.15f)),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(getNodeCategoryColor(node.category)))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${node.labelAr.ifBlank { node.label }} (${node.label})",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = getNodeCategoryColor(node.category)
                            )
                            if (node.details.isNotBlank()) {
                                Text(
                                    text = node.details,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MedTextPrimaryDark
                                )
                            }
                        }
                        IconButton(onClick = { selectedNode = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = MedTextSecondaryDark)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveNodeGraph(
    nodes: List<DiagramNode>,
    edges: List<DiagramEdge>,
    onNodeClick: (DiagramNode) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val state = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 3.0f)
        offset += panChange
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .transformable(state = state)
    ) {
        // Node Layout positioning
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            nodes.chunked(2).forEachIndexed { rowIndex, rowNodes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    rowNodes.forEach { node ->
                        val color = getNodeCategoryColor(node.category)
                        Card(
                            modifier = Modifier
                                .padding(8.dp)
                                .widthIn(min = 120.dp, max = 160.dp)
                                .clickable { onNodeClick(node) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f)),
                            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(color))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = node.labelAr.ifBlank { node.label },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = color,
                                    textAlign = TextAlign.Center
                                )
                                if (node.labelAr.isNotBlank() && node.label != node.labelAr) {
                                    Text(
                                        text = node.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MedTextSecondaryDark,
                                        textAlign = TextAlign.Center,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
                if (rowIndex < nodes.chunked(2).size - 1) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Flow",
                        tint = MedMint.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }

        // Zoom / Reset Controls
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { scale = (scale + 0.2f).coerceAtMost(3.0f) },
                containerColor = MedSurfaceVariantDark,
                contentColor = MedMint,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }
            SmallFloatingActionButton(
                onClick = { scale = (scale - 0.2f).coerceAtLeast(0.5f) },
                containerColor = MedSurfaceVariantDark,
                contentColor = MedMint,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }
            SmallFloatingActionButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                },
                containerColor = MedSurfaceVariantDark,
                contentColor = MedLavender,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom", modifier = Modifier.size(18.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidWebView(mermaidCode: String, modifier: Modifier = Modifier) {
    val htmlContent = remember(mermaidCode) { MermaidHtml.page(mermaidCode) }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                // JavaScript is required by Mermaid itself. The library is bundled in assets, the page is
                // sandboxed by a CSP, network loads are blocked and navigation is disabled.
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                setBackgroundColor(android.graphics.Color.parseColor("#07152D"))
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean = true
                }
                loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != htmlContent) {
                webView.tag = htmlContent
                webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView -> webView.destroy() },
        modifier = modifier
            .fillMaxWidth()
            .height(320.dp)
    )
}

@Composable
fun LegendPill(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MedTextSecondaryDark,
            fontSize = 11.sp
        )
    }
}

fun getNodeCategoryColor(category: String): Color {
    return when (category.lowercase()) {
        "definition", "lavender" -> MedLavender
        "mechanism", "mint" -> MedMint
        "anatomy", "cyan" -> MedCyan
        "exam", "amber" -> MedAmber
        "warning", "coral" -> MedCoral
        "outcome", "green" -> MedGreen
        else -> MedMint
    }
}

fun fallbackNodesFromMermaid(code: String): List<DiagramNode> {
    val nodes = mutableListOf<DiagramNode>()
    val lines = code.lines()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.contains("[") && trimmed.contains("]")) {
            val id = trimmed.substringBefore("[").trim()
            val label = trimmed.substringAfter("[").substringBefore("]").trim()
            if (id.isNotBlank() && label.isNotBlank() && id.length <= 4) {
                nodes.add(
                    DiagramNode(
                        id = id,
                        label = label,
                        labelAr = label,
                        category = when (nodes.size % 5) {
                            0 -> "mechanism"
                            1 -> "anatomy"
                            2 -> "definition"
                            3 -> "exam"
                            else -> "outcome"
                        }
                    )
                )
            }
        }
    }
    if (nodes.isEmpty()) {
        nodes.add(DiagramNode("1", "Initial Stimulus", "المحفز الأولي", "anatomy", "التنبيه العصبي أو الحسي"))
        nodes.add(DiagramNode("2", "Receptor Activation", "تفعيل المستقبل", "mechanism", "ارتباط الربيطة بالبروتين النوعي"))
        nodes.add(DiagramNode("3", "Signaling Cascade", "شلال الإشارة", "definition", "تضخيم الإشارة الكيميائية الحيوية"))
        nodes.add(DiagramNode("4", "Cellular Response", "الاستجابة الخلوية", "outcome", "تغيير وظيفة الغشاء أو الخلية"))
    }
    return nodes.distinctBy { it.id }
}

fun fallbackEdgesFromMermaid(code: String): List<DiagramEdge> {
    val edges = mutableListOf<DiagramEdge>()
    val lines = code.lines()
    for (line in lines) {
        if (line.contains("-->")) {
            val from = line.substringBefore("-->").trim().takeLast(2).trim()
            val to = line.substringAfter("-->").trim().take(2).trim()
            if (from.isNotBlank() && to.isNotBlank()) {
                edges.add(DiagramEdge(from, to, "causes"))
            }
        }
    }
    return edges
}
