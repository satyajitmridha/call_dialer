package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CallLogEntity
import com.example.data.ContactModel
import com.example.ui.theme.*
import kotlin.math.sin

enum class DialerTab(val title: String, val icon: ImageVector) {
    DIALER("Keypad", Icons.Default.Dialpad),
    LOGS("Call Logs", Icons.Default.Call),
    PREFERENCES("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureDialerApp(viewModel: SecureDialerViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(DialerTab.DIALER) }
    
    val activeCall by viewModel.activeCall.collectAsStateWithLifecycle()
    val isBackingUp by viewModel.isBackingUp.collectAsStateWithLifecycle()

    // Request permissions launcher
    val permissionsToRequest = arrayOf(
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE
    )

    var permissionsGranted by remember {
        mutableStateOf(
            permissionsToRequest.all {
                context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
        if (permissionsGranted) {
            viewModel.fetchNativeContacts()
            Toast.makeText(context, "Permissions updated successfully", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "AeroDial Secure",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "AES-256 Local Encrypted",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.backupAllToCloud() },
                        enabled = !isBackingUp,
                        modifier = Modifier.testTag("backup_top_button")
                    ) {
                        if (isBackingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = EmeraldPrimary)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sync all logs",
                                tint = CyberCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                DialerTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldPrimary,
                            selectedTextColor = EmeraldPrimary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                DialerTab.DIALER -> DialerTabContent(
                    viewModel = viewModel,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { launcher.launch(permissionsToRequest) }
                )
                DialerTab.LOGS -> LogsTabContent(viewModel = viewModel)
                DialerTab.PREFERENCES -> SettingsTabContent(
                    viewModel = viewModel,
                    permissionsGranted = permissionsGranted,
                    onRequestPermissions = { launcher.launch(permissionsToRequest) }
                )
            }

            // High priority absolute immersive Overlay Call Screen
            AnimatedVisibility(
                visible = activeCall != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                activeCall?.let { session ->
                    ActiveCallOverlayScreen(viewModel = viewModel, session = session)
                }
            }
        }
    }
}

@Composable
fun DialerTabContent(
    viewModel: SecureDialerViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val digits by viewModel.dialInput.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filteredContacts by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Quick contacts search input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            label = { Text("Search contact name or number...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("contact_search_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = EmeraldPrimary,
                focusedLabelColor = EmeraldPrimary
            )
        )

        if (searchQuery.isNotEmpty()) {
            // Contacts filter list
            Text(
                text = "MATCHED CONTACTS (${filteredContacts.size})",
                style = MaterialTheme.typography.labelSmall,
                color = EmeraldPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            if (filteredContacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No native contacts matched.\nType a number below to initiate direct dial.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredContacts) { contact ->
                        ContactItemCard(
                            contact = contact,
                            onClick = {
                                viewModel.setDialInput(contact.phoneNumber)
                                viewModel.updateSearchQuery("")
                            },
                            onDial = {
                                viewModel.initiateCall(contact.phoneNumber)
                            }
                        )
                    }
                }
            }
        } else {
            // Permission Hint standard card if contacts not fully granted
            if (!permissionsGranted) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = SecurityGold, modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Contacts access disabled", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Grant native contacts permission to search contacts flawlessly.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            onClick = onRequestPermissions,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                        ) {
                            Text("Allow", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Custom high quality interactive keypad
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Number screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = digits.ifEmpty { "Enter phone number" },
                            fontSize = if (digits.length > 12) 20.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (digits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else EmeraldPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (digits.isNotEmpty()) {
                            IconButton(onClick = { viewModel.deleteDigit() }) {
                                Icon(Icons.Default.Backspace, contentDescription = "Delete last character", tint = Color.Red)
                            }
                        }
                    }
                }

                // Grid keypad
                val keypadLayout = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    keypadLayout.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { d ->
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    DialKeyButton(
                                        digit = d,
                                        onClick = { viewModel.appendDigit(d[0]) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dial/Call launch trigger button
                IconButton(
                    onClick = { viewModel.initiateCall(digits) },
                    enabled = digits.isNotEmpty(),
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            if (digits.isNotEmpty()) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .testTag("dialcall_action_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Trigger outgoing call",
                        tint = if (digits.isNotEmpty()) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DialKeyButton(digit: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clickable(onClick = onClick)
            .testTag("keypad_btn_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            // Little alphabet letters under digit like traditional analog phone
            val subs = when (digit) {
                "2" -> "A B C"
                "3" -> "D E F"
                "4" -> "G H I"
                "5" -> "J K L"
                "6" -> "M N O"
                "7" -> "P Q R S"
                "8" -> "T U V"
                "9" -> "W X Y Z"
                "0" -> "+"
                else -> ""
            }
            if (subs.isNotEmpty()) {
                Text(
                    text = subs,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ContactItemCard(
    contact: ContactModel,
    onClick: () -> Unit,
    onDial: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Short initials avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CyberCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan,
                        fontSize = 18.sp
                    )
                }
                Column {
                    Text(
                        text = contact.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = contact.phoneNumber,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onDial,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(EmeraldPrimary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Quick Dial contact",
                    tint = EmeraldPrimary
                )
            }
        }
    }
}

@Composable
fun LogsTabContent(viewModel: SecureDialerViewModel) {
    val logs by viewModel.callLogs.collectAsStateWithLifecycle()
    val selectedLogId by viewModel.selectedLogId.collectAsStateWithLifecycle()
    val masterPassphrase by viewModel.masterPassphrase.collectAsStateWithLifecycle()
    
    var typedKeyInput by remember { mutableStateOf("") }
    var keyIsIncorrect by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "SECURE CALL ARCHIVES",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${logs.size} Logs",
                    fontWeight = FontWeight.Bold,
                    color = CyberCyan,
                    fontSize = 12.sp
                )
            }
        }

        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PhoneMissed, contentDescription = "Empty Log", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
                    Text(
                        text = "No encrypted logs recorded yet.\nDial a contact above to populate archives.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(logs) { log ->
                    val isExpanded = selectedLogId == log.id
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isExpanded) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("log_card_${log.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        onClick = {
                            if (isExpanded) {
                                viewModel.setSelectedLogId(null)
                                typedKeyInput = ""
                                keyIsIncorrect = false
                            } else {
                                viewModel.setSelectedLogId(log.id)
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val logColor = when (log.direction) {
                                        "INCOMING" -> StatusIncoming
                                        "OUTGOING" -> StatusOutgoing
                                        else -> StatusMissed
                                    }
                                    Icon(
                                        imageVector = when (log.direction) {
                                            "INCOMING" -> Icons.Default.CallReceived
                                            "OUTGOING" -> Icons.Default.CallMade
                                            else -> Icons.Default.CallMissed
                                        },
                                        contentDescription = log.direction,
                                        tint = logColor
                                    )
                                    Column {
                                        Text(
                                            text = log.contactName ?: log.phoneNumber,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault()).format(
                                                Date(log.timestamp)
                                            ),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${log.durationSeconds}s",
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan,
                                        fontSize = 14.sp
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (log.voiceNoteEncrypted != null) {
                                            Icon(Icons.Default.Lock, contentDescription = "Has transcript", tint = SecurityGold, modifier = Modifier.size(14.dp))
                                        }
                                        if (log.recordingPath != null) {
                                            Icon(Icons.Default.Mic, contentDescription = "Has calling record", tint = EmeraldPrimary, modifier = Modifier.size(14.dp))
                                        }
                                        if (log.isCloudSynced) {
                                            Icon(Icons.Default.CloudQueue, contentDescription = "Synced", tint = CyberCyan, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }

                            // Expanded detail pane with actual AES decrypt inputs
                            if (isExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.EnhancedEncryption, contentDescription = "AES", tint = SecurityGold, modifier = Modifier.size(16.dp))
                                    Text("Cryptographic Authentication Necessary", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = SecurityGold)
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))

                                var isDecrypted by remember { mutableStateOf(false) }
                                var decryptedNoteText by remember { mutableStateOf("") }

                                if (!isDecrypted) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = typedKeyInput,
                                            onValueChange = {
                                                typedKeyInput = it
                                                keyIsIncorrect = false
                                            },
                                            label = { Text("AES Key Passphrase") },
                                            singleLine = true,
                                            isError = keyIsIncorrect,
                                            modifier = Modifier.weight(1f).testTag("decrypt_passphrase_input"),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = SecurityGold,
                                                focusedLabelColor = SecurityGold
                                            )
                                        )
                                        Button(
                                            onClick = {
                                                if (typedKeyInput == masterPassphrase) {
                                                    val decrypted = viewModel.decryptLogNote(log.voiceNoteEncrypted)
                                                    decryptedNoteText = decrypted.ifEmpty { "No call notes transcription was recorded." }
                                                    isDecrypted = true
                                                    keyIsIncorrect = false
                                                } else {
                                                    keyIsIncorrect = true
                                                    decryptedNoteText = ""
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = SecurityGold),
                                            modifier = Modifier.testTag("decrypt_log_button")
                                        ) {
                                            Text("Decrypt", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                    if (keyIsIncorrect) {
                                        Text("Incorrect passphrase. Ensure it matches setting.", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                MaterialTheme.colorScheme.background,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp)
                                    ) {
                                        Text("DECRYPTED LOG TRANSCRIPT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(decryptedNoteText, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
                                    }

                                    if (log.recordingPath != null) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        MockAudioPlayerWidget(filePath = log.recordingPath)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.deleteCallLog(log.id) },
                                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete Log")
                                    }
                                    if (!log.isCloudSynced) {
                                        Button(
                                            onClick = { viewModel.backupAllToCloud() },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f), contentColor = CyberCyan),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = "Sync", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Sync to secure cloud", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "Cloud secured", tint = EmeraldPrimary, modifier = Modifier.size(16.dp))
                                            Text("Backed up to Server", fontSize = 11.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MockAudioPlayerWidget(filePath: String) {
    var playing by remember { mutableStateOf(false) }
    var soundProgress by remember { mutableStateOf(0.2f) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { playing = !playing },
                colors = IconButtonDefaults.iconButtonColors(containerColor = EmeraldPrimary, contentColor = Color.Black),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause record" else "Play record",
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Secure Call Recording", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                    Text(if (playing) "0:08 / 0:24" else "0:00 / 0:24", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = if (playing) 0.35f else 0.0f,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = EmeraldPrimary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
fun SettingsTabContent(
    viewModel: SecureDialerViewModel,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    val masterPassphrase by viewModel.masterPassphrase.collectAsStateWithLifecycle()
    var editKeyInput by remember { mutableStateOf(masterPassphrase) }
    var isEditingKey by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "GLOBAL CRYPTO & SECURITY CONFIG",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // AES Passphrase config card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.VpnKey, contentDescription = "AES-256", tint = SecurityGold)
                            Text("Database Master Passphrase", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        if (!isEditingKey) {
                            TextButton(onClick = { isEditingKey = true }) {
                                Text("Change", color = CyberCyan)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "All voice transcription logs and active database records are AES-256 encrypted symmetrically using this key. Keep it backed up locally.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    if (isEditingKey) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = editKeyInput,
                                onValueChange = { editKeyInput = it },
                                singleLine = true,
                                label = { Text("New Passphrase") },
                                modifier = Modifier.weight(1f).testTag("settings_master_key_field")
                            )
                            Button(
                                onClick = {
                                    if (editKeyInput.isNotBlank()) {
                                        viewModel.updateMasterPassphrase(editKeyInput)
                                        isEditingKey = false
                                        Toast.makeText(context, "AES key updated successfully", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(
                            text = "••••••••••••••••",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SecurityGold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Noise Cancellation DSP Setting Card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.GraphicEq, contentDescription = "DSP Mode", tint = EmeraldPrimary)
                        Text("Noise Cancellation (DSP Core)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Enables advanced sound filtering during calling sequences. Mitigates ambient feedback, echo leakage, wind rumbles, and background high decibel noise.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Active Filtering Engine", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Badge(containerColor = EmeraldPrimary, contentColor = Color.Black) {
                            Text("ENABLED (COMPATIBLE 12+)", fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
            }
        }

        // Server backup config card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CloudSync, contentDescription = "Cloud backup", tint = CyberCyan)
                        Text("Simulated Cloud Backup Core", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Synchronize locally encrypted AES archives into secure, encrypted server-database storage automatically.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.backupAllToCloud() },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        modifier = Modifier.fillMaxWidth().testTag("backup_db_action")
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = "Backup Database")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Trigger Encrypted Database Sync Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Default Handler Request Option
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Shield", tint = EmeraldPrimary)
                        Text("Default Phone Handler Setting", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Set AeroDial as your standard phone handler application for automatic call intercepts, native audio-record support, and dialers integration context.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val intent = viewModel.requestDefaultDialerIntent(context)
                            if (intent != null) {
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Dialer request not supported on this emulation profile", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "System service telecom is unavailable.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.fillMaxWidth().testTag("set_default_dialer_btn")
                    ) {
                        Text("Request Default Dialer Designation", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Permissions control card
        item {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Privacy & Authorization Manifest",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Permissions Status", fontSize = 12.sp)
                        Text(
                            text = if (permissionsGranted) "FULLY COMPLIANT" else "PENDING ACTIONS",
                            color = if (permissionsGranted) EmeraldPrimary else SecurityGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermissions,
                        enabled = !permissionsGranted,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("req_permissions_btn")
                    ) {
                        Text("Authorize All Core Capabilities", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Full Immersive Active Call overlay layout with visual waveform cancellation
@Composable
fun ActiveCallOverlayScreen(
    viewModel: SecureDialerViewModel,
    session: ActiveCallSession
) {
    var ttsText by remember { mutableStateOf("") }
    
    val noiseLevel by viewModel.noiseLevel.collectAsStateWithLifecycle()
    val speechLevel by viewModel.speechLevel.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val liveRecognitionText by viewModel.liveRecognitionText.collectAsStateWithLifecycle()

    val formattedDuration = String.format(
        Locale.US,
        "%02d:%02d",
        session.durationSeconds / 60,
        session.durationSeconds % 60
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("active_call_overlay_root"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header panel (Caller Identity)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ACTIVE SECURED ENCRYPTED CONNECTION",
                    fontSize = 10.sp,
                    color = EmeraldPrimary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Big pulsating avatar
                val infiniteTransition = rememberInfiniteTransition()
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .border(2.dp, EmeraldPrimary.copy(alpha = 0.5f), CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        EmeraldPrimary.copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (session.contactName ?: session.phoneNumber).take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 38.sp,
                            color = EmeraldPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = session.contactName ?: "Unknown Destination",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = session.phoneNumber,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = formattedDuration,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 22.sp,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )

                // Recording Status flashing label
                if (session.isRecording) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val recordPulse by infiniteTransition.animateFloat(
                            initialValue = 0.2f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(800, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color.Red.copy(alpha = recordPulse))
                        )
                        Text("AUTO-RECORDING SECURE AUDIO", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }

            // Real-time Canvas Waveforms: The "Noise Cancellation Visualizer"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(DarkSurface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "DSP DIGITAL SIGNAL ANALYZER",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(WaveformAmbient, CircleShape))
                        Text("Feedback Noise", fontSize = 9.sp, color = WaveformAmbient)
                        Box(modifier = Modifier.size(6.dp).background(WaveformFiltered, CircleShape))
                        Text("Filtered Voice", fontSize = 9.sp, color = WaveformFiltered)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Animating canvas paths on the fly!
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val midY = height / 2

                        // 1. Draw Ambient Noise Wave (Jagged Red)
                        val noisePath = Path()
                        noisePath.moveTo(0f, midY)
                        for (x in 0..width.toInt() step 5) {
                            // Fluctuates based on active setting
                            val amplitudeMultiplier = if (session.noiseCancellationActive) 3f else 22f
                            val noiseFluc = sin((x.toFloat() * 0.08f) + (System.currentTimeMillis() * 0.01f)) * amplitudeMultiplier
                            val randomness = (0..6).random() - 3 // Makes it jagged!
                            val finalY = midY + noiseFluc + (if (session.noiseCancellationActive) 0f else randomness * 2f)
                            noisePath.lineTo(x.toFloat(), finalY.coerceIn(5f, height - 5f))
                        }
                        drawPath(
                            path = noisePath,
                            color = WaveformAmbient.copy(alpha = if (session.noiseCancellationActive) 0.15f else 0.75f),
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // 2. Draw Filtered Pure Voice Wave (Smooth Emerald Sine Wave)
                        val voicePath = Path()
                        voicePath.moveTo(0f, midY)
                        for (x in 0..width.toInt() step 5) {
                            val amplitudeMultiplier = 16f
                            val voiceFluc = sin((x.toFloat() * 0.04f) - (System.currentTimeMillis() * 0.008f)) * amplitudeMultiplier * speechLevel
                            voicePath.lineTo(x.toFloat(), midY + voiceFluc)
                        }
                        drawPath(
                            path = voicePath,
                            color = WaveformFiltered.copy(alpha = 0.9f),
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (session.noiseCancellationActive) "NOISE SHIELD: ON (CLARITY OPTIMIZED)" else "NOISE SHIELD: OFF (AMBIENT BYPASS ON)",
                        color = if (session.noiseCancellationActive) EmeraldPrimary else SecurityGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "SNR Ratio: ${if (session.noiseCancellationActive) "+34dB" else "+8dB"}",
                        color = if (session.noiseCancellationActive) EmeraldPrimary else WaveformAmbient,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Real Voice-to-Note dictation and accessibility TTS tools
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Live voice note box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VOICE TO SECURE TRANSCRIPT NOTE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                        IconButton(
                            onClick = {
                                if (isListening) viewModel.stopListeningVoiceToNote() else viewModel.startListeningVoiceToNote()
                            },
                            modifier = Modifier.size(24.dp).testTag("record_voice_to_note_btn")
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.StopCircle else Icons.Default.Mic,
                                contentDescription = "Listen",
                                tint = if (isListening) Color.Red else EmeraldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (session.voiceNotesList.isEmpty() && !isListening) {
                            Text(
                                "No active spoken transcripts captured. Tap mic above to speak or auto-simulate caller voice notes.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(session.voiceNotesList) { transcript ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Locked", tint = SecurityGold, modifier = Modifier.size(10.dp))
                                        Text(transcript, fontSize = 12.sp, color = Color.White)
                                    }
                                }
                                if (isListening && liveRecognitionText.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = liveRecognitionText,
                                            fontSize = 12.sp,
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Text To Voice accessibility typing keyboard
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("TEXT-TO-VOICE (ACCESSIBILITY SYNTHESIS)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = ttsText,
                            onValueChange = { ttsText = it },
                            placeholder = { Text("Type voice message to caller...", fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("accessibility_tts_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                focusedLabelColor = CyberCyan
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.speakText(ttsText)
                                ttsText = ""
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            enabled = ttsText.isNotBlank(),
                            modifier = Modifier.testTag("speak_accessibility_btn")
                        ) {
                            Text("Speak", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                        }
                    }
                }
            }

            // Central audio & calling operation controllers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggle Noise Cancellation inside calling sequence
                IconButton(
                    onClick = { viewModel.toggleNoiseCancellation() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (session.noiseCancellationActive) EmeraldPrimary else DarkSurface)
                        .testTag("active_call_toggle_noise")
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Noise cancellation",
                        tint = if (session.noiseCancellationActive) Color.Black else Color.White
                    )
                }

                // Call Recording switch button
                IconButton(
                    onClick = { viewModel.toggleCallRecording() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (session.isRecording) Color.Red else DarkSurface)
                        .testTag("active_call_toggle_record")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Recording toggle",
                        tint = if (session.isRecording) Color.White else Color.Red
                    )
                }

                // Mute microphone
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (session.isMuted) SecurityGold else DarkSurface)
                        .testTag("active_call_toggle_mute")
                ) {
                    Icon(
                        imageVector = if (session.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (session.isMuted) Color.Black else Color.White
                    )
                }

                // Hang Up Outgoing Call
                IconButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .testTag("active_call_hangup_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Hang up call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
