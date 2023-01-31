package cloud.mindbox.mobile_sdk.monitoring.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity

@Database(entities = [MonitoringEntity::class], version = 1)
internal abstract class MonitoringDatabase : RoomDatabase() {

    abstract fun monitoringDao(): MonitoringDao

}