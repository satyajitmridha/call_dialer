package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val contactName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Long = 0,
    val direction: String, // "INCOMING", "OUTGOING", "MISSED"
    val voiceNoteEncrypted: String? = null,
    val recordingPath: String? = null,
    val isCloudSynced: Boolean = false
)
