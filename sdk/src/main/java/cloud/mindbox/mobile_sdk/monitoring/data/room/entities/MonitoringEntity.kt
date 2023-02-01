package cloud.mindbox.mobile_sdk.monitoring.data.room.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = MonitoringEntity.TABLE_NAME)
internal data class MonitoringEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = COLUMN_ID)
    val id: Int,
    @ColumnInfo(name = COLUMN_TIMESTAMP)
    val time: String,
    @ColumnInfo(name = COLUMN_LOG)
    val log: String,
) {

    companion object {
        const val TABLE_NAME = "monitoring"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_LOG = "log"
    }
}