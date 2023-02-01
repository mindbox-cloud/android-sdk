package cloud.mindbox.mobile_sdk.monitoring.data.checkers

import android.content.Context
import cloud.mindbox.mobile_sdk.di.monitoringDatabaseName
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogStoringDataChecker
import java.io.File

class LogStoringDataCheckerImpl(private val context: Context) : LogStoringDataChecker {

    override fun isDatabaseMemorySizeExceeded(): Boolean {
        val dbFolderPath = context.filesDir.absolutePath.replace(
            "files",
            "databases"
        )
        val dbFile = File("$dbFolderPath/$monitoringDatabaseName")

        if (!dbFile.exists()) throw Exception("${dbFile.absolutePath} doesn't exist")

        return dbFile.length() >= MAX_LOG_SIZE
    }

    companion object {
        /**
         * Ten megabytes in bytes. It is used as the maximum size of database in memory.
         *
         **/
        private const val MAX_LOG_SIZE = 10 * 1024 * 1024

    }
}