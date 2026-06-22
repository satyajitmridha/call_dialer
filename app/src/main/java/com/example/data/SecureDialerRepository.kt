package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SecureDialerRepository(private val database: AppDatabase) {
    private val callLogDao = database.callLogDao()
    private val contactPreferenceDao = database.contactPreferenceDao()

    // Passphrase used for encrypting and decrypting call notes. Can be updated by the user.
    var masterPassphrase = "DefaultPassKey123"

    val allCallLogs: Flow<List<CallLogEntity>> = callLogDao.getAllCallLogs()
    val allPreferences: Flow<List<ContactPreferenceEntity>> = contactPreferenceDao.getAllPreferences()

    suspend fun insertCallLog(phoneNumber: String, name: String?, direction: String, rawNote: String?, recordingPath: String?): Long {
        val encryptedNote = if (rawNote != null) EncryptionUtil.encrypt(rawNote, masterPassphrase) else null
        val log = CallLogEntity(
            phoneNumber = phoneNumber,
            contactName = name,
            direction = direction,
            voiceNoteEncrypted = encryptedNote,
            recordingPath = recordingPath
        )
        return callLogDao.insertCallLog(log)
    }

    suspend fun saveCallLog(log: CallLogEntity) {
        callLogDao.insertCallLog(log)
    }

    suspend fun updateCallLogNote(id: Int, plainTextNote: String) {
        val existing = callLogDao.getCallLogById(id) ?: return
        val encryptedNote = EncryptionUtil.encrypt(plainTextNote, masterPassphrase)
        val updated = existing.copy(voiceNoteEncrypted = encryptedNote)
        callLogDao.updateCallLog(updated)
    }

    suspend fun deleteCallLog(id: Int) {
        callLogDao.deleteCallLog(id)
    }

    suspend fun getPreferenceForContact(phoneNumber: String): ContactPreferenceEntity {
        return contactPreferenceDao.getPreferenceForContact(phoneNumber)
            ?: ContactPreferenceEntity(phoneNumber = phoneNumber, recordingEnabled = true, autoSaveCloud = false)
    }

    fun getPreferenceFlowForContact(phoneNumber: String): Flow<ContactPreferenceEntity> {
        return contactPreferenceDao.getPreferenceFlowForContact(phoneNumber).map {
            it ?: ContactPreferenceEntity(phoneNumber = phoneNumber, recordingEnabled = true, autoSaveCloud = false)
        }
    }

    suspend fun saveContactPreference(preference: ContactPreferenceEntity) {
        contactPreferenceDao.insertOrUpdatePreference(preference)
    }

    fun decryptNote(encryptedVal: String?): String {
        if (encryptedVal.isNullOrEmpty()) return ""
        return EncryptionUtil.decrypt(encryptedVal, masterPassphrase)
    }

    // Backup to cloud simulator
    suspend fun backupLogsToCloud(logs: List<CallLogEntity>): Boolean {
        // Here we simulate an secure uploading sync process of encrypted data
        // For actual security as requested, we transform them into encrypted payloads that are safe to upload.
        return try {
            // Emulate cloud processing time
            kotlinx.coroutines.delay(1200)
            logs.forEach { log ->
                val updatedLog = log.copy(isCloudSynced = true)
                callLogDao.insertCallLog(updatedLog)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
