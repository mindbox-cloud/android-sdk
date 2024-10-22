package cloud.mindbox.mobile_sdk.monitoring.data.room.dao

import androidx.room.*
import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity

@Dao
internal interface MonitoringDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: MonitoringEntity)

    @Transaction
    @Insert(onConflict = OnConflictStrategy.NONE)
    suspend fun insertLogs(entities: List<MonitoringEntity>)

    @Query("DELETE FROM ${MonitoringEntity.TABLE_NAME} WHERE id IN (SELECT id FROM ${MonitoringEntity.TABLE_NAME} ORDER BY id ASC LIMIT (SELECT CNT/10 FROM (SELECT COUNT(id) as CNT FROM ${MonitoringEntity.TABLE_NAME})))")
    suspend fun deleteFirstTenPercentOfLogs()

    @Delete
    suspend fun deleteLog(log: MonitoringEntity)

    @Query("DELETE FROM ${MonitoringEntity.TABLE_NAME} WHERE id IN (SELECT id FROM ${MonitoringEntity.TABLE_NAME} ORDER BY id ASC LIMIT 1)")
    suspend fun deleteFirstLog()

    @Query("SELECT * FROM ${MonitoringEntity.TABLE_NAME} WHERE timestamp BETWEEN :startTime and :endTime ORDER BY timestamp ASC")
    suspend fun getLogs(startTime: String, endTime: String): List<MonitoringEntity>

    @Query("SELECT * FROM ${MonitoringEntity.TABLE_NAME} ORDER BY id ASC LIMIT 1")
    suspend fun getFirstLog(): MonitoringEntity

    @Query("SELECT * FROM ${MonitoringEntity.TABLE_NAME} ORDER BY id DESC LIMIT 1")
    suspend fun getLastLog(): MonitoringEntity
}
