package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class ReminderRepository(private val reminderDao: ReminderDao) {

    val allUsers: Flow<List<User>> = reminderDao.getAllUsers()
    val allReminders: Flow<List<Reminder>> = reminderDao.getAllReminders()
    val allCompletions: Flow<List<ReminderCompletion>> = reminderDao.getAllCompletions()
    val allPlannedLeaves: Flow<List<PlannedLeave>> = reminderDao.getAllPlannedLeaves()

    fun getPlannedLeavesByUserId(userId: String): Flow<List<PlannedLeave>> =
        reminderDao.getPlannedLeavesByUserId(userId)

    suspend fun insertPlannedLeave(plannedLeave: PlannedLeave) = withContext(Dispatchers.IO) {
        reminderDao.insertPlannedLeave(plannedLeave)
    }

    suspend fun deletePlannedLeave(plannedLeave: PlannedLeave) = withContext(Dispatchers.IO) {
        reminderDao.deletePlannedLeave(plannedLeave)
    }

    fun getCompletionsByDate(dateString: String): Flow<List<ReminderCompletion>> =
        reminderDao.getCompletionsByDate(dateString)

    fun getCompletionsByContactAndDate(userId: String, dateString: String): Flow<List<ReminderCompletion>> =
        reminderDao.getCompletionsByContactAndDate(userId, dateString)

    suspend fun insertUser(user: User) = withContext(Dispatchers.IO) {
        reminderDao.insertUser(user)
    }

    suspend fun deleteUser(user: User) = withContext(Dispatchers.IO) {
        reminderDao.deleteUser(user)
    }

    suspend fun getUserById(userId: String): User? = withContext(Dispatchers.IO) {
        reminderDao.getUserByIdSynchronous(userId)
    }

    suspend fun insertReminder(reminder: Reminder): Long = withContext(Dispatchers.IO) {
        reminderDao.insertReminder(reminder)
    }

    suspend fun deleteReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        reminderDao.deleteReminder(reminder)
    }

    suspend fun insertCompletion(completion: ReminderCompletion) = withContext(Dispatchers.IO) {
        reminderDao.insertCompletion(completion)
    }

    suspend fun removeCompletion(reminderId: Int, userId: String, dateString: String) = withContext(Dispatchers.IO) {
        reminderDao.deleteSpecificCompletion(reminderId, userId, dateString)
    }

    suspend fun deleteCompletionsByReminderId(reminderId: Int) = withContext(Dispatchers.IO) {
        reminderDao.deleteCompletionsByReminderId(reminderId)
    }

    suspend fun clearUsers() = withContext(Dispatchers.IO) {
        reminderDao.clearAllUsers()
    }

    suspend fun clearReminders() = withContext(Dispatchers.IO) {
        reminderDao.clearAllReminders()
    }

    suspend fun clearCompletions() = withContext(Dispatchers.IO) {
        reminderDao.clearAllCompletions()
    }

    suspend fun clearPlannedLeaves() = withContext(Dispatchers.IO) {
        reminderDao.clearAllPlannedLeaves()
    }

    suspend fun prepopulateIfNeeded() = withContext(Dispatchers.IO) {
        // Seed default admin, lead and employee users if none exists
        val currentUsers = reminderDao.getAllUsers().first()
        if (currentUsers.isEmpty()) {
            val defaultAdmin = User(
                id = "admin",
                name = "System Admin",
                role = "ADMIN",
                password = "password"
            )
            reminderDao.insertUser(defaultAdmin)

            val defaultLead = User(
                id = "lead",
                name = "Sarah Connor (Lead)",
                role = "LEAD",
                password = "password"
            )
            reminderDao.insertUser(defaultLead)

            val defaultEmployee = User(
                id = "employee",
                name = "Charlie Sterling",
                role = "EMPLOYEE",
                password = "password"
            )
            reminderDao.insertUser(defaultEmployee)
        }

        // 2. Check & seed default system reminders as requested by the user prompt
        val currentReminders = reminderDao.getAllReminders().first()
        if (currentReminders.isEmpty()) {
            val systemReminders = listOf(
                Reminder(
                    title = "Fill Daily UX Timesheet",
                    description = "Log internal project hours daily.",
                    frequency = "DAILY",
                    isSystemDefault = true,
                    createdBy = "system"
                ),
                Reminder(
                    title = "Fill Client Timesheet",
                    description = "Submit client billing hours.",
                    frequency = "FRIDAY",
                    isSystemDefault = true,
                    createdBy = "system"
                ),
                Reminder(
                    title = "Update WFO Attendance Status",
                    description = "Declare office or remote work status.",
                    frequency = "DAILY",
                    isSystemDefault = true,
                    createdBy = "system"
                ),
                Reminder(
                    title = "Raise WFO Exceptions",
                    description = "Raise WFH Exception in the system",
                    frequency = "TUE_THU",
                    customDays = "Tuesday,Thursday",
                    isSystemDefault = true,
                    createdBy = "system"
                )
            )
            for (reminder in systemReminders) {
                reminderDao.insertReminder(reminder)
            }
        } else {
            // Update existing reminder to have the requested description
            val existingList = reminderDao.getAllRemindersList()
            val exceptionReminder = existingList.find { it.title == "Raise WFO Exceptions" }
            if (exceptionReminder != null && exceptionReminder.description != "Raise WFH Exception in the system") {
                val updated = exceptionReminder.copy(description = "Raise WFH Exception in the system")
                reminderDao.insertReminder(updated)
            }
        }
    }
}
