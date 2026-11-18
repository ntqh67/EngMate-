package com.engmate.app.di

import com.engmate.app.feature.flashcard.data.RoomReviewRepository
import com.engmate.app.feature.flashcard.domain.ReviewRepository
import com.engmate.app.feature.notebook.data.RoomHandbookRepository
import com.engmate.app.feature.notebook.data.RoomWordNoteRepository
import com.engmate.app.feature.notebook.domain.HandbookRepository
import com.engmate.app.feature.notebook.domain.WordNoteRepository
import com.engmate.app.feature.vocabulary.data.RoomWordRepository
import com.engmate.app.feature.vocabulary.domain.WordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DAOs come from DatabaseModule. */
@Module @InstallIn(SingletonComponent::class)
abstract class VocabularyRepositoryModule {
    @Binds @Singleton abstract fun translator(implementation: com.engmate.app.feature.vocabulary.data.MlKitVietnameseTranslator): com.engmate.app.feature.vocabulary.domain.VietnameseTranslator
    @Binds @Singleton abstract fun words(implementation: RoomWordRepository): WordRepository
    @Binds @Singleton abstract fun handbooks(implementation: RoomHandbookRepository): HandbookRepository
    @Binds @Singleton abstract fun wordNotes(implementation: RoomWordNoteRepository): WordNoteRepository
    @Binds @Singleton abstract fun reviews(implementation: RoomReviewRepository): ReviewRepository
}
