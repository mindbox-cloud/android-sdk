package cloud.mindbox.mobile_sdk.monitoring.domain.managers

import android.content.Context
import cloud.mindbox.mobile_sdk.di.monitoringDatabaseName
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogStoringDataManager
import java.io.File

class LogStoringDataManagerImpl(private val context: Context) : LogStoringDataManager {

    override fun isDatabaseMemorySizeExceeded(): Boolean {
        val dbFolderPath = context.filesDir.absolutePath.replace("files", "databases")
        val dbFile = File("$dbFolderPath/$monitoringDatabaseName")

        if (!dbFile.exists()) throw Exception("${dbFile.absolutePath} doesn't exist")

        return dbFile.length() >= TEN_MEGABYTES_IN_BYTES
    }

    companion object {
        private const val TEN_MEGABYTES_IN_BYTES = 10 * 1024 * 1024
    }
}