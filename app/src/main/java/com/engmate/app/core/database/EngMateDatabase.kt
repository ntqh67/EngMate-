package com.engmate.app.core.database

import androidx.room.*
import com.engmate.app.feature.profile.data.local.UserProfileEntity
import com.engmate.app.feature.profile.data.local.UserProfileDao
import com.engmate.app.feature.vocabulary.data.local.VocabularyDao
import com.engmate.app.feature.notebook.data.local.NotebookDao
import com.engmate.app.feature.flashcard.data.local.FlashcardDao
import com.engmate.app.feature.grammar.data.local.GrammarDao
import com.engmate.app.feature.quiz.data.local.QuizDao
import com.engmate.app.feature.progress.data.local.StudyDao
import com.engmate.app.feature.vocabulary.data.local.TopicEntity
import com.engmate.app.feature.vocabulary.data.local.WordEntity
import com.engmate.app.feature.flashcard.data.local.WordProgressEntity
import com.engmate.app.feature.notebook.data.local.WordNoteEntity
import com.engmate.app.feature.notebook.data.local.NotebookWordEntity
import com.engmate.app.feature.flashcard.data.local.FlashcardCollectionEntity
import com.engmate.app.feature.flashcard.data.local.FlashcardEntity
import com.engmate.app.feature.grammar.data.local.GrammarLessonEntity
import com.engmate.app.feature.quiz.data.local.QuestionEntity
import com.engmate.app.feature.quiz.data.local.QuestionOptionEntity
import com.engmate.app.feature.quiz.data.local.QuizAttemptEntity
import com.engmate.app.feature.quiz.data.local.AttemptAnswerEntity
import com.engmate.app.feature.progress.data.local.LearningActivityEntity
import com.engmate.app.feature.progress.data.local.DailyStudyStatEntity

@Database(
    entities = [
        UserProfileEntity::class,
        TopicEntity::class,
        WordEntity::class,
        WordProgressEntity::class,
        WordNoteEntity::class,
        NotebookWordEntity::class,
        FlashcardCollectionEntity::class,
        FlashcardEntity::class,
        GrammarLessonEntity::class,
        QuestionEntity::class,
        QuestionOptionEntity::class,
        QuizAttemptEntity::class,
        AttemptAnswerEntity::class,
        LearningActivityEntity::class,
        DailyStudyStatEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(DatabaseConverters::class)
abstract class EngMateDatabase : RoomDatabase() {
    abstract fun profiles(): UserProfileDao
    abstract fun vocabulary(): VocabularyDao
    abstract fun notebook(): NotebookDao
    abstract fun flashcards(): FlashcardDao
    abstract fun grammar(): GrammarDao
    abstract fun quiz(): QuizDao
    abstract fun study(): StudyDao
}
