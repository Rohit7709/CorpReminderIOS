package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.MainActivity
import com.example.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        // Reset alarms on reboot to keep background checker alive!
        if (Intent.ACTION_BOOT_COMPLETED == action) {
            NotificationHelper.scheduleRepeatingCheck(context)
            return
        }

        // Handle the check reminders intent action
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                checkAndNotifyIncompletedTasks(context)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun checkAndNotifyIncompletedTasks(context: Context) {
        if (MainActivity.isAppInForeground) {
            android.util.Log.d("ReminderReceiver", "App in foreground. Suppressing status bar notification checks.")
            return
        }

        val prefs = context.getSharedPreferences("CorpRemind_Prefs", Context.MODE_PRIVATE)
        // Check for active login, falling back to the saved user ID when logged out to allow hourly background notifications
        val lastUserId = prefs.getString("last_logged_in_user_id", null) 
            ?: prefs.getString("pref_saved_user_id", null) 
            ?: return
        
        val db = AppDatabase.getDatabase(context)
        val dao = db.reminderDao()

        // Get user details
        val user = dao.getUserByIdSynchronous(lastUserId) ?: return
        
        // Admins don't have personal task reminders, only employees do
        if (user.role == "ADMIN") return

        // Get current day name and date string
        val cal = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEEE", Locale.US)
        val dayName = dayFormat.format(cal.time)

        val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        if (!daysOfWeek.contains(dayName)) return

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val todayDateStr = sdf.format(cal.time)

        // Check if today is a public holiday
        val isHoliday = try {
            val jsonString = context.assets.open("holidays.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonString)
            var foundHoliday = false
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val holidayDate = obj.optString("date", "")
                if (holidayDate == todayDateStr) {
                    foundHoliday = true
                    break
                }
            }
            foundHoliday
        } catch (e: Exception) {
            false
        }

        if (isHoliday) {
            android.util.Log.d("ReminderReceiver", "Today is a corporate holiday ($todayDateStr). Skipping reminders.")
            return
        }

        // Check if the user is on leave today
        val userLeaves = dao.getPlannedLeavesByUserIdList(user.id)
        val isOnLeave = userLeaves.any { leave ->
            todayDateStr >= leave.startDate && todayDateStr <= leave.endDate
        }

        if (isOnLeave) {
            android.util.Log.d("ReminderReceiver", "User ${user.name} is on planned leave today ($todayDateStr). Skipping reminders.")
            return
        }

        // 1. Fetch total reminders
        val reminders = dao.getAllRemindersList()

        // 2. Filter active reminders for today's day of week
        val activeReminders = reminders.filter { reminder ->
            val userMatches = reminder.targetUserId.isNullOrEmpty() || reminder.targetUserId.split(",").map { it.trim() }.contains(user.id)
            if (!userMatches) return@filter false

            when (reminder.frequency) {
                "DAILY" -> dayName != "Saturday" && dayName != "Sunday"
                "FRIDAY" -> dayName == "Friday"
                "TUE_THU" -> dayName == "Tuesday" || dayName == "Thursday"
                "CUSTOM" -> {
                    val targetDays = reminder.customDays?.split(",")?.map { it.trim().lowercase() } ?: emptyList()
                    targetDays.contains(dayName.lowercase())
                }
                "ONETIME" -> {
                    reminder.startDay == todayDateStr || reminder.startDay == dayName
                }
                else -> false
            }
        }

        if (activeReminders.isEmpty()) return

        // 3. Fetch completions for today's date
        val completions = dao.getCompletionsByContactAndDateList(user.id, todayDateStr)
        val completedIds = completions.map { it.reminderId }.toSet()

        // 4. Determine incomplete tasks
        val pendingTasks = activeReminders.filter { !completedIds.contains(it.id) }

        // 5. Send notifications
        val requiredVersion = prefs.getInt("required_app_version", 1)
        val localVersion = prefs.getInt("local_app_version", 1)
        if (localVersion < requiredVersion) {
            NotificationHelper.sendNativeNotification(
                context = context,
                id = 999124,
                title = "Critical: Update Corporate Task App",
                message = "An urgent update is available. Please update the application setup immediately."
            )
            // Re-post notification/check every 5 minutes exclusively for this task!
            NotificationHelper.scheduleCheck(context, 5 * 60 * 1000L)
        }

        // Post the normal outstanding tasks notification if any exist
        val generalPendingTasks = pendingTasks.filter { it.title != "Update Corporate Task App" }
        if (generalPendingTasks.isNotEmpty()) {
            val taskCount = generalPendingTasks.size
            val mainTaskTitle = generalPendingTasks.first().title
            
            val notificationTitle = "Alert: $taskCount Outstanding Task${if (taskCount > 1) "s" else ""}"
            val notificationContent = if (taskCount == 1) {
                "You have 1 pending task today: $mainTaskTitle. Tap to complete!"
            } else {
                "Pending: $mainTaskTitle and ${taskCount - 1} other task${if (taskCount > 2) "s" else ""}. Tap to log!"
            }

            // Post the system notification
            NotificationHelper.sendNativeNotification(
                context = context,
                id = 999123,
                title = notificationTitle,
                message = notificationContent
            )
        }
    }
}
