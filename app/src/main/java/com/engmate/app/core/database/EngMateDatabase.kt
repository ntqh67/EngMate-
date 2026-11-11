package com.engmate.app.core.database
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.engmate.app.feature.grammar.data.local.GrammarConverters
import com.engmate.app.feature.grammar.data.local.GrammarLessonDao
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.profile.data.local.UserProfileDao
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.quiz.data.local.*
@Database(entities = [UserProfileEntity::class,
    GrammarLessonEntity::class, QuestionEntity::class, QuestionOptionEntity::class, QuizAttemptEntity::class, AttemptAnswerEntity::class],
    version = 2, exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = GrammarQuizSeed.Migration1To2::class)])
@TypeConverters(GrammarConverters::class, QuizConverters::class)
abstract class EngMateDatabase : RoomDatabase() { abstract fun profiles(): UserProfileDao
    abstract fun grammarLessons(): GrammarLessonDao
    abstract fun questions(): QuestionDao
    abstract fun quizAttempts(): QuizAttemptDao
}
