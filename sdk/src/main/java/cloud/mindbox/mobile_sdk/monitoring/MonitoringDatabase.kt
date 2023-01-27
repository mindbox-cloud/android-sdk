package cloud.mindbox.mobile_sdk.monitoring

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [MonitoringEntity::class], version = 1)
internal abstract class MonitoringDatabase : RoomDatabase() {

    abstract fun monitoringDao(): MonitoringDao

}