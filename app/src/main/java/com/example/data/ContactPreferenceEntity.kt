package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contact_preferences")
data class ContactPreferenceEntity(
    @PrimaryKey val phoneNumber: String,
    val recordingEnabled: Boolean = true,
    val autoSaveCloud: Boolean = false
)
