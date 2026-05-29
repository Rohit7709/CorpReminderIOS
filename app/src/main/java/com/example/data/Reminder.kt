package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val frequency: String, // "DAILY", "FRIDAY", "TUE_THU", "CUSTOM"
    val customDays: String? = null, // Comma separated day numbers e.g. "Tuesday,Thursday" or "2,4" 
    val targetUserId: String? = null, // null means "All Employees", else targets specific user ID
    val isSystemDefault: Boolean = false,
    val createdBy: String = "system",
    val createdAt: Long = System.currentTimeMillis(),
    val isRepetitive: Boolean = true,
    val startDay: String? = null
)
