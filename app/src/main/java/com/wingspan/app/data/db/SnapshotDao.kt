package com.wingspan.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<SnapshotEntity>>

    @Query("SELECT * FROM snapshots ORDER BY timestampMs DESC")
    suspend fun getAll(): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun getById(id: Long): SnapshotEntity?

    @Insert
    suspend fun insert(s: SnapshotEntity): Long

    @Update
    suspend fun update(s: SnapshotEntity)

    @Query("DELETE FROM snapshots WHERE id = :id")
    suspend fun deleteById(id: Long)
}
