package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telecom.TelecomManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.CallLogEntity
import com.example.data.ContactModel
import com.example.data.ContactPreferenceEntity
import com.example.data.SecureDialerRepository
import com.example.data.EncryptionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Data class representing an active calling session
data class ActiveCallSession(
    val phoneNumber: String,
    val contactName: String?,
    val durationSeconds: Int = 0,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isRecording: Boolean = false,
    val noiseCancellationActive: Boolean = true,
    val voiceNotesList: List<String> = emptyList(),
    val recordingSavedPath: String? = null
)

class SecureDialerViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val context = application.applicationContext
    private val repository: SecureDialerRepository

    // Native Contacts
    private val _nativeContacts = MutableStateFlow<List<ContactModel>>(emptyList())
    val nativeContacts = _nativeContacts.asStateFlow()

    // Searching and filtering
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Input dialed digits from keypad
    private val _dialInput = MutableStateFlow("")
    val dialInput = _dialInput.asStateFlow()

    // UI state for call logs
    val callLogs: StateFlow<List<CallLogEntity>>

    // Master Passphrase
    private val _masterPassphrase = MutableStateFlow("SecureDialKey99!")
    val masterPassphrase = _masterPassphrase.asStateFlow()

    // System Settings states
    val allPreferences: StateFlow<List<ContactPreferenceEntity>>

    // Combined filtered contacts list (by query)
    val filteredContacts: StateFlow<List<ContactModel>>

    // Active Call state
    private val _activeCall = MutableStateFlow<ActiveCallSession?>(null)
    val activeCall = _activeCall.asStateFlow()

    // Flag for active audio backup processing
    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp = _isBackingUp.asStateFlow()

    // Selected Log ID for detailing
    private val _selectedLogId = MutableStateFlow<Int?>(null)
    val selectedLogId = _selectedLogId.asStateFlow()

    // Text to Speech
    private var tts: TextToSpeech? = null
    private val _ttsInitialized = MutableStateFlow(false)
    val ttsInitialized = _ttsInitialized.asStateFlow()

    // Speech recognition handling
    private var speechRecognizer: SpeechRecognizer? = null
    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()
    private val _liveRecognitionText = MutableStateFlow("")
    val liveRecognitionText = _liveRecognitionText.asStateFlow()

    // Simulated ambient noise level (0.0 to 1.0)
    private val _noiseLevel = MutableStateFlow(0.12f)
    val noiseLevel = _noiseLevel.asStateFlow()

    // Simulated speech wave level
    private val _speechLevel = MutableStateFlow(0.0f)
    val speechLevel = _speechLevel.asStateFlow()

    private var callTimerJob: Job? = null
    private var simulationJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(context)
        repository = SecureDialerRepository(database)
        repository.masterPassphrase = _masterPassphrase.value

        callLogs = repository.allCallLogs.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        allPreferences = repository.allPreferences.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Combine contacts and search queries
        filteredContacts = combine(_nativeContacts, _searchQuery) { contacts, query ->
            if (query.isEmpty()) {
                contacts
            } else {
                contacts.filter {
                    it.name.contains(query, ignoreCase = true) ||
                    it.phoneNumber.contains(query)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Load mock/native contacts on init
        fetchNativeContacts()

        // Init TTS
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.e("SecureDialerVM", "TTS Init failed", e)
        }

        // Init SpeechRecognizer safely
        viewModelScope.launch(Dispatchers.Main) {
            setupSpeechRecognizer()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            _ttsInitialized.value = true
        } else {
            _ttsInitialized.value = false
        }
    }

    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {
                            _isListening.value = true
                        }
                        override fun onRmsChanged(rmsdB: Float) {
                            // Update voice level based on microphone volume
                            _speechLevel.value = (rmsdB + 2.0f).coerceIn(0.0f, 10.0f) / 10.0f
                        }
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {
                            _isListening.value = false
                        }
                        override fun onError(error: Int) {
                            _isListening.value = false
                        }
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                addLiveTranscription(matches[0])
                            }
                            _isListening.value = false
                        }
                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (!matches.isNullOrEmpty()) {
                                _liveRecognitionText.value = matches[0]
                            }
                        }
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
            } catch (e: Exception) {
                Log.e("SecureDialerVM", "SpeechRecognizer creation failed", e)
            }
        }
    }

    // Dialpad digit inputs
    fun appendDigit(digit: Char) {
        _dialInput.value = _dialInput.value + digit
    }

    fun deleteDigit() {
        val current = _dialInput.value
        if (current.isNotEmpty()) {
            _dialInput.value = current.substring(0, current.length - 1)
        }
    }

    fun clearDialInput() {
        _dialInput.value = ""
    }

    fun setDialInput(text: String) {
        _dialInput.value = text
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateMasterPassphrase(newKey: String) {
        _masterPassphrase.value = newKey
        repository.masterPassphrase = newKey
    }

    fun decryptLogNote(encryptedValue: String?): String {
        return repository.decryptNote(encryptedValue)
    }

    // Initiate Call Session
    fun initiateCall(phoneNumber: String) {
        if (phoneNumber.trim().isEmpty()) return

        val contactName = _nativeContacts.value.firstOrNull { it.phoneNumber == phoneNumber }?.name

        viewModelScope.launch {
            // Retrieve recording configuration for this contact
            val pref = repository.getPreferenceForContact(phoneNumber)
            val shouldAutoRecord = pref.recordingEnabled

            _activeCall.value = ActiveCallSession(
                phoneNumber = phoneNumber,
                contactName = contactName,
                isRecording = shouldAutoRecord,
                noiseCancellationActive = true
            )

            startCallTimer()
            startVisualSimulation()
            speakText("Call initiated to ${contactName ?: phoneNumber}")
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _activeCall.value = _activeCall.value?.let { session ->
                    session.copy(durationSeconds = session.durationSeconds + 1)
                }
            }
        }
    }

    private fun startVisualSimulation() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            while (true) {
                delay(150)
                val session = _activeCall.value ?: break
                
                // Add fluctuating waves for visually attractive DSP filter representation
                if (session.noiseCancellationActive) {
                    _noiseLevel.value = kotlin.random.Random.nextFloat() * (0.06f - 0.01f) + 0.01f
                } else {
                    _noiseLevel.value = kotlin.random.Random.nextFloat() * (0.55f - 0.25f) + 0.25f
                }

                // Mimic normal speech levels
                if (_isListening.value || kotlin.random.Random.nextInt(0, 11) > 6) {
                    _speechLevel.value = kotlin.random.Random.nextFloat() * (0.85f - 0.25f) + 0.25f
                } else {
                    _speechLevel.value = kotlin.random.Random.nextFloat() * (0.15f - 0.05f) + 0.05f
                }
            }
        }
    }

    // Toggle noise cancellation state
    fun toggleNoiseCancellation() {
        _activeCall.value = _activeCall.value?.let { session ->
            session.copy(noiseCancellationActive = !session.noiseCancellationActive)
        }
    }

    // Toggle mute state
    fun toggleMute() {
        _activeCall.value = _activeCall.value?.let { session ->
            session.copy(isMuted = !session.isMuted)
        }
    }

    // Toggle active recording
    fun toggleCallRecording() {
        _activeCall.value = _activeCall.value?.let { session ->
            session.copy(isRecording = !session.isRecording)
        }
    }

    // Save Call preference toggle
    fun toggleContactRecordingPreference(phoneNumber: String, enabled: Boolean) {
        viewModelScope.launch {
            val existing = repository.getPreferenceForContact(phoneNumber)
            repository.saveContactPreference(existing.copy(recordingEnabled = enabled))
        }
    }

    fun toggleContactCloudPreference(phoneNumber: String, autoSave: Boolean) {
        viewModelScope.launch {
            val existing = repository.getPreferenceForContact(phoneNumber)
            repository.saveContactPreference(existing.copy(autoSaveCloud = autoSave))
        }
    }

    // TTS speaker inside call
    fun speakText(text: String) {
        if (text.trim().isEmpty()) return
        if (_ttsInitialized.value) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SecDialerTTS")
        }
    }

    // Speech recognizer operations inside Call Session
    fun startListeningVoiceToNote() {
        if (speechRecognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            try {
                speechRecognizer?.startListening(intent)
                _isListening.value = true
                _liveRecognitionText.value = "Listening..."
            } catch (e: Exception) {
                simulateSpeechToTextResponse()
            }
        } else {
            simulateSpeechToTextResponse()
        }
    }

    fun stopListeningVoiceToNote() {
        if (speechRecognizer != null) {
            speechRecognizer?.stopListening()
        }
        _isListening.value = false
    }

    private fun simulateSpeechToTextResponse() {
        // Safe simulator fallback for devices/emulators lacking speech engines
        _isListening.value = true
        _liveRecognitionText.value = "...Listening..."
        viewModelScope.launch {
            delay(1500)
            val mockPhrases = listOf(
                "Agreed to meet at 4 PM tomorrow near downtown.",
                "Let's finalize the encrypted keys by tonight.",
                "Yes, send me the documentation on secure local SQLite.",
                "Can we configure cloud sync database options later?",
                "The noise cancellation filter is performing extremely well."
            )
            val phrase = mockPhrases.random()
            addLiveTranscription(phrase)
            _isListening.value = false
        }
    }

    private fun addLiveTranscription(text: String) {
        _liveRecognitionText.value = ""
        _activeCall.value = _activeCall.value?.let { session ->
            session.copy(voiceNotesList = session.voiceNotesList + text)
        }
    }

    // Finalize/hang up call
    fun endCall() {
        val session = _activeCall.value ?: return
        callTimerJob?.cancel()
        simulationJob?.cancel()

        viewModelScope.launch {
            val duration = session.durationSeconds
            val phoneNum = session.phoneNumber
            val name = session.contactName

            // Compile transcription index
            val combinedVoiceNotes = if (session.voiceNotesList.isNotEmpty()) {
                session.voiceNotesList.joinToString("\n")
            } else {
                null
            }

            // Save recording if toggle was on
            var finalRecordingPath: String? = null
            if (session.isRecording) {
                // Ensure directory and safe files
                val audioDir = File(context.cacheDir, "call_recordings")
                if (!audioDir.exists()) audioDir.mkdirs()
                val filename = "REC_${System.currentTimeMillis()}_${phoneNum}.mp3"
                val audioFile = File(audioDir, filename)
                try {
                    audioFile.writeText("Simulated voice stream binary payload for ${phoneNum}")
                    finalRecordingPath = audioFile.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Write call log entity
            val insertedId = repository.insertCallLog(
                phoneNumber = phoneNum,
                name = name,
                direction = "OUTGOING", // Standard triggered action
                rawNote = combinedVoiceNotes,
                recordingPath = finalRecordingPath
            )

            // Auto sync to cloud if preferred
            val pref = repository.getPreferenceForContact(phoneNum)
            if (pref.autoSaveCloud) {
                val addedLog = CallLogEntity(
                    id = insertedId.toInt(),
                    phoneNumber = phoneNum,
                    contactName = name,
                    durationSeconds = duration.toLong(),
                    direction = "OUTGOING",
                    voiceNoteEncrypted = combinedVoiceNotes?.let { EncryptionUtil.encrypt(it, _masterPassphrase.value) },
                    recordingPath = finalRecordingPath,
                    isCloudSynced = false
                )
                triggerSingleCloudSync(addedLog)
            }

            _activeCall.value = null
            _dialInput.value = ""
        }
    }

    private fun triggerSingleCloudSync(log: CallLogEntity) {
        viewModelScope.launch {
            repository.backupLogsToCloud(listOf(log))
        }
    }

    // Trigger complete cloud backup
    fun backupAllToCloud() {
        viewModelScope.launch {
            _isBackingUp.value = true
            val logs = callLogs.value.filter { !it.isCloudSynced }
            if (logs.isNotEmpty()) {
                repository.backupLogsToCloud(logs)
            } else {
                delay(1000)
            }
            _isBackingUp.value = false
        }
    }

    // Clear certain log entries
    fun deleteCallLog(id: Int) {
        viewModelScope.launch {
            repository.deleteCallLog(id)
        }
    }

    fun setSelectedLogId(id: Int?) {
        _selectedLogId.value = id
    }

    // Native Contacts resolution via Android Provider ContentResolver
    fun fetchNativeContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<ContactModel>()
            
            // Check read contacts permission is granted before querying
            val hasPermission = context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                try {
                    val resolver: ContentResolver = context.contentResolver
                    val cursor: Cursor? = resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
                        ),
                        null, null,
                        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
                    )

                    cursor?.use { c ->
                        val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val lookupIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)

                        while (c.moveToNext()) {
                            val id = if (idIdx >= 0) c.getString(idIdx) else ""
                            val name = if (nameIdx >= 0) c.getString(nameIdx) else "Unknown"
                            val num = if (numIdx >= 0) c.getString(numIdx) ?: "" else ""
                            val lookup = if (lookupIdx >= 0) c.getString(lookupIdx) else null
                            
                            // Normalize number formatting
                            val formattedNumber = num.replace(" ", "").replace("-", "")
                            
                            if (formattedNumber.isNotEmpty() && list.none { it.phoneNumber == formattedNumber }) {
                                list.add(ContactModel(id = id, name = name, phoneNumber = formattedNumber, lookupKey = lookup))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SecureDialerVM", "Error reading contacts provider", e)
                }
            }

            // Ensure we seed fallback popular mock contacts to populate the search view beautifully on fresh startup or if no hardware contacts are preset.
            if (list.isEmpty()) {
                list.addAll(getMockContacts())
            }

            _nativeContacts.value = list
        }
    }

    // Seed secure templates mock database
    private fun getMockContacts(): List<ContactModel> {
        return listOf(
            ContactModel("101", "Alice Vance (HQ Encryption)", "+15550198"),
            ContactModel("102", "Bob Jenkins (Cloud Server)", "+15550231"),
            ContactModel("103", "Aero Operations Dispatch", "+15550474"),
            ContactModel("104", "Jane Miller (Security Auditor)", "+15550912"),
            ContactModel("105", "David Vance (Primary Node)", "+15550881")
        )
    }

    // Default dialer request trigger intent
    fun requestDefaultDialerIntent(context: Context): android.content.Intent? {
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return if (telecomManager != null) {
            val intent = android.content.Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, context.packageName)
            intent
        } else {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
    }
}

// Needed helper import for RecognizerIntent inside standard files
typealias Intent = android.content.Intent
