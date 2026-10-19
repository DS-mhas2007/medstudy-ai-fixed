package com.example.core

import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ExamQuestion(
    val id: String,
    val questionEn: String,
    val questionAr: String,
    val options: List<String>,
    val correctIndex: Int,
    val explanationAr: String,
    val explanationEn: String = "",
    val topic: String,
    val specialty: String,
    val courseId: Long = 0L,
    val courseName: String = ""
)

@Serializable
data class ExamReport(
    val examTitle: String,
    val totalQuestions: Int,
    val correctCount: Int,
    val scorePercent: Int,
    val timeSpentSeconds: Long,
    val timeLimitSeconds: Long,
    val isPassed: Boolean,
    val specialtyScores: Map<String, Int>, // Specialty -> score %
    val weakTopics: List<String>,
    val strongTopics: List<String>,
    val detailedResults: List<QuestionResult>
)

@Serializable
data class QuestionResult(
    val question: ExamQuestion,
    val selectedIndex: Int,
    val isCorrect: Boolean
)

/**
 * High-yield medical examination and quiz engine.
 * Generates questions from lecture content or clinical banks, grades exams,
 * and compiles comprehensive weakness diagnostics.
 */
object MedicalExamEngine {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Curated high-yield clinical question bank covering core medical specialties.
     * Used for offline lecture exams and multi-subject mock simulations.
     */
    val SAMPLE_QUESTION_BANK: List<ExamQuestion> = listOf(
        ExamQuestion(
            id = "q1",
            questionEn = "A 58-year-old male with acute anterior STEMI presents with ST elevation in leads V1-V4. Which coronary artery is most likely occluded?",
            questionAr = "مريض يبلغ من العمر 58 عاماً يعاني من احتشاء عضلة القلب الحاد (STEMI) مع ارتفاع قطعة ST في الاتجاهات V1-V4. ما هو الشريان التاجي الأكثر احتمالاً للانسداد؟",
            options = listOf(
                "Left anterior descending artery (LAD) / الشريان التاجي الأيسر النازل",
                "Right coronary artery (RCA) / الشريان التاجي الأيمن",
                "Left circumflex artery (LCx) / الشريان المنعطف الأيسر",
                "Posterior descending artery (PDA) / الشريان الخلفي النازل"
            ),
            correctIndex = 0,
            explanationAr = "انسداد الشريان التاجي الأيسر النازل (LAD) يسبب احتشاء في الجدار الأمامي للبطين الأيسر والحاجز بين البطينين، وتظهر تغيرات تخطيط القلب في الاتجاهات V1-V4.",
            topic = "Myocardial Infarction",
            specialty = "Cardiology"
        ),
        ExamQuestion(
            id = "q2",
            questionEn = "Which anti-hypertensive class is contraindicated in pregnant patients due to teratogenicity causing fetal renal dysgenesis?",
            questionAr = "أي من فئات خافضات ضغط الدم التالية يُمنع استخدامها للحوامل لتسببها في تشوهات كلوية للجنين؟",
            options = listOf(
                "ACE Inhibitors / مثبطات الإنزيم المحول للأنجيوتنسين",
                "Methyldopa / ميثيل دوبا",
                "Labetalol / لابيترول",
                "Nifedipine / نيفيديبين"
            ),
            correctIndex = 0,
            explanationAr = "مثبطات ACE وARBs مشوهة للجنين (Teratogenic) وتسبب خلل في تنسج الكلى ونقص السائل الأمنيوسي، بينما ميثيل دوبا ولابيترول من أدوية الخط الأول الآمنة للحامل.",
            topic = "Hypertension Pharmacology",
            specialty = "Pharmacology"
        ),
        ExamQuestion(
            id = "q3",
            questionEn = "During an appendectomy, which anatomic landmark reliably converges at the base of the appendix on the cecum?",
            questionAr = "أثناء استئصال الزائدة الدودية، ما المعلم التشريحي الموثوق الذي يلتقي عند قاعدة الزائدة الدودية على الأعور؟",
            options = listOf(
                "Taeniae coli / الأشرطة القولونية",
                "Ileocecal valve / الصمام اللفائفي الأعوري",
                "Mesoappendix / مساريقا الزائدة",
                "Epiploic appendages / الزوائد الثربية"
            ),
            correctIndex = 0,
            explanationAr = "تلتقي الأشرطة القولونية الثلاثة (Taeniae coli) على الأعور عند نقطة منشأ قاعدة الزائدة الدودية وتعد الدليل الجراحي الذهبي لتحديد موقعها.",
            topic = "Gastrointestinal Anatomy",
            specialty = "Anatomy"
        ),
        ExamQuestion(
            id = "q4",
            questionEn = "A patient with severe emphysema exhibits which compensatory mechanism on arterial blood gas analysis?",
            questionAr = "مريض يعاني من انتفاخ الرئة الشديد (Emphysema)، ما هي الآلية التعويضية التي تظهر في تحليل غازات الدم الشرياني؟",
            options = listOf(
                "Renal bicarbonate retention / احتباس البيكربونات كلوياً",
                "Hyperventilation-induced hypocarbia / فرط تنفس مسبب لنقص ثاني أكسيد الكربون",
                "Decreased plasma buffer capacity / انخفاض سعة المحاليل المنظمة",
                "Respiratory alkalosis / قلاء تنفسي"
            ),
            correctIndex = 0,
            explanationAr = "في حالات احتباس ثاني أكسيد الكربون المزمن (الحماض التنفسي)، تعوض الكلى باحتباس وإعادة امتصاص بيكربونات الصوديوم (HCO3-) لضبط درجة حموضة الدم.",
            topic = "Acid-Base Balance",
            specialty = "Physiology"
        ),
        ExamQuestion(
            id = "q5",
            questionEn = "Which bacteria characteristically produces a golden pigment, coagulase positivity, and catalase positivity?",
            questionAr = "أي بكتيريا تتميز بإنتاج صبغة ذهبية وإيجابية اختبار الكواجيوليز والكاتاليز؟",
            options = listOf(
                "Staphylococcus aureus / المكورات العنقودية الذهبية",
                "Streptococcus pyogenes / المكورات العقدية المقيحة",
                "Staphylococcus epidermidis / المكورات العنقودية الجلدية",
                "Enterococcus faecalis / المكورات المعوية البرازية"
            ),
            correctIndex = 0,
            explanationAr = "تتميز المكورات العنقودية الذهبية (Staphylococcus aureus) بأنها موجبة الكاتاليز وموجبة الكواجيوليز وتنتج صبغة ذهبية تسمى staphyloxanthin.",
            topic = "Bacterial Pathogens",
            specialty = "Microbiology"
        ),
        ExamQuestion(
            id = "q6",
            questionEn = "A patient presents with sudden 'thunderclap' headache and nuchal rigidity. Lumbar puncture demonstrates xanthochromia. What is the diagnosis?",
            questionAr = "مريض يعاني من صداع شديد ومفاجئ كالصاعقة مع صلابة نقرة. أظهر البزل القطني اصفرار السائل (Xanthochromia). ما هو التشخيص؟",
            options = listOf(
                "Subarachnoid hemorrhage / نزيف تحت العنكبوتية",
                "Bacterial meningitis / التهاب السحايا الجرثومي",
                "Migraine with aura / شقيقة مصحوبة بأورة",
                "Temporal arteritis / التهاب الشريان الصدغي"
            ),
            correctIndex = 0,
            explanationAr = "الصداع الصاعق مع اصفرار السائل الدماغي الشوكي (Xanthochromia) ناتج عن تحلل كريات الدم الحمراء وهو علامة كلاسيكية على نزيف تحت العنكبوتية (Subarachnoid hemorrhage).",
            topic = "Cerebrovascular Pathology",
            specialty = "Neurology"
        ),
        ExamQuestion(
            id = "q7",
            questionEn = "Which receptor is competitively blocked by Naloxone to rapidly reverse opioid overdose induced respiratory depression?",
            questionAr = "أي مستقبل يُحصر تنافسياً بواسطة دواء النالوكسون لعكس تثبيط التنفس الناتج عن الجرعة الزائدة من الأفيونات؟",
            options = listOf(
                "Mu (μ) opioid receptor / مستقبل مو الأفيوني",
                "GABA-A receptor / مستقبل غابا أ",
                "Beta-2 adrenergic receptor / مستقبل بيتا 2 الأدريناليني",
                "NMDA receptor / مستقبل NMDA"
            ),
            correctIndex = 0,
            explanationAr = "النالوكسون هو مضاد تنافسي نقي لمستقبلات مو (Mu) الأفيونية، ويزيح الأفيون بسرعة ليعيد التنفس الطبيعي خلال دقائق.",
            topic = "Toxicology & Antidotes",
            specialty = "Pharmacology"
        ),
        ExamQuestion(
            id = "q8",
            questionEn = "In cardiac electrophysiology, which phase of the ventricular action potential corresponds to rapid depolarization via fast Na+ channels?",
            questionAr = "في فيزيولوجيا كهربية القلب، أي مرحلة من جهد الفعل البطيني تقابل إزالة الاستقطاب السريعة عبر قنوات الصوديوم السريعة؟",
            options = listOf(
                "Phase 0 / المرحلة 0",
                "Phase 1 / المرحلة 1",
                "Phase 2 / المرحلة 2",
                "Phase 3 / المرحلة 3"
            ),
            correctIndex = 0,
            explanationAr = "المرحلة 0 (Phase 0) ناتجة عن تدفق سريع لأيونات الصوديوم (Inward Na+ current) عبر قنوات الصوديوم المعتمدة على الفولتية، مما يسبب صعوداً حاداً في جهد الغشاء.",
            topic = "Cardiac Action Potential",
            specialty = "Physiology"
        )
    )

    /**
     * Builds a tailored quiz for a lecture or chapter.
     * Can extract terms from text or generate from specialty topics.
     */
    fun generateLectureQuiz(
        lectureTitle: String,
        lectureText: String,
        courseId: Long = 0L,
        courseName: String = "",
        count: Int = 5
    ): List<ExamQuestion> {
        val questions = mutableListOf<ExamQuestion>()

        // Check if lecture matches specific specialties
        val matchedQuestions = SAMPLE_QUESTION_BANK.filter { q ->
            lectureText.contains(q.topic, ignoreCase = true) ||
                lectureText.contains(q.specialty, ignoreCase = true) ||
                lectureTitle.contains(q.specialty, ignoreCase = true)
        }

        if (matchedQuestions.isNotEmpty()) {
            questions.addAll(matchedQuestions.map { it.copy(courseId = courseId, courseName = courseName) })
        }

        // Fill remaining with diversified clinical questions
        val pool = SAMPLE_QUESTION_BANK.shuffled()
        for (q in pool) {
            if (questions.size >= count) break
            if (questions.none { it.id == q.id }) {
                questions.add(q.copy(courseId = courseId, courseName = courseName))
            }
        }

        return questions.take(count)
    }

    /**
     * Creates a mixed multi-course exam from selected courses.
     */
    fun createMultiCourseExam(
        selectedCourses: List<Pair<Long, String>>, // (courseId, courseName)
        questionCount: Int = 10
    ): List<ExamQuestion> {
        val questions = mutableListOf<ExamQuestion>()
        val pool = SAMPLE_QUESTION_BANK.shuffled()

        var courseIndex = 0
        for (q in pool) {
            if (questions.size >= questionCount) break
            val targetCourse = if (selectedCourses.isNotEmpty()) {
                selectedCourses[courseIndex % selectedCourses.size]
            } else {
                0L to "طب عام"
            }
            questions.add(
                q.copy(
                    id = "exam_${q.id}_${questions.size}",
                    courseId = targetCourse.first,
                    courseName = targetCourse.second
                )
            )
            courseIndex++
        }

        return questions
    }

    /**
     * Grades an exam session and builds an analytical diagnostic report.
     */
    fun evaluateExam(
        examTitle: String,
        questions: List<ExamQuestion>,
        answers: Map<Int, Int>, // question index -> selected option index
        timeSpentSeconds: Long,
        timeLimitSeconds: Long
    ): ExamReport {
        var correctCount = 0
        val detailedResults = mutableListOf<QuestionResult>()
        val specialtyStats = mutableMapOf<String, Pair<Int, Int>>() // specialty -> (correct, total)

        questions.forEachIndexed { index, question ->
            val selected = answers[index] ?: -1
            val isCorrect = selected == question.correctIndex
            if (isCorrect) correctCount++

            detailedResults.add(QuestionResult(question, selected, isCorrect))

            val current = specialtyStats.getOrDefault(question.specialty, 0 to 0)
            specialtyStats[question.specialty] = (current.first + if (isCorrect) 1 else 0) to (current.second + 1)
        }

        val total = questions.size.coerceAtLeast(1)
        val scorePercent = ((correctCount.toFloat() / total.toFloat()) * 100f).roundToInt()

        val specialtyScores = specialtyStats.mapValues { (_, pair) ->
            if (pair.second > 0) ((pair.first.toFloat() / pair.second.toFloat()) * 100f).roundToInt() else 0
        }

        val weakTopics = specialtyScores.filter { it.value < 70 }.keys.toList()
        val strongTopics = specialtyScores.filter { it.value >= 70 }.keys.toList()

        return ExamReport(
            examTitle = examTitle,
            totalQuestions = total,
            correctCount = correctCount,
            scorePercent = scorePercent,
            timeSpentSeconds = timeSpentSeconds,
            timeLimitSeconds = timeLimitSeconds,
            isPassed = scorePercent >= 60,
            specialtyScores = specialtyScores,
            weakTopics = weakTopics,
            strongTopics = strongTopics,
            detailedResults = detailedResults
        )
    }
}
