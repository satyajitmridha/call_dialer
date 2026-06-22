package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(log: CallLogEntity): Long

    @Update
    suspend fun updateCallLog(log: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: Int)

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getCallLogById(id: Int): CallLogEntity?
}

@Dao
interface ContactPreferenceDao {
    @Query("SELECT * FROM contact_preferences WHERE phoneNumber = :phoneNumber")
    suspend fun getPreferenceForContact(phoneNumber: String): ContactPreferenceEntity?

    @Query("SELECT * FROM contact_preferences WHERE phoneNumber = :phoneNumber")
    fun getPreferenceFlowForContact(phoneNumber: String): Flow<ContactPreferenceEntity?>

    @Query("SELECT * FROM contact_preferences")
    fun getAllPreferences(): Flow<List<ContactPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePreference(preference: ContactPreferenceEntity)
}

@Database(entities = [CallLogEntity::class, ContactPreferenceEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callLogDao(): CallLogDao
    abstract fun contactPreferenceDao(): ContactPreferenceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "secure_dialer_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
