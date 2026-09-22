package com.wingspan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY createdAt")
    fun observeAll(): Flow<List<ZoneEntity>>

    @Query("SELECT * FROM zones ORDER BY createdAt")
    suspend fun getAll(): List<ZoneEntity>

    @Query("SELECT * FROM zones WHERE id = :id")
    suspend fun getById(id: Long): ZoneEntity?

    @Insert
    suspend fun insert(zone: ZoneEntity): Long

    @Insert
    suspend fun insertAll(zones: List<ZoneEntity>)

    @Update
    suspend fun update(zone: ZoneEntity)

    @Query("DELETE FROM zones WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM zones")
    suspend fun deleteAll()
}
