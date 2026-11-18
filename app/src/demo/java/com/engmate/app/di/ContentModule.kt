package com.engmate.app.di
import com.engmate.app.core.sync.ContentSync
import com.engmate.app.feature.grammar.data.RoomGrammarRepository
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.quiz.data.RoomQuestionRepository
import com.engmate.app.feature.quiz.domain.QuestionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Demo works fully offline: content lives only in Room. */
@Module @InstallIn(SingletonComponent::class)
abstract class ContentModule {
    @Binds @Singleton abstract fun grammar(implementation: RoomGrammarRepository): GrammarRepository
    @Binds @Singleton abstract fun questions(implementation: RoomQuestionRepository): QuestionRepository

    companion object {
        @Provides @Singleton fun sync(): ContentSync = object : ContentSync { override fun start() = Unit }
    }
}
