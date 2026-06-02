package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.NotificationItem
import com.example.ui.AgentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationManagerTab(
    viewModel: AgentViewModel,
    onNavigateToChat: () -> Unit
) {
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val isAutoReplyEnabled by viewModel.isAutoReplyEnabled.collectAsStateWithLifecycle()
    var openAddDialog by remember { mutableStateOf(false) }

    var appInput by remember { mutableStateOf("WhatsApp") }
    var senderInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Notifications Header
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
                        "Notifications",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "${notifications.size} unread events",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.clearNotifications() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear notifications",
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
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Simulate notification",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

        // Toggle Auto-Reply
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_auto_reply_title), fontWeight = FontWeight.Bold)
                Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_auto_reply_desc), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = isAutoReplyEnabled,
                onCheckedChange = { viewModel.isAutoReplyEnabled.value = it }
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

        // Notifications Column
        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsOff,
                        contentDescription = "No notifications",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "No notifications", 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
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
                items(notifications, key = { it.id }) { notif ->
                    NotificationCard(notif, onReplyWithAgent = {
                        val prompt = "Analyze this notification and draft a reply:\n\n" +
                                "From: ${notif.sender} (App: ${notif.appName})\n" +
                                "Content: ${notif.message}\n"
                        viewModel.sendMessage(prompt)
                        onNavigateToChat()
                    })
                }
            }
        }
    }

    if (openAddDialog) {
        AlertDialog(
            onDismissRequest = { openAddDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { 
                Text(
                    "Simulate notification", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 18.sp
                ) 
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = appInput,
                        onValueChange = { appInput = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_app_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_sender)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bodyInput,
                        onValueChange = { bodyInput = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_message)) },
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (senderInput.isNotEmpty() && bodyInput.isNotEmpty()) {
                            viewModel.addNotification(appInput, senderInput, bodyInput)
                            appInput = "WhatsApp"
                            senderInput = ""
                            bodyInput = ""
                            openAddDialog = false
                        }
                    }
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_receive))
                }
            },
            dismissButton = {
                TextButton(onClick = { openAddDialog = false }) { Text(androidx.compose.ui.res.stringResource(com.example.R.string.memory_cancel)) }
            }
        )
    }
}

@Composable
fun NotificationCard(
    notif: NotificationItem,
    onReplyWithAgent: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp),
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
                            Icons.Default.Notifications,
                            contentDescription = "Message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            notif.sender,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            notif.appName,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                notif.message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (notif.aiReplied && notif.aiReplyText != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)).padding(8.dp)
                ) {
                    Column {
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_ai_reply), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(notif.aiReplyText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onReplyWithAgent,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        Icons.Default.SupportAgent,
                        contentDescription = "Process",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(androidx.compose.ui.res.stringResource(com.example.R.string.notif_manual_ai_reply), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
