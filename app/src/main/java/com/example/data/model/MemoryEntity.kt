package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memory_table")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val factText: String,
    val timestamp: Long = System.currentTimeMillis()
)
