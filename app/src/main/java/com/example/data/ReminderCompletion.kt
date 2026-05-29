package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_completions")
data class ReminderCompletion(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reminderId: Int,
    val userId: String,
    val dateString: String, // format "yyyy-MM-dd"
    val isCompleted: Boolean = true,
    val payload: String? = null, // e.g., "Visiting Office", "Work from Home", orException reason
    val timestamp: Long = System.currentTimeMillis()
)
