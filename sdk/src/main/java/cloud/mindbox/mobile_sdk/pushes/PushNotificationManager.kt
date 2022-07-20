package cloud.mindbox.mobile_sdk.pushes

import android.app.*
import android.app.Notification.DEFAULT_ALL
import android.app.Notification.VISIBILITY_PRIVATE
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.pushes.handler.MessageHandlingState
import cloud.mindbox.mobile_sdk.pushes.handler.MindboxMessageHandler
import cloud.mindbox.mobile_sdk.pushes.handler.image.ImageRetryStrategy
import cloud.mindbox.mobile_sdk.services.BackgroundWorkManager
import cloud.mindbox.mobile_sdk.utils.Generator
import cloud.mindbox.mobile_sdk.utils.LoggingExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal object PushNotificationManager {

    private const val EXTRA_NOTIFICATION_ID = "notification_id"
    private const val EXTRA_URL = "push_url"
    private const val EXTRA_UNIQ_PUSH_KEY = "uniq_push_key"
    private const val EXTRA_UNIQ_PUSH_BUTTON_KEY = "uniq_push_button_key"
    private const val EXTRA_PAYLOAD = "push_payload"

    private const val MAX_ACTIONS_COUNT = 3

    internal var messageHandler: MindboxMessageHandler = MindboxMessageHandler()

    internal fun isNotificationsEnabled(
        context: Context,
    ): Boolean = LoggingExceptionHandler.runCatching(defaultValue = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (manager?.areNotificationsEnabled() != true) {
                return@runCatching false
            }
            manager.notificationChannels
                .firstOrNull { it.importance == NotificationManager.IMPORTANCE_NONE } == null
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isNotificationActive(
        notificationManager: NotificationManager,
        notificationId: Int,
    ): Boolean = LoggingExceptionHandler.runCatching(
        defaultValue = false,
    ) {
        notificationManager.activeNotifications.find { it.id == notificationId } != null
    }

    internal suspend fun handleRemoteMessage(
        context: Context,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        channelDescription: String?,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ): Boolean = LoggingExceptionHandler.runCatchingSuspending(defaultValue = false) {
        tryNotifyRemoteMessage(
            notificationId = Generator.generateUniqueInt(),
            context = context,
            remoteMessage = remoteMessage,
            channelId = channelId,
            channelName = channelName,
            pushSmallIcon = pushSmallIcon,
            channelDescription = channelDescription,
            activities = activities,
            defaultActivity = defaultActivity,
            state = MessageHandlingState(
                attemptNumber = 1,
                isMessageDisplayed = false,
            ),
        )
    }

    internal suspend fun tryNotifyRemoteMessage(
        notificationId: Int,
        context: Context,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        channelDescription: String?,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
        state: MessageHandlingState,
    ): Boolean = LoggingExceptionHandler.runCatchingSuspending(defaultValue = false) {
        MindboxLoggerImpl.d(
            parent = this,
            message = "Notify message ${remoteMessage.uniqueKey} started with state $state",
        )
        val applicationContext = context.applicationContext

        val uniqueKey = remoteMessage.uniqueKey
        Mindbox.onPushReceived(applicationContext, uniqueKey)

        val notificationManager: NotificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (isNotificationCancelled(notificationManager, notificationId, state)) {
            MindboxLoggerImpl.d(
                parent = this,
                message = "Notify message ${remoteMessage.uniqueKey}: An attempt to update " +
                        "the notification was canceled because the notification was canceled",
            )
            return@runCatchingSuspending true
        }

        val image = withContext(Dispatchers.IO) {
            runCatching {
                messageHandler.imageLoader.onLoadImage(
                    context = context,
                    message = remoteMessage,
                    state = state,
                )
            }
        }

        if (isNotificationCancelled(notificationManager, notificationId, state)) {
            MindboxLoggerImpl.d(
                parent = this,
                message = "Notify message ${remoteMessage.uniqueKey}: An attempt to update " +
                        "the notification was canceled because the notification was canceled",
            )
            return@runCatchingSuspending true
        }

        val fallback = image.exceptionOrNull()?.let { error ->
            MindboxLoggerImpl.e(
                parent = this,
                message = "Notify message ${remoteMessage.uniqueKey}: Image loading failed",
                exception = error,
            )
            messageHandler.imageFailureHandler.onImageLoadingFailed(
                context = context,
                message = remoteMessage,
                state = state,
                error = error,
            ).also {
                MindboxLoggerImpl.d(
                    parent = this,
                    message = "Notify message ${remoteMessage.uniqueKey}: Solution for failed " +
                            "image loading - $it",
                )
            }
        }

        if (fallback is ImageRetryStrategy.ApplyDefaultAndRetry && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            MindboxLoggerImpl.e(
                parent = this,
                message = "ShowAndRetry works correctly only on SDK >= 23",
            )
        }

        when (fallback) {
            is ImageRetryStrategy.Retry -> retryNotifyRemoteMessage(
                context = context,
                notificationId = notificationId,
                remoteMessage = remoteMessage,
                channelId = channelId,
                channelName = channelName,
                pushSmallIcon = pushSmallIcon,
                channelDescription = channelDescription,
                activities = activities,
                defaultActivity = defaultActivity,
                state = state,
                delay = fallback.delay,
            )
            is ImageRetryStrategy.Cancel -> {}
            is ImageRetryStrategy.ApplyDefaultAndRetry -> applyDefaultAndRetryNotifyRemoteMessage(
                context = applicationContext,
                notificationManager = notificationManager,
                remoteMessage = remoteMessage,
                channelId = channelId,
                channelName = channelName,
                channelDescription = channelDescription,
                notificationId = notificationId,
                pushSmallIcon = pushSmallIcon,
                activities = activities,
                defaultActivity = defaultActivity,
                delay = fallback.delay,
                imagePlaceholder = fallback.defaultImage,
                currentState = state,
            )
            is ImageRetryStrategy.ApplyDefault -> applyDefaultNotifyRemoteMessage(
                context = applicationContext,
                notificationManager = notificationManager,
                remoteMessage = remoteMessage,
                channelId = channelId,
                channelName = channelName,
                channelDescription = channelDescription,
                notificationId = notificationId,
                pushSmallIcon = pushSmallIcon,
                activities = activities,
                defaultActivity = defaultActivity,
                imagePlaceholder = fallback.defaultImage,
            )
            null -> {
                notifyRemoteMessage(
                    context = applicationContext,
                    notificationManager = notificationManager,
                    remoteMessage = remoteMessage,
                    channelId = channelId,
                    channelName = channelName,
                    channelDescription = channelDescription,
                    notificationId = notificationId,
                    pushSmallIcon = pushSmallIcon,
                    activities = activities,
                    defaultActivity = defaultActivity,
                    image = image.getOrNull(),
                )
                MindboxLoggerImpl.d(
                    parent = this,
                    message = "Notify message ${remoteMessage.uniqueKey}: successfully notified",
                )
            }
        }

        true
    }

    private fun isNotificationCancelled(
        notificationManager: NotificationManager,
        notificationId: Int,
        state: MessageHandlingState,
    ) = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            state.attemptNumber > 1 &&
            state.isMessageDisplayed &&
            !isNotificationActive(notificationManager, notificationId)

    private fun retryNotifyRemoteMessage(
        context: Context,
        notificationId: Int,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        pushSmallIcon: Int,
        channelDescription: String?,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
        state: MessageHandlingState,
        delay: Long,
    ) = BackgroundWorkManager.startNotificationWork(
        context = context,
        notificationId = notificationId,
        remoteMessage = remoteMessage,
        channelId = channelId,
        channelName = channelName,
        pushSmallIcon = pushSmallIcon,
        channelDescription = channelDescription,
        activities = activities,
        defaultActivity = defaultActivity,
        delay = delay,
        state = state,
    )

    private fun applyDefaultAndRetryNotifyRemoteMessage(
        context: Context,
        notificationManager: NotificationManager,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        channelDescription: String?,
        notificationId: Int,
        pushSmallIcon: Int,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
        delay: Long,
        imagePlaceholder: Bitmap?,
        currentState: MessageHandlingState,
    ) {
        createNotificationChannel(
            notificationManager = notificationManager,
            channelId = channelId,
            channelName = channelName,
            channelDescription = channelDescription,
        )
        val notification = buildNotification(
            context = context,
            notificationId = notificationId,
            uniqueKey = remoteMessage.uniqueKey,
            title = remoteMessage.title,
            text = remoteMessage.description,
            pushActions = remoteMessage.pushActions,
            pushLink = remoteMessage.pushLink,
            payload = remoteMessage.payload,
            image = imagePlaceholder,
            channelId = channelId,
            pushSmallIcon = pushSmallIcon,
            activities = activities,
            defaultActivity = defaultActivity,
        )
        notificationManager.notify(notificationId, notification)
        BackgroundWorkManager.startNotificationWork(
            context = context,
            notificationId = notificationId,
            remoteMessage = remoteMessage,
            channelId = channelId,
            channelName = channelName,
            pushSmallIcon = pushSmallIcon,
            channelDescription = channelDescription,
            activities = activities,
            defaultActivity = defaultActivity,
            delay = delay,
            state = currentState.copy(isMessageDisplayed = true),
        )
    }

    private fun applyDefaultNotifyRemoteMessage(
        context: Context,
        notificationManager: NotificationManager,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        channelDescription: String?,
        notificationId: Int,
        pushSmallIcon: Int,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
        imagePlaceholder: Bitmap?,
    ) {
        createNotificationChannel(
            notificationManager = notificationManager,
            channelId = channelId,
            channelName = channelName,
            channelDescription = channelDescription,
        )
        val notification = buildNotification(
            context = context,
            notificationId = notificationId,
            uniqueKey = remoteMessage.uniqueKey,
            title = remoteMessage.title,
            text = remoteMessage.description,
            pushActions = remoteMessage.pushActions,
            pushLink = remoteMessage.pushLink,
            payload = remoteMessage.payload,
            image = imagePlaceholder,
            channelId = channelId,
            pushSmallIcon = pushSmallIcon,
            activities = activities,
            defaultActivity = defaultActivity,
        )
        notificationManager.notify(notificationId, notification)
    }

    private fun notifyRemoteMessage(
        context: Context,
        notificationManager: NotificationManager,
        remoteMessage: RemoteMessage,
        channelId: String,
        channelName: String,
        channelDescription: String?,
        notificationId: Int,
        pushSmallIcon: Int,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
        image: Bitmap?,
    ) {
        createNotificationChannel(
            notificationManager = notificationManager,
            channelId = channelId,
            channelName = channelName,
            channelDescription = channelDescription,
        )
        val notification = buildNotification(
            context = context,
            notificationId = notificationId,
            uniqueKey = remoteMessage.uniqueKey,
            title = remoteMessage.title,
            text = remoteMessage.description,
            pushActions = remoteMessage.pushActions,
            pushLink = remoteMessage.pushLink,
            payload = remoteMessage.payload,
            image = image,
            channelId = channelId,
            pushSmallIcon = pushSmallIcon,
            activities = activities,
            defaultActivity = defaultActivity,
        )
        notificationManager.notify(notificationId, notification)
    }

    private fun buildNotification(
        context: Context,
        notificationId: Int,
        uniqueKey: String,
        title: String,
        text: String,
        pushActions: List<PushAction>,
        pushLink: String?,
        payload: String?,
        image: Bitmap?,
        channelId: String,
        @DrawableRes pushSmallIcon: Int,
        activities: Map<String, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ): Notification {
        val correctedLinksActivities = activities?.mapKeys { (key, _) ->
            key.replace("*", ".*").toRegex()
        }
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(pushSmallIcon)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(DEFAULT_ALL)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .handlePushClick(
                context = context,
                notificationId = notificationId,
                uniqueKey = uniqueKey,
                payload = payload,
                pushLink = pushLink,
                activities = correctedLinksActivities,
                defaultActivity = defaultActivity,
            )
            .handleActions(
                context = context,
                notificationId = notificationId,
                uniqueKey = uniqueKey,
                payload = payload,
                pushActions = pushActions,
                activities = correctedLinksActivities,
                defaultActivity = defaultActivity,
            )
            .setNotificationStyle(
                image = image,
                title = title,
                text = text,
            )
            .build()
    }

    internal fun getUniqKeyFromPushIntent(
        intent: Intent,
    ) = intent.getStringExtra(EXTRA_UNIQ_PUSH_KEY)

    internal fun getUniqPushButtonKeyFromPushIntent(
        intent: Intent,
    ) = intent.getStringExtra(EXTRA_UNIQ_PUSH_BUTTON_KEY)

    internal fun getUrlFromPushIntent(intent: Intent) = intent.getStringExtra(EXTRA_URL)

    internal fun getPayloadFromPushIntent(intent: Intent) = intent.getStringExtra(EXTRA_PAYLOAD)

    private fun createNotificationChannel(
        notificationManager: NotificationManager,
        channelId: String,
        channelName: String,
        channelDescription: String?,
    ) = LoggingExceptionHandler.runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                channelDescription.let { description = it }
                lockscreenVisibility = VISIBILITY_PRIVATE
            }

            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createPendingIntent(
        context: Context,
        activity: Class<out Activity>,
        id: Int,
        payload: String?,
        pushKey: String,
        url: String?,
        pushButtonKey: String? = null,
    ): PendingIntent? = LoggingExceptionHandler.runCatching(defaultValue = null) {
        val intent = getIntent(
            context = context,
            activity = activity,
            id = id,
            payload = payload,
            pushKey = pushKey,
            url = url,
            pushButtonKey = pushButtonKey,
        )

        val flags = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        PendingIntent.getActivity(
            context,
            Random.nextInt(),
            intent,
            flags,
        )
    }

    private fun NotificationCompat.Builder.handlePushClick(
        context: Context,
        notificationId: Int,
        uniqueKey: String,
        payload: String?,
        pushLink: String?,
        activities: Map<Regex, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ) = apply {
        val activity = resolveActivity(activities, pushLink, defaultActivity)
        createPendingIntent(
            context = context,
            activity = activity,
            id = notificationId,
            payload = payload,
            pushKey = uniqueKey,
            url = pushLink,
        )?.let(this::setContentIntent)
    }

    private fun NotificationCompat.Builder.handleActions(
        context: Context,
        notificationId: Int,
        uniqueKey: String,
        payload: String?,
        pushActions: List<PushAction>,
        activities: Map<Regex, Class<out Activity>>?,
        defaultActivity: Class<out Activity>,
    ) = apply {
        runCatching {
            pushActions.take(MAX_ACTIONS_COUNT).forEach { pushAction ->
                val activity = resolveActivity(activities, pushAction.url, defaultActivity)
                createPendingIntent(
                    context = context,
                    activity = activity,
                    id = notificationId,
                    pushKey = uniqueKey,
                    payload = payload,
                    url = pushAction.url,
                    pushButtonKey = pushAction.uniqueKey,
                )?.let { addAction(0, pushAction.text ?: "", it) }
            }
        }
    }

    private fun resolveActivity(
        activities: Map<Regex, Class<out Activity>>?,
        link: String?,
        defaultActivity: Class<out Activity>,
    ): Class<out Activity> {
        val key = link?.let { activities?.keys?.find { it.matches(link) } }
        return activities?.get(key) ?: defaultActivity
    }

    private fun NotificationCompat.Builder.setNotificationStyle(
        image: Bitmap?,
        title: String,
        text: String?,
    ) = apply {
        LoggingExceptionHandler.runCatching(
            block = {
                if (image != null) {
                    setImage(image, title, text)
                } else {
                    setText(text)
                }
            },
            defaultValue = { setText(text) }
        )
    }

    private fun NotificationCompat.Builder.setImage(
        imageBitmap: Bitmap,
        title: String,
        text: String?,
    ): NotificationCompat.Builder {
        setLargeIcon(imageBitmap)

        val style = NotificationCompat.BigPictureStyle()
            .bigPicture(imageBitmap)
            .bigLargeIcon(null)
            .setBigContentTitle(title)
        text?.let(style::setSummaryText)

        return setStyle(style)
    }

    private fun NotificationCompat.Builder.setText(
        text: String?,
    ) = LoggingExceptionHandler.runCatching {
        setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(text),
        )
    }

    private fun getIntent(
        context: Context,
        activity: Class<*>,
        id: Int,
        payload: String?,
        pushKey: String,
        url: String?,
        pushButtonKey: String?,
    ) = Intent(context, activity).apply {
        putExtra(EXTRA_PAYLOAD, payload)
        putExtra(Mindbox.IS_OPENED_FROM_PUSH_BUNDLE_KEY, true)
        putExtra(EXTRA_NOTIFICATION_ID, id)
        putExtra(EXTRA_UNIQ_PUSH_KEY, pushKey)
        putExtra(EXTRA_UNIQ_PUSH_BUTTON_KEY, pushButtonKey)
        url?.let { url -> putExtra(EXTRA_URL, url) }
        `package` = context.packageName
    }

}
