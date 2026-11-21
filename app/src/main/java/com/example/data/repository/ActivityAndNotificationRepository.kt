package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.ActivityLog
import com.example.data.model.AppNotification
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ActivityAndNotificationRepository(
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

    fun observeNotifications(uid: String): Flow<List<AppNotification>> = flow {
        require(uid.isNotBlank())
        val path = "users/$uid/notifications"
        emitAll(
            db.collection("users").document(uid).collection("notifications")
                .snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun markNotificationRead(uid: String, notificationId: String): Result<Unit> {
        val path = "users/$uid/notifications/$notificationId"
        return try {
            requireUserId()
            db.collection("users").document(uid)
                .collection("notifications").document(notificationId)
                .update(mapOf("read" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(uid: String, ids: List<String>): Result<Unit> {
        val path = "users/$uid/notifications"
        return try {
            requireUserId()
            val batch = db.batch()
            ids.forEach { id ->
                val ref = db.collection("users").document(uid).collection("notifications").document(id)
                batch.update(ref, mapOf("read" to true))
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeActivityLogs(uid: String, isAdmin: Boolean): Flow<List<ActivityLog>> = flow {
        require(uid.isNotBlank())
        val path = "activityLogs"
        val query = if (isAdmin) {
            db.collection(path)
        } else {
            db.collection(path).whereEqualTo("userId", uid)
        }
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(ActivityLog::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun logAction(
        actorName: String,
        actorEmail: String,
        action: String,
        targetType: String,
        targetId: String,
        details: String
    ): Result<Unit> {
        val path = "activityLogs"
        return try {
            val uid = requireUserId()
            db.collection(path).document().set(
                mapOf(
                    "userId" to uid,
                    "actorName" to actorName.ifBlank { "User" }.take(80),
                    "actorEmail" to actorEmail.ifBlank { "$uid@smarttracker.local" }.take(120),
                    "action" to action.take(50),
                    "targetType" to targetType.take(32),
                    "targetId" to targetId.take(128),
                    "details" to details.take(400),
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
}
