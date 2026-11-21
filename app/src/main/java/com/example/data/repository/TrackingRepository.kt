package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.NotificationType
import com.example.data.model.TrackingAttachment
import com.example.data.model.TrackingPriority
import com.example.data.model.TrackingRecord
import com.example.data.model.TrackingSourceType
import com.example.data.model.TrackingStatus
import com.example.data.model.TrackingUpdate
import com.example.data.unwrapFirestoreThrowable
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import kotlin.random.Random

class TrackingRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeTrackingRecords(uid: String, isAdmin: Boolean): Flow<List<TrackingRecord>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "trackingRecords"
        val stream = if (isAdmin) {
            db.collection(path).snapshots().map { snap ->
                snap.toObjects(TrackingRecord::class.java, ServerTimestampBehavior.ESTIMATE)
            }
        } else {
            val ownedFlow = db.collection(path).whereEqualTo("userId", uid).snapshots()
                .map { it.toObjects(TrackingRecord::class.java, ServerTimestampBehavior.ESTIMATE) }
            val assignedFlow = db.collection(path).whereEqualTo("assigneeId", uid).snapshots()
                .map { it.toObjects(TrackingRecord::class.java, ServerTimestampBehavior.ESTIMATE) }
            combine(ownedFlow, assignedFlow) { owned, assigned ->
                (owned + assigned).distinctBy { it.id }
            }
        }
        emitAll(
            stream.map { records ->
                records.sortedByDescending { it.updatedAt?.toDate()?.time ?: it.startDateMillis }
            }.catch { error ->
                val unwrapped = unwrapFirestoreThrowable(error)
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(unwrapped, OperationType.LIST, path)
                }
                throw error
            }
        )
    }

    fun observeRecordUpdates(
        recordId: String,
        recordOwnerId: String,
        assigneeId: String,
        currentUserId: String,
        isAdmin: Boolean
    ): Flow<List<TrackingUpdate>> = flow {
        require(recordId.isNotBlank() && currentUserId.isNotBlank())
        val base = db.collection("trackingRecords").document(recordId).collection("updates")
        val query = when {
            isAdmin -> base
            currentUserId == recordOwnerId -> base.whereEqualTo("recordOwnerId", currentUserId)
            currentUserId == assigneeId -> base.whereEqualTo("assigneeId", currentUserId)
            else -> base.whereEqualTo("userId", currentUserId)
        }
        val path = "trackingRecords/$recordId/updates"
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(TrackingUpdate::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRecordAttachments(
        recordId: String,
        recordOwnerId: String,
        assigneeId: String,
        currentUserId: String,
        isAdmin: Boolean
    ): Flow<List<TrackingAttachment>> = flow {
        require(recordId.isNotBlank() && currentUserId.isNotBlank())
        val base = db.collection("trackingRecords").document(recordId).collection("attachments")
        val query = when {
            isAdmin -> base
            currentUserId == recordOwnerId -> base.whereEqualTo("recordOwnerId", currentUserId)
            currentUserId == assigneeId -> base.whereEqualTo("assigneeId", currentUserId)
            else -> base.whereEqualTo("userId", currentUserId)
        }
        val path = "trackingRecords/$recordId/attachments"
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(TrackingAttachment::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createTrackingRecord(
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        priority: TrackingPriority,
        status: TrackingStatus,
        progressPercent: Int,
        assigneeId: String,
        assigneeName: String,
        ownerName: String,
        ownerEmail: String,
        startDateMillis: Long,
        dueDateMillis: Long,
        sourceType: TrackingSourceType,
        notifyOnUpdate: Boolean
    ): Result<String> {
        var path = "trackingRecords"
        return try {
            val uid = requireUserId()
            val trimmedTitle = title.trim()
            require(trimmedTitle.length in 2..100) { "Title must be 2–100 characters" }
            require(categoryId.isNotBlank()) { "Category is required" }
            require(dueDateMillis >= startDateMillis) { "Expected completion date must be on or after start date" }

            val docRef = db.collection("trackingRecords").document()
            path = docRef.path
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val suffix = Random.nextInt(1000, 9999)
            val trackingCode = "TRK-$year-$suffix"
            val clampedProgress = progressPercent.coerceIn(0, 100).toLong()
            val safeAssigneeId = assigneeId.ifBlank { uid }
            val safeAssigneeName = assigneeName.trim().ifBlank { ownerName.ifBlank { "Owner" } }.take(80)
            val safeOwnerName = ownerName.trim().ifBlank { "SmartTracker User" }.take(80)

            val payload = mapOf(
                "trackingCode" to trackingCode,
                "userId" to uid,
                "ownerName" to safeOwnerName,
                "assigneeId" to safeAssigneeId,
                "assigneeName" to safeAssigneeName,
                "title" to trimmedTitle,
                "description" to description.trim().take(1000),
                "categoryId" to categoryId,
                "categoryName" to categoryName.trim().ifBlank { "General" }.take(50),
                "priority" to priority.name,
                "status" to status.name,
                "progressPercent" to clampedProgress,
                "startDateMillis" to startDateMillis,
                "dueDateMillis" to dueDateMillis,
                "sourceType" to sourceType.name,
                "notesCount" to 1L,
                "attachmentsCount" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            // Append initial TrackingUpdate
            docRef.collection("updates").document().set(
                mapOf(
                    "recordId" to docRef.id,
                    "userId" to uid,
                    "recordOwnerId" to uid,
                    "assigneeId" to safeAssigneeId,
                    "authorName" to safeOwnerName,
                    "previousStatus" to status.name,
                    "newStatus" to status.name,
                    "progressPercent" to clampedProgress,
                    "note" to "Tracking record initialized ($trackingCode)",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            // Immutable ActivityLog
            db.collection("activityLogs").document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to safeOwnerName,
                    "actorEmail" to ownerEmail.ifBlank { "$uid@smarttracker.local" }.take(120),
                    "action" to "RECORD_CREATED",
                    "targetType" to "RECORD",
                    "targetId" to docRef.id,
                    "details" to "Created $trackingCode: $trimmedTitle",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (notifyOnUpdate) {
                db.collection("users").document(uid).collection("notifications").document().set(
                    mapOf(
                        "userId" to uid,
                        "recordId" to docRef.id,
                        "trackingCode" to trackingCode,
                        "title" to "Tracking Created",
                        "message" to "$trackingCode ($trimmedTitle) assigned to $safeAssigneeName",
                        "type" to NotificationType.STATUS_UPDATE.name,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTrackingDetails(
        record: TrackingRecord,
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        priority: TrackingPriority,
        assigneeId: String,
        assigneeName: String,
        startDateMillis: Long,
        dueDateMillis: Long,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        val path = "trackingRecords/${record.id}"
        return try {
            val uid = requireUserId()
            require(record.id.isNotBlank()) { "Record ID required" }
            val trimmedTitle = title.trim()
            require(trimmedTitle.length in 2..100) { "Title must be 2–100 characters" }
            require(dueDateMillis >= startDateMillis) { "Due date must be on or after start date" }

            db.collection("trackingRecords").document(record.id).update(
                mapOf(
                    "title" to trimmedTitle,
                    "description" to description.trim().take(1000),
                    "categoryId" to categoryId,
                    "categoryName" to categoryName.trim().take(50),
                    "priority" to priority.name,
                    "assigneeId" to assigneeId.ifBlank { record.assigneeId },
                    "assigneeName" to assigneeName.trim().ifBlank { record.assigneeName }.take(80),
                    "startDateMillis" to startDateMillis,
                    "dueDateMillis" to dueDateMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("activityLogs").document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to actorName.ifBlank { "User" }.take(80),
                    "actorEmail" to actorEmail.ifBlank { "$uid@smarttracker.local" }.take(120),
                    "action" to "RECORD_UPDATED",
                    "targetType" to "RECORD",
                    "targetId" to record.id,
                    "details" to "Edited metadata for ${record.trackingCode}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateStatusAndProgress(
        record: TrackingRecord,
        newStatus: TrackingStatus,
        progressPercent: Int,
        note: String,
        actorName: String,
        actorEmail: String,
        notifyOnUpdate: Boolean
    ): Result<Unit> {
        val path = "trackingRecords/${record.id}"
        return try {
            val uid = requireUserId()
            require(record.id.isNotBlank()) { "Record ID required" }
            val safeProgress = if (newStatus == TrackingStatus.COMPLETED) 100L else progressPercent.coerceIn(0, 100).toLong()
            val safeNote = note.trim().take(500).ifBlank { "Status updated to ${newStatus.label} ($safeProgress%)" }
            val safeActor = actorName.ifBlank { "User" }.take(80)

            val docRef = db.collection("trackingRecords").document(record.id)
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "progressPercent" to safeProgress,
                    "notesCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            docRef.collection("updates").document().set(
                mapOf(
                    "recordId" to record.id,
                    "userId" to uid,
                    "recordOwnerId" to record.userId,
                    "assigneeId" to record.assigneeId,
                    "authorName" to safeActor,
                    "previousStatus" to record.status,
                    "newStatus" to newStatus.name,
                    "progressPercent" to safeProgress,
                    "note" to safeNote,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("activityLogs").document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to safeActor,
                    "actorEmail" to actorEmail.ifBlank { "$uid@smarttracker.local" }.take(120),
                    "action" to "STATUS_UPDATED",
                    "targetType" to "RECORD",
                    "targetId" to record.id,
                    "details" to "${record.trackingCode}: ${record.parsedStatus.label} → ${newStatus.label} ($safeProgress%)",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            if (notifyOnUpdate) {
                db.collection("users").document(uid).collection("notifications").document().set(
                    mapOf(
                        "userId" to uid,
                        "recordId" to record.id,
                        "trackingCode" to record.trackingCode,
                        "title" to "${record.trackingCode} Updated",
                        "message" to "Status changed to ${newStatus.label} ($safeProgress%)",
                        "type" to if (newStatus == TrackingStatus.OVERDUE) NotificationType.OVERDUE_ALERT.name else NotificationType.STATUS_UPDATE.name,
                        "read" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun addAttachment(
        record: TrackingRecord,
        fileName: String,
        mimeType: String,
        sizeBytes: Long,
        localUriString: String,
        uploadedByName: String,
        actorEmail: String
    ): Result<Unit> {
        val path = "trackingRecords/${record.id}/attachments"
        return try {
            val uid = requireUserId()
            require(record.id.isNotBlank()) { "Record ID required" }
            val docRef = db.collection("trackingRecords").document(record.id)
            docRef.collection("attachments").document().set(
                mapOf(
                    "recordId" to record.id,
                    "userId" to uid,
                    "recordOwnerId" to record.userId,
                    "assigneeId" to record.assigneeId,
                    "fileName" to fileName.trim().ifBlank { "attachment" }.take(120),
                    "mimeType" to mimeType.trim().ifBlank { "application/octet-stream" }.take(80),
                    "sizeBytes" to sizeBytes.coerceAtLeast(0L),
                    "localUriString" to localUriString.take(500),
                    "uploadedByName" to uploadedByName.ifBlank { "User" }.take(80),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            docRef.update(
                mapOf(
                    "attachmentsCount" to FieldValue.increment(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("activityLogs").document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to uploadedByName.ifBlank { "User" }.take(80),
                    "actorEmail" to actorEmail.ifBlank { "$uid@smarttracker.local" }.take(120),
                    "action" to "ATTACHMENT_ADDED",
                    "targetType" to "RECORD",
                    "targetId" to record.id,
                    "details" to "Attached ${fileName.take(60)} to ${record.trackingCode}",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTrackingRecord(
        record: TrackingRecord,
        actorName: String,
        actorEmail: String
    ): Result<Unit> {
        val path = "trackingRecords/${record.id}"
        return try {
            val uid = requireUserId()
            require(record.id.isNotBlank())
            db.collection("trackingRecords").document(record.id).delete().await()
            db.collection("activityLogs").document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to actorName.ifBlank { "User" }.take(80),
                    "actorEmail" to actorEmail.ifBlank { "$uid@smarttracker.local" }.take(120),
                    "action" to "RECORD_DELETED",
                    "targetType" to "RECORD",
                    "targetId" to record.id,
                    "details" to "Deleted ${record.trackingCode} (${record.title})",
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun evaluateAutomatedRules(
        records: List<TrackingRecord>,
        autoMarkOverdue: Boolean,
        deadlineWarningHours: Long,
        notifyOverdue: Boolean,
        actorName: String,
        actorEmail: String
    ): Result<Int> {
        val uid = auth.currentUser?.uid ?: return Result.success(0)
        return try {
            val nowMillis = System.currentTimeMillis()
            val warningMillis = deadlineWarningHours * 3600_000L
            var updatedCount = 0

            for (rec in records) {
                val st = rec.parsedStatus
                if (st.isTerminal) continue
                val canModify = rec.userId == uid || rec.assigneeId == uid
                if (!canModify) continue

                if (autoMarkOverdue && rec.dueDateMillis in 1 until nowMillis && st != TrackingStatus.OVERDUE) {
                    updateStatusAndProgress(
                        record = rec,
                        newStatus = TrackingStatus.OVERDUE,
                        progressPercent = rec.progressPercent.toInt(),
                        note = "Automatically marked Overdue after passing expected completion date",
                        actorName = actorName,
                        actorEmail = actorEmail,
                        notifyOnUpdate = notifyOverdue
                    )
                    updatedCount++
                } else if (notifyOverdue && rec.dueDateMillis in nowMillis..(nowMillis + warningMillis)) {
                    // Deterministic reminder doc ID prevents duplicate spam for the same record deadline
                    val reminderId = "rem_${rec.id}_${rec.dueDateMillis / 86400000L}"
                    val notifRef = db.collection("users").document(uid)
                        .collection("notifications").document(reminderId)
                    if (!notifRef.get().await().exists()) {
                        notifRef.set(
                            mapOf(
                                "userId" to uid,
                                "recordId" to rec.id,
                                "trackingCode" to rec.trackingCode,
                                "title" to "Approaching Deadline",
                                "message" to "${rec.trackingCode} (${rec.title}) is due within ${deadlineWarningHours}h",
                                "type" to NotificationType.DEADLINE_REMINDER.name,
                                "read" to false,
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
            }
            Result.success(updatedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "trackingRecords")
            Result.failure(e)
        }
    }
}
