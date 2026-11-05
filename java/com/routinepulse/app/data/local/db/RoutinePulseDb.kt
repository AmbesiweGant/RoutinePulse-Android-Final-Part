package com.routinepulse.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The phone's offline database (Room on top of SQLite). */
@Database(
    entities = [HabitEntity::class, CompletionEntity::class, NoteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RoutinePulseDb : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun completionDao(): CompletionDao
    abstract fun noteDao(): NoteDao

    companion object {
        fun create(context: Context): RoutinePulseDb =
            Room.databaseBuilder(context.applicationContext, RoutinePulseDb::class.java, "routinepulse.db").build()
    }
}
