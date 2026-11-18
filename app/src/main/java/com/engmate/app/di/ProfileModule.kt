package com.engmate.app.di
import android.content.Context
import androidx.room.Room
import com.engmate.app.BuildConfig
import com.engmate.app.core.database.EngMateDatabase
import com.engmate.app.feature.profile.data.RoomProfileRepository
import com.engmate.app.feature.profile.domain.ProfileRepository
import dagger.*
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
@Module @InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): EngMateDatabase =
        Room.databaseBuilder(context, EngMateDatabase::class.java, "engmate-${BuildConfig.FLAVOR}.db")
            .addMigrations(com.engmate.app.core.database.MergeDatabaseMigration)
            .addCallback(com.engmate.app.feature.quiz.data.local.GrammarQuizSeed.Callback()).build()
    @Provides fun profiles(database: EngMateDatabase) = database.profiles()
    @Provides fun vocabulary(database: EngMateDatabase) = database.vocabulary()
    @Provides fun notebook(database: EngMateDatabase) = database.notebook()
    @Provides fun flashcards(database: EngMateDatabase) = database.flashcards()
    @Provides fun grammar(database: EngMateDatabase) = database.grammar()
    @Provides fun quiz(database: EngMateDatabase) = database.quiz()
    @Provides fun study(database: EngMateDatabase) = database.study()
    @Provides fun clock(): Clock = Clock.systemUTC()
}
@Module @InstallIn(SingletonComponent::class)
abstract class ProfileRepositoryModule {
    @Binds @Singleton abstract fun repository(implementation: RoomProfileRepository): ProfileRepository
}
