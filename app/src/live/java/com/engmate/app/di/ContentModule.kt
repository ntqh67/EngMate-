package com.engmate.app.di
import com.engmate.app.core.sync.ContentSync
import com.engmate.app.feature.grammar.domain.GrammarRepository
import com.engmate.app.feature.quiz.domain.QuestionRepository
import com.engmate.app.sync.FirestoreContentSync
import com.engmate.app.sync.SyncedGrammarRepository
import com.engmate.app.sync.SyncedQuestionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Live: grammar lessons and questions are managed in Cloud Firestore and mirrored into Room. */
@Module @InstallIn(SingletonComponent::class)
abstract class ContentModule {
    @Binds @Singleton abstract fun grammar(implementation: SyncedGrammarRepository): GrammarRepository
    @Binds @Singleton abstract fun questions(implementation: SyncedQuestionRepository): QuestionRepository
    @Binds @Singleton abstract fun sync(implementation: FirestoreContentSync): ContentSync
}
