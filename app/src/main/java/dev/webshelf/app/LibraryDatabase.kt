package dev.webshelf.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Delete
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "web_apps")
data class WebApp(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val kind: String, // "LOCAL" or "ONLINE"
    val location: String, // relative entry point for LOCAL, URL for ONLINE
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface WebAppDao {
    @Query("SELECT * FROM web_apps ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<WebApp>>

    @Upsert
    suspend fun upsert(app: WebApp)

    @Delete
    suspend fun delete(app: WebApp)
}

@Database(entities = [WebApp::class], version = 1, exportSchema = false)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun webAppDao(): WebAppDao

    companion object {
        @Volatile private var INSTANCE: LibraryDatabase? = null
        fun get(context: Context): LibraryDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LibraryDatabase::class.java,
                    "webshelf.db"
                ).build().also { INSTANCE = it }
            }
    }
}
