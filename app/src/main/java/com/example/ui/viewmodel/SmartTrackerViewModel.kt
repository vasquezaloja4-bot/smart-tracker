package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesManager
import com.example.data.model.ActivityLog
import com.example.data.model.AppNotification
import com.example.data.model.SystemSettings
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingAttachment
import com.example.data.model.TrackingCategory
import com.example.data.model.TrackingPriority
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingSourceType
import com.example.data.model.TrackingStatus
import com.example.data.model.TrackingUpdate
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.repository.ActivityAndNotificationRepository
import com.example.data.repository.TrackingRepository
import com.example.data.repository.UserAndSettingsRepository
import com.example.data.unwrapFirestoreThrowable
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class DateRangeFilter(val label: String) {
    ALL("All Dates"),
    TODAY("Updated Today"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month"),
    DUE_SOON("Due Within 7 Days")
}

enum class RecordSortOrder(val label: String) {
    NEWEST("Newest First"),
    OLDEST("Oldest First"),
    PRIORITY("Highest Priority")
}

enum class ReportType(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    COMPLETED("Completed"),
    PENDING("Pending"),
    OVERDUE("Overdue"),
    USER_ACTIVITY("User Activity")
}

data class DashboardStats(
    val total: Int = 0,
    val active: Int = 0,
    val completed: Int = 0,
    val pending: Int = 0,
    val overdue: Int = 0,
    val onHold: Int = 0,
    val cancelled: Int = 0,
    val averageProgress: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class SmartTrackerViewModel(
    private val currentUserId: String,
    private val firebaseUser: FirebaseUser,
    private val userRepo: UserAndSettingsRepository,
    private val trackingRepo: TrackingRepository,
    private val activityRepo: ActivityAndNotificationRepository,
    private val prefsManager: PreferencesManager
) : ViewModel() {

    private val _isBootstrapping = MutableStateFlow(true)
    val isBootstrapping: StateFlow<Boolean> = _isBootstrapping.asStateFlow()

    private val _bootstrapError = MutableStateFlow<String?>(null)
    val bootstrapError: StateFlow<String?> = _bootstrapError.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Search & filter controls
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow<TrackingStatus?>(null)
    val statusFilter: StateFlow<TrackingStatus?> = _statusFilter.asStateFlow()

    private val _categoryFilterId = MutableStateFlow<String?>(null)
    val categoryFilterId: StateFlow<String?> = _categoryFilterId.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateRangeFilter.ALL)
    val dateFilter: StateFlow<DateRangeFilter> = _dateFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(RecordSortOrder.NEWEST)
    val sortOrder: StateFlow<RecordSortOrder> = _sortOrder.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = prefsManager.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), ThemeMode.LIGHT)

    val currentUserProfile: StateFlow<UiState<UserProfile?>> = userRepo.observeCurrentUser(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { profile ->
            if (profile != null) {
                viewModelScope.launch { prefsManager.setThemeMode(profile.parsedThemeMode) }
            }
            UiState.Success(profile)
        }
        .catch { error ->
            val root = unwrapFirestoreThrowable(error)
            emit(UiState.Error(root.message ?: "Failed to load user profile"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    private val isAdminFlow: Flow<Boolean> = currentUserProfile
        .map { state ->
            val profile = (state as? UiState.Success)?.data
            val bootstrappedAdmin = firebaseUser.isEmailVerified &&
                firebaseUser.email.equals(UserAndSettingsRepository.ADMIN_EMAIL, ignoreCase = true)
            bootstrappedAdmin || (profile?.parsedRole == UserRole.ADMIN && profile.active)
        }
        .distinctUntilChanged()

    val isAdmin: StateFlow<Boolean> = isAdminFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), false)

    val recordsState: StateFlow<UiState<List<TrackingRecord>>> = isAdminFlow
        .flatMapLatest { admin ->
            trackingRepo.observeTrackingRecords(currentUserId, admin)
                .map<List<TrackingRecord>, UiState<List<TrackingRecord>>> { UiState.Success(it) }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    emit(UiState.Error(root.message ?: "Failed to load tracking records"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val categoriesState: StateFlow<UiState<List<TrackingCategory>>> = isAdminFlow
        .flatMapLatest { admin ->
            userRepo.observeCategories(currentUserId, admin)
                .map<List<TrackingCategory>, UiState<List<TrackingCategory>>> { UiState.Success(it) }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    emit(UiState.Error(root.message ?: "Failed to load categories"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val notificationsState: StateFlow<UiState<List<AppNotification>>> =
        activityRepo.observeNotifications(currentUserId)
            .map<List<AppNotification>, UiState<List<AppNotification>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                emit(UiState.Error(root.message ?: "Failed to load notifications"))
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val activityLogsState: StateFlow<UiState<List<ActivityLog>>> = isAdminFlow
        .flatMapLatest { admin ->
            activityRepo.observeActivityLogs(currentUserId, admin)
                .map<List<ActivityLog>, UiState<List<ActivityLog>>> { UiState.Success(it) }
                .catch { error ->
                    val root = unwrapFirestoreThrowable(error)
                    emit(UiState.Error(root.message ?: "Failed to load activity logs"))
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allUsersState: StateFlow<UiState<List<UserProfile>>> = isAdminFlow
        .flatMapLatest { admin ->
            if (!admin) {
                flowOf(UiState.Success(emptyList()))
            } else {
                userRepo.observeAllUsers()
                    .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                    .catch { error ->
                        val root = unwrapFirestoreThrowable(error)
                        emit(UiState.Error(root.message ?: "Failed to load users"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val systemSettingsState: StateFlow<UiState<SystemSettings>> = userRepo.observeSystemSettings()
        .map<SystemSettings, UiState<SystemSettings>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreThrowable(error)
            emit(UiState.Error(root.message ?: "Failed to load settings"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val unreadNotificationsCount: StateFlow<Int> = notificationsState
        .map { state ->
            (state as? UiState.Success)?.data?.count { !it.read } ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), 0)

    val dashboardStats: StateFlow<DashboardStats> = recordsState
        .map { state ->
            val list = (state as? UiState.Success)?.data ?: emptyList()
            val pending = list.count { it.parsedStatus == TrackingStatus.PENDING }
            val inProgress = list.count { it.parsedStatus == TrackingStatus.IN_PROGRESS }
            val onHold = list.count { it.parsedStatus == TrackingStatus.ON_HOLD }
            val completed = list.count { it.parsedStatus == TrackingStatus.COMPLETED }
            val cancelled = list.count { it.parsedStatus == TrackingStatus.CANCELLED }
            val overdue = list.count { it.parsedStatus == TrackingStatus.OVERDUE }
            val avg = if (list.isEmpty()) 0 else (list.sumOf { it.progressPercent } / list.size).toInt()
            DashboardStats(
                total = list.size,
                active = pending + inProgress + onHold + overdue,
                completed = completed,
                pending = pending,
                overdue = overdue,
                onHold = onHold,
                cancelled = cancelled,
                averageProgress = avg
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), DashboardStats())

    val filteredRecords: StateFlow<List<TrackingRecord>> = combine(
        recordsState,
        _searchQuery,
        _statusFilter,
        _categoryFilterId,
        combine(_dateFilter, _sortOrder) { d, s -> d to s }
    ) { state, query, status, catId, (dateRange, sort) ->
        val base = (state as? UiState.Success)?.data ?: emptyList()
        val q = query.trim().lowercase()
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filtered = base.filter { rec ->
            val matchesQuery = q.isBlank() ||
                rec.trackingCode.lowercase().contains(q) ||
                rec.title.lowercase().contains(q) ||
                rec.assigneeName.lowercase().contains(q) ||
                rec.categoryName.lowercase().contains(q)
            val matchesStatus = status == null || rec.parsedStatus == status
            val matchesCat = catId == null || rec.categoryId == catId
            val recTime = rec.updatedAt?.toDate()?.time ?: rec.startDateMillis
            val matchesDate = when (dateRange) {
                DateRangeFilter.ALL -> true
                DateRangeFilter.TODAY -> recTime >= startOfToday
                DateRangeFilter.LAST_7_DAYS -> recTime >= (now - 7 * dayMs)
                DateRangeFilter.THIS_MONTH -> recTime >= (now - 30 * dayMs)
                DateRangeFilter.DUE_SOON -> rec.dueDateMillis in now..(now + 7 * dayMs) && !rec.parsedStatus.isTerminal
            }
            matchesQuery && matchesStatus && matchesCat && matchesDate
        }

        when (sort) {
            RecordSortOrder.NEWEST -> filtered.sortedByDescending { it.createdAt?.toDate()?.time ?: it.startDateMillis }
            RecordSortOrder.OLDEST -> filtered.sortedBy { it.createdAt?.toDate()?.time ?: it.startDateMillis }
            RecordSortOrder.PRIORITY -> filtered.sortedByDescending { it.parsedPriority.weight }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        bootstrap()
    }

    fun bootstrap() {
        viewModelScope.launch {
            _isBootstrapping.value = true
            _bootstrapError.value = null
            userRepo.ensureUserBootstrapped(firebaseUser)
                .onFailure { err ->
                    _bootstrapError.value = err.localizedMessage ?: "Failed to initialize user workspace"
                }
            _isBootstrapping.value = false
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setStatusFilter(s: TrackingStatus?) { _statusFilter.value = s }
    fun setCategoryFilter(id: String?) { _categoryFilterId.value = id }
    fun setDateFilter(d: DateRangeFilter) { _dateFilter.value = d }
    fun setSortOrder(s: RecordSortOrder) { _sortOrder.value = s }
    fun clearFilters() {
        _searchQuery.value = ""
        _statusFilter.value = null
        _categoryFilterId.value = null
        _dateFilter.value = DateRangeFilter.ALL
        _sortOrder.value = RecordSortOrder.NEWEST
    }

    fun observeRecordUpdates(record: TrackingRecord): Flow<UiState<List<TrackingUpdate>>> =
        trackingRepo.observeRecordUpdates(
            recordId = record.id,
            recordOwnerId = record.userId,
            assigneeId = record.assigneeId,
            currentUserId = currentUserId,
            isAdmin = isAdmin.value
        ).map<List<TrackingUpdate>, UiState<List<TrackingUpdate>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                emit(UiState.Error(root.message ?: "Failed to load history"))
            }

    fun observeRecordAttachments(record: TrackingRecord): Flow<UiState<List<TrackingAttachment>>> =
        trackingRepo.observeRecordAttachments(
            recordId = record.id,
            recordOwnerId = record.userId,
            assigneeId = record.assigneeId,
            currentUserId = currentUserId,
            isAdmin = isAdmin.value
        ).map<List<TrackingAttachment>, UiState<List<TrackingAttachment>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreThrowable(error)
                emit(UiState.Error(root.message ?: "Failed to load attachments"))
            }

    private fun actorProfile(): UserProfile {
        val fromState = (currentUserProfile.value as? UiState.Success)?.data
        return fromState ?: UserProfile(
            id = currentUserId,
            userId = currentUserId,
            displayName = firebaseUser.displayName ?: "SmartTracker User",
            email = firebaseUser.email ?: ""
        )
    }

    fun createCategory(name: String, colorHex: String, description: String) {
        viewModelScope.launch {
            _isBusy.value = true
            userRepo.createCategory(name, colorHex, description, shared = isAdmin.value)
                .onSuccess { _messages.emit("Category \"$name\" created") }
                .onFailure { _messages.emit(it.localizedMessage ?: "Failed to create category") }
            _isBusy.value = false
        }
    }

    fun createTrackingRecord(
        title: String,
        description: String,
        category: TrackingCategory,
        priority: TrackingPriority,
        status: TrackingStatus,
        progressPercent: Int,
        assigneeId: String,
        assigneeName: String,
        startDateMillis: Long,
        dueDateMillis: Long,
        sourceType: TrackingSourceType,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            trackingRepo.createTrackingRecord(
                title = title,
                description = description,
                categoryId = category.id,
                categoryName = category.name,
                priority = priority,
                status = status,
                progressPercent = progressPercent,
                assigneeId = assigneeId,
                assigneeName = assigneeName,
                ownerName = actor.displayName,
                ownerEmail = actor.email,
                startDateMillis = startDateMillis,
                dueDateMillis = dueDateMillis,
                sourceType = sourceType,
                notifyOnUpdate = actor.notifyOnUpdate
            ).onSuccess { newId ->
                _messages.emit("Tracking record created")
                onCreated(newId)
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Failed to create record")
            }
            _isBusy.value = false
        }
    }

    fun updateTrackingDetails(
        record: TrackingRecord,
        title: String,
        description: String,
        category: TrackingCategory,
        priority: TrackingPriority,
        assigneeId: String,
        assigneeName: String,
        startDateMillis: Long,
        dueDateMillis: Long,
        onUpdated: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            trackingRepo.updateTrackingDetails(
                record = record,
                title = title,
                description = description,
                categoryId = category.id,
                categoryName = category.name,
                priority = priority,
                assigneeId = assigneeId,
                assigneeName = assigneeName,
                startDateMillis = startDateMillis,
                dueDateMillis = dueDateMillis,
                actorName = actor.displayName,
                actorEmail = actor.email
            ).onSuccess {
                _messages.emit("Record details saved")
                onUpdated()
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Failed to update record")
            }
            _isBusy.value = false
        }
    }

    fun updateRecordStatus(
        record: TrackingRecord,
        newStatus: TrackingStatus,
        progressPercent: Int,
        note: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            trackingRepo.updateStatusAndProgress(
                record = record,
                newStatus = newStatus,
                progressPercent = progressPercent,
                note = note,
                actorName = actor.displayName,
                actorEmail = actor.email,
                notifyOnUpdate = actor.notifyOnUpdate
            ).onSuccess {
                _messages.emit("Status updated to ${newStatus.label}")
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Status update rejected")
            }
            _isBusy.value = false
        }
    }

    fun addAttachment(
        record: TrackingRecord,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        localUriString: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            trackingRepo.addAttachment(
                record = record,
                fileName = fileName,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                localUriString = localUriString,
                uploadedByName = actor.displayName,
                actorEmail = actor.email
            ).onSuccess {
                _messages.emit("Attachment added to ${record.trackingCode}")
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Failed to attach file")
            }
            _isBusy.value = false
        }
    }

    fun deleteRecord(record: TrackingRecord, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            trackingRepo.deleteTrackingRecord(record, actor.displayName, actor.email)
                .onSuccess {
                    _messages.emit("${record.trackingCode} deleted")
                    onDeleted()
                }.onFailure {
                    _messages.emit(it.localizedMessage ?: "Delete failed")
                }
            _isBusy.value = false
        }
    }

    fun runAutomatedDeadlineCheck() {
        viewModelScope.launch {
            val records = (recordsState.value as? UiState.Success)?.data ?: return@launch
            val settings = (systemSettingsState.value as? UiState.Success)?.data ?: SystemSettings()
            val actor = actorProfile()
            trackingRepo.evaluateAutomatedRules(
                records = records,
                autoMarkOverdue = settings.autoMarkOverdue,
                deadlineWarningHours = settings.deadlineWarningHours,
                notifyOverdue = actor.notifyOverdue,
                actorName = actor.displayName,
                actorEmail = actor.email
            ).onSuccess { count ->
                val msg = if (count > 0) "$count overdue record(s) automatically flagged" else "Deadlines scanned — all up to date"
                _messages.emit(msg)
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Rule evaluation failed")
            }
        }
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch {
            activityRepo.markNotificationRead(currentUserId, id)
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            val list = (notificationsState.value as? UiState.Success)?.data ?: emptyList()
            val unreadIds = list.filter { !it.read }.map { it.id }
            if (unreadIds.isNotEmpty()) {
                activityRepo.markAllNotificationsRead(currentUserId, unreadIds)
                    .onSuccess { _messages.emit("All notifications marked as read") }
            }
        }
    }

    fun logReportExport(reportType: ReportType, formatLabel: String) {
        viewModelScope.launch {
            val actor = actorProfile()
            activityRepo.logAction(
                actorName = actor.displayName,
                actorEmail = actor.email,
                action = "REPORT_EXPORTED",
                targetType = "REPORT",
                targetId = reportType.name,
                details = "Exported ${reportType.label} report as $formatLabel"
            )
        }
    }

    fun saveUserPreferences(
        displayName: String,
        notifyOnUpdate: Boolean,
        notifyOverdue: Boolean,
        themeMode: ThemeMode
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            prefsManager.setThemeMode(themeMode)
            userRepo.updateUserPreferences(
                userId = currentUserId,
                displayName = displayName,
                notifyOnUpdate = notifyOnUpdate,
                notifyOverdue = notifyOverdue,
                themeMode = themeMode
            ).onSuccess {
                _messages.emit("Preferences saved")
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Failed to save preferences")
            }
            _isBusy.value = false
        }
    }

    fun adminUpdateUser(target: UserProfile, role: UserRole, active: Boolean, permissions: List<String>) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            userRepo.adminUpdateUserAccount(target.userId, role, active, permissions)
                .onSuccess {
                    activityRepo.logAction(
                        actorName = actor.displayName,
                        actorEmail = actor.email,
                        action = "USER_ROLE_CHANGED",
                        targetType = "USER",
                        targetId = target.userId,
                        details = "Updated ${target.displayName} → ${role.label}, active=$active"
                    )
                    _messages.emit("Updated ${target.displayName}")
                }.onFailure {
                    _messages.emit(it.localizedMessage ?: "Failed to update user")
                }
            _isBusy.value = false
        }
    }

    fun saveSystemSettings(
        autoMarkOverdue: Boolean,
        deadlineWarningHours: Long,
        defaultPriority: TrackingPriority,
        allowUserRegistration: Boolean,
        externalTrackerStatus: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val actor = actorProfile()
            userRepo.updateSystemSettings(
                autoMarkOverdue = autoMarkOverdue,
                deadlineWarningHours = deadlineWarningHours,
                defaultPriority = defaultPriority.name,
                allowUserRegistration = allowUserRegistration,
                externalTrackerStatus = externalTrackerStatus,
                updatedBy = actor.displayName
            ).onSuccess {
                activityRepo.logAction(
                    actorName = actor.displayName,
                    actorEmail = actor.email,
                    action = "SETTINGS_UPDATED",
                    targetType = "SETTINGS",
                    targetId = "global",
                    details = "System config updated (autoOverdue=$autoMarkOverdue, warning=${deadlineWarningHours}h)"
                )
                _messages.emit("System settings updated")
            }.onFailure {
                _messages.emit(it.localizedMessage ?: "Failed to update system settings")
            }
            _isBusy.value = false
        }
    }
}
