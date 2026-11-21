package com.example.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.TrackingAttachment
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingStatus
import com.example.data.model.TrackingUpdate
import com.example.data.model.UiState
import com.example.ui.components.PriorityPill
import com.example.ui.components.StatusBadge
import com.example.ui.components.formatShortDate
import com.example.ui.components.formatTimestamp
import com.example.ui.components.statusColors
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

@Composable
fun TrackingDetailScreen(
    record: TrackingRecord,
    currentUserId: String,
    isAdmin: Boolean,
    isBusy: Boolean,
    updatesFlow: Flow<UiState<List<TrackingUpdate>>>,
    attachmentsFlow: Flow<UiState<List<TrackingAttachment>>>,
    onBack: () -> Unit,
    onEdit: (TrackingRecord) -> Unit,
    onDelete: (TrackingRecord) -> Unit,
    onUpdateStatus: (TrackingStatus, Int, String) -> Unit,
    onAddAttachment: (fileName: String, mimeType: String, sizeBytes: Long, uri: String) -> Unit
) {
    val context = LocalContext.current
    val updatesState by updatesFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)
    val attachmentsState by attachmentsFlow.collectAsStateWithLifecycle(initialValue = UiState.Loading)

    val canEditDetails = (isAdmin || record.userId == currentUserId) && (!record.parsedStatus.isTerminal || isAdmin)
    val canDelete = isAdmin || record.userId == currentUserId
    val canUpdateProgress = !record.parsedStatus.isTerminal || isAdmin

    var selectedStatus by remember(record.status) { mutableStateOf(record.parsedStatus) }
    var progress by remember(record.progressPercent) { mutableFloatStateOf(record.progressPercent.toFloat()) }
    var updateNote by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = queryUriMeta(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            onAddAttachment(name, mime, size, uri.toString())
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, size) = queryUriMeta(context, uri)
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            onAddAttachment(name, mime, size, uri.toString())
        }
    }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_btn")) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = record.trackingCode,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canEditDetails) {
                        IconButton(onClick = { onEdit(record) }, modifier = Modifier.testTag("detail_edit_btn")) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit Record")
                        }
                    }
                    if (canDelete) {
                        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.testTag("detail_delete_btn")) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete Record", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Core Metadata Card
        item {
            val (_, statusFg) = statusColors(record.parsedStatus)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusBadge(status = record.parsedStatus)
                        PriorityPill(priority = record.parsedPriority)
                    }
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (record.description.isNotBlank()) {
                        Text(
                            text = record.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (record.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = statusFg,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "${record.progressPercent}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    MetaRow("Category", record.categoryName)
                    MetaRow("Owner", record.ownerName)
                    MetaRow("Assignee", record.assigneeName)
                    MetaRow("Start Date", formatShortDate(record.startDateMillis))
                    MetaRow("Expected Completion", formatShortDate(record.dueDateMillis))
                    MetaRow("Source", record.parsedSource.label)
                    MetaRow("Last Live Update", formatTimestamp(record.updatedAt?.toDate()))
                }
            }
        }

        // Status & Progress Update Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Update Tracking Progress",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (!canUpdateProgress) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "This record is in a terminal state (${record.parsedStatus.label}). Only an Administrator can reopen it.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TrackingStatus.entries.forEach { st ->
                                FilterChip(
                                    selected = selectedStatus == st,
                                    onClick = {
                                        selectedStatus = st
                                        if (st == TrackingStatus.COMPLETED) progress = 100f
                                    },
                                    label = { Text(st.label) }
                                )
                            }
                        }
                        Text(
                            text = "Progress: ${progress.roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Slider(
                            value = progress,
                            onValueChange = { progress = it },
                            valueRange = 0f..100f
                        )
                        OutlinedTextField(
                            value = updateNote,
                            onValueChange = { if (it.length <= 500) updateNote = it },
                            label = { Text("Progress Note (Optional)") },
                            placeholder = { Text("Summarize milestone or blocker...") },
                            modifier = Modifier.fillMaxWidth().testTag("detail_note_input")
                        )
                        Button(
                            onClick = {
                                onUpdateStatus(selectedStatus, progress.roundToInt(), updateNote)
                                updateNote = ""
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().testTag("detail_save_status_btn")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Save Status Update")
                            }
                        }
                    }
                }
            }
        }

        // Attachments Section (Zero-Permission Photo Picker & SAF Document Picker)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attachments (${record.attachmentsCount})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.testTag("attach_photo_btn")
                            ) {
                                Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("Photo")
                            }
                            OutlinedButton(
                                onClick = { documentPickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.testTag("attach_file_btn")
                            ) {
                                Icon(Icons.Outlined.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.size(4.dp))
                                Text("File")
                            }
                        }
                    }

                    when (val att = attachmentsState) {
                        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        is UiState.Error -> Text(att.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        is UiState.Success -> {
                            if (att.data.isEmpty()) {
                                Text(
                                    text = "No attachments added yet. Photo & document metadata syncs via Firestore; binary files remain on-device via Photo Picker / SAF URIs.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                att.data.forEach { item ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (item.mimeType.startsWith("image/") && item.localUriString.isNotBlank()) {
                                                AsyncImage(
                                                    model = item.localUriString,
                                                    contentDescription = item.fileName,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Outlined.Description,
                                                    contentDescription = item.fileName,
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                                Text(
                                                    "${item.mimeType} • ${item.uploadedByName} • ${formatTimestamp(item.createdAt?.toDate())}",
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

        // Chronological Tracking History
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Tracking History", style = MaterialTheme.typography.titleMedium)
            }
        }

        when (val upd = updatesState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                Text(upd.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            is UiState.Success -> {
                items(upd.data, key = { it.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${item.parsedPreviousStatus.label} → ${item.parsedNewStatus.label} (${item.progressPercent}%)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = formatTimestamp(item.createdAt?.toDate()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (item.note.isNotBlank()) {
                                Text(item.note, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = "By ${item.authorName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${record.trackingCode}?") },
            text = { Text("This permanently removes the tracking record and logs the deletion in the audit trail.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(record)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun queryUriMeta(context: android.content.Context, uri: Uri): Pair<String, Long> {
    var name = "attachment"
    var size = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
    }
    return name to size
}
