package cloud.mindbox.mobile_sdk.monitoring.data.checkers

import android.util.Log
import cloud.mindbox.mobile_sdk.monitoring.domain.interfaces.LogStoringDataChecker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class LogStoringDataCheckerImpl(private val dbFile: File) : LogStoringDataChecker {


    private var previousSize: Long? = null

    override fun isDatabaseMemorySizeExceeded(): Boolean {
        if (!dbFile.exists()) throw Exception("${dbFile.absolutePath} doesn't exist")
        val fileSize = dbFile.length()
        if (previousSize == null) previousSize = fileSize
        return if (needCleanLog.get()) {
            if (fileSize < MAX_LOG_SIZE || previousSize != fileSize) {
                needCleanLog.set(false)
                deletionIsInProgress.set(false)
                previousSize = fileSize
            }
            false
        } else {
            fileSize >= MAX_LOG_SIZE
        }
    }

    companion object {
        /**
         * Ten megabytes in bytes. It is used as the maximum size of database in memory.
         *
         **/
        var needCleanLog: AtomicBoolean = AtomicBoolean(false)
        var deletionIsInProgress: AtomicBoolean = AtomicBoolean(false)
        const val MAX_LOG_SIZE = 200 * 1024

    }
}