package com.mindbox.example

import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object NotificationStorage {

    //Don't use this approach in your app. This storage serves demonstration purpose only.
    private val scope = CoroutineScope(Dispatchers.IO)
    private val testNotifications = mutableListOf(
        MindboxRemoteMessage(
            "1",
            title = "First notification title",
            description = "First notification text",
            pushActions = emptyList(),
            "2",
            "testUrl",
            payload = "{ \"mobilePhoneNumber\":\"<Phone number>\",\"pushName\":\"<Push name>\",\"pushDate\":\"<Push date>\"}"
        ), MindboxRemoteMessage(
            "2",
            title = "Second notification title",
            description = "Second notification text",
            pushActions = emptyList(),
            "2",
            "testUrl",
            payload = "{ \"mobilePhoneNumber\":\"<Phone number>\",\"pushName\":\"<Push name>\",\"pushDate\":\"<Push date>\"}"
        ), MindboxRemoteMessage(
            "3",
            title = "Third notification title",
            description = "Third notification text",
            pushActions = emptyList(),
            "2",
            "testUrl",
            payload = "{ \"mobilePhoneNumber\":\"<Phone number>\",\"pushName\":\"<Push name>\",\"pushDate\":\"<Push date>\"}"
        )
    )

    val notifications: MutableList<MindboxRemoteMessage> = testNotifications
    val notificationsFlow = MutableSharedFlow<List<MindboxRemoteMessage>>(replay = 100)


    fun addNotification(notification: MindboxRemoteMessage) {
        notifications.add(notification)
        scope.launch {
            notificationsFlow.emit(notifications)
        }
    }

}

