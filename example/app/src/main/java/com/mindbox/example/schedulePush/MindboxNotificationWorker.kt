package com.mindbox.example.schedulePush

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.Level
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import com.google.gson.Gson
import com.mindbox.example.toMindboxRemoteMessage

internal class MindboxNotificationWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val data = inputData.keyValueMap.mapValues { it.value.toString() }
        val message = data.toMindboxRemoteMessage()
        Mindbox.writeLog("MindboxNotificationWorker try to show scheduled notification", Level.INFO)

        val result = handleMindboxRemoteMessage(applicationContext, message)
        return if (result) Result.success() else Result.failure()
    }
}
