package com.example.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.TrackingCategory
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingStatus
import com.example.data.model.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.TrackingRecordCard
import com.example.ui.viewmodel.DateRangeFilter
import com.example.ui.viewmodel.RecordSortOrder

@Composable
fun TrackingListScreen(
    recordsState: UiState<List<TrackingRecord>>,
    filteredRecords: List<TrackingRecord>,
    categories: List<TrackingCategory>,
    searchQuery: String,
    statusFilter: TrackingStatus?,
    categoryFilterId: String?,
    dateFilter: DateRangeFilter,
    sortOrder: RecordSortOrder,
    onSearchChange: (String) -> Unit,
    onStatusChange: (TrackingStatus?) -> Unit,
    onCategoryChange: (String?) -> Unit,
    onDateFilterChange: (DateRangeFilter) -> Unit,
    onSortChange: (RecordSortOrder) -> Unit,
    onClearFilters: () -> Unit,
    onCreateNew: () -> Unit,
    onOpenRecord: (TrackingRecord) -> Unit
) {
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showDateMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }

    val hasActiveFilters = searchQuery.isNotBlank() || statusFilter != null ||
        categoryFilterId != null || dateFilter != DateRangeFilter.ALL || sortOrder != RecordSortOrder.NEWEST

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateNew,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.testTag("fab_create_tracking")
            ) {
                Icon(Icons.Outlined.Add, contentDescription = "Create New Tracking")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_tracking_input"),
                    placeholder = { Text("Search by TRK ID, title, or assignee...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                // Status chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { onStatusChange(null) },
                        label = { Text("All") }
                    )
                    TrackingStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { onStatusChange(if (statusFilter == st) null else st) },
                            label = { Text(st.label) }
                        )
                    }
                }

                // Category, Date, Sort dropdowns
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        val selectedCatName = categories.firstOrNull { it.id == categoryFilterId }?.name ?: "Category"
                        OutlinedButton(onClick = { showCategoryMenu = true }) {
                            Icon(Icons.Outlined.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(selectedCatName)
                        }
                        DropdownMenu(expanded = showCategoryMenu, onDismissRequest = { showCategoryMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("All Categories") },
                                onClick = { onCategoryChange(null); showCategoryMenu = false }
                            )
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { onCategoryChange(cat.id); showCategoryMenu = false }
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(onClick = { showDateMenu = true }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(dateFilter.label)
                        }
                        DropdownMenu(expanded = showDateMenu, onDismissRequest = { showDateMenu = false }) {
                            DateRangeFilter.entries.forEach { range ->
                                DropdownMenuItem(
                                    text = { Text(range.label) },
                                    onClick = { onDateFilterChange(range); showDateMenu = false }
                                )
                            }
                        }
                    }

                    Box {
                        OutlinedButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(sortOrder.label)
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            RecordSortOrder.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label) },
                                    onClick = { onSortChange(sort); showSortMenu = false }
                                )
                            }
                        }
                    }

                    if (hasActiveFilters) {
                        TextButton(onClick = onClearFilters) {
                            Text("Reset")
                        }
                    }
                }
            }

            when (recordsState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.padding(16.dp)) {
                        EmptyStateCard(title = "Error loading records", message = recordsState.message)
                    }
                }
                is UiState.Success -> {
                    if (filteredRecords.isEmpty()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            EmptyStateCard(
                                title = "No matching tracking records",
                                message = if (hasActiveFilters) "Try clearing your search or status filters."
                                else "Tap + to create a new tracking record."
                            ) {
                                if (hasActiveFilters) {
                                    OutlinedButton(onClick = onClearFilters) { Text("Clear Filters") }
                                } else {
                                    Button(onClick = onCreateNew) { Text("Create Tracking Record") }
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredRecords, key = { it.id }) { rec ->
                                TrackingRecordCard(record = rec, onClick = { onOpenRecord(rec) })
                            }
                        }
                    }
                }
            }
        }
    }
}
