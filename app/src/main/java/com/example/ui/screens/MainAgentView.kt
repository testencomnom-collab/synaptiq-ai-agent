package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
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
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.example.data.model.ChatMessage
import com.example.data.model.NotificationItem
import com.example.data.model.LocalAgentRepository
import com.example.services.CalendarEvent
import com.example.services.CalendarManager
import com.example.ui.AgentViewModel
import com.example.permissions.PermissionsManager
import com.example.permissions.AgentPermission
import com.example.permissions.SpecialPermissionsHelper
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.res.stringResource
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.example.R

@Composable
fun AnimatedLiquidBackground(modifier: Modifier = Modifier) {
    val color1 = Color(0xFF100F17)
    val color2 = Color(0xFF1E1432)
    val color3 = Color(0xFF152A34)

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = listOf(color1, color2, color3, color1)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .background(Color.Black.copy(alpha = 0.2f))
    )
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAgentView(
    viewModel: AgentViewModel,
    permissionsManager: PermissionsManager,
    windowSizeClass: WindowSizeClass,
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
    
    val requestCalendarPermissions: () -> Unit = {
        coroutineScope.launch {
            val results = permissionsManager.requestPermissions(
                AgentPermission.READ_CALENDAR,
                AgentPermission.WRITE_CALENDAR
            )
            hasCalendarPerms = results.values.all { it }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedLiquidBackground()
        
        val useNavigationRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact
        
        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                    modifier = Modifier.testTag("side_nav_rail").width(80.dp)
                ) {
                    Spacer(modifier = Modifier.height(32.dp))
                    NavigationRailItem(
                        selected = currentTab == "chat",
                        onClick = { currentTab = "chat" },
                        icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                        label = { Text(stringResource(R.string.nav_chat)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == "notifications",
                        onClick = { currentTab = "notifications" },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                        label = { Text(stringResource(R.string.nav_notifications)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == "calendar",
                        onClick = { currentTab = "calendar" },
                        icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                        label = { Text(stringResource(R.string.nav_calendar)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == "library",
                        onClick = { currentTab = "library" },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Library") },
                        label = { Text(stringResource(R.string.nav_library)) }
                    )
                    NavigationRailItem(
                        selected = currentTab == "settings",
                        onClick = { currentTab = "settings" },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            }
            
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = Color.Transparent,
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                            modifier = Modifier.testTag("bottom_nav_bar")
                        ) {
                            NavigationBarItem(
                                selected = currentTab == "chat",
                                onClick = { currentTab = "chat" },
                                icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                                label = { Text(stringResource(R.string.nav_chat)) }
                            )
                            NavigationBarItem(
                                selected = currentTab == "notifications",
                                onClick = { currentTab = "notifications" },
                                icon = { Icon(Icons.Default.Notifications, contentDescription = "Notifications") },
                                label = { Text(stringResource(R.string.nav_notifications)) }
                            )
                            NavigationBarItem(
                                selected = currentTab == "calendar",
                                onClick = { currentTab = "calendar" },
                                icon = { Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar") },
                                label = { Text(stringResource(R.string.nav_calendar)) }
                            )
                            NavigationBarItem(
                                selected = currentTab == "library",
                                onClick = { currentTab = "library" },
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Library") },
                                label = { Text(stringResource(R.string.nav_library)) }
                            )
                            NavigationBarItem(
                                selected = currentTab == "settings",
                                onClick = { currentTab = "settings" },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text(stringResource(R.string.nav_settings)) }
                            )
                        }
                    }
                },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(modifier = Modifier.widthIn(max = 800.dp).fillMaxSize()) {
                        when (currentTab) {
                            "chat" -> AgentChatTab(viewModel, hasCalendarPerms, onRequestPermissions = { requestCalendarPermissions() }, onNavigateToMemory = { currentTab = "memory" })
                            "notifications" -> NotificationManagerTab(viewModel, onNavigateToChat = { currentTab = "chat" })
                            "calendar" -> SystemCalendarTab(viewModel, hasCalendarPerms, onRequestPermissions = { requestCalendarPermissions() })
                            "library" -> AgentLibraryTab(viewModel)
                            "memory" -> MemoryScreen(viewModel, onBack = { currentTab = "chat" })
                            "settings" -> AgentSettingsTab(
                                viewModel = viewModel,
                                hasCalendarPerms = hasCalendarPerms,
                                hasContactsPerms = hasContactsPerms,
                                hasLocationPerms = hasLocationPerms,
                                hasSmsPerms = hasSmsPerms,
                                hasAccountsPerms = hasAccountsPerms,
                                permissionsManager = permissionsManager,
                                coroutineScope = coroutineScope,
                                onRequestCalendarPerms = requestCalendarPermissions,
                                onRequestContactsPerms = {
                                    coroutineScope.launch {
                                        val results = permissionsManager.requestPermissions(AgentPermission.READ_CONTACTS)
                                        hasContactsPerms = results.values.all { it }
                                    }
                                    Unit
                                },
                                onRequestLocationPerms = {
                                    coroutineScope.launch {
                                        val results = permissionsManager.requestPermissions(AgentPermission.ACCESS_FINE_LOCATION)
                                        hasLocationPerms = results.values.firstOrNull() ?: false
                                    }
                                    Unit
                                },
                                onRequestSmsPerms = {
                                    coroutineScope.launch {
                                        val results = permissionsManager.requestPermissions(AgentPermission.SEND_SMS)
                                        hasSmsPerms = results.values.firstOrNull() ?: false
                                    }
                                    Unit
                                },
                                onRequestAccountsPerms = {
                                    coroutineScope.launch {
                                        val results = permissionsManager.requestPermissions(AgentPermission.GET_ACCOUNTS)
                                        hasAccountsPerms = results.values.firstOrNull() ?: false
                                    }
                                    Unit
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==================== AGENT CHAT TAB ====================
@Composable
fun AgentChatTab(
    viewModel: AgentViewModel,
    hasCalendarPerms: Boolean,
    onRequestPermissions: () -> Unit,
    onNavigateToMemory: () -> Unit
) {
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var inputQuery by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val isAutopilotRunning by com.example.services.ContinuousAgentService.isRunning.collectAsStateWithLifecycle()

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrEmpty()) {
                inputQuery = spokenText
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

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
                                text = stringResource(R.string.sys_active),
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
                                    text = stringResource(R.string.offline_mode),
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateToMemory,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Outlined.Memory,
                            contentDescription = "Langzeit-Gedächtnis öffnen",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Chat-Verlauf leeren",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
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
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_allow), fontSize = 11.sp)
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
                items(items = chatHistory, key = { it.id }) { message ->
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(message.id) { isVisible = true }
                    
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVisible,
                        enter = androidx.compose.animation.slideInVertically(
                            initialOffsetY = { 50 },
                            animationSpec = tween(durationMillis = 300)
                        ) + androidx.compose.animation.fadeIn(animationSpec = tween(durationMillis = 300))
                    ) {
                        ChatMessageBubble(message, onExecuteAction = {
                            viewModel.executeProposedAction(message)
                        })
                    }
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
                    enabled = !isLoading,
                    placeholder = { 
                        Text(
                            if (isLoading) stringResource(R.string.chat_generating) else stringResource(R.string.chat_input_hint), 
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
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (inputQuery.trim().isNotEmpty() && !isLoading) {
                                viewModel.sendMessage(inputQuery)
                                inputQuery = ""
                                keyboardController?.hide()
                            }
                        }
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            }
                            speechRecognizerLauncher.launch(intent)
                        } catch (e: Exception) {
                            // Ignored if STT not available
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Spracheingabe",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (isAutopilotRunning) {
                            val intent = android.content.Intent(context, com.example.services.ContinuousAgentService::class.java).apply {
                                action = com.example.services.ContinuousAgentService.ACTION_STOP_SERVICE
                            }
                            context.startService(intent)
                        } else if (inputQuery.trim().isNotEmpty() && !isLoading) {
                            val intent = android.content.Intent(context, com.example.services.ContinuousAgentService::class.java).apply {
                                putExtra(com.example.services.ContinuousAgentService.EXTRA_TASK, inputQuery)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            inputQuery = ""
                            keyboardController?.hide()
                        }
                    },
                    enabled = isAutopilotRunning || (!isLoading && inputQuery.trim().isNotEmpty()),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (isAutopilotRunning) Color(0xFFEF4444) // Red for Stop
                            else if (inputQuery.trim().isNotEmpty() && !isLoading) Color(0xFF8B5CF6) // Purple for autopilot
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                ) {
                    Icon(
                        if (isAutopilotRunning) Icons.Default.Stop else Icons.Default.RocketLaunch,
                        contentDescription = if (isAutopilotRunning) "Autopilot stoppen" else "Autopilot starten",
                        tint = if (isAutopilotRunning || (inputQuery.trim().isNotEmpty() && !isLoading)) Color.White 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        if (inputQuery.trim().isNotEmpty() && !isLoading) {
                            viewModel.sendMessage(inputQuery)
                            inputQuery = ""
                            keyboardController?.hide()
                        }
                    },
                    enabled = !isLoading && inputQuery.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (inputQuery.trim().isNotEmpty() && !isLoading) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        .testTag("chat_send_button")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Senden",
                        tint = if (inputQuery.trim().isNotEmpty() && !isLoading) MaterialTheme.colorScheme.onPrimary 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val userBackgroundBrush = remember(primaryColor, secondaryColor) {
        Brush.linearGradient(
            colors = listOf(primaryColor, secondaryColor)
        )
    }

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
                            Modifier.background(userBackgroundBrush)
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
                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                Column {
                    // Message Text
                    Text(
                        text = message.message,
                        color = textCol,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    if (!isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { 
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.message)) 
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Kopieren",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

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
    var systemActionApp = ""
    var systemActionRecipient = ""
    var systemActionInstruction = ""

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

        systemActionApp = json.optString("systemActionApp", "")
        systemActionRecipient = json.optString("recipient", "")
        systemActionInstruction = json.optString("instruction", "")
        if (systemActionInstruction.isEmpty()) {
            systemActionInstruction = json.optString("systemActionInstruction", "")
        }
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

            // Action Details (System Action)
            if (type == "SYSTEM_ACTION" || type == "BOTH") {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SettingsApplications,
                            contentDescription = "System Action",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Deep System Automation",
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
                            Text("Target App: $systemActionApp", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (systemActionRecipient.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Recipient: $systemActionRecipient", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Sequence: $systemActionInstruction", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    ) {
        // Calendar Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
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
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_calendar), fontWeight = FontWeight.Bold)
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
                            contentDescription = "No events",
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            "Calendar is clear", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Create an event or let the AI Agent plan your agenda via chat!",
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
                    items(eventList, key = { it.id }) { event ->
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
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.calendar_title)) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = createDesc,
                        onValueChange = { createDesc = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.calendar_desc)) },
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
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.calendar_book))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { testCreateDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { 
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.calendar_cancel)) 
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
    permissionsManager: com.example.permissions.PermissionsManager,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
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
    var customModelText by remember { mutableStateOf(viewModel.preferencesManager.selectedModel) }

    var showPasswordOpenai by remember { mutableStateOf(false) }
    var showPasswordAnthropic by remember { mutableStateOf(false) }
    var showPasswordGemini by remember { mutableStateOf(false) }

    val agentLang by viewModel.agentLanguageFlow.collectAsStateWithLifecycle()
    var showLangMenu by remember { mutableStateOf(false) }
    val supportedLanguages = listOf(
        "Deutsch", "English", "Español", "Français", "Italiano", "Português", 
        "Русский", "日本語", "한국어", "中文", "Nederlands", "Polski", "Türkçe", "Svenska", "العربية"
    )

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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    stringResource(R.string.settings_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    "Manage your local AI models, system permissions, and API credentials securely and isolated on your smartphone.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // --- Unified Language Configuration (App + Agent) ---
        item {
            var showLangMenu by remember { mutableStateOf(false) }
            val currentAppLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().let { 
                if (it.contains("de", ignoreCase = true)) "Deutsch" 
                else if (it.contains("es", ignoreCase = true)) "Español"
                else "English"
            }
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
                            Icons.Default.Language,
                            contentDescription = stringResource(R.string.settings_language),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.settings_language),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        stringResource(R.string.settings_language_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { showLangMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(currentAppLang)
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Language")
                        }
                        DropdownMenu(
                            expanded = showLangMenu,
                            onDismissRequest = { showLangMenu = false },
                            modifier = Modifier.fillMaxWidth(0.9f)
                        ) {
                            listOf("English" to "en", "Deutsch" to "de", "Español" to "es").forEach { (langName, tag) ->
                                DropdownMenuItem(
                                    text = { Text(langName) },
                                    onClick = {
                                        viewModel.setAgentLanguage(langName)
                                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                                        showLangMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- TTS Configuration ---
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
                            Icons.Default.RecordVoiceOver,
                            contentDescription = "Sprachausgabe (TTS)",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Sprachausgabe (TTS)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Der Agent liest seine Antworten laut vor.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        
                        var isTtsEnabled by remember { mutableStateOf(viewModel.preferencesManager.isTtsEnabled) }
                        Switch(
                            checked = isTtsEnabled,
                            onCheckedChange = { 
                                isTtsEnabled = it
                                viewModel.preferencesManager.isTtsEnabled = it
                                if (it) {
                                    viewModel.initTts()
                                }
                            }
                        )
                    }
                }
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

                    Spacer(modifier = Modifier.height(12.dp))

                    // Config Key Custom Model (Dropdown)
                    Column {
                        Text(
                            stringResource(R.string.select_model_title), 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 11.sp, 
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                        )
                        
                        var showModelMenu by remember { mutableStateOf(false) }
                        
                        // Dynamische Modell-Listen basierend auf dem aktiven Provider
                        val availableModels = when (activeProvider) {
                            "GEMINI" -> listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-3.5-flash", "gemini-3.1-pro")
                            "OPENAI" -> listOf("gpt-4o", "gpt-4o-mini", "o1", "o1-mini", "o3-mini")
                            "ANTHROPIC" -> listOf("claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest", "claude-3-opus-latest")
                            else -> listOf()
                        }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showModelMenu = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = model.ifEmpty { stringResource(R.string.select_model_hint) }, 
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.select_model_title), tint = MaterialTheme.colorScheme.onSurface)
                            }
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                availableModels.forEach { modelName ->
                                    DropdownMenuItem(
                                        text = { Text(modelName) },
                                        onClick = {
                                            viewModel.updateModel(modelName)
                                            showModelMenu = false
                                        }
                                    )
                                }
                            }
                        }
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
                        title = "Calendar Synchronization",
                        description = "Allows automatic checking and booking of events.",
                        hasPermission = hasCalendarPerms,
                        onRequest = onRequestCalendarPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Contacts permission row
                    PermissionItemRow(
                        title = "Contacts Synchronization",
                        description = "Allows linking names and contact details.",
                        hasPermission = hasContactsPerms,
                        onRequest = onRequestContactsPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Location permission row
                    PermissionItemRow(
                        title = "Location Access",
                        description = "For location-based planning and recommendations.",
                        hasPermission = hasLocationPerms,
                        onRequest = onRequestLocationPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // SMS permission row
                    PermissionItemRow(
                        title = "SMS Interface",
                        description = "Enables automatic drafting of SMS replies.",
                        hasPermission = hasSmsPerms,
                        onRequest = onRequestSmsPerms
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Audio permission row
                    val hasAudioPerms = remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.RECORD_AUDIO)) }
                    PermissionItemRow(
                        title = "Microphone Access",
                        description = "Allows voice control and dictation features.",
                        hasPermission = hasAudioPerms.value,
                        onRequest = {
                            coroutineScope.launch {
                                val results = permissionsManager.requestPermissions(AgentPermission.RECORD_AUDIO)
                                hasAudioPerms.value = results.values.all { it }
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Camera permission row
                    val hasCameraPerms = remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.CAMERA)) }
                    PermissionItemRow(
                        title = "Camera Access",
                        description = "For image analysis and computer vision features.",
                        hasPermission = hasCameraPerms.value,
                        onRequest = {
                            coroutineScope.launch {
                                val results = permissionsManager.requestPermissions(AgentPermission.CAMERA)
                                hasCameraPerms.value = results.values.all { it }
                            }
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Phone permission row
                    val hasPhonePerms = remember { mutableStateOf(permissionsManager.isGranted(AgentPermission.CALL_PHONE) && permissionsManager.isGranted(AgentPermission.READ_CALL_LOG)) }
                    PermissionItemRow(
                        title = "Phone Calls",
                        description = "Allows making calls and reading history.",
                        hasPermission = hasPhonePerms.value,
                        onRequest = {
                            coroutineScope.launch {
                                val results = permissionsManager.requestPermissions(AgentPermission.CALL_PHONE, AgentPermission.READ_CALL_LOG)
                                hasPhonePerms.value = results.values.all { it }
                            }
                        }
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

        // --- Deep System Integrations (Accessibility, Notifications, Usage) ---
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            // We use derivedStateOf or just read directly, but since we want it to update we can re-read on click or just let Compose re-evaluate when activity resumes
            // For simplicity, we just call the helper methods directly. They will be evaluated on recomposition.
            
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
                            Icons.Default.Android,
                            contentDescription = "Deep System",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tiefe System-Integration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    // System Alert Window Row
                    val hasOverlay = remember { mutableStateOf(SpecialPermissionsHelper.canDrawOverlays(context)) }
                    SpecialPermissionItemRow(
                        title = "Über anderen Apps einblenden",
                        description = "Der Agent kann als schwebende Blase überall angezeigt werden.",
                        hasPermission = hasOverlay.value,
                        onOpenSettings = {
                            context.startActivity(SpecialPermissionsHelper.getOverlaySettingsIntent(context))
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Write Settings Row
                    val hasWriteSettings = remember { mutableStateOf(SpecialPermissionsHelper.canWriteSettings(context)) }
                    SpecialPermissionItemRow(
                        title = "Systemeinstellungen ändern",
                        description = "Der Agent kann WLAN, Bluetooth, Helligkeit etc. steuern.",
                        hasPermission = hasWriteSettings.value,
                        onOpenSettings = {
                            context.startActivity(SpecialPermissionsHelper.getWriteSettingsIntent(context))
                        }
                    )
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Manage Storage Row
                    val hasManageStorage = remember { mutableStateOf(SpecialPermissionsHelper.isExternalStorageManager()) }
                    SpecialPermissionItemRow(
                        title = "Full Storage Access",
                        description = "Allows reading and writing of all files on the device.",
                        hasPermission = hasManageStorage.value,
                        onOpenSettings = {
                            context.startActivity(SpecialPermissionsHelper.getManageStorageIntent(context))
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Accessibility Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_accessibility), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_accessibility_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (com.example.permissions.SpecialPermissionsHelper.isAccessibilityServiceEnabled(context)) {
                            Icon(Icons.Default.CheckCircle, "Aktiv", tint = Color(0xFF4CAF50))
                        } else {
                            Button(
                                onClick = { context.startActivity(com.example.permissions.SpecialPermissionsHelper.getAccessibilitySettingsIntent()) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_enable), fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Notification Listener Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_notifications), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_notifications_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (com.example.permissions.SpecialPermissionsHelper.isNotificationListenerEnabled(context)) {
                            Icon(Icons.Default.CheckCircle, "Aktiv", tint = Color(0xFF4CAF50))
                        } else {
                            Button(
                                onClick = { context.startActivity(com.example.permissions.SpecialPermissionsHelper.getNotificationListenerSettingsIntent()) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_enable), fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // Usage Stats Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_usage), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_perm_usage_desc), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (com.example.permissions.SpecialPermissionsHelper.isUsageStatsEnabled(context)) {
                            Icon(Icons.Default.CheckCircle, "Aktiv", tint = Color(0xFF4CAF50))
                        } else {
                            Button(
                                onClick = { context.startActivity(com.example.permissions.SpecialPermissionsHelper.getUsageStatsSettingsIntent()) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_enable), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // --- System Access (Admin) ---
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val isAdmin = dpm.isAdminActive(android.content.ComponentName(context, com.example.permissions.MyDeviceAdminReceiver::class.java))
            
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
                            Icons.Default.AdminPanelSettings,
                            contentDescription = "System Access",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Advanced System Access",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "To allow the AI Agent to fully access and automate system functions, the app must be configured as a device administrator.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Device Administrator Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Device Administrator",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "For deep automation capabilities",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isAdmin) {
                            Icon(Icons.Default.CheckCircle, "Active", tint = Color(0xFF4CAF50))
                        } else {
                            Button(
                                onClick = { 
                                    try {
                                        val intent = android.content.Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                                        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, android.content.ComponentName(context, com.example.permissions.MyDeviceAdminReceiver::class.java))
                                        intent.putExtra(android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Necessary for advanced AI system automations")
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), contentColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_enable), fontSize = 12.sp)
                            }
                        }
                    }
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
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_reset_memory), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SpecialPermissionItemRow(
    title: String,
    description: String,
    hasPermission: Boolean,
    onOpenSettings: () -> Unit
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
                onClick = onOpenSettings,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Einstellungen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.settings_allow), fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 0, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -10f,
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
        Box(modifier = Modifier.offset(y = dot1Offset.dp).size(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.offset(y = dot2Offset.dp).size(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
        Box(modifier = Modifier.offset(y = dot3Offset.dp).size(6.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary))
    }
}
