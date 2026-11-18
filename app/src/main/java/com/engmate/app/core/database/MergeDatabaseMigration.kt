package com.engmate.app.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Both branches shipped version 2 with different schemas. Preserve either shape in v3. */
object MergeDatabaseMigration : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'room_master_table' AND name != 'android_metadata'").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        val known = listOf("user_profile", "topics", "words", "grammar_lessons", "flashcard_collections", "flashcards", "word_progress", "word_notes", "notebook_words", "questions", "question_options", "quiz_attempts", "attempt_answers", "learning_activity", "daily_study_stat")
        for (table in known.filter { it in tables }) db.execSQL("ALTER TABLE `$table` RENAME TO `merge_old_$table`")
        // Names of explicit indices are global, so free the source names before creating v3.
        val indices = db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL AND tbl_name LIKE 'merge_old_%'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        for (index in indices) db.execSQL("DROP INDEX `$index`")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `user_profile` (`uid` TEXT NOT NULL, `email` TEXT NOT NULL, `display_name` TEXT NOT NULL, `photo_url` TEXT, `english_level` TEXT NOT NULL, `daily_goal_minutes` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, PRIMARY KEY(`uid`))""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `topics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `category` TEXT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `words` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `topic_id` INTEGER, `word` TEXT NOT NULL COLLATE NOCASE, `phonetic` TEXT, `part_of_speech` TEXT, `meaning_en` TEXT NOT NULL, `meaning_vi` TEXT, `example_sentence` TEXT, `example_meaning` TEXT, `audio_url` TEXT, `collocations` TEXT NOT NULL, `idioms` TEXT NOT NULL, `is_cached` INTEGER NOT NULL DEFAULT 0, `created_at` INTEGER, FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_words_topic_id` ON `words` (`topic_id`)""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_words_word` ON `words` (`word`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `grammar_lessons` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `level` TEXT NOT NULL, `summary` TEXT, `content` TEXT NOT NULL, `examples` TEXT NOT NULL, `order_index` INTEGER NOT NULL DEFAULT 0, `updated_at` INTEGER)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_grammar_lessons_level_order_index` ON `grammar_lessons` (`level`, `order_index`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcard_collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT, `created_at` INTEGER, FOREIGN KEY(`user_id`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_flashcard_collections_user_id` ON `flashcard_collections` (`user_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `flashcards` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `collection_id` INTEGER NOT NULL, `word` TEXT NOT NULL, `meaning` TEXT NOT NULL, `example` TEXT, `phonetic` TEXT, FOREIGN KEY(`collection_id`) REFERENCES `flashcard_collections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_flashcards_collection_id` ON `flashcards` (`collection_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `word_progress` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, `word_id` INTEGER NOT NULL, `repetition` INTEGER NOT NULL DEFAULT 0, `interval` INTEGER NOT NULL DEFAULT 1, `ease_factor` REAL NOT NULL DEFAULT 2.5, `last_reviewed_at` INTEGER, `next_review_at` INTEGER NOT NULL, `status` TEXT NOT NULL, FOREIGN KEY(`user_id`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_word_progress_user_id_word_id` ON `word_progress` (`user_id`, `word_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_word_progress_word_id` ON `word_progress` (`word_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_word_progress_user_id_next_review_at` ON `word_progress` (`user_id`, `next_review_at`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `word_notes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_uid` TEXT NOT NULL, `word_id` INTEGER NOT NULL, `custom_meaning` TEXT, `custom_examples` TEXT NOT NULL, `custom_collocations` TEXT NOT NULL, `custom_idioms` TEXT NOT NULL, `personal_note` TEXT, `updated_at` INTEGER, FOREIGN KEY(`user_uid`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_word_notes_user_uid_word_id` ON `word_notes` (`user_uid`, `word_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_word_notes_word_id` ON `word_notes` (`word_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `notebook_words` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_uid` TEXT NOT NULL, `word_id` INTEGER NOT NULL, `note` TEXT, `created_at` INTEGER, FOREIGN KEY(`user_uid`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`word_id`) REFERENCES `words`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_notebook_words_user_uid_word_id` ON `notebook_words` (`user_uid`, `word_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_notebook_words_word_id` ON `notebook_words` (`word_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `questions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `topic_id` INTEGER, `lesson_id` INTEGER, `is_placement` INTEGER NOT NULL DEFAULT 0, `answer_type` TEXT NOT NULL, `content` TEXT NOT NULL, `explanation` TEXT, `correct_answer` TEXT, `created_at` INTEGER, FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`lesson_id`) REFERENCES `grammar_lessons`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_questions_topic_id` ON `questions` (`topic_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_questions_lesson_id` ON `questions` (`lesson_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_questions_is_placement` ON `questions` (`is_placement`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `question_options` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question_id` INTEGER NOT NULL, `content` TEXT NOT NULL, `is_correct` INTEGER NOT NULL DEFAULT 0, `order_index` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_question_options_question_id` ON `question_options` (`question_id`)""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_question_options_id_question_id` ON `question_options` (`id`, `question_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `quiz_attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `user_id` TEXT NOT NULL, `topic_id` INTEGER, `lesson_id` INTEGER, `is_placement` INTEGER NOT NULL DEFAULT 0, `correct_count` INTEGER NOT NULL DEFAULT 0, `total_questions` INTEGER NOT NULL, `xp_earned` INTEGER NOT NULL DEFAULT 0, `completed_at` INTEGER NOT NULL, `updated_at` INTEGER, FOREIGN KEY(`user_id`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`topic_id`) REFERENCES `topics`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`lesson_id`) REFERENCES `grammar_lessons`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_quiz_attempts_user_id_completed_at` ON `quiz_attempts` (`user_id`, `completed_at`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_quiz_attempts_topic_id` ON `quiz_attempts` (`topic_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_quiz_attempts_lesson_id` ON `quiz_attempts` (`lesson_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `attempt_answers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `attempt_id` INTEGER NOT NULL, `question_id` INTEGER NOT NULL, `selected_option_id` INTEGER, `answer_text` TEXT, `question_snapshot` TEXT NOT NULL, `selected_option_snapshot` TEXT, `explanation_snapshot` TEXT, `is_correct` INTEGER NOT NULL DEFAULT 0, FOREIGN KEY(`attempt_id`) REFERENCES `quiz_attempts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`selected_option_id`, `question_id`) REFERENCES `question_options`(`id`, `question_id`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE UNIQUE INDEX IF NOT EXISTS `index_attempt_answers_attempt_id_question_id` ON `attempt_answers` (`attempt_id`, `question_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_attempt_answers_question_id` ON `attempt_answers` (`question_id`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_attempt_answers_selected_option_id_question_id` ON `attempt_answers` (`selected_option_id`, `question_id`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `learning_activity` (`activity_id` TEXT NOT NULL, `user_uid` TEXT NOT NULL, `activity_type` TEXT NOT NULL, `source_id` TEXT, `xp_earned` INTEGER NOT NULL DEFAULT 0, `duration_seconds` INTEGER NOT NULL DEFAULT 0, `occurred_at` INTEGER NOT NULL, `study_date` TEXT NOT NULL, `words_learned` INTEGER NOT NULL DEFAULT 0, `words_reviewed` INTEGER NOT NULL DEFAULT 0, `quizzes_completed` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`activity_id`), FOREIGN KEY(`user_uid`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_learning_activity_user_uid_study_date` ON `learning_activity` (`user_uid`, `study_date`)""")
        db.execSQL("""CREATE INDEX IF NOT EXISTS `index_learning_activity_user_uid_occurred_at` ON `learning_activity` (`user_uid`, `occurred_at`)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `daily_study_stat` (`user_uid` TEXT NOT NULL, `study_date` TEXT NOT NULL, `xp_earned` INTEGER NOT NULL DEFAULT 0, `study_seconds` INTEGER NOT NULL DEFAULT 0, `words_learned` INTEGER NOT NULL DEFAULT 0, `words_reviewed` INTEGER NOT NULL DEFAULT 0, `quizzes_completed` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`user_uid`, `study_date`), FOREIGN KEY(`user_uid`) REFERENCES `user_profile`(`uid`) ON UPDATE NO ACTION ON DELETE RESTRICT )""")
        if ("user_profile" in tables) {
            val columns = columns(db, "merge_old_user_profile")
            val values = listOf(column(columns, "uid", "uid", "''"), column(columns, "email", "email", "''"), column(columns, "display_name", "display_name", "''"), column(columns, "photo_url", "photo_url", "NULL"), column(columns, "english_level", "english_level", "''"), column(columns, "daily_goal_minutes", "daily_goal_minutes", "0"), column(columns, "created_at", "created_at", "0"), column(columns, "updated_at", "updated_at", "0")).joinToString(", ")
            db.execSQL("INSERT INTO `user_profile` (`uid`, `email`, `display_name`, `photo_url`, `english_level`, `daily_goal_minutes`, `created_at`, `updated_at`) SELECT $values FROM `merge_old_user_profile` AS source")
        }
        if ("topics" in tables) {
            val columns = columns(db, "merge_old_topics")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "title", "title", "''"), column(columns, "description", "description", "NULL"), column(columns, "category", "category", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `topics` (`id`, `title`, `description`, `category`) SELECT $values FROM `merge_old_topics` AS source")
        }
        if ("words" in tables) {
            val columns = columns(db, "merge_old_words")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "topic_id", "topic_id", "NULL"), column(columns, "word", "word", "''"), column(columns, "phonetic", "phonetic", "NULL"), column(columns, "part_of_speech", "part_of_speech", "NULL"), column(columns, "meaning_en", "meaning_en", "''"), column(columns, "meaning_vi", "meaning_vi", "NULL"), column(columns, "example_sentence", "example_sentence", "NULL"), column(columns, "example_meaning", "example_meaning", "NULL"), column(columns, "audio_url", "audio_url", "NULL"), column(columns, "collocations", "collocations", "''"), column(columns, "idioms", "idioms", "''"), column(columns, "is_cached", "is_cached", "0"), column(columns, "created_at", "created_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `words` (`id`, `topic_id`, `word`, `phonetic`, `part_of_speech`, `meaning_en`, `meaning_vi`, `example_sentence`, `example_meaning`, `audio_url`, `collocations`, `idioms`, `is_cached`, `created_at`) SELECT $values FROM `merge_old_words` AS source")
        }
        if ("grammar_lessons" in tables) {
            val columns = columns(db, "merge_old_grammar_lessons")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "title", "title", "''"), column(columns, "level", "level", "''"), column(columns, "summary", "summary", "NULL"), column(columns, "content", "content", "''"), column(columns, "examples", "examples", "''"), column(columns, "order_index", "order_index", "0"), column(columns, "updated_at", "updated_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `grammar_lessons` (`id`, `title`, `level`, `summary`, `content`, `examples`, `order_index`, `updated_at`) SELECT $values FROM `merge_old_grammar_lessons` AS source")
        }
        if ("flashcard_collections" in tables) {
            val columns = columns(db, "merge_old_flashcard_collections")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "user_id", "user_uid", "''"), column(columns, "title", "title", "''"), column(columns, "description", "description", "NULL"), column(columns, "created_at", "created_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `flashcard_collections` (`id`, `user_id`, `title`, `description`, `created_at`) SELECT $values FROM `merge_old_flashcard_collections` AS source")
        }
        if ("flashcards" in tables) {
            val columns = columns(db, "merge_old_flashcards")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "collection_id", "collection_id", "0"), column(columns, "word", "word", "''"), column(columns, "meaning", "meaning", "''"), column(columns, "example", "example", "NULL"), column(columns, "phonetic", "phonetic", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `flashcards` (`id`, `collection_id`, `word`, `meaning`, `example`, `phonetic`) SELECT $values FROM `merge_old_flashcards` AS source")
        }
        if ("word_progress" in tables) {
            val columns = columns(db, "merge_old_word_progress")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "user_id", "user_uid", "''"), column(columns, "word_id", "word_id", "0"), column(columns, "repetition", "repetition", "0"), column(columns, "interval", "interval_days", "1"), column(columns, "ease_factor", "ease_factor", "2.5"), column(columns, "last_reviewed_at", "last_reviewed_at", "NULL"), column(columns, "next_review_at", "next_review_at", "0"), column(columns, "status", "status", "''")).joinToString(", ")
            db.execSQL("INSERT INTO `word_progress` (`id`, `user_id`, `word_id`, `repetition`, `interval`, `ease_factor`, `last_reviewed_at`, `next_review_at`, `status`) SELECT $values FROM `merge_old_word_progress` AS source")
        }
        if ("word_notes" in tables) {
            val columns = columns(db, "merge_old_word_notes")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "user_uid", "user_uid", "''"), column(columns, "word_id", "word_id", "0"), column(columns, "custom_meaning", "custom_meaning", "NULL"), column(columns, "custom_examples", "custom_examples", "''"), column(columns, "custom_collocations", "custom_collocations", "''"), column(columns, "custom_idioms", "custom_idioms", "''"), column(columns, "personal_note", "personal_note", "NULL"), column(columns, "updated_at", "updated_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `word_notes` (`id`, `user_uid`, `word_id`, `custom_meaning`, `custom_examples`, `custom_collocations`, `custom_idioms`, `personal_note`, `updated_at`) SELECT $values FROM `merge_old_word_notes` AS source")
        }
        if ("notebook_words" in tables) {
            val columns = columns(db, "merge_old_notebook_words")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "user_uid", "user_uid", "''"), column(columns, "word_id", "word_id", "0"), column(columns, "note", "note", "NULL"), column(columns, "created_at", "created_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `notebook_words` (`id`, `user_uid`, `word_id`, `note`, `created_at`) SELECT $values FROM `merge_old_notebook_words` AS source")
        }
        if ("questions" in tables) {
            val columns = columns(db, "merge_old_questions")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "topic_id", "topic_id", "NULL"), column(columns, "lesson_id", "lesson_id", "NULL"), column(columns, "is_placement", "is_placement", "0"), column(columns, "answer_type", "answer_type", "''"), column(columns, "content", "content", "''"), column(columns, "explanation", "explanation", "NULL"), column(columns, "correct_answer", "correct_answer", "NULL"), column(columns, "created_at", "created_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `questions` (`id`, `topic_id`, `lesson_id`, `is_placement`, `answer_type`, `content`, `explanation`, `correct_answer`, `created_at`) SELECT $values FROM `merge_old_questions` AS source")
        }
        if ("question_options" in tables) {
            val columns = columns(db, "merge_old_question_options")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "question_id", "question_id", "0"), column(columns, "content", "content", "''"), column(columns, "is_correct", "is_correct", "0"), column(columns, "order_index", "order_index", "0")).joinToString(", ")
            db.execSQL("INSERT INTO `question_options` (`id`, `question_id`, `content`, `is_correct`, `order_index`) SELECT $values FROM `merge_old_question_options` AS source")
        }
        if ("quiz_attempts" in tables) {
            val columns = columns(db, "merge_old_quiz_attempts")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "user_id", "user_uid", "''"), column(columns, "topic_id", "topic_id", "NULL"), column(columns, "lesson_id", "lesson_id", "NULL"), column(columns, "is_placement", "is_placement", "0"), column(columns, "correct_count", "correct_count", "0"), column(columns, "total_questions", "total_questions", "0"), column(columns, "xp_earned", "xp_earned", "0"), column(columns, "completed_at", "completed_at", "0"), column(columns, "updated_at", "updated_at", "NULL")).joinToString(", ")
            db.execSQL("INSERT INTO `quiz_attempts` (`id`, `user_id`, `topic_id`, `lesson_id`, `is_placement`, `correct_count`, `total_questions`, `xp_earned`, `completed_at`, `updated_at`) SELECT $values FROM `merge_old_quiz_attempts` AS source")
        }
        if ("attempt_answers" in tables) {
            val columns = columns(db, "merge_old_attempt_answers")
            val values = listOf(column(columns, "id", "id", "0"), column(columns, "attempt_id", "attempt_id", "0"), column(columns, "question_id", "question_id", "0"), column(columns, "selected_option_id", "selected_option_id", "NULL"), column(columns, "answer_text", "answer_text", "NULL"), column(columns, "question_snapshot", "question_snapshot", "(SELECT content FROM questions WHERE id = source.question_id)"), column(columns, "selected_option_snapshot", "selected_option_snapshot", "(SELECT content FROM question_options WHERE id = source.selected_option_id)"), column(columns, "explanation_snapshot", "explanation_snapshot", "(SELECT explanation FROM questions WHERE id = source.question_id)"), column(columns, "is_correct", "is_correct", "0")).joinToString(", ")
            db.execSQL("INSERT INTO `attempt_answers` (`id`, `attempt_id`, `question_id`, `selected_option_id`, `answer_text`, `question_snapshot`, `selected_option_snapshot`, `explanation_snapshot`, `is_correct`) SELECT $values FROM `merge_old_attempt_answers` AS source")
        }
        if ("learning_activity" in tables) {
            val columns = columns(db, "merge_old_learning_activity")
            val values = listOf(column(columns, "activity_id", "activity_id", "''"), column(columns, "user_uid", "user_uid", "''"), column(columns, "activity_type", "activity_type", "''"), column(columns, "source_id", "source_id", "NULL"), column(columns, "xp_earned", "xp_earned", "0"), column(columns, "duration_seconds", "duration_seconds", "0"), column(columns, "occurred_at", "occurred_at", "0"), column(columns, "study_date", "study_date", "''"), column(columns, "words_learned", "words_learned", "0"), column(columns, "words_reviewed", "words_reviewed", "0"), column(columns, "quizzes_completed", "quizzes_completed", "0")).joinToString(", ")
            db.execSQL("INSERT INTO `learning_activity` (`activity_id`, `user_uid`, `activity_type`, `source_id`, `xp_earned`, `duration_seconds`, `occurred_at`, `study_date`, `words_learned`, `words_reviewed`, `quizzes_completed`) SELECT $values FROM `merge_old_learning_activity` AS source")
        }
        if ("daily_study_stat" in tables) {
            val columns = columns(db, "merge_old_daily_study_stat")
            val values = listOf(column(columns, "user_uid", "user_uid", "''"), column(columns, "study_date", "study_date", "''"), column(columns, "xp_earned", "xp_earned", "0"), column(columns, "study_seconds", "study_seconds", "0"), column(columns, "words_learned", "words_learned", "0"), column(columns, "words_reviewed", "words_reviewed", "0"), column(columns, "quizzes_completed", "quizzes_completed", "0")).joinToString(", ")
            db.execSQL("INSERT INTO `daily_study_stat` (`user_uid`, `study_date`, `xp_earned`, `study_seconds`, `words_learned`, `words_reviewed`, `quizzes_completed`) SELECT $values FROM `merge_old_daily_study_stat` AS source")
        }
        for (table in known.asReversed().filter { it in tables }) db.execSQL("DROP TABLE `merge_old_$table`")
        db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Migration produced invalid references" } }
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(1)) }
        }

    private fun column(columns: Set<String>, name: String, legacy: String, fallback: String): String = when {
        name in columns -> "source.`$name`"
        legacy in columns -> "source.`$legacy`"
        else -> fallback
    }
}
