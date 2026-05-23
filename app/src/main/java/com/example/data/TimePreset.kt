package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_presets")
data class TimePreset(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val initialTimeMs: Long,
    val incrementSeconds: Int = 0,
    val timingStyle: String = "NONE", // NONE, INCREMENT, DELAY
    val delaySeconds: Int = 0,
    val isCustom: Boolean = true
)
