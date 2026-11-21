package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingStatus
import com.example.data.model.UiState
import com.example.ui.components.ChartSlice
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.HorizontalBarChart
import com.example.ui.components.StatusBadge
import com.example.ui.components.StatusDonutChart
import com.example.ui.components.formatShortDate
import com.example.ui.components.statusColors
import com.example.ui.viewmodel.ReportType
import com.example.util.ExportFormat
import com.example.util.ReportExporter

@Composable
fun ReportsScreen(
    recordsState: UiState<List<TrackingRecord>>,
    logsState: UiState<List<ActivityLog>>,
    onExported: (ReportType, String) -> Unit,
    onExportError: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedReport by remember { mutableStateOf(ReportType.DAILY) }

    val allRecords = (recordsState as? UiState.Success)?.data ?: emptyList()
    val allLogs = (logsState as? UiState.Success)?.data ?: emptyList()

    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L

    val filteredRecords = remember(allRecords, selectedReport) {
        when (selectedReport) {
            ReportType.DAILY -> allRecords.filter { (it.updatedAt?.toDate()?.time ?: it.startDateMillis) >= now - dayMs }
            ReportType.WEEKLY -> allRecords.filter { (it.updatedAt?.toDate()?.time ?: it.startDateMillis) >= now - 7 * dayMs }
            ReportType.MONTHLY -> allRecords.filter { (it.updatedAt?.toDate()?.time ?: it.startDateMillis) >= now - 30 * dayMs }
            ReportType.COMPLETED -> allRecords.filter { it.parsedStatus == TrackingStatus.COMPLETED }
            ReportType.PENDING -> allRecords.filter { it.parsedStatus == TrackingStatus.PENDING }
            ReportType.OVERDUE -> allRecords.filter { it.parsedStatus == TrackingStatus.OVERDUE }
            ReportType.USER_ACTIVITY -> allRecords
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Reports & Analytics", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedReport == type,
                        onClick = { selectedReport = type },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag("report_tab_${type.name.lowercase()}")
                    )
                }
            }
        }

        // Export Actions (PDF, Excel, CSV)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "${selectedReport.label} Report (${if (selectedReport == ReportType.USER_ACTIVITY) allLogs.size else filteredRecords.size} entries)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                ReportExporter.exportAndShare(
                                    context = context,
                                    reportTitle = "${selectedReport.label} Report",
                                    records = filteredRecords,
                                    format = ExportFormat.PDF
                                ).onSuccess { onExported(selectedReport, "PDF") }
                                    .onFailure { onExportError(it.localizedMessage ?: "PDF export failed") }
                            },
                            modifier = Modifier.weight(1f).testTag("export_pdf_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                ReportExporter.exportAndShare(
                                    context = context,
                                    reportTitle = "${selectedReport.label} Report",
                                    records = filteredRecords,
                                    format = ExportFormat.EXCEL
                                ).onSuccess { onExported(selectedReport, "Excel") }
                                    .onFailure { onExportError(it.localizedMessage ?: "Excel export failed") }
                            },
                            modifier = Modifier.weight(1f).testTag("export_excel_btn")
                        ) {
                            Icon(Icons.Outlined.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Excel")
                        }
                        OutlinedButton(
                            onClick = {
                                ReportExporter.exportAndShare(
                                    context = context,
                                    reportTitle = "${selectedReport.label} Report",
                                    records = filteredRecords,
                                    format = ExportFormat.CSV
                                ).onSuccess { onExported(selectedReport, "CSV") }
                                    .onFailure { onExportError(it.localizedMessage ?: "CSV export failed") }
                            },
                            modifier = Modifier.weight(1f).testTag("export_csv_btn")
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("CSV")
                        }
                    }
                }
            }
        }

        // Analytics Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (selectedReport == ReportType.USER_ACTIVITY) {
                        Text("Actions by User", style = MaterialTheme.typography.titleMedium)
                        val byActor = allLogs.groupBy { it.actorName.ifBlank { "User" } }
                            .entries.take(6)
                            .map { (actor, items) ->
                                ChartSlice(actor, items.size, MaterialTheme.colorScheme.secondary)
                            }
                        if (byActor.isEmpty()) {
                            Text("No user activity logs recorded yet.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            HorizontalBarChart(items = byActor)
                        }
                    } else {
                        Text("Status Breakdown", style = MaterialTheme.typography.titleMedium)
                        val slices = TrackingStatus.entries.map { st ->
                            ChartSlice(st.label, filteredRecords.count { it.parsedStatus == st }, statusColors(st).second)
                        }
                        StatusDonutChart(
                            slices = slices,
                            centerLabel = "${filteredRecords.size}",
                            centerTitle = selectedReport.label
                        )
                    }
                }
            }
        }

        // Tabular Preview
        if (selectedReport == ReportType.USER_ACTIVITY) {
            if (allLogs.isEmpty()) {
                item {
                    EmptyStateCard("No user activity", "User activity audit logs appear here.")
                }
            } else {
                items(allLogs.take(30), key = { it.id }) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(log.actorName, style = MaterialTheme.typography.titleSmall)
                                Text(log.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(log.action, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        } else {
            if (filteredRecords.isEmpty()) {
                item {
                    EmptyStateCard("No records in ${selectedReport.label} view", "Try another report period or status tab.")
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ID / Title", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1.4f))
                                Text("Status", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                                Text("Due", style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            filteredRecords.forEach { rec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1.4f).padding(end = 8.dp)) {
                                        Text(rec.trackingCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text(rec.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                    }
                                    BoxWrapper(modifier = Modifier.weight(1f)) {
                                        StatusBadge(status = rec.parsedStatus)
                                    }
                                    Text(formatShortDate(rec.dueDateMillis), style = MaterialTheme.typography.bodySmall)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWrapper(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
}
