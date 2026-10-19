package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MedBorderDark
import com.example.ui.theme.MedMint
import com.example.ui.theme.MedSurfaceDark
import com.example.ui.theme.MedTextPrimaryDark
import com.example.ui.theme.MedTextSecondaryDark
import com.example.worker.PdfIndexingProgressBus

@Composable
fun PdfIndexingBanner(
    modifier: Modifier = Modifier
) {
    val tasksMap by PdfIndexingProgressBus.tasks.collectAsStateWithLifecycle()
    val activeTasks = tasksMap.values.toList()

    AnimatedVisibility(
        visible = activeTasks.isNotEmpty(),
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeTasks.forEach { task ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
                    border = BorderStroke(1.dp, if (task.isCompleted) MedMint else MedBorderDark)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = if (task.isCompleted) Icons.Default.CheckCircle
                                else if (task.isFailed) Icons.Default.Error
                                else Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = if (task.isCompleted) MedMint else if (task.isFailed) MaterialTheme.colorScheme.error else MedMint,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MedTextPrimaryDark,
                                    maxLines = 1
                                )
                                Text(
                                    text = task.statusText,
                                    fontSize = 12.sp,
                                    color = MedTextSecondaryDark,
                                    maxLines = 1
                                )
                            }

                            if (!task.isCompleted && !task.isFailed) {
                                CircularProgressIndicator(
                                    progress = { task.progressPercent / 100f },
                                    modifier = Modifier.size(24.dp),
                                    color = MedMint,
                                    strokeWidth = 2.5.dp,
                                    trackColor = MedBorderDark,
                                    strokeCap = StrokeCap.Round
                                )
                            } else {
                                IconButton(
                                    onClick = { PdfIndexingProgressBus.clearTask(task.resourceId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إغلاق",
                                        tint = MedTextSecondaryDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (!task.isCompleted && !task.isFailed) {
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { task.progressPercent / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = MedMint,
                                trackColor = MedBorderDark,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}
