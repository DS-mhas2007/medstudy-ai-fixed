package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CourseEntity::class,
        ModuleEntity::class,
        LectureEntity::class,
        LectureResourceEntity::class,
        LectureNoteEntity::class,
        AIResultEntity::class,
        FlashcardEntity::class,
        ReviewLogEntity::class,
        StudyTaskEntity::class,
        StudySessionEntity::class,
        AiCacheEntity::class,
        AiUsageEntity::class,
        QuizAttemptEntity::class,
        TopicWeaknessEntity::class,
        ExamAttemptEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class MedStudyDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun resourceDao(): ResourceDao
    abstract fun aiResultDao(): AIResultDao
    abstract fun flashcardDao(): FlashcardDao
    abstract fun studyPlannerDao(): StudyPlannerDao
    abstract fun aiCacheDao(): AiCacheDao
    abstract fun aiUsageDao(): AiUsageDao
    abstract fun quizAttemptDao(): QuizAttemptDao
    abstract fun topicWeaknessDao(): TopicWeaknessDao
    abstract fun examAttemptDao(): ExamAttemptDao

    companion object {
        @Volatile
        private var INSTANCE: MedStudyDatabase? = null

        /**
         * v1 -> v2
         *  - flashcards: lapses + lastReviewedAt, duplicates removed, unique (front, back) index
         *  - review_logs: new table (streak / daily statistics)
         *  - ai_results: isOfflineGenerated flag (existing rows are treated as AI generated)
         *  - study_tasks: free text dates ("اليوم" / "غداً") become real ISO dates
         * User data is never dropped: there is deliberately no destructive fallback.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN lastReviewedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "DELETE FROM flashcards WHERE id NOT IN " +
                        "(SELECT MIN(id) FROM flashcards GROUP BY frontText, backText)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_flashcards_frontText_backText` " +
                        "ON `flashcards` (`frontText`, `backText`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `review_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`flashcardId` INTEGER NOT NULL, " +
                        "`grade` INTEGER NOT NULL, " +
                        "`reviewedAt` INTEGER NOT NULL, " +
                        "`intervalAfterDays` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_reviewedAt` ON `review_logs` (`reviewedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_logs_flashcardId` ON `review_logs` (`flashcardId`)")

                db.execSQL("ALTER TABLE ai_results ADD COLUMN isOfflineGenerated INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    "UPDATE study_tasks SET targetDate = date('now','localtime') " +
                        "WHERE targetDate = 'اليوم' OR targetDate = ''"
                )
                db.execSQL(
                    "UPDATE study_tasks SET targetDate = date('now','localtime','+1 day') " +
                        "WHERE targetDate = 'غداً'"
                )
            }
        }

        /** v2 -> v3: response cache and request log (no existing table is touched). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_cache` (" +
                        "`cacheKey` TEXT NOT NULL, `payload` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, `lastUsedAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_cache_lastUsedAt` ON `ai_cache` (`lastUsedAt`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ai_usage` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `requestedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_usage_requestedAt` ON `ai_usage` (`requestedAt`)")
            }
        }

        /** v3 -> v4: FSRS card fields, cloze, anatomy, quiz attempts, topic weaknesses, and exam attempts. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE flashcards ADD COLUMN cardType TEXT NOT NULL DEFAULT 'STANDARD'")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN clozeAnswer TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN imageUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN fsrsStability REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN fsrsDifficulty REAL NOT NULL DEFAULT 5.0")
                db.execSQL("ALTER TABLE flashcards ADD COLUMN fsrsState TEXT NOT NULL DEFAULT 'NEW'")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `quiz_attempts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`lectureId` INTEGER, " +
                        "`courseId` INTEGER, " +
                        "`title` TEXT NOT NULL, " +
                        "`topic` TEXT NOT NULL, " +
                        "`specialty` TEXT NOT NULL, " +
                        "`totalQuestions` INTEGER NOT NULL, " +
                        "`correctCount` INTEGER NOT NULL, " +
                        "`scorePercent` INTEGER NOT NULL, " +
                        "`detailsJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_lectureId` ON `quiz_attempts` (`lectureId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_courseId` ON `quiz_attempts` (`courseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_specialty` ON `quiz_attempts` (`specialty`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_attempts_createdAt` ON `quiz_attempts` (`createdAt`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `topic_weaknesses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`courseId` INTEGER, " +
                        "`topic` TEXT NOT NULL, " +
                        "`specialty` TEXT NOT NULL, " +
                        "`totalAttempts` INTEGER NOT NULL, " +
                        "`correctAttempts` INTEGER NOT NULL, " +
                        "`accuracyPercent` INTEGER NOT NULL, " +
                        "`lastAssessedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_weaknesses_courseId` ON `topic_weaknesses` (`courseId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topic_weaknesses_specialty` ON `topic_weaknesses` (`specialty`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_topic_weaknesses_topic_specialty` ON `topic_weaknesses` (`topic`, `specialty`)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exam_attempts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`timeSpentSeconds` INTEGER NOT NULL, " +
                        "`totalQuestions` INTEGER NOT NULL, " +
                        "`correctCount` INTEGER NOT NULL, " +
                        "`scorePercent` INTEGER NOT NULL, " +
                        "`courseIdsJson` TEXT NOT NULL, " +
                        "`topicBreakdownJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exam_attempts_createdAt` ON `exam_attempts` (`createdAt`)")
            }
        }

        fun getDatabase(context: Context): MedStudyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MedStudyDatabase::class.java,
                    "medstudy_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
