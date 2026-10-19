package com.example.data.sample

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {
    fun generateSampleLecturePdf(context: Context): File {
        val cacheFile = File(context.filesDir, "lecture_medical_terminology.pdf")
        if (cacheFile.exists() && cacheFile.length() > 0) {
            return cacheFile
        }

        val document = PdfDocument()
        val pageWidth = 595 // standard A4 points
        val pageHeight = 842

        val titlePaint = Paint().apply {
            color = Color.rgb(1, 10, 25)
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val headerPaint = Paint().apply {
            color = Color.rgb(16, 37, 71)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val textPaint = Paint().apply {
            color = Color.rgb(30, 41, 59)
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val badgePaint = Paint().apply {
            color = Color.rgb(96, 245, 210)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // --- PAGE 1: Medical Word Structure ---
        val pageInfo1 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page1 = document.startPage(pageInfo1)
        val canvas1 = page1.canvas

        // Header decorative bar
        canvas1.drawRect(0f, 0f, pageWidth.toFloat(), 40f, badgePaint)
        canvas1.drawText("MedStudy AI — Academic Lecture Series", 30f, 25f, titlePaint)

        canvas1.drawText("Lecture 1: Fundamental Components of Medical Terms", 30f, 75f, titlePaint)
        canvas1.drawText("Course: Medical Terminology (MED101) — Faculty of Medicine", 30f, 95f, textPaint)

        canvas1.drawText("1. Overview of Medical Word Building", 30f, 130f, headerPaint)
        val p1Lines = listOf(
            "Medical terminology is a specialized language used by healthcare professionals to communicate",
            "accurately and concisely about the human body, diseases, procedures, and pharmacology.",
            "Most medical terms are derived from classical Greek and Latin word elements.",
            "Understanding the four core building blocks enables a medical student to decipher thousands of terms:",
            "",
            "• Word Root (الجذر): The fundamental foundation of the term that establishes its primary meaning.",
            "  Example: cardi- (heart), gastr- (stomach), neur- (nerve), nephr- (kidney), hepat- (liver).",
            "",
            "• Combining Vowel (حرف العلة الرابط): Usually the letter 'o', inserted between roots or before suffixes.",
            "  Example: cardi/o, gastr/o, enter/o. Used to ease pronunciation when the suffix begins with a consonant.",
            "",
            "• Prefix (البادئة): Placed before the word root to modify its meaning regarding location, time, or number.",
            "  Example: brady- (slow), tachy- (fast), sub- (under), epi- (above), dys- (difficult or painful).",
            "",
            "• Suffix (اللاحقة): Attached to the end of the root to specify a disease condition, symptom, or diagnostic test.",
            "  Example: -itis (inflammation), -ectomy (surgical excision), -megaly (enlargement), -algia (pain)."
        )
        var yPos = 155f
        for (line in p1Lines) {
            canvas1.drawText(line, 35f, yPos, textPaint)
            yPos += 18f
        }

        canvas1.drawText("2. Clinical Example Analysis: Gastroenteritis", 30f, yPos + 20f, headerPaint)
        yPos += 45f
        val exLines = listOf(
            "Consider the clinical diagnosis: GASTROENTERITIS (التهاب المعدة والأمعاء)",
            "• Root 1: Gastr/o (stomach - المعدة)",
            "• Root 2: Enter/o (intestines - الأمعاء)",
            "• Suffix: -itis (inflammation - التهاب)",
            "Translation & Meaning: An acute inflammation of the mucous membranes of both stomach and intestine.",
            "Clinical pearl: The suffix is interpreted first, followed by the prefix and then the roots."
        )
        for (line in exLines) {
            canvas1.drawText(line, 35f, yPos, textPaint)
            yPos += 18f
        }

        canvas1.drawText("Page 1 of 3 — MedStudy AI Educational Companion", 200f, 810f, textPaint)
        document.finishPage(page1)

        // --- PAGE 2: Prefixes & Suffixes ---
        val pageInfo2 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
        val page2 = document.startPage(pageInfo2)
        val canvas2 = page2.canvas

        canvas2.drawRect(0f, 0f, pageWidth.toFloat(), 40f, badgePaint)
        canvas2.drawText("MedStudy AI — Academic Lecture Series", 30f, 25f, titlePaint)
        canvas2.drawText("Section 2: High-Yield Prefixes and Anatomical Directions", 30f, 75f, titlePaint)

        yPos = 110f
        val p2Lines = listOf(
            "Anatomical Directional Terminology (المصطلحات الاتجاهية التشريحية):",
            "• Anterior / Ventral: Toward the front of the body (أمامي / بطني).",
            "• Posterior / Dorsal: Toward the back of the body (خلفي / ظهري).",
            "• Superior / Cranial: Toward the head or upper part of a structure (علوي / قحفي).",
            "• Inferior / Caudal: Away from the head, toward the feet (سفلي / ذيلي).",
            "• Medial: Nearer to the midline of the body (إنسي).",
            "• Lateral: Farther from the midline of the body (وحشي).",
            "• Proximal: Closer to the point of attachment of a limb (داني / قريب).",
            "• Distal: Farther from the point of attachment of a limb (قاصي / بعيد).",
            "",
            "Common High-Yield Prefixes in First-Year Medicine:",
            "1. Brady- (بطيء): Bradycardia = resting heart rate < 60 bpm.",
            "2. Tachy- (سريع): Tachycardia = resting heart rate > 100 bpm.",
            "3. Hyper- (مفرط / زائد): Hyperkalemia = elevated serum potassium level.",
            "4. Hypo- (ناقص / منخفض): Hyponatremia = low serum sodium concentration.",
            "5. Dys- (عسر / صعوبة): Dyspnea = shortness of breath / difficult breathing."
        )
        for (line in p2Lines) {
            canvas2.drawText(line, 35f, yPos, textPaint)
            yPos += 20f
        }

        canvas2.drawText("Page 2 of 3 — MedStudy AI Educational Companion", 200f, 810f, textPaint)
        document.finishPage(page2)

        // --- PAGE 3: Neurophysiology & Action Potential Summary ---
        val pageInfo3 = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
        val page3 = document.startPage(pageInfo3)
        val canvas3 = page3.canvas

        canvas3.drawRect(0f, 0f, pageWidth.toFloat(), 40f, badgePaint)
        canvas3.drawText("MedStudy AI — Academic Lecture Series", 30f, 25f, titlePaint)
        canvas3.drawText("Section 3: Excitable Tissues & Action Potential", 30f, 75f, titlePaint)

        yPos = 110f
        val p3Lines = listOf(
            "Neurophysiology: The Action Potential in Excitable Membranes:",
            "• Resting Membrane Potential (RMP): Typically -70 mV across neural membranes.",
            "• Sodium-Potassium Pump (Na+/K+ ATPase): Actively transports 3 Na+ out and 2 K+ in.",
            "• Depolarization: Stimulus brings membrane to threshold (-55 mV), triggering voltage-gated Na+ channels.",
            "• Repolarization: Voltage-gated K+ channels open as Na+ channels inactivate.",
            "• Synaptic Transmission at the Neuromuscular Junction (NMJ):",
            "  1. Action potential invades the axon terminal.",
            "  2. Depolarization opens voltage-gated Ca2+ channels.",
            "  3. Ca2+ influx causes exocytosis of acetylcholine (ACh) into the synaptic cleft.",
            "  4. ACh binds nicotinic receptors on the motor end-plate, inducing muscle contraction.",
            "  5. Acetylcholinesterase (AChE) rapidly cleaves ACh to terminate the signal."
        )
        for (line in p3Lines) {
            canvas3.drawText(line, 35f, yPos, textPaint)
            yPos += 20f
        }

        canvas3.drawText("Page 3 of 3 — MedStudy AI Educational Companion", 200f, 810f, textPaint)
        document.finishPage(page3)

        FileOutputStream(cacheFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return cacheFile
    }
}
