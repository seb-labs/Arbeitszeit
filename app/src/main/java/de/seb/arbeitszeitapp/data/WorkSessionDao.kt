
package de.seb.arbeitszeitapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkSessionDao {
    @Query("SELECT * FROM work_sessions ORDER BY startTimestamp ASC")
    fun observeAllSessions(): Flow<List<WorkSessionEntity>>

    @Query("SELECT * FROM work_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): WorkSessionEntity?

    @Query("SELECT * FROM work_sessions ORDER BY startTimestamp ASC")
    suspend fun getAllSessions(): List<WorkSessionEntity>

    @Query(
        """
        SELECT * FROM work_sessions
        WHERE source = :source AND endTimestamp IS NULL
        ORDER BY startTimestamp DESC
        LIMIT 1
        """
    )
    suspend fun findOpenSessionBySource(source: SessionSource): WorkSessionEntity?

    @Query(
        """
        SELECT * FROM work_sessions
        WHERE id != :excludeId
        AND startTimestamp < :validationEnd
        AND COALESCE(endTimestamp, :validationEnd) > :validationStart
        ORDER BY startTimestamp ASC
        """
    )
    suspend fun findOverlappingSessions(
        validationStart: Long,
        validationEnd: Long,
        excludeId: Long = -1,
    ): List<WorkSessionEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: WorkSessionEntity): Long

    @Update
    suspend fun update(session: WorkSessionEntity)

    @Delete
    suspend fun delete(session: WorkSessionEntity)
}
