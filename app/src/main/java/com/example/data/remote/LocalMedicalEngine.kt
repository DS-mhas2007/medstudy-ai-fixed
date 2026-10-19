package com.example.data.remote

import com.example.data.model.*

/**
 * Small hand-written offline study content (action potential, autonomic nervous system, acetylcholine).
 *
 * It is NOT a translator or a general explainer: it only answers the few topics it was written for, and the
 * service falls back to it only for those topics. Everything it returns is marked [MedicalAiResult.isOfflineGenerated]
 * so the UI never presents it as AI output.
 */
class LocalMedicalEngine {

    fun hasCuratedTranslation(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("acetylcholine") || lower.contains("action potential") || lower.contains("synapse")
    }

    fun hasCuratedExplanation(topic: String): Boolean {
        val lower = topic.lowercase()
        return lower.contains("action potential") || lower.contains("depolarization") || lower.contains("جهد الفعل") ||
            lower.contains("autonomic") || lower.contains("sympathetic") || lower.contains("parasympathetic") || lower.contains("الودي")
    }

    fun generateLocalTranslation(text: String, direction: TranslationDirection, specialty: MedicalSpecialty): MedicalAiResult {
        val lower = text.lowercase()
        return when {
            lower.contains("acetylcholine") || lower.contains("action potential") || lower.contains("synapse") -> {
                MedicalAiResult(
                    title = "الترجمة الطبية: النواقل العصبية والجهد الغشائي",
                    topic = "Neurotransmission & Acetylcholine",
                    mode = "translation",
                    originalText = text,
                    translation = "الأسيتيل كولين (ACh) هو ناقل عصبي رئيسي في الجهازين العصبيين المحيطي والمركزي، يعمل على تفعيل المستقبلات النيكوتينية والمسكارينية لتحفيز التقلص العضلي والنقل المشبكي.",
                    simpleExplanation = "الأسيتيل كولين هو الرسول الكيميائي الذي يربط بين الأعصاب والعضلات، وبدونه لا يمكن حدوث تقلص العضلات الإرادية أو وظائف الجهاز نظير الودي.",
                    detailedExplanation = "يتم تصنيع الأسيتيل كولين في النهايات العصبية من الكولين والأسيتيل مرافق الإنزيم أ (Acetyl-CoA) بواسطة إنزيم Choline Acetyltransferase (ChAT). بعد إفرازه في الفالق المشبكي، يتم تفكيكه فوراً بواسطة Acetylcholinesterase (AChE) إلى كولين وأسيتات لمنع التحفيز المستمر.",
                    keyTerms = listOf(
                        MedicalTerm("Acetylcholine (ACh)", "الأسيتيل كولين", "ناقل عصبي كوليني أساسي في الوصل العضلي العصبي"),
                        MedicalTerm("Neuromuscular Junction (NMJ)", "الوصل العضلي العصبي", "المشبك الكيميائي بين العصبون الحركي والألياف العضلية"),
                        MedicalTerm("Acetylcholinesterase (AChE)", "أستيل كولين إستراز", "الإنزيم المسؤول عن الحلمهة السريعة للأسيتيل كولين"),
                        MedicalTerm("Nicotinic Receptors", "مستقبلات نيكوتينية", "قنوات أيونية مبوبة بالربيطة (Ligand-gated ion channels)")
                    ),
                    mechanismSteps = listOf(
                        "وصول جهد الفعل (Action Potential) إلى نهاية المحور العصبي",
                        "انفتاح قنوات الكالسيوم المبوبة بالجهد وتدفق Ca²⁺ للداخل",
                        "التحام الحويصلات المشبكية بالغشاء وإفراز الأسيتيل كولين في الفالق المشبكي",
                        "ارتباط الأسيتيل كولين بمستقبلاته على الغشاء بعد المشبكي",
                        "إزالة الاستقطاب (Depolarization) وتوليد جهد اللوحة الانتهائية",
                        "تفكيك سريع بواسطة إنزيم أستيل كولين إستراز"
                    ),
                    keyPoints = listOf(
                        "الأسيتيل كولين هو الناقل العصبي الوحيد في الوصل العضلي العصبي (NMJ)",
                        "تثبيط AChE بواسطة الأدوية (مثل Neostigmine) يستخدم في علاج الوهن العضلي الوبيل (Myasthenia Gravis)",
                        "الغازات العصبية والمبيدات الحشرية الفوسفورية العضوية تثبط AChE بشكل لا عكوس مسببة أزمة كولينية"
                    ),
                    misconceptions = listOf(
                        "الاعتقاد بأن الأسيتيل كولين ناقل مثير دائماً؛ في القلب يعمل عبر مستقبلات M2 لتقليل معدل ضربات القلب (تأثير مثبط)"
                    ),
                    comparisonTable = null,
                    diagramType = "node_flow",
                    diagramCode = "graph TD\n  A[جهد الفعل العصبي] --> B[تدفق أيونات Ca²⁺]\n  B --> C[إفراز الأسيتيل كولين ACh]\n  C --> D[تفعيل المستقبلات النيكوتينية]\n  D --> E[تدفق Na⁺ وتقلص العضلة]\n  C --> F[تفكيك بواسطة AChE]",
                    diagramNodes = listOf(
                        DiagramNode("A", "Action Potential", "جهد الفعل العصبي", "mechanism", "إشارة كهربائية تنتشر عبر المحور العصبوني"),
                        DiagramNode("B", "Ca²⁺ Influx", "تدفق أيونات الكالسيوم", "mechanism", "فتح قنوات الكالسيوم المبوبة بالفولتية"),
                        DiagramNode("C", "ACh Exocytosis", "إفراز الأسيتيل كولين", "anatomy", "التحام الحويصلات وإطلاق الناقل في الفالق المشبكي"),
                        DiagramNode("D", "Receptor Binding", "ارتباط بالمستقبلات", "definition", "تفعيل مستقبلات النيكوتين على اللوحة الحركية"),
                        DiagramNode("E", "Muscle Contraction", "تقلص العضلة", "outcome", "تدفق Na⁺ وإزالة الاستقطاب"),
                        DiagramNode("F", "AChE Cleavage", "تفكيك بالإستراز", "warning", "حلمهة سريعة لمنع التسمم والتشنج المستمر")
                    ),
                    diagramEdges = listOf(
                        DiagramEdge("A", "B", "يفتح القنوات"),
                        DiagramEdge("B", "C", "يحفز الإفراز"),
                        DiagramEdge("C", "D", "يرتبط"),
                        DiagramEdge("D", "E", "يسبب التقلص"),
                        DiagramEdge("C", "F", "إنهاء الإشارة")
                    ),
                    questions = listOf(
                        QuizQuestion(
                            "ما هو الإنزيم المسؤول عن تفكيك الأسيتيل كولين في المشبك العصبوني العضلي؟",
                            listOf("Acetylcholinesterase", "Choline Acetyltransferase", "Monoamine Oxidase", "Tyrosine Hydroxylase"),
                            0,
                            "يعمل إنزيم Acetylcholinesterase بسرعة فائقة على شطر الأسيتيل كولين لمنع استمرار إزالة الاستقطاب."
                        )
                    ),
                    flashcards = listOf(
                        FlashcardItem("Acetylcholine (ACh)", "الأسيتيل كولين: ناقل عصبي أساسي للوصل العصبي العضلي والجهاز العصبي المستقل", "ACh is synthesized from Choline + Acetyl-CoA."),
                        FlashcardItem("Myasthenia Gravis target", "المستقبلات النيكوتينية للأسيتيل كولين (مهاجمة بالأجسام المضادة الذاتية)", "Autoantibodies block or destroy nicotinic ACh receptors.")
                    ),
                    translationNotes = "كلمة 'Choline' مشتقة من اليونانية وتعني المرارة أو الصفراء حيث عُزلت لأول مرة. في النصوص الطبية يُختصر غالباً كـ ACh.",
                    alternativeTranslation = "يمكن ترجمته أدبياً بـ 'خام الأسيتيل كولين'، لكن الاصطلاح الأكاديمي المعتمد في كليات الطب هو 'الأسيتيل كولين'.",
                    isOfflineGenerated = true
                )
            }
            else -> unavailable(text, "translation")
        }
    }

    fun generateLocalExplanation(
        topic: String,
        mode: ExplanationMode,
        lang: String,
        sourceDoc: String?,
        sourcePage: Int?
    ): MedicalAiResult {
        val lower = topic.lowercase()
        return when {
            lower.contains("action potential") || lower.contains("depolarization") || lower.contains("جهد الفعل") -> {
                MedicalAiResult(
                    title = "جهد الفعل والنقل العصبي (Action Potential)",
                    topic = "Cell Physiology: Action Potential",
                    mode = mode.id,
                    originalText = topic,
                    translation = "جهد الفعل: تغير كهربائي سريع ومؤقت ينعكس فيه جهد الغشاء العصبوني نتيجة تدفق أيوني منظم عبر قنوات الجهد.",
                    simpleExplanation = "تخيل جهد الفعل مثل شعلة تتقدم على طول فتيل بارود: موجة كهربائية تندفع سريعاً ولا تتراجع، تبدأ عندما يصل التنبيه إلى عتبة محددة، مما يفتح الأبواب لدخول الصوديوم وخروج البوتاسيوم بالتعاقب.",
                    detailedExplanation = "في حالة الراحة (Resting Membrane Potential)، يكون الجهد الداخلي حوالي -70 mV مدعوماً بمضخة Na⁺/K⁺ ATPase وقنوات تسريب K⁺. عند وصول منبه كافٍ للوصول إلى العتبة (Threshold ≈ -55 mV)، تنفتح قنوات الصوديوم المبوبة بالجهد (Voltage-gated Na⁺ channels) سريعاً مسببة إزالة الاستقطاب (Depolarization) إلى +30 mV. تليها عودة الاستقطاب (Repolarization) بفعل تدفق البوتاسيوم للخارج، ثم فرط الاستقطاب (Hyperpolarization) قبل العودة للاستقرار.",
                    keyTerms = listOf(
                        MedicalTerm("Resting Potential (-70mV)", "جهد الغشاء أثناء الراحة", "الاستقطاب الأساسي للخلية قبل أي تنبيه"),
                        MedicalTerm("Threshold (-55mV)", "عتبة التنبيه", "الجهد الحرج الذي يؤدي لتفعيل قانون الكل أو العدم"),
                        MedicalTerm("Depolarization", "إزالة الاستقطاب", "انعكاس الشحنة بدخول الصوديوم السريع عبر قنوات الفولتية"),
                        MedicalTerm("Repolarization", "عودة الاستقطاب", "انغلاق قنوات Na⁺ وتدفق أيونات K⁺ خارج الخلية"),
                        MedicalTerm("Refractory Period", "فترة الحِران / العصيان", "فترة يستحيل (مطلق) أو يصعب (نسبي) توليد جهد فعل جديد خلالها")
                    ),
                    mechanismSteps = listOf(
                        "1. مرحلة الراحة (Resting State): بقاء الجهد عند -70 mV تحت سيطرة مضخة Na⁺/K⁺ (3 Na⁺ للخارج مقابل 2 K⁺ للداخل)",
                        "2. بلوغ العتبة (Threshold ≈ -55 mV): يفتح قنوات Na⁺ المبوبة بالفولتية استجابة للتنبيه",
                        "3. إزالة الاستقطاب السريعة (Rapid Depolarization): تدفق هائل لـ Na⁺ يجعل داخل الغشاء موجباً (+30 mV)",
                        "4. عودة الاستقطاب (Repolarization): تعطل قنوات الصوديوم وانفتاح قنوات البوتاسيوم Voltage-gated K⁺",
                        "5. فرط الاستقطاب المؤقت (Hyperpolarization): استمرار خروج K⁺ ينزل الجهد إلى نحو -85 mV",
                        "6. العودة للراحة (Recovery): إعادة توازن الأيونات بواسطة مضخة الصوديوم والبوتاسيوم"
                    ),
                    keyPoints = listOf(
                        "قانون الكل أو العدم (All-or-None Principle): إما أن يتولد جهد الفعل بكامل قوته أو لا يتولد مطلقاً",
                        "فترة الحران المطلق (Absolute Refractory Period) تضمن انتشار السيال باتجاه واحد فقط للأمام",
                        "الميالين (Myelin sheath) يزيد سرعة التوصيل 10 أضعاف عبر التوصيل القفزي (Saltatory Conduction)"
                    ),
                    misconceptions = listOf(
                        "الاعتقاد بأن مضخة الصوديوم/البوتاسيوم هي التي تولد جهد الفعل؛ دورها الحقيقي هو الحفاظ على التدرج التركيزي وليس التغير اللحظي السريع",
                        "الظن بأن شدة الإحساس تزيد من ارتفاع فولتية جهد الفعل؛ بل تزيد من تردد (Frequency) الجهود وليس سعتها (Amplitude)"
                    ),
                    comparisonTable = ComparisonTable(
                        title = "مقارنة فترتي الحِران (Refractory Periods)",
                        columns = listOf("المعيار", "الحِران المطلق (Absolute)", "الحِران النسبي (Relative)"),
                        rows = listOf(
                            listOf("حالة قنوات Na⁺", "معطلة تماماً (Inactivated)", "عائدة لحالة الراحة وقابلة للفتح"),
                            listOf("إمكانية توليد جهد جديد", "مستحيل تماماً بأي قوة منبه", "ممكن بشرط وجود منبه فائق القوة (Suprathreshold)"),
                            listOf("الأهمية الفسيولوجية", "تمنع تداخل الإشارات وتضمن الاتجاهية", "تحدد التردد الأقصى لإطلاق النبضات")
                        )
                    ),
                    diagramType = "node_flow",
                    diagramCode = "graph TD\n  A[منبه أولي] --> B{بلوغ العتبة -55mV?}\n  B -- نعم --> C[فتح قنوات Na⁺ السريعة]\n  C --> D[إزالة الاستقطاب إلى +30mV]\n  D --> E[غلق قنوات Na⁺ وفتح قنوات K⁺]\n  E --> F[عودة وفرط الاستقطاب]\n  F --> G[استعادة الراحة -70mV]\n  B -- لا --> H[جهد تدرجي متلاشٍ]",
                    diagramNodes = listOf(
                        DiagramNode("A", "Stimulus", "المنبه الأولي", "mechanism", "إشارة عصبية أو حسية تغير استقطاب الغشاء"),
                        DiagramNode("B", "Threshold", "عتبة التنبيه -55mV", "exam", "نقطة اللاعودة وقانون الكل أو العدم"),
                        DiagramNode("C", "Na⁺ Influx", "تدفق أيونات الصوديوم", "anatomy", "انفتاح بوابات التفعيل السريعة"),
                        DiagramNode("D", "Overshoot (+30mV)", "ذروة الجهد", "outcome", "انعكاس الشحنة الداخلية لتصبح موجبة"),
                        DiagramNode("E", "K⁺ Efflux", "خروج أيونات البوتاسيوم", "mechanism", "انفتاح بوابات K⁺ البطيئة"),
                        DiagramNode("F", "Hyperpolarization", "فرط الاستقطاب", "warning", "وصول الجهد مؤقتاً إلى -85mV"),
                        DiagramNode("G", "Resting Potential", "العودة للراحة -70mV", "definition", "استعادة التوازن بمضخة Na⁺/K⁺")
                    ),
                    diagramEdges = listOf(
                        DiagramEdge("A", "B", "يغير الجهد"),
                        DiagramEdge("B", "C", "يحقق الشرط"),
                        DiagramEdge("C", "D", "يزيل الاستقطاب"),
                        DiagramEdge("D", "E", "يفعل بوابات K⁺"),
                        DiagramEdge("E", "F", "يعيد الاستقطاب"),
                        DiagramEdge("F", "G", "يعيد الضبط")
                    ),
                    questions = listOf(
                        QuizQuestion(
                            "ما هو الحدث الأيوني المسؤول بشكل رئيسي عن مرحلة إزالة الاستقطاب (Depolarization) في العصبون؟",
                            listOf("تدفق أيونات Na⁺ السريع إلى داخل الخلية", "تدفق أيونات K⁺ إلى خارج الخلية", "ضخ أيونات Ca²⁺ للداخل", "خروج أيونات Cl⁻"),
                            0,
                            "انفتاح قنوات الصوديوم المبوبة بالجهد يؤدي لتدفق مفاجئ للداخل بفعل التدرج الكهربائي والتركيزي."
                        ),
                        QuizQuestion(
                            "خلال فترة الحران المطلق (Absolute Refractory Period)، تكون قنوات الصوديوم المبوبة بالجهد في حالة:",
                            listOf("معطلة وغير قابلة للتنبيه (Inactivated)", "مغلقة وجاهزة للفتح (Closed-Resting)", "مفتوحة بالكامل وتمرر البوتاسيوم", "متحللة إنزيمياً"),
                            0,
                            "تكون بوابة التعطيل (Inactivation gate) مغلقة، ولا يمكن إعادة فتحها إلا بعد عودة الجهد لمرحلة الراحة."
                        )
                    ),
                    flashcards = listOf(
                        FlashcardItem("Threshold Potential", "جهد العتبة: -55 mV تقريباً، الجهد الذي يبدأ عنده قانون الكل أو العدم", "Triggers opening of voltage-gated Na⁺ channels."),
                        FlashcardItem("Saltatory Conduction", "التوصيل القفزي: انتقال جهد الفعل بسرعة هائلة بين عقد رانفييه (Nodes of Ranvier)", "Occurs in myelinated axons, saving time and energy."),
                        FlashcardItem("Na⁺/K⁺ ATPase ratio", "3 Na⁺ خارج الخلية مقابل 2 K⁺ داخل الخلية باستهلاك 1 ATP", "Electrogenic pump maintaining negative resting membrane potential.")
                    ),
                    translationNotes = "تسمى أيضاً باللغة الإنجليزية Action Potential والفرنسية Potentiel d'action.",
                    alternativeTranslation = "جهد الفعالية أو كمون العمل.",
                    isOfflineGenerated = true
                )
            }
            lower.contains("autonomic") || lower.contains("sympathetic") || lower.contains("parasympathetic") || lower.contains("الودي") -> {
                MedicalAiResult(
                    title = "الجهاز العصبي المستقل: الودي مقابل نظير الودي",
                    topic = "Autonomic Nervous System",
                    mode = mode.id,
                    originalText = topic,
                    translation = "الجهاز العصبي اللاإرادي (ANS): ينقسم إلى القسم الودي (المسؤول عن القتال أو الهروب) والقسم نظير الودي (المسؤول عن الراحة والهضم).",
                    simpleExplanation = "القسم الودي هو دواسة البنزين في حالات الطوارئ (تسريع القلب وتوسيع الحدقة)، بينما نظير الودي هو الفرامل ووقت الراحة (تهدئة القلب وتنشيط الهضم).",
                    detailedExplanation = "ينشأ القسم الودي (Sympathetic) من القطع الصدرية والقطنية للنخاع الشوكي (Thoracolumbar Outflow T1-L2). أليافه قبل العقدية قصيرة وتحرر الأسيتيل كولين، بينما بعد العقدية طويلة وتحرر النورأدرينالين (Norepinephrine). بالمقابل، ينشأ القسم نظير الودي (Parasympathetic) من جذع الدماغ والعجز (Craniosacral Outflow: الأعصاب III, VII, IX, X و S2-S4)، وأليافه بعد العقدية قريبة من الأعضاء المستهدفة وتحرر الأسيتيل كولين حصرياً.",
                    keyTerms = listOf(
                        MedicalTerm("Sympathetic Nervous System", "الجهاز العصبي الودي", "مسؤول عن استجابة الكر والفر (Fight or Flight)"),
                        MedicalTerm("Parasympathetic Nervous System", "الجهاز العصبي نظير الودي", "مسؤول عن استجابة الراحة والهضم (Rest and Digest)"),
                        MedicalTerm("Thoracolumbar Outflow", "المخرج الصدري القطني", "المنشأ التشريحي للألياف الودية من T1 حتى L2"),
                        MedicalTerm("Craniosacral Outflow", "المخرج القحفي العجزي", "المنشأ التشريحي لألياف نظير الودي (CN III, VII, IX, X + S2-S4)"),
                        MedicalTerm("Adrenergic Receptors (α, β)", "المستقبلات الأدرينرجية", "مستقبلات النورأدرينالين والأدرينالين")
                    ),
                    mechanismSteps = listOf(
                        "1. رصد الخطر/التوتر عبر اللوزة الدماغية والوطاء (Hypothalamus)",
                        "2. تفعيل المخرج الصدري القطني T1-L2 وإطلاق النورأدرينالين",
                        "3. تحفيز لب الكظرية (Adrenal Medulla) لإفراز الأدرينالين في مجرى الدم",
                        "4. تفعيل مستقبلات β1 في القلب: زيادة ضربات القلب وقوة الانقباض",
                        "5. تفعيل مستقبلات β2 في القصبات: توسع قصبي لتحسين الأكسجة",
                        "6. تفعيل مستقبلات α1 في الأوعية المحيطية: تقبض وعائي لرفع ضغط الدم وتوجيه التروية للعضلات"
                    ),
                    keyPoints = listOf(
                        "العصب المبهم (Vagus Nerve - CN X) يحمل 75% من ألياف نظير الودي في الجسم كله",
                        "الغدد العرقية تُعصّب بألياف ودية ولكنها مفرزة للأسيتيل كولين (ACh) استثناءً فسيولوجياً هاماً للامتحانات!",
                        "مستقبلات القلب الودية هي β1 (واحد للقلب)، بينما مستقبلات الرئة هي β2 (اثنان للرئتين)"
                    ),
                    misconceptions = listOf(
                        "الاعتقاد بأن كل الألياف الودية تفرز نورأدرينالين؛ الغدد العرقية والأوعية العضلية لبعض الحيوانات تستخدم الأسيتيل كولين استثناءً",
                        "الظن بأن الجهازين يعملان بنظام الإيقاف والتشغيل التام؛ بل هما في توازن مستمر (Autonomic Tone)"
                    ),
                    comparisonTable = ComparisonTable(
                        title = "مقارنة الجهاز العصبي الودي ونظير الودي",
                        columns = listOf("وجه المقارنة", "الجهاز الودي (Sympathetic)", "الجهاز نظير الودي (Parasympathetic)"),
                        rows = listOf(
                            listOf("المنشأ التشريحي", "صدر قطني (Thoracolumbar: T1-L2)", "قحفي عجزي (Craniosacral: CN III, VII, IX, X, S2-S4)"),
                            listOf("طول الليف قبل العقدي", "قصير (العقد قريبة من النخاع)", "طويل (العقد قرب العضو أو داخل جداره)"),
                            listOf("الناقل بعد العقدي", "نورأدرينالين (Norepinephrine) غالباً", "أسيتيل كولين (ACh) دائماً"),
                            listOf("التأثير على القلب", "زيادة معدل النبض وقوة الضخ (β1)", "إبطاء معدل النبض (M2)"),
                            listOf("التأثير على الحدقة", "توسع الحدقة Mydriasis (α1)", "تضيق الحدقة Miosis (M3)"),
                            listOf("التأثير على الهضم", "تثبيط الحركة والإفرازات", "تنشيط الحركة والإفرازات الهضمية")
                        )
                    ),
                    diagramType = "node_flow",
                    diagramCode = "graph TD\n  A[الجهاز العصبي الذاتي ANS] --> B[القسم الودي Sympathetic]\n  A --> C[القسم نظير الودي Parasympathetic]\n  B --> D[المخرج T1-L2]\n  C --> E[المخرج القحفي العجزي]\n  D --> F[إفراز Norepinephrine]\n  E --> G[إفراز Acetylcholine]\n  F --> H[كر وفر Fight or Flight]\n  G --> I[راحة وهضم Rest & Digest]",
                    diagramNodes = listOf(
                        DiagramNode("A", "Autonomic Nervous System", "الجهاز العصبي الذاتي", "definition", "التحكم اللاإرادي بوظائف الأحشاء"),
                        DiagramNode("B", "Sympathetic Branch", "القسم الودي", "mechanism", "تجهيز الجسم لحالات الشدة والطوارئ"),
                        DiagramNode("C", "Parasympathetic Branch", "القسم نظير الودي", "mechanism", "حفظ الطاقة وتجديد الموارد"),
                        DiagramNode("D", "Thoracolumbar", "المخرج الصدري القطني", "anatomy", "خروج الألياف من T1 حتى L2"),
                        DiagramNode("E", "Craniosacral", "المخرج القحفي العجزي", "anatomy", "الأعصاب القحفية 3,7,9,10 وجذور العجز"),
                        DiagramNode("F", "Norepinephrine", "النورأدرينالين", "outcome", "تفعيل مستقبلات ألفا وبيتا الأدرينرجية"),
                        DiagramNode("G", "Acetylcholine", "الأسيتيل كولين", "outcome", "تفعيل المستقبلات المسكارينية"),
                        DiagramNode("H", "Fight or Flight", "استجابة الكر والفر", "warning", "تسارع القلب، توسع القصبات، إمداد العضلات"),
                        DiagramNode("I", "Rest & Digest", "استجابة الراحة والهضم", "mechanism", "تباطؤ القلب، إفراز اللعاب، تنشيط الأمعاء")
                    ),
                    diagramEdges = listOf(
                        DiagramEdge("A", "B", "يتفرع إلى"),
                        DiagramEdge("A", "C", "يتفرع إلى"),
                        DiagramEdge("B", "D", "ينشأ من"),
                        DiagramEdge("C", "E", "ينشأ من"),
                        DiagramEdge("D", "F", "يفرز"),
                        DiagramEdge("E", "G", "يفرز"),
                        DiagramEdge("F", "H", "يؤدي إلى"),
                        DiagramEdge("G", "I", "يؤدي إلى")
                    ),
                    questions = listOf(
                        QuizQuestion(
                            "ما هو العصب القحفي الذي يحمل الجزء الأكبر من الألياف نظيرة الودية إلى أحشاء الصدر والبطن؟",
                            listOf("العصب المبهم (Vagus Nerve - CN X)", "العصب الوجهي (Facial Nerve - CN VII)", "العصب البلعومي اللساني (CN IX)", "العصب محرك العين (CN III)"),
                            0,
                            "العصب المبهم (CN X) مسؤول عن حوالي 75% من التعصيب نظير الودي لأعضاء الصدر والبطن حتى ثلثي القولون المعترض."
                        ),
                        QuizQuestion(
                            "أي من الغدد التالية تُعصب ودياً ولكن تستخدم الأسيتيل كولين (ACh) كناقل عصبي بعد عقدي؟",
                            listOf("الغدد العرقية المفرزة (Eccrine Sweat Glands)", "الغدة الدرقية", "لب الكظرية", "الغدد اللعابية"),
                            0,
                            "الغدد العرقية هي استثناء تشريحي كلاسيكي: العصب ودي، لكن الناقل بعد العقدي هو أسيتيل كولين يرتبط بمستقبلات مسكارينية."
                        )
                    ),
                    flashcards = listOf(
                        FlashcardItem("Craniosacral Outflow", "الأعصاب القحفية 1973 (CN III, VII, IX, X) والجذور العجزية S2-S4", "Origin of the Parasympathetic division."),
                        FlashcardItem("Heart adrenergic receptor", "β1 Adrenergic Receptor (1 Heart)", "Increases heart rate and contractility."),
                        FlashcardItem("Lungs adrenergic receptor", "β2 Adrenergic Receptor (2 Lungs)", "Causes bronchodilation.")
                    ),
                    translationNotes = "يسمى الجهاز الذاتي أيضاً بالجهاز الإنباتي أو العصبي الحشوي.",
                    alternativeTranslation = "الجهاز العصبي التلقائي أو الذاتي.",
                    isOfflineGenerated = true
                )
            }
            else -> unavailable(topic, mode.id)
        }
    }

    fun generateLocalDiagram(topic: String, lang: String): MedicalAiResult {
        return generateLocalExplanation(topic, ExplanationMode.MECHANISM, lang, null, null)
    }

    /** Honest placeholder for topics the offline engine does not cover. */
    private fun unavailable(input: String, mode: String) = MedicalAiResult(
        title = "المحتوى غير متوفر دون اتصال",
        topic = input.take(40),
        mode = mode,
        originalText = input,
        translation = "",
        simpleExplanation = "هذا الموضوع غير مضمّن في المحتوى المحلي الجاهز. فعّل الذكاء الاصطناعي من الإعدادات للحصول على شرح حقيقي.",
        detailedExplanation = "",
        keyTerms = emptyList(),
        mechanismSteps = emptyList(),
        keyPoints = emptyList(),
        misconceptions = emptyList(),
        comparisonTable = null,
        diagramType = "mermaid",
        diagramCode = "",
        questions = emptyList(),
        flashcards = emptyList(),
        isOfflineGenerated = true
    )
}
