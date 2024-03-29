package cloud.mindbox.mobile_sdk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cloud.mindbox.mobile_sdk.managers.DbManager.CONFIGURATION_TABLE_NAME
import cloud.mindbox.mobile_sdk.models.Configuration
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ConfigurationsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(configuration: Configuration)

    @Query("SELECT * FROM $CONFIGURATION_TABLE_NAME ORDER BY configurationId DESC LIMIT 1")
    fun get(): Configuration

    @Query("SELECT * FROM $CONFIGURATION_TABLE_NAME ORDER BY configurationId DESC LIMIT 1")
    fun listenConfiguration(): Flow<Configuration>
}
