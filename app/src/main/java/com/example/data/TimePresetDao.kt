package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TimePresetDao {
    @Query("SELECT * FROM time_presets ORDER BY isCustom ASC, initialTimeMs ASC, id ASC")
    fun getAllPresetsFlow(): Flow<List<TimePreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: TimePreset): Long

    @Query("DELETE FROM time_presets WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustomPresetById(id: Int)
}
