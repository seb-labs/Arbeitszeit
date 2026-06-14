
package de.seb.arbeitszeitapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Database(
    entities = [WorkSessionEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(WorkDatabaseConverters::class)
abstract class WorkDatabase : RoomDatabase() {
    abstract fun workSessionDao(): WorkSessionDao

    companion object {
        @Volatile
        private var INSTANCE: WorkDatabase? = null

        fun getInstance(context: Context): WorkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WorkDatabase::class.java,
                    "work-sessions.db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class WorkDatabaseConverters {
    @TypeConverter
    fun fromSessionSource(source: SessionSource): String = source.name

    @TypeConverter
    fun toSessionSource(value: String): SessionSource = SessionSource.valueOf(value)
}
