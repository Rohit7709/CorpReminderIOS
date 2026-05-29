package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String,
    val name: String,
    val role: String, // "EMPLOYEE", "ADMIN"
    val imageUrl: String? = null,
    val password: String = "password",
    val passwordCreated: Boolean = false,
    val question1: String? = null,
    val answer1: String? = null,
    val question2: String? = null,
    val answer2: String? = null,
    val question3: String? = null,
    val answer3: String? = null
)

