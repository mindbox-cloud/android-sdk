package cloud.mindbox.mobile_sdk.monitoring.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import cloud.mindbox.mobile_sdk.monitoring.data.room.dao.MonitoringDao
import cloud.mindbox.mobile_sdk.monitoring.data.room.entities.MonitoringEntity

@Database(entities = [MonitoringEntity::class], exportSchema = false, version = 2)
internal abstract class MonitoringDatabase : RoomDatabase() {

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM monitoring")
                db.execSQL("ALTER TABLE monitoring RENAME TO mb_monitoring")
            }
        }
    }

    abstract fun monitoringDao(): MonitoringDao
}
