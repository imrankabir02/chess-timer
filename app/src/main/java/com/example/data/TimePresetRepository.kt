package com.example.data

import kotlinx.coroutines.flow.Flow

class TimePresetRepository(private val dao: TimePresetDao) {
    val allPresets: Flow<List<TimePreset>> = dao.getAllPresetsFlow()

    suspend fun insert(preset: TimePreset): Long {
        return dao.insertPreset(preset)
    }

    suspend fun deleteCustom(id: Int) {
        dao.deleteCustomPresetById(id)
    }
}
