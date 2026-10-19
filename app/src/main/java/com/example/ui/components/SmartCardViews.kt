package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FsrsScheduler
import com.example.core.ReviewGrade
import com.example.core.SmartCardParser
import com.example.data.local.FlashcardEntity
import com.example.ui.theme.*

/**
 * Intelligent interactive flashcard view supporting:
 * - Standard Q&A
 * - Cloze Deletions with interactive reveal
 * - Anatomical Diagram occlusion
 * - FSRS memory analytics (Retrievability %, Stability, Difficulty, State)
 */
@Composable
fun SmartFlashcardView(
    flashcard: FlashcardEntity,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onAnswerGrade: (ReviewGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    val intervals = remember(flashcard) {
        val elapsed = if (flashcard.lastReviewedAt > 0) {
            ((System.currentTimeMillis() - flashcard.lastReviewedAt).toFloat() / (24f * 60f * 60f * 1000f)).coerceAtLeast(0f)
        } else {
            0f
        }
        FsrsScheduler.previewIntervals(
            stability = flashcard.fsrsStability,
            difficulty = flashcard.fsrsDifficulty,
            repetitions = flashcard.repetitionCount,
            elapsedDays = elapsed
        )
    }

    val retrievabilityPercent = remember(flashcard) {
        val elapsed = if (flashcard.lastReviewedAt > 0) {
            ((System.currentTimeMillis() - flashcard.lastReviewedAt).toFloat() / (24f * 60f * 60f * 1000f)).coerceAtLeast(0f)
        } else {
            0f
        }
        (FsrsScheduler.calculateRetrievability(elapsed, flashcard.fsrsStability) * 100f).toInt()
    }

    val isCloze = flashcard.cardType == "CLOZE" || SmartCardParser.isClozeCard(flashcard.frontText)
    val isAnatomy = flashcard.cardType == "ANATOMY"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onFlip() },
        colors = CardDefaults.cardColors(containerColor = MedSurfaceDark),
        border = BorderStroke(1.dp, if (isFlipped) MedMint.copy(alpha = 0.5f) else MedBorderDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Card Type & FSRS Metrics Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Card Type Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isCloze -> MedTeal.copy(alpha = 0.2f)
                        isAnatomy -> Color(0xFFE67E22).copy(alpha = 0.2f)
                        else -> MedMint.copy(alpha = 0.15f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isCloze -> Icons.Default.EditNote
                                isAnatomy -> Icons.Default.Visibility
                                else -> Icons.Default.Quiz
                            },
                            contentDescription = null,
                            tint = when {
                                isCloze -> MedMint
                                isAnatomy -> Color(0xFFF39C12)
                                else -> MedMint
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when {
                                isCloze -> "بطاقة فراغات (Cloze)"
                                isAnatomy -> "تشريح وصور"
                                else -> "بطاقة ذكية"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCloze -> MedMint
                                isAnatomy -> Color(0xFFF39C12)
                                else -> MedMint
                            }
                        )
                    }
                }

                // FSRS Memory Health Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            retrievabilityPercent >= 80 -> MedMint.copy(alpha = 0.2f)
                            retrievabilityPercent >= 50 -> Color(0xFFF39C12).copy(alpha = 0.2f)
                            else -> MedCoral.copy(alpha = 0.2f)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = when {
                                    retrievabilityPercent >= 80 -> MedMint
                                    retrievabilityPercent >= 50 -> Color(0xFFF39C12)
                                    else -> MedCoral
                                },
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "توقع التذكر: $retrievabilityPercent%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    retrievabilityPercent >= 80 -> MedMint
                                    retrievabilityPercent >= 50 -> Color(0xFFF39C12)
                                    else -> MedCoral
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card Body
            if (isCloze) {
                ClozeCardContent(rawText = flashcard.frontText, isRevealed = isFlipped)
            } else if (isAnatomy) {
                AnatomyCardContent(
                    front = flashcard.frontText,
                    back = flashcard.backText,
                    imageUrl = flashcard.imageUrl,
                    isRevealed = isFlipped
                )
            } else {
                StandardCardContent(
                    front = flashcard.frontText,
                    back = flashcard.backText,
                    isFlipped = isFlipped
                )
            }

            // Explanation Section when flipped
            if (isFlipped && flashcard.explanation.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MedBorderDark)
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MedBackgroundDark.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MedMint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "إيضاح سريري / التعليل:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MedMint
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = flashcard.explanation,
                            fontSize = 12.sp,
                            color = MedTextPrimaryDark,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action / Rating Buttons
            if (!isFlipped) {
                Button(
                    onClick = onFlip,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer)
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipToBack,
                        contentDescription = null,
                        tint = MedMint,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إظهار الإجابة (اقلب البطاقة)", color = MedMint, fontWeight = FontWeight.Bold)
                }
            } else {
                Text(
                    text = "تقييم التذكر (خوارزمية FSRS الذكية):",
                    fontSize = 11.sp,
                    color = MedTextSecondaryDark,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Again
                    FsrsRatingButton(
                        label = "إعادة",
                        interval = intervals[ReviewGrade.AGAIN] ?: "<10د",
                        color = MedCoral,
                        modifier = Modifier.weight(1f),
                        onClick = { onAnswerGrade(ReviewGrade.AGAIN) }
                    )
                    // Hard
                    FsrsRatingButton(
                        label = "صعب",
                        interval = intervals[ReviewGrade.HARD] ?: "يوم",
                        color = Color(0xFFF39C12),
                        modifier = Modifier.weight(1f),
                        onClick = { onAnswerGrade(ReviewGrade.HARD) }
                    )
                    // Good
                    FsrsRatingButton(
                        label = "جيد",
                        interval = intervals[ReviewGrade.GOOD] ?: "3أيام",
                        color = MedMint,
                        modifier = Modifier.weight(1f),
                        onClick = { onAnswerGrade(ReviewGrade.GOOD) }
                    )
                    // Easy
                    FsrsRatingButton(
                        label = "سهل",
                        interval = intervals[ReviewGrade.EASY] ?: "8أيام",
                        color = Color(0xFF3498DB),
                        modifier = Modifier.weight(1f),
                        onClick = { onAnswerGrade(ReviewGrade.EASY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClozeCardContent(rawText: String, isRevealed: Boolean) {
    val segments = remember(rawText) { SmartCardParser.parseCloze(rawText) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "املأ الفراغ الطبي بالمصطلح الدقيق:",
            fontSize = 12.sp,
            color = MedTextSecondaryDark,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MedBackgroundDark,
            border = BorderStroke(1.dp, MedBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isRevealed) {
                    val questionText = SmartCardParser.formatQuestionSide(rawText)
                    Text(
                        text = questionText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MedTextPrimaryDark,
                        lineHeight = 24.sp
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                        Text(
                            text = buildString {
                                segments.forEach { seg ->
                                    if (seg.isCloze) {
                                        append("【${seg.answer}】")
                                    } else {
                                        append(seg.text)
                                    }
                                }
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MedMint,
                            lineHeight = 24.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnatomyCardContent(
    front: String,
    back: String,
    imageUrl: String,
    isRevealed: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "تحديد الهيكل التشريحي:",
            fontSize = 12.sp,
            color = MedTextSecondaryDark,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MedBackgroundDark,
            border = BorderStroke(1.dp, MedBorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Diagnostic Anatomy Icon / Graphic Box
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MedMintContainer.copy(alpha = 0.5f))
                        .border(1.dp, MedMint.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = MedMint,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = front,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MedTextPrimaryDark,
                    textAlign = TextAlign.Center
                )

                if (isRevealed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MedMint.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, MedMint.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "العضو / الهيكل المستهدف: $back",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MedMint,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardCardContent(
    front: String,
    back: String,
    isFlipped: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (!isFlipped) "السؤال / المفهوم الطبي:" else "الإجابة النموذجية:",
            fontSize = 12.sp,
            color = if (!isFlipped) MedTextSecondaryDark else MedMint,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (!isFlipped) front else back,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = if (!isFlipped) MedTextPrimaryDark else MedMint,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun FsrsRatingButton(
    label: String,
    interval: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = interval,
                fontSize = 10.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}
