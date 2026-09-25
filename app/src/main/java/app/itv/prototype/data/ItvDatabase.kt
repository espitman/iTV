package app.itv.prototype.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SeriesEntity::class, SeasonEntity::class, EpisodeEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class ItvDatabase : RoomDatabase() {
    abstract fun series(): SeriesDao
    abstract fun seasons(): SeasonDao
    abstract fun episodes(): EpisodeDao

    companion object {
        fun create(context: Context): ItvDatabase =
            Room.databaseBuilder(context.applicationContext, ItvDatabase::class.java, "itv.db")
                .addMigrations(object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE series ADD COLUMN isMovie INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE series ADD COLUMN durationMinutes INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE series ADD COLUMN backdropUrl TEXT")
                    }
                })
                .build()
    }
}
