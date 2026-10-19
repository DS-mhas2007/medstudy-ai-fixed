package com.example.ui.components

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.SmartCardParser
import com.example.ui.theme.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet modal for scanning textbook pages, lecture slides, or anatomy diagrams
 * using on-device ML Kit OCR and generating smart cards or notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerSheet(
    courseId: Long?,
    lectureId: Long?,
    onDismiss: () -> Unit,
    onCardCreated: (front: String, back: String, isCloze: Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var selectedClozeTerm by remember { mutableStateOf("") }
    var customQuestion by remember { mutableStateOf("") }
    var customAnswer by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) } // 0: Cloze mode, 1: Q&A mode

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isScanning = true
            scope.launch {
                try {
                    val inputImage = withContext(Dispatchers.IO) {
                        InputImage.fromFilePath(context, uri)
                    }
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(inputImage)
                        .addOnSuccessListener { visionText ->
                            val cleaned = SmartCardParser.cleanOcrText(visionText.text)
                            recognizedText = cleaned
                            isScanning = false
                            if (cleaned.isBlank()) {
                                Toast.makeText(context, "لم يتم العثور على نص واضح في الصورة", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener { e ->
                            isScanning = false
                            Toast.makeText(context, "تعذر قراءة الصورة: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } catch (e: Exception) {
                    isScanning = false
                    Toast.makeText(context, "خطأ أثناء معالجة الصورة: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MedSurfaceDark,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MedMint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "الماسح الضوئي الذكي (OCR)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedTextPrimaryDark
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = MedTextSecondaryDark)
                }
            }

            Text(
                text = "التقط صورة لصفحة المحاضرة أو الشريحة لقراءتها وتحويلها لبطاقة فراغات فوراً.",
                fontSize = 12.sp,
                color = MedTextSecondaryDark,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Select Image Button
            Button(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MedMintContainer)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MedMint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (recognizedText.isBlank()) "اختر صورة المحاضرة أو لقطة الشاشة" else "اختيار صورة أخرى",
                    color = MedMint,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(color = MedMint, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("جاري استخراج المصطلحات والنصوص عبر ML Kit...", fontSize = 13.sp, color = MedMint)
                }
            }

            if (recognizedText.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))

                // Text Display / Edit Box
                OutlinedTextField(
                    value = recognizedText,
                    onValueChange = { recognizedText = it },
                    label = { Text("النص المستخرج من الصورة", color = MedTextSecondaryDark) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MedMint,
                        unfocusedBorderColor = MedBorderDark,
                        focusedTextColor = MedTextPrimaryDark,
                        unfocusedTextColor = MedTextPrimaryDark
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Suggest keywords to occlude for cloze
                val keywords = remember(recognizedText) {
                    SmartCardParser.suggestClozeKeywords(recognizedText)
                }

                if (keywords.isNotEmpty()) {
                    Text(
                        text = "اختر مصطلحاً لإخفائه كفراغ (Cloze):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MedMint
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        keywords.take(4).forEach { kw ->
                            FilterChip(
                                selected = selectedClozeTerm == kw,
                                onClick = {
                                    selectedClozeTerm = if (selectedClozeTerm == kw) "" else kw
                                },
                                label = { Text(kw, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MedMint,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action: Create Cloze Card
                Button(
                    onClick = {
                        val term = selectedClozeTerm.ifBlank { keywords.firstOrNull() ?: "" }
                        val clozeText = if (term.isNotBlank()) {
                            SmartCardParser.wrapWordAsCloze(recognizedText, term)
                        } else {
                            recognizedText
                        }
                        onCardCreated(clozeText, term, true)
                        Toast.makeText(context, "تم حفظ بطاقة الفراغات بنجاح!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MedMint)
                ) {
                    Icon(Icons.Default.PostAdd, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("حفظ كبطاقة فراغات ذكية (Cloze)", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action: Create Standard Q&A Card
                OutlinedButton(
                    onClick = {
                        onCardCreated(recognizedText, "راجع المفهوم في المحاضرة", false)
                        Toast.makeText(context, "تم حفظ بطاقة السؤال بنجاح!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MedBorderDark)
                ) {
                    Text("حفظ كبطاقة سؤال وجواب عادية", color = MedTextPrimaryDark)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
