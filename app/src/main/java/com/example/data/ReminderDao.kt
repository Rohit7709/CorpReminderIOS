package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    // Users Queries
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserByIdSynchronous(userId: String): User?

    // Reminders Queries
    @Query("SELECT * FROM reminders ORDER BY createdAt DESC")
    fun getAllReminders(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders")
    suspend fun getAllRemindersList(): List<Reminder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder): Long

    @Delete
    suspend fun deleteReminder(reminder: Reminder)

    // Completions Queries
    @Query("SELECT * FROM reminder_completions")
    fun getAllCompletions(): Flow<List<ReminderCompletion>>

    @Query("SELECT * FROM reminder_completions WHERE dateString = :dateString")
    fun getCompletionsByDate(dateString: String): Flow<List<ReminderCompletion>>

    @Query("SELECT * FROM reminder_completions WHERE userId = :userId AND dateString = :dateString")
    fun getCompletionsByContactAndDate(userId: String, dateString: String): Flow<List<ReminderCompletion>>

    @Query("SELECT * FROM reminder_completions WHERE userId = :userId AND dateString = :dateString")
    suspend fun getCompletionsByContactAndDateList(userId: String, dateString: String): List<ReminderCompletion>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: ReminderCompletion)

    @Query("DELETE FROM reminder_completions WHERE reminderId = :reminderId AND userId = :userId AND dateString = :dateString")
    suspend fun deleteSpecificCompletion(reminderId: Int, userId: String, dateString: String)

    @Query("DELETE FROM reminder_completions WHERE reminderId = :reminderId")
    suspend fun deleteCompletionsByReminderId(reminderId: Int)

    // Planned Leaves Queries
    @Query("SELECT * FROM planned_leaves ORDER BY timestamp DESC")
    fun getAllPlannedLeaves(): Flow<List<PlannedLeave>>

    @Query("SELECT * FROM planned_leaves WHERE userId = :userId ORDER BY timestamp DESC")
    fun getPlannedLeavesByUserId(userId: String): Flow<List<PlannedLeave>>

    @Query("SELECT * FROM planned_leaves WHERE userId = :userId")
    suspend fun getPlannedLeavesByUserIdList(userId: String): List<PlannedLeave>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlannedLeave(plannedLeave: PlannedLeave)

    @Delete
    suspend fun deletePlannedLeave(plannedLeave: PlannedLeave)

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()

    @Query("DELETE FROM reminders")
    suspend fun clearAllReminders()

    @Query("DELETE FROM reminder_completions")
    suspend fun clearAllCompletions()

    @Query("DELETE FROM planned_leaves")
    suspend fun clearAllPlannedLeaves()
}
