package com.mindbox.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

object NotificationStorage {

    //Don't use this approach in your app. This storage serves demonstration purpose only.
    private val scope = CoroutineScope(Dispatchers.IO)
    private val testNotifications = listOf(
        ClientPush(
            messageId = "Push unique key: 1",
            title = "First notification title",
            text = "First notification text",
            buttons = emptyList(),
            clickUrl = "Link from push 1",
            imageUrl = "https://mobpush-images.mindbox.ru/Mpush-test/1a73ebaa-3e5f-49f4-ae6c-462c9b64d34c/307be696-77e6-4d83-b7eb-c94be85f7a03.png",
            payload = "{\"pushName\":\"<Push name>\",\"pushDate\":\"<Push date>\"}"
        ), ClientPush(
            messageId = "Push unique key: 2",
            title = "Second notification title",
            text = "Second notification text",
            buttons = emptyList(),
            clickUrl = "Link from push 2",
            imageUrl = "https://mobpush-images.mindbox.ru/Mpush-test/1a73ebaa-3e5f-49f4-ae6c-462c9b64d34c/2397fea9-383d-49bf-a6a0-181a267faa94.png",
            payload = "{\"pushName\":\"<Push name>\",\"pushDate\":\"<Push date>\"}"
        ), ClientPush(
            messageId = "Push unique key: 3",
            title = "Third notification title",
            text = "Third notification text",
            buttons = emptyList(),
            clickUrl = "Link from push: 3",
            imageUrl = "https://mobpush-images.mindbox.ru/Mpush-test/1a73ebaa-3e5f-49f4-ae6c-462c9b64d34c/bd4250b1-a7ac-4b8a-b91b-481b3b5c565c.png",
            payload = "{\"pushName\":\"<Push name>\",\"pushDate\":\"<Push date>\"}"
        )
    )

    var notifications: List<ClientPush> = testNotifications
    val notificationsFlow = MutableSharedFlow<List<ClientPush>>(replay = 100)


    fun addNotification(notification: ClientPush) {
        notifications = notifications + notification
        scope.launch {
            notificationsFlow.emit(notifications)
        }
    }

}

