package com.engmate.app.core.database
import androidx.room.Database
import androidx.room.RoomDatabase
import com.engmate.app.feature.profile.data.local.UserProfileDao
import com.engmate.app.feature.profile.data.local.UserProfileEntity
@Database(entities = [UserProfileEntity::class], version = 1, exportSchema = true)
abstract class EngMateDatabase : RoomDatabase() { abstract fun profiles(): UserProfileDao }
