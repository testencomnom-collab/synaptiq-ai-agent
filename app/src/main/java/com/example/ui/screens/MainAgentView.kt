package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatMessage
import com.example.data.model.EmailItem
import com.example.data.model.LocalAgentRepository
import com.example.services.CalendarEvent
import com.example.services.CalendarManager
import com.example.ui.AgentViewModel
import com.example.permissions.PermissionsManager
import com.example.permissions.AgentPermission
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAgentView(
    viewModel: AgentViewModel,
    permissionsManager: PermissionsManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentTab by remember { mutableStateOf("chat") }
    
    // Track runtime permission states
    var hasCalendarPerms by remember {
        mutableStateOf(
            permissionsManager.isGranted(AgentPermission.READ_CALENDAR) &&
            permissionsManager.isGranted(AgentPermission.WRITE_CALENDAR)
        )
    }
    var hasContactsPerms by remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.READ_CONTACTS)) }
    var hasLocationPerms by remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.ACCESS_FINE_LOCATION)) }
    var hasSmsPerms by remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.SEND_SMS)) }
    var hasAccountsPerms by remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.GET_ACCOUNTS)) }
    
    val requestCalendarPermissions = {
        coroutineScope.launch {
            val results = permissionsManager.requestPermissions(
                AgentPermission.READ_CALENDAR,
                AgentPermission.WRITE_CALENDAR
            )
            hasCalendarPerms = results.values.all { it }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == "chat",
                    onClick = { currentTab = "chat" },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                    label = { Text("Agent") }
                )
                NavigationBarItem(
                    selected = currentTab == "inbox",
                    onClick = { currentTab = "inbox" },
                    icon = { Icon(Icons.Default.Email, contentDescription = "Inbox") },
                    label = { Text("Inbox") }
                )
                NavigationBarItem(
                    selected = currentTab == "calendar",
                    onClick = { currentTab = "calendar" },
                    icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                    label = { Text("Schedule") }
                )
                NavigationBarItem(
                    selected = currentTab == "library",
                    onClick = { currentTab = "library" },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Library") },
                    label = { Text("Library") }
                )
                NavigationBarItem(
                    selected = currentTab == "settings",
                    onClick = { currentTab = "settings" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                "chat" -> AgentChatTab(viewModel, hasCalendarPerms, onRequestPermissions = { requestCalendarPermissions() })
                "inbox" -> SimulatedInboxTab(viewModel, onNavigateToChat = { currentTab = "chat" })
                "calendar" -> SystemCalendarTab(viewModel, hasCalendarPerms, onRequestPermissions = { requestCalendarPermissions() })
                "library" -> AgentLibraryTab(viewModel)
                "settings" -> AgentSettingsTab(
                    viewModel = viewModel,
                    hasCalendarPerms = hasCalendarPerms,
                    hasContactsPerms = hasContactsPerms,
                    hasLocationPerms = hasLocationPerms,
                    hasSmsPerms = hasSmsPerms,
                    hasAccountsPerms = hasAccountsPerms,
                    onRequestCalendarPerms = { requestCalendarPermissions() },
                    onRequestContactsPerms = {
                        coroutineScope.launch {
                            val results = permissionsManager.requestPermissions(AgentPermission.READ_CONTACTS)
                            hasContactsPerms = results.values.firstOrNull() ?: false
                        }
                    },
                    onRequestLocationPerms = {
                        coroutineScope.launch {
                            val results = permissionsManager.requestPermissions(AgentPermission.ACCESS_FINE_LOCATION)
                            hasLocationPerms = results.values.firstOrNull() ?: false
                        }
                    },
                    onRequestSmsPerms = {
                        coroutineScope.launch {
                            val results = permissionsManager.requestPermissions(AgentPermission.SEND_SMS)
                            hasSmsPerms = results.values.firstOrNull() ?: false
                        }
                    },
                    onRequestAccountsPerms = {
                        coroutineScope.launch {
                            val results = permissionsManager.requestPermissions(AgentPermission.GET_ACCOUNTS)
                            hasAccountsPerms = results.values.firstOrNull() ?: false
                        }
                    }
                )
            }
        }
    }
}

// ==================== AGENT CHAT TAB ====================
@Composable
fun AgentChatTab(
    viewModel: AgentViewModel,
    hasCalendarPerms: Boolean,
    onRequestPermissions: () -> Unit
) {
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var inputQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(modifier = Modifier.fillMaxSize()) {
        // App Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = "Agent Icon",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Aura Agent",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "SYSTEM ACTIVE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        // Active State Warnings
        if (!hasCalendarPerms) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Permission Warning",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Calendar disconnected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Text(
                            "Grant access to allow the agent to detect slots and book meetings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Button(
                        onClick = onRequestPermissions,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Allow", fontSize = 11.sp)
                    }
                }
            }
        }

        // Check if API Key missing completely
        val apiKey = viewModel.preferencesManager.getActiveApiKey()
        if (apiKey.trim().isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = "No API Key",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "No API Key Configured",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            "Please add a provider key in Settings",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color(0xFFEF4444)) // Red indicator for error
                    )
                }
            }
        } else {
             // Show active configured API state pill (from the design)
             Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Key,
                        contentDescription = "API Key Active",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        val provider by viewModel.activeProviderFlow.collectAsStateWithLifecycle()
                        val model by viewModel.activeModelFlow.collectAsStateWithLifecycle()
                        Text(
                            "$provider $model",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            "API Key configured successfully",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color(0xFF4ADE80)) // Green indicator
                    )
                }
            }
        }

        // Messages List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (chatHistory.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillParentMaxHeight(0.8f)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(128.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.onPrimary,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Waves,
                                contentDescription = "Active Agent",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        Text(
                            text = "System Online.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            } else {
                items(chatHistory) { message ->
                    ChatMessageBubble(message, onExecuteAction = {
                        viewModel.executeProposedAction(message)
                    })
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Agent is reasoning...", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        // Input bottom bar
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = { Text("Ask anything...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_text_field"),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputQuery.trim().isNotEmpty()) {
                                viewModel.sendMessage(inputQuery)
                                inputQuery = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (inputQuery.trim().isNotEmpty()) {
                            viewModel.sendMessage(inputQuery)
                            inputQuery = ""
                            keyboardController?.hide()
                        }
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send prompt",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    onExecuteAction: () -> Unit
) {
    val isUser = message.role == "user"
    val align = if (isUser) Alignment.End else Alignment.Start
    val bg = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val textCol = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface

    var showThoughts by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                Icon(
                    Icons.Default.SupportAgent,
                    contentDescription = "Agent",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .padding(end = 6.dp, top = 2.dp)
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = bg),
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Message Text
                    Text(
                        text = message.message,
                        color = textCol,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Assistant Thoughts scratchpad
                    if (!isUser && !message.thought.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { showThoughts = !showThoughts }
                                .padding(8.dp)
                                .animateContentSize()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.Psychology,
                                        contentDescription = "Thoughts",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Agent Reasonings",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Icon(
                                    if (showThoughts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle Reasonings",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (showThoughts) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.thought,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Proposed Actions Card
                    if (!isUser && message.hasAction && message.actionData != null) {
                        ProposedActionBlock(
                            actionDataJson = message.actionData,
                            isExecuted = message.actionExecuted,
                            onConfirm = onExecuteAction
                        )
                    }
                }
            }
        }
        Text(
            text = formatTime(message.timestamp),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp, start = if (isUser) 0.dp else 36.dp, end = if (isUser) 8.dp else 0.dp)
        )
    }
}

@Composable
fun ProposedActionBlock(
    actionDataJson: String,
    isExecuted: Boolean,
    onConfirm: () -> Unit
) {
    var type = "NONE"
    var emailRecipient = ""
    var emailSubject = ""
    var emailBody = ""
    var calendarTitle = ""
    var calendarDesc = ""
    var calendarStart = 0L
    var calendarEnd = 0L

    try {
        val json = JSONObject(actionDataJson)
        type = json.optString("type", "NONE")
        emailRecipient = json.optString("emailRecipient", "")
        emailSubject = json.optString("emailSubject", "")
        emailBody = json.optString("emailBody", "")
        calendarTitle = json.optString("calendarTitle", "")
        calendarDesc = json.optString("calendarDesc", "")
        calendarStart = json.optLong("calendarStart", 0L)
        calendarEnd = json.optLong("calendarEnd", 0L)
    } catch (e: Exception) {
        Log.e("ProposedActionBlock", "Parsing failed", e)
    }

    Spacer(modifier = Modifier.height(12.dp))
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Celebration,
                    contentDescription = "Proposal",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Proposed Actions (${if (isExecuted) "Completed" else "Waiting Approval"})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Action Details (Calendar Event)
            if (type == "CALENDAR" || type == "BOTH") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Event",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Schedule Event",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(calendarTitle, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (calendarDesc.isNotEmpty()) {
                                Text(calendarDesc, fontSize = 11.sp, maxLines = 1)
                            }
                            val sdf = SimpleDateFormat("EEEE, MMM d, yyyy @ h:mm a", Locale.getDefault())
                            Text(
                                "Start: ${sdf.format(Date(calendarStart))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Action Details (Email Draft)
            if (type == "EMAIL" || type == "BOTH") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Email",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Compose Draft",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("To: $emailRecipient", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Subj: $emailSubject", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(emailBody, fontSize = 10.sp, maxLines = 4)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            if (!isExecuted) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Confirm",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Execute Agent Action", fontSize = 12.sp)
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Executed",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Actions successfully launched",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}


// ==================== SIMULATED INBOX TAB ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimulatedInboxTab(
    viewModel: AgentViewModel,
    onNavigateToChat: () -> Unit
) {
    val emails by viewModel.emails.collectAsStateWithLifecycle()
    var openAddDialog by remember { mutableStateOf(false) }

    var senderInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Inbox Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "On-Device Simulated Inbox",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "${emails.size} messages in simulated memory",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                Row {
                    IconButton(onClick = { viewModel.clearInbox() }) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear Inbox",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(
                        onClick = { openAddDialog = true },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .testTag("add_email_button")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Receive Email",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Emails Column
        if (emails.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.MailOutline,
                        contentDescription = "No Mail",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Inbox is dry", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Simulate a incoming mail to test agent reply routing!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp, start = 20.dp, end = 20.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(emails) { email ->
                    EmailCard(email, onReplyWithAgent = {
                        val prompt = "Analyze this email draft response and schedule a meeting if they requested:\n\n" +
                                "From: ${email.sender}\n" +
                                "Subject: ${email.subject}\n" +
                                "Body: ${email.body}"
                        viewModel.sendMessage(prompt)
                        onNavigateToChat()
                    }, onDelete = {
                        viewModel.removeEmail(email.id)
                    })
                }
            }
        }
    }

    // Compose Email Simulation Modal
    if (openAddDialog) {
        AlertDialog(
            onDismissRequest = { openAddDialog = false },
            title = { Text("Simulate Email Delivery", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Receive a new user email mock directly into memory.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text("From Sender (e.g., boss@work.com)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_sender_input")
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("Subject Line") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_subject_input")
                    )
                    OutlinedTextField(
                        value = bodyInput,
                        onValueChange = { bodyInput = it },
                        label = { Text("Message Body Prompt") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .testTag("email_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (senderInput.isNotEmpty() && bodyInput.isNotEmpty()) {
                            viewModel.addEmailToInbox(senderInput, subjectInput, bodyInput)
                            senderInput = ""
                            subjectInput = ""
                            bodyInput = ""
                            openAddDialog = false
                        }
                    },
                    modifier = Modifier.testTag("email_confirm_add")
                ) {
                    Text("Deliver")
                }
            },
            dismissButton = {
                TextButton(onClick = { openAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EmailCard(
    email: EmailItem,
    onReplyWithAgent: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiary
        ),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = "Avatar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        email.sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(20.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Delete mock",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                email.subject,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                email.body,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onReplyWithAgent,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    Icons.Default.SupportAgent,
                    contentDescription = "Delegate",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Process Reply with AI Agent", fontSize = 11.sp)
            }
        }
    }
}


// ==================== CALENDAR TAB ====================
@Composable
fun SystemCalendarTab(
    viewModel: AgentViewModel,
    hasCalendarPerms: Boolean,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    var eventList by remember { mutableStateOf<List<CalendarEvent>>(emptyList()) }
    var refreshIndicator by remember { mutableStateOf(0) }

    var testCreateDialog by remember { mutableStateOf(false) }
    var createTitle by remember { mutableStateOf("") }
    var createDesc by remember { mutableStateOf("") }

    // Fetch calendar events dynamically
    LaunchedEffect(hasCalendarPerms, refreshIndicator) {
        if (hasCalendarPerms) {
            val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0) }.timeInMillis
            val end = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }.timeInMillis
            eventList = CalendarManager.fetchEvents(context, start, end)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "On-Device Active Planner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        "Querying local calendar (Next 7 Days)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row {
                    IconButton(onClick = { refreshIndicator++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh data")
                    }
                    if (hasCalendarPerms) {
                        IconButton(
                            onClick = { testCreateDialog = true },
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add slot",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        if (!hasCalendarPerms) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Outlined.LockClock,
                        contentDescription = "Permission Off",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Calendar System Blocked",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "The agent reads dynamic calendar conflicts from local device schemas to execute scheduling without overlaps. Allow runtime permissions.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRequestPermissions) {
                        Text("Grant Calendar Access")
                    }
                }
            }
        } else {
            if (eventList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.CalendarToday,
                            contentDescription = "No Events",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Calendar is clean", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Create an appointment or delegate schedule parsing directly via the Agent!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(eventList) { event ->
                        CalendarEventCard(event)
                    }
                }
            }
        }
    }

    // Modal adding manual test appointment to real on-device calendar for testing availability
    if (testCreateDialog) {
        AlertDialog(
            onDismissRequest = { testCreateDialog = false },
            title = { Text("Book System Appointment", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Reserve standard 1-hour slot in local agenda beginning tomorrow morning directly.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = createTitle,
                        onValueChange = { createTitle = it },
                        label = { Text("Event Title") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = createDesc,
                        onValueChange = { createDesc = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (createTitle.isNotEmpty()) {
                        // schedule tomorrow at 10 AM
                        val tomorrow = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 10)
                            set(Calendar.MINUTE, 0)
                        }
                        val end = Calendar.getInstance().apply {
                            timeInMillis = tomorrow.timeInMillis
                            add(Calendar.HOUR_OF_DAY, 1)
                        }

                        CalendarManager.insertEvent(
                            context = context,
                            title = createTitle,
                            description = createDesc,
                            startMillis = tomorrow.timeInMillis,
                            endMillis = end.timeInMillis
                        )
                        
                        createTitle = ""
                        createDesc = ""
                        testCreateDialog = false
                        refreshIndicator++
                    }
                }) {
                    Text("Schedule")
                }
            },
            dismissButton = {
                TextButton(onClick = { testCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CalendarEventCard(event: CalendarEvent) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Event icon",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (!event.description.isNullOrEmpty()) {
                    Text(
                        event.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
                
                val sdf = SimpleDateFormat("E d MMM | h:mm a", Locale.getDefault())
                val endSdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                Text(
                    "${sdf.format(Date(event.startTime))} - ${endSdf.format(Date(event.endTime))}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}


// ==================== SETTINGS TAB ====================
@Composable
fun AgentSettingsTab(
    viewModel: AgentViewModel,
    hasCalendarPerms: Boolean,
    hasContactsPerms: Boolean,
    hasLocationPerms: Boolean,
    hasSmsPerms: Boolean,
    hasAccountsPerms: Boolean,
    onRequestCalendarPerms: () -> Unit,
    onRequestContactsPerms: () -> Unit,
    onRequestLocationPerms: () -> Unit,
    onRequestSmsPerms: () -> Unit,
    onRequestAccountsPerms: () -> Unit
) {
    val activeProvider by viewModel.activeProviderFlow.collectAsStateWithLifecycle()
    val model by viewModel.activeModelFlow.collectAsStateWithLifecycle()

    var openaiKey by remember { mutableStateOf(viewModel.preferencesManager.openAiApiKey) }
    var anthropicKey by remember { mutableStateOf(viewModel.preferencesManager.anthropicApiKey) }
    var geminiKey by remember { mutableStateOf(viewModel.preferencesManager.geminiApiKey) }

    var showPasswordOpenai by remember { mutableStateOf(false) }
    var showPasswordAnthropic by remember { mutableStateOf(false) }
    var showPasswordGemini by remember { mutableStateOf(false) }

    val statusMsg by viewModel.statusMessage.collectAsStateWithLifecycle()

    if (statusMsg != null) {
        LaunchedEffect(statusMsg) {
            viewModel.clearStatus()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Agent Configurations",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "Configure your LLM provider Keys below. All credentials remain fully isolated locally on your device within SharedPreferences.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        // --- LLM Providers configuration ---
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "LLM Selection & API Credentials",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Select provider Segment tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("OPENAI", "ANTHROPIC", "GEMINI").forEach { prov ->
                            val isSelected = activeProvider == prov
                            val col = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            val txt = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.selectProvider(prov) }
                                    .background(col)
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    prov,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = txt
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Config Key OpenAI
                    Column {
                        Text("OpenAI Key", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = openaiKey,
                            onValueChange = {
                                openaiKey = it
                                viewModel.updateApiKey("OPENAI", it)
                            },
                            visualTransformation = if (showPasswordOpenai) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPasswordOpenai = !showPasswordOpenai }) {
                                    Icon(
                                        if (showPasswordOpenai) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            placeholder = { Text("sk-proj-...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openai_api_key_field"),
                            maxLines = 1,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Config Key Anthropic
                    Column {
                        Text("Anthropic Key", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = anthropicKey,
                            onValueChange = {
                                anthropicKey = it
                                viewModel.updateApiKey("ANTHROPIC", it)
                            },
                            visualTransformation = if (showPasswordAnthropic) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPasswordAnthropic = !showPasswordAnthropic }) {
                                    Icon(
                                        if (showPasswordAnthropic) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            placeholder = { Text("sk-ant-...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("anthropic_api_key_field"),
                            maxLines = 1,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    // Config Key Gemini
                    Column {
                        Text("Gemini Key", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = geminiKey,
                            onValueChange = {
                                geminiKey = it
                                viewModel.updateApiKey("GEMINI", it)
                            },
                            visualTransformation = if (showPasswordGemini) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPasswordGemini = !showPasswordGemini }) {
                                    Icon(
                                        if (showPasswordGemini) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_field"),
                            maxLines = 1,
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Current Active Model Default: $model",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Permissions Card status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "On-Device System Access",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Calendar permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Calendar Integration Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Read and write calendar events", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasCalendarPerms) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Granted", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onRequestCalendarPerms,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Connect", fontSize = 10.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // Contacts permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Contacts Sync", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Identify friends and colleagues", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasContactsPerms) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Granted", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onRequestContactsPerms,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Connect", fontSize = 10.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // Location permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Precise Location", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Provide localized suggestions", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasLocationPerms) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Granted", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onRequestLocationPerms,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Connect", fontSize = 10.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // SMS permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SMS Integration", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Draft and send message responses", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasSmsPerms) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Granted", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onRequestSmsPerms,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Connect", fontSize = 10.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceVariant)

                    // Accounts permission row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Device Accounts Access", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Scan email profiles dynamically", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasAccountsPerms) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, "Granted", tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Active", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onRequestAccountsPerms,
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Connect", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Maintenance
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Workspace Maintenance",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Button(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, "Clear database", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Agent Conversations", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// Helpers
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
