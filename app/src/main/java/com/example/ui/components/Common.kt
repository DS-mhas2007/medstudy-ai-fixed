package com.example.ui.components

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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preference.UserSettings
import com.example.data.remote.AiRequestConfig
import com.example.data.remote.MedicalAiResult
import com.example.ui.theme.MedAmber
import com.example.ui.theme.MedAmberContainer
import com.example.ui.theme.MedBorderDark
import com.example.ui.theme.MedCardDark
import com.example.ui.theme.MedCoral
import com.example.ui.theme.MedCoralContainer
import com.example.ui.theme.MedMint
import com.example.ui.theme.MedTextPrimaryDark
import com.example.ui.theme.MedTextSecondaryDark

fun UserSettings.toAiConfig() = AiRequestConfig(
    apiKey = apiKey,
    model = aiModel,
    consentGranted = hasAcceptedAiConsent
)

/**
 * Shown above every result that did NOT come from the AI model, so offline sample content is never mistaken
 * for a fresh, personalised answer.
 */
@Composable
fun AiStatusBanner(result: MedicalAiResult, onOpenSettings: (() -> Unit)? = null) {
    if (!result.isOfflineGenerated) {
        if (result.fromCache) {
            Text(
                text = "من الذاكرة المحلية: هذا الجواب محفوظ سابقاً ولم يستهلك من حصتك.",
                style = MaterialTheme.typography.labelSmall,
                color = MedTextSecondaryDark
            )
        }
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MedAmberContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MedAmber.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudOff, contentDescription = null, tint = MedAmber, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "محتوى محلي جاهز، وليس من الذكاء الاصطناعي",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MedAmber
                )
            }
            if (result.offlineReason.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(result.offlineReason, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark)
            }
            if (onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) { Text("فتح الإعدادات", color = MedMint, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
fun AiErrorCard(message: String, onOpenSettings: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MedCoralContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MedCoral.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MedCoral, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تعذر إكمال الطلب", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MedCoral)
            }
            Text(message, style = MaterialTheme.typography.bodySmall, color = MedTextPrimaryDark)
            if (onOpenSettings != null) {
                TextButton(onClick = onOpenSettings) { Text("فتح الإعدادات", color = MedMint, fontSize = 12.sp) }
            }
        }
    }
}

/** Reminder that generated study material must be checked against the official course sources. */
@Composable
fun MedicalDisclaimer(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MedCardDark),
        border = BorderStroke(1.dp, MedBorderDark)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MedTextSecondaryDark, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "مادة تعليمية للمراجعة فقط وليست نصيحة طبية. تأكد دائماً من المعلومات من محاضراتك ومراجعك المعتمدة، خصوصاً الأرقام والجرعات.",
                style = MaterialTheme.typography.labelSmall,
                color = MedTextSecondaryDark
            )
        }
    }
}
