package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.MemoryEntity

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_table ORDER BY timestamp DESC")
    suspend fun getAllMemories(): List<MemoryEntity>

    @Insert
    suspend fun insertMemory(memory: MemoryEntity)

    @Query("DELETE FROM memory_table")
    suspend fun clearMemories()
}
