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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.SystemSettings
import com.example.data.model.TrackingPriority
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingStatus
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.ui.components.ChartSlice
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HorizontalBarChart
import com.example.ui.components.KpiCard
import com.example.ui.components.StatusDonutChart
import com.example.ui.components.TrackingRecordCard
import com.example.ui.components.formatTimestamp
import com.example.ui.components.priorityColor
import com.example.ui.components.statusColors
import com.example.ui.viewmodel.DashboardStats

@Composable
fun DashboardScreen(
    userProfile: UserProfile?,
    isAdmin: Boolean,
    stats: DashboardStats,
    recordsState: UiState<List<TrackingRecord>>,
    logsState: UiState<List<ActivityLog>>,
    systemSettings: SystemSettings,
    onCreateTracking: () -> Unit,
    onNavigateToRecordsWithStatus: (TrackingStatus?) -> Unit,
    onOpenRecord: (TrackingRecord) -> Unit,
    onNavigateToReports: () -> Unit,
    onRunOverdueScan: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome back, ${userProfile?.displayName?.ifBlank { "User" } ?: "User"}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (isAdmin) "Administrator Workspace • Global Oversight"
                        else "Personal Workspace • Real-Time Sync",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = onCreateTracking,
                    modifier = Modifier.testTag("dashboard_create_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("New Track")
                }
            }
        }

        // Honest live data-source architecture banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDone,
                        contentDescription = "Live sync status",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Firestore Real-Time Sync Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "External GPS/IoT connector: ${systemSettings.externalTrackerStatus}. Configure webhook endpoints in Settings to stream external telemetry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onRunOverdueScan,
                        modifier = Modifier.testTag("dashboard_scan_btn")
                    ) {
                        Text("Scan")
                    }
                }
            }
        }

        // KPI Grid (Total, Active, Completed, Pending, Overdue)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        title = "Total Tracked",
                        value = stats.total.toString(),
                        subtitle = "${stats.averageProgress}% avg progress",
                        icon = Icons.Outlined.Layers,
                        accentColor = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigateToRecordsWithStatus(null) },
                        testTag = "kpi_total",
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Active",
                        value = stats.active.toString(),
                        subtitle = "In motion & queued",
                        icon = Icons.Outlined.PlayCircleOutline,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigateToRecordsWithStatus(TrackingStatus.IN_PROGRESS) },
                        testTag = "kpi_active",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    KpiCard(
                        title = "Completed",
                        value = stats.completed.toString(),
                        subtitle = "Verified closed",
                        icon = Icons.Outlined.CheckCircle,
                        accentColor = statusColors(TrackingStatus.COMPLETED).second,
                        onClick = { onNavigateToRecordsWithStatus(TrackingStatus.COMPLETED) },
                        testTag = "kpi_completed",
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Pending",
                        value = stats.pending.toString(),
                        subtitle = "Awaiting start",
                        icon = Icons.Outlined.HourglassTop,
                        accentColor = statusColors(TrackingStatus.PENDING).second,
                        onClick = { onNavigateToRecordsWithStatus(TrackingStatus.PENDING) },
                        testTag = "kpi_pending",
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Overdue",
                        value = stats.overdue.toString(),
                        subtitle = "Needs attention",
                        icon = Icons.Outlined.WarningAmber,
                        accentColor = statusColors(TrackingStatus.OVERDUE).second,
                        onClick = { onNavigateToRecordsWithStatus(TrackingStatus.OVERDUE) },
                        testTag = "kpi_overdue",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Visual Charts Card
        item {
            val records = (recordsState as? UiState.Success)?.data ?: emptyList()
            val statusSlices = TrackingStatus.entries.map { status ->
                ChartSlice(
                    label = status.label,
                    count = records.count { it.parsedStatus == status },
                    color = statusColors(status).second
                )
            }
            val prioritySlices = TrackingPriority.entries.map { pri ->
                ChartSlice(
                    label = pri.label,
                    count = records.count { it.parsedPriority == pri },
                    color = priorityColor(pri)
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tracking Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onNavigateToReports,
                            modifier = Modifier.testTag("dashboard_reports_btn")
                        ) {
                            Icon(Icons.Outlined.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Reports")
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    StatusDonutChart(
                        slices = statusSlices,
                        centerLabel = "${stats.total}",
                        centerTitle = "Records"
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Priority Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalBarChart(items = prioritySlices)
                }
            }
        }

        // Recent Tracking Records
        item {
            Text(
                text = "Active & Recent Tracking",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        when (recordsState) {
            is UiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item {
                    EmptyStateCard(
                        title = "Unable to load records",
                        message = recordsState.message
                    )
                }
            }
            is UiState.Success -> {
                val recent = recordsState.data.take(4)
                if (recent.isEmpty()) {
                    item {
                        EmptyStateCard(
                            title = "No tracking records yet",
                            message = "Create your first tracking record to monitor deadlines, assignees, attachments, and live status updates."
                        ) {
                            Button(onClick = onCreateTracking) {
                                Text("Create First Record")
                            }
                        }
                    }
                } else {
                    items(recent, key = { it.id }) { rec ->
                        TrackingRecordCard(
                            record = rec,
                            onClick = { onOpenRecord(rec) }
                        )
                    }
                }
            }
        }

        // Recent Activity Feed
        item {
            Text(
                text = "Recent Activities",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        val logs = (logsState as? UiState.Success)?.data?.take(5) ?: emptyList()
        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = "Activity audit logs will appear here as records are created or updated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(logs, key = { it.id }) { log ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.details,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${log.actorName} • ${log.action}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatTimestamp(log.createdAt?.toDate()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
