package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "planned_leaves")
data class PlannedLeave(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val userName: String,
    val startDate: String, // format "yyyy-MM-dd"
    val endDate: String, // format "yyyy-MM-dd"
    val reason: String,
    val status: String = "APPROVED", // "PENDING", "APPROVED", "REJECTED"
    val timestamp: Long = System.currentTimeMillis()
)
