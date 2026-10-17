package com.tteumsae.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedPlaceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TteumsaeDatabase : RoomDatabase() {
    abstract fun savedPlaceDao(): SavedPlaceDao

    companion object {
        const val NAME = "tteumsae.db"
    }
}
