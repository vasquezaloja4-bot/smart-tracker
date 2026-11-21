package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val label: String) {
    ADMIN("Administrator"),
    USER("Regular User");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: USER
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: LIGHT
    }
}

enum class TrackingStatus(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    ON_HOLD("On Hold"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    OVERDUE("Overdue");

    val isTerminal: Boolean
        get() = this == COMPLETED || this == CANCELLED

    companion object {
        fun fromRaw(raw: String?): TrackingStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}

enum class TrackingPriority(val label: String, val weight: Int) {
    LOW("Low", 1),
    MEDIUM("Medium", 2),
    HIGH("High", 3),
    CRITICAL("Critical", 4);

    companion object {
        fun fromRaw(raw: String?): TrackingPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class TrackingSourceType(val label: String) {
    MANUAL("Manual Entry (Live Firestore)"),
    WEBHOOK_PENDING("External Webhook (Requires Endpoint)"),
    IOT_UNCONFIGURED("IoT / GPS Feed (Not Configured)");

    companion object {
        fun fromRaw(raw: String?): TrackingSourceType =
            entries.firstOrNull { it.name == raw } ?: MANUAL
    }
}

enum class NotificationType(val label: String) {
    STATUS_UPDATE("Status Update"),
    DEADLINE_REMINDER("Deadline Reminder"),
    OVERDUE_ALERT("Overdue Alert"),
    SYSTEM("System");

    companion object {
        fun fromRaw(raw: String?): NotificationType =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val role: String = UserRole.USER.name,
    val active: Boolean = true,
    val permissions: List<String> = listOf("CREATE_RECORD", "UPDATE_OWN_RECORD", "VIEW_REPORTS"),
    val notifyOnUpdate: Boolean = true,
    val notifyOverdue: Boolean = true,
    val themeMode: String = ThemeMode.LIGHT.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedRole: UserRole get() = UserRole.fromRaw(role)
    val parsedThemeMode: ThemeMode get() = ThemeMode.fromRaw(themeMode)
}

@Keep
@IgnoreExtraProperties
data class TrackingCategory(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#0F294A",
    val description: String = "",
    val active: Boolean = true,
    val shared: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class TrackingRecord(
    @DocumentId val id: String = "",
    val trackingCode: String = "",
    val userId: String = "",
    val ownerName: String = "",
    val assigneeId: String = "",
    val assigneeName: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val priority: String = TrackingPriority.MEDIUM.name,
    val status: String = TrackingStatus.PENDING.name,
    val progressPercent: Long = 0L,
    val startDateMillis: Long = 0L,
    val dueDateMillis: Long = 0L,
    val sourceType: String = TrackingSourceType.MANUAL.name,
    val notesCount: Long = 0L,
    val attachmentsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedStatus: TrackingStatus get() = TrackingStatus.fromRaw(status)
    val parsedPriority: TrackingPriority get() = TrackingPriority.fromRaw(priority)
    val parsedSource: TrackingSourceType get() = TrackingSourceType.fromRaw(sourceType)
}

@Keep
@IgnoreExtraProperties
data class TrackingUpdate(
    @DocumentId val id: String = "",
    val recordId: String = "",
    val userId: String = "",
    val recordOwnerId: String = "",
    val assigneeId: String = "",
    val authorName: String = "",
    val previousStatus: String = TrackingStatus.PENDING.name,
    val newStatus: String = TrackingStatus.PENDING.name,
    val progressPercent: Long = 0L,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedPreviousStatus: TrackingStatus get() = TrackingStatus.fromRaw(previousStatus)
    val parsedNewStatus: TrackingStatus get() = TrackingStatus.fromRaw(newStatus)
}

@Keep
@IgnoreExtraProperties
data class TrackingAttachment(
    @DocumentId val id: String = "",
    val recordId: String = "",
    val userId: String = "",
    val recordOwnerId: String = "",
    val assigneeId: String = "",
    val fileName: String = "",
    val mimeType: String = "",
    val sizeBytes: Long = 0L,
    val localUriString: String = "",
    val uploadedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val recordId: String = "",
    val trackingCode: String = "",
    val title: String = "",
    val message: String = "",
    val type: String = NotificationType.SYSTEM.name,
    val read: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val parsedType: NotificationType get() = NotificationType.fromRaw(type)
}

@Keep
@IgnoreExtraProperties
data class ActivityLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val actorName: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SystemSettings(
    @DocumentId val id: String = "",
    val shared: Boolean = true,
    val autoMarkOverdue: Boolean = true,
    val deadlineWarningHours: Long = 24L,
    val defaultPriority: String = TrackingPriority.MEDIUM.name,
    val allowUserRegistration: Boolean = true,
    val externalTrackerStatus: String = "NOT_CONFIGURED",
    val updatedBy: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val parsedDefaultPriority: TrackingPriority get() = TrackingPriority.fromRaw(defaultPriority)
}
