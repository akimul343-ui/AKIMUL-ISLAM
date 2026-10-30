package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "mahi" or "husband"
    val message: String,
    val action: String = "none",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "husband_care")
data class HusbandCare(
    @PrimaryKey
    val id: Long = 1,
    val screenTimeMinutes: Int = 30,
    val loveScore: String = "১০০% অনন্ত ভালোবাসা",
    val mood: String = "খুবই আদুরে ও রোমান্টিক",
    val lastScoldNotice: String = "",
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}

@Dao
interface CareDao {
    @Query("SELECT * FROM husband_care WHERE id = 1")
    fun getCareData(): Flow<HusbandCare?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateCare(care: HusbandCare)
}

@Database(entities = [ChatMessage::class, HusbandCare::class], version = 1, exportSchema = false)
abstract class MahiDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun careDao(): CareDao

    companion object {
        @Volatile
        private var INSTANCE: MahiDatabase? = null

        fun getDatabase(context: Context): MahiDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MahiDatabase::class.java,
                    "mahi_android_memory.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
