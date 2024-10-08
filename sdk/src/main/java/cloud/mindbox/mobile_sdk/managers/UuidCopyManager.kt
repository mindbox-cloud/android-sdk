package cloud.mindbox.mobile_sdk.managers

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.ContextCompat
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import java.util.Date

internal object UuidCopyManager {
    private const val COPY_UUID_APP_OPEN_TIMES = 5
    private const val COPY_UUID_APP_OPEN_TIME_LIMIT = 10_000L

    // map of activity hash to list of activity start timestamps
    private val appOpenTimestampMap = HashMap<Int, ArrayList<Long>>()

    fun onAppMovedToForeground(activity: Activity) {
        val activityHash = activity.hashCode()
        appOpenTimestampMap[activityHash]?.let { activityOpenTimestampList ->
            activityOpenTimestampList.add(Date().time)
            appOpenTimestampMap.put(activityHash, activityOpenTimestampList)
        } ?: appOpenTimestampMap.put(activityHash, arrayListOf(Date().time))
        if (shouldCopyUuid(activityHash)) copyUuidToClipboard(activity.applicationContext)
    }

    private fun shouldCopyUuid(activityHash: Int): Boolean {
        val appOpenTimestampList = appOpenTimestampMap[activityHash] ?: return false
        if (!MindboxPreferences.uuidDebugEnabled) return false
        if (appOpenTimestampList.size < COPY_UUID_APP_OPEN_TIMES) return false
        val combinationStartTimestamp = appOpenTimestampList[
            appOpenTimestampList.size - COPY_UUID_APP_OPEN_TIMES
        ]
        val combinationEndTimestamp = appOpenTimestampList.last()
        return combinationStartTimestamp + COPY_UUID_APP_OPEN_TIME_LIMIT > combinationEndTimestamp
    }

    private fun copyUuidToClipboard(appContext: Context) {
        val uuid = MindboxPreferences.deviceUuid
        if (uuid.isNotEmpty()) {
            ContextCompat.getSystemService(appContext, ClipboardManager::class.java)
                ?.let { clipboardManager ->
                    val clip = ClipData.newPlainText(uuid, uuid)
                    clipboardManager.setPrimaryClip(clip)
                }
        }
    }
}
