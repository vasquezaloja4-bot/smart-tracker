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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.SystemSettings
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingPriority
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.ui.components.EmptyStateCard

@Composable
fun UserManagementScreen(
    isAdmin: Boolean,
    currentUserId: String,
    usersState: UiState<List<UserProfile>>,
    onUpdateUser: (UserProfile, UserRole, Boolean, List<String>) -> Unit
) {
    if (!isAdmin) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            EmptyStateCard(
                title = "Administrator Access Required",
                message = "User management, role assignment, and permission controls are restricted to Administrator accounts.",
                icon = Icons.Outlined.Security
            )
        }
        return
    }

    var editingUser by remember { mutableStateOf<UserProfile?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("User Management", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Manage roles, account status, and permissions. Users join securely via Google Sign-In.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (usersState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Box(Modifier.padding(16.dp)) {
                EmptyStateCard("Unable to load users", usersState.message)
            }
            is UiState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(usersState.data, key = { it.userId }) { user ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("user_card_${user.userId}"),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!user.photoUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = user.photoUrl,
                                        contentDescription = user.displayName,
                                        modifier = Modifier.size(44.dp).clip(CircleShape)
                                    )
                                } else {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = CircleShape
                                    ) {
                                        Icon(
                                            imageVector = if (user.parsedRole == UserRole.ADMIN) Icons.Outlined.AdminPanelSettings else Icons.Outlined.ManageAccounts,
                                            contentDescription = user.displayName,
                                            modifier = Modifier.padding(10.dp).size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = if (user.parsedRole == UserRole.ADMIN) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = user.parsedRole.label,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(
                                            color = if (user.active) MaterialTheme.colorScheme.secondaryContainer
                                            else MaterialTheme.colorScheme.errorContainer,
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = if (user.active) "Active" else "Deactivated",
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                OutlinedButton(
                                    onClick = { editingUser = user },
                                    enabled = user.userId != currentUserId
                                ) {
                                    Text(if (user.userId == currentUserId) "You" else "Manage")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingUser?.let { target ->
        var role by remember(target) { mutableStateOf(target.parsedRole) }
        var active by remember(target) { mutableStateOf(target.active) }
        val allPerms = listOf("CREATE_RECORD", "UPDATE_OWN_RECORD", "VIEW_REPORTS", "MANAGE_USERS", "MANAGE_SETTINGS")
        var selectedPerms by remember(target) { mutableStateOf(target.permissions.toSet()) }

        AlertDialog(
            onDismissRequest = { editingUser = null },
            title = { Text("Manage ${target.displayName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Role", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UserRole.entries.forEach { r ->
                            FilterChip(
                                selected = role == r,
                                onClick = { role = r },
                                label = { Text(r.label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Account Active")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                    Text("Permissions", style = MaterialTheme.typography.labelLarge)
                    allPerms.forEach { perm ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(perm, style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = perm in selectedPerms,
                                onCheckedChange = { checked ->
                                    selectedPerms = if (checked) selectedPerms + perm else selectedPerms - perm
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateUser(target, role, active, selectedPerms.toList())
                    editingUser = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsScreen(
    userProfile: UserProfile?,
    isAdmin: Boolean,
    systemSettings: SystemSettings,
    isBusy: Boolean,
    onSavePreferences: (displayName: String, notifyOnUpdate: Boolean, notifyOverdue: Boolean, themeMode: ThemeMode) -> Unit,
    onSaveSystemSettings: (autoOverdue: Boolean, warningHours: Long, defaultPriority: TrackingPriority, allowReg: Boolean, externalStatus: String) -> Unit
) {
    var displayName by remember(userProfile) { mutableStateOf(userProfile?.displayName ?: "") }
    var notifyOnUpdate by remember(userProfile) { mutableStateOf(userProfile?.notifyOnUpdate ?: true) }
    var notifyOverdue by remember(userProfile) { mutableStateOf(userProfile?.notifyOverdue ?: true) }
    var themeMode by remember(userProfile) { mutableStateOf(userProfile?.parsedThemeMode ?: ThemeMode.LIGHT) }

    var autoOverdue by remember(systemSettings) { mutableStateOf(systemSettings.autoMarkOverdue) }
    var warningHoursText by remember(systemSettings) { mutableStateOf(systemSettings.deadlineWarningHours.toString()) }
    var defaultPriority by remember(systemSettings) { mutableStateOf(systemSettings.parsedDefaultPriority) }
    var allowReg by remember(systemSettings) { mutableStateOf(systemSettings.allowUserRegistration) }
    var externalStatus by remember(systemSettings) { mutableStateOf(systemSettings.externalTrackerStatus) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings & Preferences", style = MaterialTheme.typography.headlineSmall)

        // Profile & Preferences
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("User Profile & Notifications", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { if (it.length <= 80) displayName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("settings_name_input")
                )
                Text(
                    text = "Signed in as ${userProfile?.email ?: ""} (${userProfile?.parsedRole?.label ?: "User"})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify on tracking updates", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = notifyOnUpdate, onCheckedChange = { notifyOnUpdate = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Notify on approaching & overdue deadlines", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = notifyOverdue, onCheckedChange = { notifyOverdue = it })
                }

                Text("Theme Appearance", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { themeMode = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                        )
                    }
                }

                Button(
                    onClick = {
                        if (displayName.isNotBlank()) {
                            onSavePreferences(displayName, notifyOnUpdate, notifyOverdue, themeMode)
                        }
                    },
                    enabled = !isBusy && displayName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("save_preferences_btn")
                ) {
                    Text("Save Personal Preferences")
                }
            }
        }

        // Password / Account Security Notice (Google Sign-In)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Account & Password Management", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "Authentication is secured by Google Sign-In (Credential Manager). Password, 2-step verification, and recovery settings are managed directly through your Google Account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Admin System Configuration
        if (isAdmin) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("System Configuration (Admin)", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-mark overdue records", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = autoOverdue, onCheckedChange = { autoOverdue = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Allow new user registration", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = allowReg, onCheckedChange = { allowReg = it })
                    }

                    OutlinedTextField(
                        value = warningHoursText,
                        onValueChange = { warningHoursText = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Deadline Warning Window (hours, 1–720)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Default Record Priority", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TrackingPriority.entries.forEach { pri ->
                            FilterChip(
                                selected = defaultPriority == pri,
                                onClick = { defaultPriority = pri },
                                label = { Text(pri.label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = externalStatus,
                        onValueChange = { if (it.length <= 100) externalStatus = it },
                        label = { Text("External GPS / IoT / Webhook Connector Status") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val hrs = warningHoursText.toLongOrNull()?.coerceIn(1L, 720L) ?: 24L
                            onSaveSystemSettings(autoOverdue, hrs, defaultPriority, allowReg, externalStatus)
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth().testTag("save_system_settings_btn")
                    ) {
                        Text("Save System Configuration")
                    }
                }
            }
        }
    }
}
