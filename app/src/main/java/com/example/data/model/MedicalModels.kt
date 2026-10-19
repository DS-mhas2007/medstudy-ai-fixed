package com.example.data.model

data class MedicalTerm(
    val term: String = "",
    val arabicEquivalent: String = "",
    val definition: String = ""
)

data class ComparisonTable(
    val title: String = "",
    val columns: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList()
)

data class QuizQuestion(
    val question: String = "",
    val options: List<String> = emptyList(),
    val correctIndex: Int = 0,
    val explanation: String = ""
)

data class FlashcardItem(
    val front: String = "",
    val back: String = "",
    val explanation: String = ""
)

data class DiagramNode(
    val id: String,
    val label: String,
    val labelAr: String = "",
    val category: String = "mechanism", // definition, mechanism, anatomy, exam, warning, outcome
    val details: String = ""
)

data class DiagramEdge(
    val from: String,
    val to: String,
    val label: String = ""
)

data class StructuredDiagram(
    val title: String,
    val nodes: List<DiagramNode>,
    val edges: List<DiagramEdge>,
    val mermaidCode: String = ""
)

enum class ExplanationMode(val id: String, val titleAr: String, val titleEn: String) {
    SIMPLE("simple", "شرح مبسط", "Simple Explanation"),
    FIRST_YEAR_MED("first_year", "طالب طب سنة أولى", "1st-Year Medical Student"),
    DETAILED("detailed", "شرح علمي مفصل", "Detailed Scientific"),
    MECHANISM("mechanism", "آلية العمل والفيزيولوجيا", "Mechanism & Pathophysiology"),
    EXAM_FOCUSED("exam", "نقاط الامتحانات العالية الأهمية", "Exam-Focused High-Yield"),
    COMPARE("compare", "مقارنة بين مفهومين", "Compare Two Concepts"),
    CLINICAL("clinical", "الربط السريري", "Clinical Correlation"),
    ANALOGY("analogy", "الشرح بالتشبيه والقياس", "Explain Using Analogy")
}

enum class TranslationDirection(val id: String, val labelAr: String, val labelEn: String) {
    EN_TO_AR("en_to_ar", "الإنجليزية إلى العربية", "English to Arabic"),
    AR_TO_EN("ar_to_en", "العربية إلى الإنجليزية", "Arabic to English")
}

enum class MedicalSpecialty(val id: String, val labelAr: String, val labelEn: String) {
    GENERAL("general", "عام / مصطلحات طبية", "General Medical"),
    ANATOMY("anatomy", "علم التشريح", "Anatomy"),
    PHYSIOLOGY("physiology", "علم وظائف الأعضاء", "Physiology"),
    BIOCHEMISTRY("biochemistry", "الكيمياء الحيوية", "Biochemistry"),
    HISTOLOGY("histology", "علم الأنسجة", "Histology"),
    PHARMACOLOGY("pharmacology", "علم الأدوية", "Pharmacology"),
    PATHOLOGY("pathology", "علم الأمراض", "Pathology")
}
