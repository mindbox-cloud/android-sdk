package cloud.mindbox.mobile_sdk.monitoring

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface MonitoringDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(entity: MonitoringEntity)

    @Query("SELECT * FROM monitoring ORDER BY timestamp ASC")
    suspend fun getLogs(): List<MonitoringEntity>
}