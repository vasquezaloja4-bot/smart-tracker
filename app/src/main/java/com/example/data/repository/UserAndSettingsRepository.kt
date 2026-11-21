package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.OperationType
import com.example.data.handleFirestoreError
import com.example.data.model.SystemSettings
import com.example.data.model.ThemeMode
import com.example.data.model.TrackingCategory
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.unwrapFirestoreThrowable
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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

class UserAndSettingsRepository(
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

    suspend fun ensureUserBootstrapped(firebaseUser: FirebaseUser): Result<UserProfile> {
        val uid = firebaseUser.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val snapshot = docRef.get().await()
            val isBootstrappedAdmin = firebaseUser.isEmailVerified &&
                firebaseUser.email.equals(ADMIN_EMAIL, ignoreCase = true)

            val profile = if (!snapshot.exists()) {
                val role = if (isBootstrappedAdmin) UserRole.ADMIN.name else UserRole.USER.name
                val permissions = if (isBootstrappedAdmin) {
                    listOf("ALL", "CREATE_RECORD", "UPDATE_OWN_RECORD", "MANAGE_USERS", "MANAGE_SETTINGS", "VIEW_REPORTS")
                } else {
                    listOf("CREATE_RECORD", "UPDATE_OWN_RECORD", "VIEW_REPORTS")
                }
                val displayName = (firebaseUser.displayName?.takeIf { it.isNotBlank() }
                    ?: firebaseUser.email?.substringBefore("@")
                    ?: "SmartTracker User").take(80)
                val email = (firebaseUser.email?.takeIf { it.length >= 3 } ?: "$uid@smarttracker.local").take(120)
                val photoUrl = firebaseUser.photoUrl?.toString()?.take(500)

                val payload = mutableMapOf<String, Any>(
                    "userId" to uid,
                    "displayName" to displayName,
                    "email" to email,
                    "role" to role,
                    "active" to true,
                    "permissions" to permissions,
                    "notifyOnUpdate" to true,
                    "notifyOverdue" to true,
                    "themeMode" to ThemeMode.LIGHT.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                if (photoUrl != null) {
                    payload["photoUrl"] = photoUrl
                }
                docRef.set(payload).await()
                docRef.get().await().toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(
                        id = uid,
                        userId = uid,
                        displayName = displayName,
                        email = email,
                        photoUrl = photoUrl,
                        role = role
                    )
            } else {
                snapshot.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                    ?: UserProfile(id = uid, userId = uid)
            }

            if (isBootstrappedAdmin || profile.parsedRole == UserRole.ADMIN) {
                seedDefaultsForAdmin(uid, profile.displayName.ifBlank { "Admin" })
            } else {
                ensureStarterCategoriesForUser(uid)
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    private suspend fun seedDefaultsForAdmin(uid: String, adminName: String) {
        val settingsRef = db.collection("systemSettings").document("global")
        val settingsSnap = settingsRef.get().await()
        if (!settingsSnap.exists()) {
            settingsRef.set(
                mapOf(
                    "shared" to true,
                    "autoMarkOverdue" to true,
                    "deadlineWarningHours" to 24L,
                    "defaultPriority" to "MEDIUM",
                    "allowUserRegistration" to true,
                    "externalTrackerStatus" to "NOT_CONFIGURED",
                    "updatedBy" to adminName.take(80),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val defaultCategories = listOf(
            Triple("cat_operations", "Operations", "#0F294A"),
            Triple("cat_logistics", "Logistics", "#059669"),
            Triple("cat_compliance", "Compliance", "#0284C7"),
            Triple("cat_systems", "IT & Systems", "#7C3AED")
        )
        for ((id, name, hex) in defaultCategories) {
            val catRef = db.collection("trackingCategories").document(id)
            if (!catRef.get().await().exists()) {
                catRef.set(
                    mapOf(
                        "userId" to uid,
                        "name" to name,
                        "colorHex" to hex,
                        "description" to "$name tracking workflows",
                        "active" to true,
                        "shared" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    private suspend fun ensureStarterCategoriesForUser(uid: String) {
        val sharedCats = db.collection("trackingCategories")
            .whereEqualTo("shared", true)
            .get()
            .await()
        val ownCats = db.collection("trackingCategories")
            .whereEqualTo("userId", uid)
            .get()
            .await()
        if (sharedCats.isEmpty && ownCats.isEmpty) {
            val personalId = "cat_${uid.take(12)}_general"
            db.collection("trackingCategories").document(personalId).set(
                mapOf(
                    "userId" to uid,
                    "name" to "General",
                    "colorHex" to "#0F294A",
                    "description" to "Default personal tracking category",
                    "active" to true,
                    "shared" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeCurrentUser(uid: String): Flow<UserProfile?> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE) }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsers(): Flow<List<UserProfile>> = flow {
        val uid = requireUserId()
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snap ->
                    snap.toObjects(UserProfile::class.java, ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.displayName.lowercase() }
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

    fun observeCategories(uid: String, isAdmin: Boolean): Flow<List<TrackingCategory>> = flow {
        require(uid.isNotBlank()) { "User ID must not be blank" }
        val path = "trackingCategories"
        val stream = if (isAdmin) {
            db.collection(path).snapshots().map { snap ->
                snap.toObjects(TrackingCategory::class.java, ServerTimestampBehavior.ESTIMATE)
            }
        } else {
            val sharedFlow = db.collection(path).whereEqualTo("shared", true).snapshots()
                .map { it.toObjects(TrackingCategory::class.java, ServerTimestampBehavior.ESTIMATE) }
            val ownFlow = db.collection(path).whereEqualTo("userId", uid).snapshots()
                .map { it.toObjects(TrackingCategory::class.java, ServerTimestampBehavior.ESTIMATE) }
            combine(sharedFlow, ownFlow) { shared, own ->
                (shared + own).distinctBy { it.id }
            }
        }
        emitAll(
            stream.map { list -> list.sortedBy { it.name.lowercase() } }
                .catch { error ->
                    val unwrapped = unwrapFirestoreThrowable(error)
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(unwrapped, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createCategory(
        name: String,
        colorHex: String,
        description: String,
        shared: Boolean
    ): Result<String> {
        var path = "trackingCategories"
        return try {
            val uid = requireUserId()
            val trimmed = name.trim()
            require(trimmed.length in 2..50) { "Category name must be 2–50 characters" }
            val docRef = db.collection("trackingCategories").document()
            path = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to trimmed,
                    "colorHex" to colorHex.ifBlank { "#0F294A" },
                    "description" to description.trim().take(200),
                    "active" to true,
                    "shared" to shared,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    fun observeSystemSettings(): Flow<SystemSettings> = flow {
        val uid = requireUserId()
        val path = "systemSettings"
        emitAll(
            db.collection(path)
                .whereEqualTo("shared", true)
                .snapshots()
                .map { snap ->
                    snap.toObjects(SystemSettings::class.java, ServerTimestampBehavior.ESTIMATE)
                        .firstOrNull() ?: SystemSettings()
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

    suspend fun updateUserPreferences(
        userId: String,
        displayName: String,
        notifyOnUpdate: Boolean,
        notifyOverdue: Boolean,
        themeMode: ThemeMode
    ): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Cannot update another user's preferences here" }
            val trimmedName = displayName.trim()
            require(trimmedName.length in 1..80) { "Display name must be 1–80 characters" }
            db.collection("users").document(userId).update(
                mapOf(
                    "displayName" to trimmedName,
                    "notifyOnUpdate" to notifyOnUpdate,
                    "notifyOverdue" to notifyOverdue,
                    "themeMode" to themeMode.name,
                    "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun adminUpdateUserAccount(
        targetUserId: String,
        role: UserRole,
        active: Boolean,
        permissions: List<String>
    ): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "role" to role.name,
                    "active" to active,
                    "permissions" to permissions.take(10),
                    "updatedAt" to FieldValue.serverTimestamp()
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

    suspend fun updateSystemSettings(
        autoMarkOverdue: Boolean,
        deadlineWarningHours: Long,
        defaultPriority: String,
        allowUserRegistration: Boolean,
        externalTrackerStatus: String,
        updatedBy: String
    ): Result<Unit> {
        val path = "systemSettings/global"
        return try {
            requireUserId()
            require(deadlineWarningHours in 1L..720L) { "Warning hours must be 1–720" }
            val ref = db.collection("systemSettings").document("global")
            val payload = mapOf(
                "shared" to true,
                "autoMarkOverdue" to autoMarkOverdue,
                "deadlineWarningHours" to deadlineWarningHours,
                "defaultPriority" to defaultPriority,
                "allowUserRegistration" to allowUserRegistration,
                "externalTrackerStatus" to externalTrackerStatus.trim().ifBlank { "NOT_CONFIGURED" }.take(100),
                "updatedBy" to updatedBy.trim().ifBlank { "Admin" }.take(80),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (ref.get().await().exists()) {
                ref.update(payload).await()
            } else {
                ref.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val ADMIN_EMAIL = "vasquezaloja4@gmail.com"
    }
}
