package cloud.mindbox.mobile_sdk.monitoring

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MonitoringDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: MonitoringEntity)

    @Query("SELECT * FROM monitoring WHERE timestamp BETWEEN :startInstant and :endInstant ORDER BY timestamp ASC")
    suspend fun getLogs(startInstant: Long, endInstant: Long): List<MonitoringEntity>
}