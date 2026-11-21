package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.AppNotification
import com.example.data.model.NotificationType
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.formatTimestamp

@Composable
fun ActivityTimelineScreen(
    logsState: UiState<List<ActivityLog>>,
    isAdmin: Boolean
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isAdmin) "System Audit & Activity Timeline" else "My Activity Timeline",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter by action, actor, or details...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("activity_search_input"),
                shape = MaterialTheme.shapes.medium
            )
        }

        when (logsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.padding(16.dp)) {
                EmptyStateCard("Unable to load timeline", logsState.message, Icons.Outlined.History)
            }
            is UiState.Success -> {
                val filtered = logsState.data.filter { log ->
                    val q = query.trim().lowercase()
                    q.isBlank() || log.details.lowercase().contains(q) ||
                        log.action.lowercase().contains(q) || log.actorName.lowercase().contains(q)
                }
                if (filtered.isEmpty()) {
                    Box(Modifier.padding(16.dp)) {
                        EmptyStateCard(
                            "No activity events",
                            "Actions across tracking records, attachments, and settings are recorded chronologically here.",
                            Icons.Outlined.History
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { log ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(8.dp).size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = log.action,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                            Text(
                                                text = formatTimestamp(log.createdAt?.toDate()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(log.details, style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${log.actorName} (${log.actorEmail})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
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
fun NotificationsScreen(
    notificationsState: UiState<List<AppNotification>>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onScanDeadlines: () -> Unit,
    onOpenRecordById: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notification Center", style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onScanDeadlines, modifier = Modifier.testTag("notif_scan_btn")) {
                    Icon(Icons.Outlined.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Scan")
                }
                OutlinedButton(onClick = onMarkAllRead, modifier = Modifier.testTag("notif_mark_all_btn")) {
                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Read All")
                }
            }
        }

        when (notificationsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.padding(16.dp)) {
                EmptyStateCard("Error loading notifications", notificationsState.message, Icons.Outlined.Notifications)
            }
            is UiState.Success -> {
                if (notificationsState.data.isEmpty()) {
                    Box(Modifier.padding(16.dp)) {
                        EmptyStateCard(
                            "You're all caught up",
                            "Alerts for record updates, approaching deadlines, and overdue rules will appear here.",
                            Icons.Outlined.Notifications
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(notificationsState.data, key = { it.id }) { notif ->
                            val isAlert = notif.parsedType == NotificationType.OVERDUE_ALERT
                            Card(
                                onClick = {
                                    if (!notif.read) onMarkRead(notif.id)
                                    if (notif.recordId.isNotBlank()) onOpenRecordById(notif.recordId)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("notif_card_${notif.id}"),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (notif.read) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isAlert && !notif.read) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = if (isAlert) Icons.Outlined.WarningAmber else Icons.Outlined.NotificationsActive,
                                        contentDescription = notif.parsedType.label,
                                        tint = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = notif.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (notif.read) FontWeight.Medium else FontWeight.Bold
                                            )
                                            Text(
                                                text = formatTimestamp(notif.createdAt?.toDate()),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(notif.message, style = MaterialTheme.typography.bodyMedium)
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
