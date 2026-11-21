package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.TrackingCategory
import com.example.data.model.TrackingPriority
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingSourceType
import com.example.data.model.TrackingStatus
import com.example.data.model.UserProfile
import com.example.ui.components.formatShortDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditTrackingScreen(
    existingRecord: TrackingRecord?,
    currentUser: UserProfile?,
    isAdmin: Boolean,
    allUsers: List<UserProfile>,
    categories: List<TrackingCategory>,
    defaultPriority: TrackingPriority,
    isBusy: Boolean,
    onCreateCategory: (String, String, String) -> Unit,
    onSaveCreate: (
        title: String,
        description: String,
        category: TrackingCategory,
        priority: TrackingPriority,
        status: TrackingStatus,
        progress: Int,
        assigneeId: String,
        assigneeName: String,
        startMillis: Long,
        dueMillis: Long,
        sourceType: TrackingSourceType
    ) -> Unit,
    onSaveEdit: (
        record: TrackingRecord,
        title: String,
        description: String,
        category: TrackingCategory,
        priority: TrackingPriority,
        assigneeId: String,
        assigneeName: String,
        startMillis: Long,
        dueMillis: Long
    ) -> Unit,
    onCancel: () -> Unit
) {
    val isEditing = existingRecord != null
    val now = remember { System.currentTimeMillis() }

    var title by remember(existingRecord) { mutableStateOf(existingRecord?.title ?: "") }
    var description by remember(existingRecord) { mutableStateOf(existingRecord?.description ?: "") }
    var selectedCategory by remember(existingRecord, categories) {
        mutableStateOf(
            categories.firstOrNull { it.id == existingRecord?.categoryId } ?: categories.firstOrNull()
        )
    }
    var selectedPriority by remember(existingRecord) {
        mutableStateOf(existingRecord?.parsedPriority ?: defaultPriority)
    }
    var selectedStatus by remember(existingRecord) {
        mutableStateOf(existingRecord?.parsedStatus ?: TrackingStatus.PENDING)
    }
    var progress by remember(existingRecord) {
        mutableFloatStateOf((existingRecord?.progressPercent ?: 0L).toFloat())
    }
    var assigneeId by remember(existingRecord, currentUser) {
        mutableStateOf(existingRecord?.assigneeId ?: currentUser?.userId ?: "")
    }
    var assigneeName by remember(existingRecord, currentUser) {
        mutableStateOf(existingRecord?.assigneeName ?: currentUser?.displayName ?: "Owner")
    }
    var startDateMillis by remember(existingRecord) {
        mutableLongStateOf(existingRecord?.startDateMillis?.takeIf { it > 0 } ?: now)
    }
    var dueDateMillis by remember(existingRecord) {
        mutableLongStateOf(existingRecord?.dueDateMillis?.takeIf { it > 0 } ?: (now + 7 * 86_400_000L))
    }
    var sourceType by remember(existingRecord) {
        mutableStateOf(existingRecord?.parsedSource ?: TrackingSourceType.MANUAL)
    }

    var validationError by remember { mutableStateOf<String?>(null) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showAssigneeDropdown by remember { mutableStateOf(false) }
    var showSourceDropdown by remember { mutableStateOf(false) }
    var showNewCatDialog by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = if (isEditing) "Edit ${existingRecord?.trackingCode}" else "Create New Tracking Record",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it; validationError = null },
                    label = { Text("Tracking Title *") },
                    placeholder = { Text("e.g., Q4 Server Migration Verification") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tracking_title")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 1000) description = it },
                    label = { Text("Description & Scope") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_tracking_description")
                )

                // Category selector + Create Category button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { showCategoryDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(selectedCategory?.name ?: "Select Category *")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showCategoryDropdown,
                            onDismissRequest = { showCategoryDropdown = false }
                        ) {
                            categories.filter { it.active }.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = {
                                        selectedCategory = cat
                                        showCategoryDropdown = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedButton(onClick = { showNewCatDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = "New Category", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Category")
                    }
                }

                // Priority chips
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TrackingPriority.entries.forEach { pri ->
                        FilterChip(
                            selected = selectedPriority == pri,
                            onClick = { selectedPriority = pri },
                            label = { Text(pri.label) }
                        )
                    }
                }

                // Responsible Person / Assignee
                if (isAdmin && allUsers.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { showAssigneeDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Responsible: $assigneeName")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showAssigneeDropdown,
                            onDismissRequest = { showAssigneeDropdown = false }
                        ) {
                            allUsers.filter { it.active }.forEach { u ->
                                DropdownMenuItem(
                                    text = { Text("${u.displayName} (${u.parsedRole.label})") },
                                    onClick = {
                                        assigneeId = u.userId
                                        assigneeName = u.displayName
                                        showAssigneeDropdown = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = assigneeName,
                        onValueChange = { if (it.length <= 80) assigneeName = it },
                        label = { Text("Responsible Person *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Start & Expected Completion Dates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showStartPicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Start: ${formatShortDate(startDateMillis)}")
                    }
                    OutlinedButton(
                        onClick = { showDuePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Due: ${formatShortDate(dueDateMillis)}")
                    }
                }

                if (!isEditing) {
                    Text("Initial Status", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            TrackingStatus.PENDING,
                            TrackingStatus.IN_PROGRESS,
                            TrackingStatus.ON_HOLD
                        ).forEach { st ->
                            FilterChip(
                                selected = selectedStatus == st,
                                onClick = { selectedStatus = st },
                                label = { Text(st.label) }
                            )
                        }
                    }

                    Text(
                        text = "Initial Progress: ${progress.roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = progress,
                        onValueChange = { progress = it },
                        valueRange = 0f..100f
                    )

                    Box {
                        OutlinedButton(
                            onClick = { showSourceDropdown = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Data Source: ${sourceType.label}")
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showSourceDropdown,
                            onDismissRequest = { showSourceDropdown = false }
                        ) {
                            TrackingSourceType.entries.forEach { src ->
                                DropdownMenuItem(
                                    text = { Text(src.label) },
                                    onClick = { sourceType = src; showSourceDropdown = false }
                                )
                            }
                        }
                    }
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val trimmedTitle = title.trim()
                            val cat = selectedCategory
                            when {
                                trimmedTitle.length < 2 -> validationError = "Title must be at least 2 characters."
                                cat == null -> validationError = "Please select or create a category."
                                assigneeName.isBlank() -> validationError = "Responsible person is required."
                                dueDateMillis < startDateMillis -> validationError = "Expected completion date cannot precede start date."
                                else -> {
                                    validationError = null
                                    if (isEditing && existingRecord != null) {
                                        onSaveEdit(
                                            existingRecord,
                                            trimmedTitle,
                                            description,
                                            cat,
                                            selectedPriority,
                                            assigneeId,
                                            assigneeName,
                                            startDateMillis,
                                            dueDateMillis
                                        )
                                    } else {
                                        onSaveCreate(
                                            trimmedTitle,
                                            description,
                                            cat,
                                            selectedPriority,
                                            selectedStatus,
                                            progress.roundToInt(),
                                            assigneeId,
                                            assigneeName,
                                            startDateMillis,
                                            dueDateMillis,
                                            sourceType
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("submit_tracking_button")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (isEditing) "Save Changes" else "Create Tracking")
                        }
                    }
                }
            }
        }
    }

    if (showNewCatDialog) {
        var newCatName by remember { mutableStateOf("") }
        var newCatDesc by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewCatDialog = false },
            title = { Text("Create Tracking Category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newCatName,
                        onValueChange = { if (it.length <= 50) newCatName = it },
                        label = { Text("Category Name *") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newCatDesc,
                        onValueChange = { if (it.length <= 200) newCatDesc = it },
                        label = { Text("Description") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCatName.trim().length >= 2) {
                            onCreateCategory(newCatName.trim(), "#0F294A", newCatDesc.trim())
                            showNewCatDialog = false
                        }
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewCatDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showStartPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = startDateMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { startDateMillis = it }
                    showStartPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showDuePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDuePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dueDateMillis = it }
                    showDuePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDuePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
}
