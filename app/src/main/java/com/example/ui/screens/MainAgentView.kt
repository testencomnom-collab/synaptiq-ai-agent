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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

    val activeAgentId by viewModel.activeChatAgentId.collectAsStateWithLifecycle()
    val activeAgentsSet by viewModel.activeAgentsFlow.collectAsStateWithLifecycle()
    val allAgents = LocalAgentRepository.agents

    val selectableAgents = mutableListOf(Pair("system", "Aura Agent"))
    activeAgentsSet.forEach { id ->
        allAgents.find { it.id == id }?.let { selectableAgents.add(Pair(it.id, it.name)) }
    }
    val currentAgentName = selectableAgents.find { it.first == activeAgentId }?.second ?: "Aura Agent"
    var expanded by remember { mutableStateOf(false) }

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
                    Column(modifier = Modifier.clickable { expanded = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentAgentName,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                letterSpacing = (-0.5).sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Agent")
                        }
                        if (activeAgentId == "system") {
                            Text(
                                text = "SYSTEM ACTIVE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CloudOff, contentDescription = "Offline", modifier = Modifier.size(12.dp), tint = Color(0xFF10B981))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "OFFLINE-MODUS AKTIV",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            selectableAgents.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        viewModel.activeChatAgentId.value = id
                                        expanded = false
                                    }
                                )
                            }
                        }
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
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(chatHistory.size, isLoading) {
            if (chatHistory.isNotEmpty()) {
                // Scroll to the very last item (messages + optional loading indicator)
                val targetIndex = chatHistory.size - 1 + (if (isLoading) 1 else 0)
                listState.animateScrollToItem(targetIndex)
            }
        }

        LazyColumn(
            state = listState,
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
                        modifier = Modifier.fillMaxWidth(0.85f).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Icon(
                            Icons.Default.SupportAgent,
                            contentDescription = "Agent",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp).padding(end = 6.dp, top = 2.dp)
                        )
                        TypingIndicatorBubble()
                    }
                }
            }
        }

        // Input bottom bar
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), RoundedCornerShape(28.dp))
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Symbol",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp).size(22.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { inputQuery = it },
                    placeholder = { 
                        Text(
                            "Automatisierungs-Befehl eingeben...", 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), 
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ) 
                    },
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
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
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
                        .size(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Senden",
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
    val shape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (isUser) 24.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 24.dp
    )
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
            Box(
                modifier = Modifier
                    .clip(shape)
                    .then(
                        if (isUser) {
                            Modifier.background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                        } else {
                            Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        }
                    )
                    .border(
                        1.dp,
                        if (isUser) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        shape
                    )
                    .padding(14.dp)
            ) {
                Column {
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0F0E13))
                                .border(1.dp, Color(0xFF23212C), RoundedCornerShape(12.dp))
                                .clickable { showThoughts = !showThoughts }
                                .padding(10.dp)
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
                                        contentDescription = "Gedankengänge",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Agenten-Denkprozess",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    if (showThoughts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Denkprozess umschalten",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            if (showThoughts) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = message.thought,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF10B981) // Matrix Green
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Inbox Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Simulierter Posteingang",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "${emails.size} Nachrichten im Gerätespeicher",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.clearInbox() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Inbox leeren",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    IconButton(
                        onClick = { openAddDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("add_email_button")
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "E-Mail simulieren",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

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
                        contentDescription = "Keine E-Mails",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Posteingang ist leer", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Simuliere eine eingehende E-Mail, um die automatische Terminplanung und Antwort-Generierung des Agenten zu testen!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(emails) { email ->
                    EmailCard(email, onReplyWithAgent = {
                        val prompt = "Analysiere diese E-Mail, entwerfe eine Antwort und plane einen Kalendertermin, falls der Absender darum gebeten hat:\n\n" +
                                "Von: ${email.sender}\n" +
                                "Betreff: ${email.subject}\n" +
                                "Inhalt: ${email.body}"
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
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "E-Mail-Eingang simulieren", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Füge eine simulierte E-Mail in den lokalen Speicher ein, um den Agenten zu testen.", 
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text("Absender (z. B. chef@firma.com)") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_sender_input")
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("Betreff") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("email_subject_input")
                    )
                    OutlinedTextField(
                        value = bodyInput,
                        onValueChange = { bodyInput = it },
                        label = { Text("Nachrichtentext") },
                        shape = RoundedCornerShape(12.dp),
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
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("email_confirm_add")
                ) {
                    Text("Empfangen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { openAddDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { 
                    Text("Abbrechen") 
                }
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = "Absender",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        email.sender,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDelete, 
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Löschen",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                email.subject,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                email.body,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onReplyWithAgent,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.SupportAgent,
                    contentDescription = "Verarbeiten",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("E-Mail mit KI-Agent verarbeiten", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Calendar Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Terminkalender",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "Lokale Kalenderkonflikte (Nächste 7 Tage)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { refreshIndicator++ },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    ) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = "Aktualisieren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (hasCalendarPerms) {
                        Spacer(modifier = Modifier.width(10.dp))
                        IconButton(
                            onClick = { testCreateDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Termin hinzufügen",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

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
                        contentDescription = "Gesperrt",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Kalenderzugriff blockiert",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Der Agent liest Kalenderkonflikte von deinem Gerät ab, um Überschneidungen bei automatischen Planungen zu vermeiden. Bitte erteile die Berechtigung.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRequestPermissions,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Kalenderzugriff erlauben", fontWeight = FontWeight.Bold)
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
                            contentDescription = "Keine Termine",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Kalender ist frei", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Erstelle einen Termin oder lasse den KI-Agenten über den Chat deine Agenda für dich planen!",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 6.dp),
                            lineHeight = 18.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "Termin manuell buchen", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Trage einen standardmäßigen 1-stündigen Testtermin für morgen früh um 10:00 Uhr ein.", 
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = createTitle,
                        onValueChange = { createTitle = it },
                        label = { Text("Titel des Termins") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = createDesc,
                        onValueChange = { createDesc = it },
                        label = { Text("Beschreibung / Details") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
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
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Buchen")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { testCreateDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { 
                    Text("Abbrechen") 
                }
            }
        )
    }
}

@Composable
fun CalendarEventCard(event: CalendarEvent) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = "Event",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    event.title, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!event.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        event.description,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                
                val sdf = SimpleDateFormat("EEEE, d. MMMM • HH:mm", Locale.getDefault())
                val endSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${sdf.format(Date(event.startTime))} - ${endSdf.format(Date(event.endTime))} Uhr",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
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
            kotlinx.coroutines.delay(3000)
            viewModel.clearStatus()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    "System-Konfigurationen",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Verwalte deine lokalen KI-Modelle, System-Berechtigungen und API-Zugangsdaten isoliert und sicher auf deinem Smartphone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // --- LLM Providers configuration ---
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "API Keys",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "API-Schlüssel & KI-Provider",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Select provider Segment tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("OPENAI", "ANTHROPIC", "GEMINI").forEach { prov ->
                            val isSelected = activeProvider == prov
                            val col = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            val txt = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.selectProvider(prov) }
                                    .background(col)
                                    .padding(vertical = 10.dp),
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
                        Text(
                            "OpenAI API-Key", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
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
                                        contentDescription = "Sichtbarkeit umschalten",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            placeholder = { Text("sk-proj-...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("openai_api_key_field"),
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Config Key Anthropic
                    Column {
                        Text(
                            "Anthropic API-Key", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
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
                                        contentDescription = "Sichtbarkeit umschalten",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            placeholder = { Text("sk-ant-...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("anthropic_api_key_field"),
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Config Key Gemini
                    Column {
                        Text(
                            "Gemini API-Key", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
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
                                        contentDescription = "Sichtbarkeit umschalten",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            placeholder = { Text("AIzaSy...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_field"),
                            maxLines = 1,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SmartToy,
                            contentDescription = "Model",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Standard-Modell: $model",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Permissions Card status
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Permissions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Geräte- & Systemberechtigungen",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    // Calendar permission row
                    PermissionItemRow(
                        title = "Kalender-Synchronisation",
                        description = "Erlaubt das automatische Prüfen und Buchen von Terminen.",
                        hasPermission = hasCalendarPerms,
                        onRequest = onRequestCalendarPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Contacts permission row
                    PermissionItemRow(
                        title = "Kontakte-Synchronisation",
                        description = "Erlaubt das Zuordnen von Namen und Kontaktdaten.",
                        hasPermission = hasContactsPerms,
                        onRequest = onRequestContactsPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Location permission row
                    PermissionItemRow(
                        title = "Standort-Zugriff",
                        description = "Für ortsabhängige Planungen und Empfehlungen.",
                        hasPermission = hasLocationPerms,
                        onRequest = onRequestLocationPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // SMS permission row
                    PermissionItemRow(
                        title = "SMS-Schnittstelle",
                        description = "Ermöglicht das automatische Entwerfen von SMS-Antworten.",
                        hasPermission = hasSmsPerms,
                        onRequest = onRequestSmsPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Accounts permission row
                    PermissionItemRow(
                        title = "Geräte-Konten-Zugriff",
                        description = "Ermöglicht das Identifizieren aktiver E-Mail-Profile.",
                        hasPermission = hasAccountsPerms,
                        onRequest = onRequestAccountsPerms
                    )
                }
            }
        }

        // Maintenance
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = "Maintenance",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "System-Wartung",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Button(
                        onClick = { viewModel.clearHistory() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f), contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline, 
                            "Verlauf löschen", 
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Verlauf & Chats vollständig zurücksetzen", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionItemRow(
    title: String,
    description: String,
    hasPermission: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
        if (hasPermission) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Check, 
                        "Aktiv", 
                        tint = Color(0xFF10B981), 
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "AKTIV", 
                        fontSize = 9.sp, 
                        color = Color(0xFF10B981), 
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Button(
                onClick = onRequest,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Zulassen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Helpers
private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot3"
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp, 24.dp, 24.dp, 6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = dot1Alpha)))
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = dot2Alpha)))
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = dot3Alpha)))
    }
}
