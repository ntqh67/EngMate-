package com.engmate.app.di

import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.grammar.data.RoomGrammarRepository
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.quiz.data.RoomQuestionRepository
import com.engmate.app.feature.quiz.data.RoomQuizAttemptRepository
import com.engmate.app.feature.quiz.domain.QuestionRepository
import com.engmate.app.feature.quiz.domain.QuizAttemptRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module @InstallIn(SingletonComponent::class)
object GrammarQuizStorageModule {
    @Provides fun grammarLessons(database: EngMateDatabase) = database.grammarLessons()
    @Provides fun questions(database: EngMateDatabase) = database.questions()
    @Provides fun quizAttempts(database: EngMateDatabase) = database.quizAttempts()
}

@Module @InstallIn(SingletonComponent::class)
abstract class GrammarQuizRepositoryModule {
    @Binds @Singleton abstract fun grammar(implementation: RoomGrammarRepository): GrammarRepository
    @Binds @Singleton abstract fun questions(implementation: RoomQuestionRepository): QuestionRepository
    @Binds @Singleton abstract fun attempts(implementation: RoomQuizAttemptRepository): QuizAttemptRepository
}
