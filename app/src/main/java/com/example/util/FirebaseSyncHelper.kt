package com.example.util

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.auth.FirebaseAuth
import com.example.data.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Extension helper to await standard Google Task operations cleanly and smoothly
suspend fun <T> Task<T>.await(): T {
    if (isComplete) {
        val e = exception
        return if (e == null) {
            @Suppress("UNCHECKED_CAST")
            result as T
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val e = task.exception
            if (e == null) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            } else {
                cont.resumeWithException(e)
            }
        }
    }
}

object FirebaseSyncHelper {
    private const val PREFS_NAME = "firebase_sync_prefs"
    val deviceSessionId = java.util.UUID.randomUUID().toString()
    private const val KEY_DB_URL = "db_url"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_AUTO_SYNC = "auto_sync"

    fun getDatabaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_DB_URL, "") ?: ""
        return if (saved.isNotBlank()) saved else "https://corpreminder-default-rtdb.asia-southeast1.firebasedatabase.app"
    }

    fun saveDatabaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DB_URL, url).apply()
    }

    fun getApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_API_KEY, "") ?: ""
        return if (saved.isNotBlank()) saved else "AIzaSyC-CpAUDqtFSMlqB520IlZuuYrJAYW5phs"
    }

    fun saveApiKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_API_KEY, key).apply()
    }

    fun getProjectId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PROJECT_ID, "") ?: ""
        return if (saved.isNotBlank()) saved else "corpreminder"
    }

    fun saveProjectId(context: Context, id: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROJECT_ID, id).apply()
    }

    fun isAutoSyncEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC, true) // ON by default!
    }

    fun setAutoSyncEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()
    }

    fun getFirebaseInstance(context: Context): FirebaseApp? {
        val url = getDatabaseUrl(context).trim()
        val apiKey = getApiKey(context).trim()
        val projectId = getProjectId(context).trim()

        if (url.isEmpty()) {
            return try {
                FirebaseApp.getInstance()
            } catch (e: Exception) {
                null
            }
        }

        val appName = "CorpRemindFirebaseApp"
        return try {
            FirebaseApp.getInstance(appName)
        } catch (e: Exception) {
            try {
                val checkedProjectId = if (projectId.isEmpty()) {
                    var extractedId = "corpremind-hq"
                    try {
                        val uri = android.net.Uri.parse(url)
                        val host = uri.host
                        if (host != null) {
                            val parts = host.split(".")
                            if (parts.isNotEmpty()) {
                                extractedId = parts[0]
                            }
                        }
                    } catch (ex: Exception) {
                        Log.w("FirebaseSyncHelper", "Failed to extract project ID from DB URL", ex)
                    }
                    extractedId
                } else {
                    projectId
                }
                val checkedApiKey = if (apiKey.isEmpty()) "AIzaSyFakeKeyForCorpremindHqSyncingApp" else apiKey
                val options = FirebaseOptions.Builder()
                    .setDatabaseUrl(url)
                    .setApiKey(checkedApiKey)
                    .setApplicationId(checkedProjectId)
                    .setProjectId(checkedProjectId)
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options, appName)
            } catch (initEx: Exception) {
                Log.e("FirebaseSyncHelper", "Error initializing Dynamic FirebaseApp: ${initEx.message}")
                null
            }
        }
    }

    fun getDatabaseReference(context: Context): DatabaseReference? {
        val app = getFirebaseInstance(context) ?: return null
        val url = getDatabaseUrl(context).trim()
        return try {
            if (url.isNotEmpty()) {
                FirebaseDatabase.getInstance(app, url).reference
            } else {
                FirebaseDatabase.getInstance(app).reference
            }
        } catch (e: Exception) {
            Log.e("FirebaseSyncHelper", "Error retrieving DatabaseReference: ${e.message}")
            try {
                FirebaseDatabase.getInstance(app).reference
            } catch (fallbackEx: Exception) {
                null
            }
        }
    }

    suspend fun ensureAuthenticated(context: Context): Boolean {
        val app = getFirebaseInstance(context) ?: return false
        return try {
            val auth = FirebaseAuth.getInstance(app)
            if (auth.currentUser != null) {
                true
            } else {
                Log.i("FirebaseSyncHelper", "No authenticated user. Signing in anonymously to guarantee secure access...")
                auth.signInAnonymously().await()
                Log.i("FirebaseSyncHelper", "Anonymous authentication completed successfully.")
                true
            }
        } catch (e: Exception) {
            Log.e("FirebaseSyncHelper", "Dynamic Firebase sign-in failed: ${e.message}")
            false
        }
    }

    suspend fun backupToCloud(context: Context, repository: ReminderRepository): Result<Unit> {
        val authOk = ensureAuthenticated(context)
        if (!authOk) {
            Log.w("FirebaseSyncHelper", "Dynamic Firebase authentication could not be completed, but proceeding with database write anyway.")
        }
        val app = getFirebaseInstance(context) ?: return Result.failure(Exception("Firebase is not initialized or configured."))
        val uid = FirebaseAuth.getInstance(app).currentUser?.uid
        val rootRef = getDatabaseReference(context) ?: return Result.failure(Exception("Firebase is not initialized or configured."))
        val ref = rootRef.child("shared_workspace")
        return try {
            val users = repository.allUsers.first()
            val reminders = repository.allReminders.first()
            val completions = repository.allCompletions.first()
            val leaves = repository.allPlannedLeaves.first()

            val usersMap = users.associate { it.id to mapOf(
                "id" to it.id,
                "name" to it.name,
                "role" to it.role,
                "imageUrl" to it.imageUrl,
                "password" to CryptoUtils.encrypt(it.password),
                "passwordCreated" to it.passwordCreated,
                "question1" to it.question1,
                "answer1" to CryptoUtils.encrypt(it.answer1),
                "question2" to it.question2,
                "answer2" to CryptoUtils.encrypt(it.answer2),
                "question3" to it.question3,
                "answer3" to CryptoUtils.encrypt(it.answer3)
            )}

            val remindersMap = reminders.associate { it.id.toString() to mapOf(
                "id" to it.id,
                "title" to it.title,
                "description" to it.description,
                "frequency" to it.frequency,
                "customDays" to it.customDays,
                "targetUserId" to it.targetUserId,
                "isSystemDefault" to it.isSystemDefault,
                "createdBy" to it.createdBy,
                "createdAt" to it.createdAt,
                "isRepetitive" to it.isRepetitive,
                "startDay" to it.startDay
            )}

            val completionsMap = completions.associate { it.id.toString() to mapOf(
                "id" to it.id,
                "reminderId" to it.reminderId,
                "userId" to it.userId,
                "dateString" to it.dateString,
                "isCompleted" to it.isCompleted,
                "payload" to it.payload,
                "timestamp" to it.timestamp
            )}

            val leavesMap = leaves.associate { it.id.toString() to mapOf(
                "id" to it.id,
                "userId" to it.userId,
                "userName" to it.userName,
                "startDate" to it.startDate,
                "endDate" to it.endDate,
                "reason" to it.reason,
                "status" to it.status,
                "timestamp" to it.timestamp
            )}

            val prefs = context.getSharedPreferences("CorpRemind_Prefs", Context.MODE_PRIVATE)
            val reqVer = prefs.getInt("required_app_version", 1)
            val desc = prefs.getString("update_description", "Please configure and run the updated application setup on your system.") ?: ""
            val link = prefs.getString("update_link", "https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing") ?: ""

            val data = mapOf(
                "users" to usersMap,
                "reminders" to remindersMap,
                "completions" to completionsMap,
                "planned_leaves" to leavesMap,
                "last_backup_timestamp" to System.currentTimeMillis(),
                "last_backup_device_id" to deviceSessionId,
                "required_app_version" to reqVer,
                "update_description" to desc,
                "update_link" to link
            )

            ref.setValue(data).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromSnapshot(context: Context, snapshot: DataSnapshot, repository: ReminderRepository): Result<Unit> {
        return try {
            val reqVerVal = snapshot.child("required_app_version").value
            val reqVer = when (reqVerVal) {
                is Number -> reqVerVal.toInt()
                is String -> reqVerVal.toIntOrNull() ?: 1
                else -> 1
            }
            val updateDesc = snapshot.child("update_description").value as? String ?: "Please configure and run the updated application setup on your system."
            val updateLink = snapshot.child("update_link").value as? String ?: "https://drive.google.com/drive/folders/1Im5CEoPEHDHXfnLi-6UdJyzp-U_p4B4y?usp=sharing"
            
            val prefs = context.getSharedPreferences("CorpRemind_Prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putInt("required_app_version", reqVer)
                .putString("update_description", updateDesc)
                .putString("update_link", updateLink)
                .apply()

            val usersSnap = snapshot.child("users")
            if (usersSnap.exists()) {
                repository.clearUsers()
                usersSnap.children.forEach { child ->
                    val id = child.key ?: return@forEach
                    val name = child.child("name").value as? String ?: return@forEach
                    val role = child.child("role").value as? String ?: "EMPLOYEE"
                    val imageUrl = child.child("imageUrl").value as? String
                    val passwordEncrypted = child.child("password").value as? String ?: "password"
                    val password = CryptoUtils.decrypt(passwordEncrypted) ?: "password"
                    val passwordCreated = child.child("passwordCreated").value as? Boolean ?: false
                    
                    val question1 = child.child("question1").value as? String
                    val answer1Encrypted = child.child("answer1").value as? String
                    val answer1 = CryptoUtils.decrypt(answer1Encrypted)
                    val question2 = child.child("question2").value as? String
                    val answer2Encrypted = child.child("answer2").value as? String
                    val answer2 = CryptoUtils.decrypt(answer2Encrypted)
                    val question3 = child.child("question3").value as? String
                    val answer3Encrypted = child.child("answer3").value as? String
                    val answer3 = CryptoUtils.decrypt(answer3Encrypted)
 
                    repository.insertUser(User(
                        id = id,
                        name = name,
                        role = role,
                        imageUrl = imageUrl,
                        password = password,
                        passwordCreated = passwordCreated,
                        question1 = question1,
                        answer1 = answer1,
                        question2 = question2,
                        answer2 = answer2,
                        question3 = question3,
                        answer3 = answer3
                    ))
                }
            }
 
            val remindersSnap = snapshot.child("reminders")
            repository.clearReminders()
            if (remindersSnap.exists()) {
                remindersSnap.children.forEach { child ->
                    val idStr = child.key ?: return@forEach
                    val id = idStr.toIntOrNull() ?: 0
                    val title = child.child("title").value as? String ?: return@forEach
                    val description = child.child("description").value as? String ?: ""
                    val frequency = child.child("frequency").value as? String ?: "DAILY"
                    val customDays = child.child("customDays").value as? String
                    val targetUserId = child.child("targetUserId").value as? String
                    val isSystemDefault = child.child("isSystemDefault").value as? Boolean ?: false
                    val createdBy = child.child("createdBy").value as? String ?: "system"
                    val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: System.currentTimeMillis()
                    val isRepetitive = child.child("isRepetitive").value as? Boolean ?: true
                    val startDay = child.child("startDay").value as? String
 
                    repository.insertReminder(Reminder(id, title, description, frequency, customDays, targetUserId, isSystemDefault, createdBy, createdAt, isRepetitive, startDay))
                }
            }
 
            val completionsSnap = snapshot.child("completions")
            repository.clearCompletions()
            if (completionsSnap.exists()) {
                completionsSnap.children.forEach { child ->
                    val idStr = child.key ?: return@forEach
                    val id = idStr.toIntOrNull() ?: 0
                    val reminderId = (child.child("reminderId").value as? Number)?.toInt() ?: 0
                    val userId = child.child("userId").value as? String ?: return@forEach
                    val dateString = child.child("dateString").value as? String ?: return@forEach
                    val isCompleted = child.child("isCompleted").value as? Boolean ?: true
                    val payload = child.child("payload").value as? String
                    val timestamp = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
 
                    repository.insertCompletion(ReminderCompletion(id, reminderId, userId, dateString, isCompleted, payload, timestamp))
                }
            }
 
            val leavesSnap = snapshot.child("planned_leaves")
            repository.clearPlannedLeaves()
            if (leavesSnap.exists()) {
                leavesSnap.children.forEach { child ->
                    val idStr = child.key ?: return@forEach
                    val id = idStr.toIntOrNull() ?: 0
                    val userId = child.child("userId").value as? String ?: return@forEach
                    val userName = child.child("userName").value as? String ?: ""
                    val startDate = child.child("startDate").value as? String ?: return@forEach
                    val endDate = child.child("endDate").value as? String ?: return@forEach
                    val reason = child.child("reason").value as? String ?: ""
                    val status = child.child("status").value as? String ?: "APPROVED"
                    val timestamp = (child.child("timestamp").value as? Number)?.toLong() ?: System.currentTimeMillis()
 
                    repository.insertPlannedLeave(PlannedLeave(id, userId, userName, startDate, endDate, reason, status, timestamp))
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreFromCloud(context: Context, repository: ReminderRepository): Result<Unit> {
        val authOk = ensureAuthenticated(context)
        if (!authOk) {
            Log.w("FirebaseSyncHelper", "Dynamic Firebase authentication could not be completed, but proceeding with database read anyway.")
        }
        val app = getFirebaseInstance(context) ?: return Result.failure(Exception("Firebase is not initialized or configured."))
        val uid = FirebaseAuth.getInstance(app).currentUser?.uid
        val rootRef = getDatabaseReference(context) ?: return Result.failure(Exception("Firebase is not initialized or configured."))
        val ref = rootRef.child("shared_workspace")
        return try {
            val snapshot = ref.get().await()
            if (!snapshot.exists()) {
                return Result.failure(Exception("No backup payload found at configured reference paths."))
            }

            restoreFromSnapshot(context, snapshot, repository)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private var currentListener: ValueEventListener? = null
    private var currentRef: DatabaseReference? = null
    private val syncScope = CoroutineScope(Dispatchers.IO)

    fun startRealtimeSync(context: Context, repository: ReminderRepository) {
        // Remove old listener if any to avoid leaks / duplicates
        stopRealtimeSync()
        
        syncScope.launch {
            val authOk = ensureAuthenticated(context)
            if (!authOk) {
                Log.w("FirebaseSyncHelper", "Dynamic Firebase authentication could not be completed, but attempting real-time sync anyway.")
            }
            val app = getFirebaseInstance(context) ?: return@launch
            val uid = FirebaseAuth.getInstance(app).currentUser?.uid
            val rootRef = getDatabaseReference(context) ?: return@launch
            val ref = rootRef.child("shared_workspace")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) return
                    
                    val lastDevice = snapshot.child("last_backup_device_id").value as? String
                    if (lastDevice == deviceSessionId) {
                        Log.d("FirebaseSyncHelper", "Skipping real-time sync restore: Change was initiated by this device.")
                        return
                    }
                    
                    syncScope.launch {
                        try {
                            restoreFromSnapshot(context, snapshot, repository)
                            Log.d("FirebaseSyncHelper", "Realtime sync update applied successfully")
                        } catch (e: Exception) {
                            Log.e("FirebaseSyncHelper", "Error processing real-time cloud data snapshot: ${e.message}")
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w("FirebaseSyncHelper", "Realtime sync listener cancelled: ${error.message}")
                }
            }
            ref.addValueEventListener(listener)
            currentListener = listener
            currentRef = ref
            Log.i("FirebaseSyncHelper", "Realtime Sync Listener activated for configured URL.")
        }
    }

    fun stopRealtimeSync() {
        try {
            currentRef?.removeEventListener(currentListener ?: return)
        } catch (e: Exception) {
            Log.e("FirebaseSyncHelper", "Error removing event listener: ${e.message}")
        }
        currentListener = null
        currentRef = null
        Log.i("FirebaseSyncHelper", "Realtime Sync Listener deactivated.")
    }
}
