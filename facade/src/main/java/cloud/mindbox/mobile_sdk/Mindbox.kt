package cloud.mindbox.mobile_sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.annotation.DrawableRes
import cloud.mindbox.mobile_sdk.models.MindboxError
import cloud.mindbox.mobile_sdk.models.operation.OperationBody
import cloud.mindbox.mobile_sdk_core.MindboxInternalCore
import cloud.mindbox.mobile_sdk_core.logger.Level
import cloud.mindbox.mobile_sdk_core.managers.*
import cloud.mindbox.mobile_sdk.models.operation.request.OperationBodyRequestBase
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponse
import cloud.mindbox.mobile_sdk.models.operation.response.OperationResponseBase
import cloud.mindbox.mobile_sdk_core.MindboxConfigurationInternal
import cloud.mindbox.mobile_sdk.models.SdkValidation
import cloud.mindbox.mobile_sdk_core.logger.MindboxLoggerInternal
import com.google.firebase.messaging.RemoteMessage
import java.util.*

object Mindbox {

    /**
     * Used for determination app open from push
     */
    const val IS_OPENED_FROM_PUSH_BUNDLE_KEY = MindboxInternalCore.IS_OPENED_FROM_PUSH_BUNDLE_KEY

    /**
     * Subscribe to gets token of Firebase Messaging Service used by SDK
     *
     * @param subscription - invocation function with FMS token
     * @return String identifier of subscription
     * @see disposeFmsTokenSubscription
     */
    fun subscribeFmsToken(
        subscription: (String?) -> Unit
    ): String = MindboxInternalCore.subscribeFmsToken(subscription)

    /**
     * Removes FMS token subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    fun disposeFmsTokenSubscription(
        subscriptionId: String
    ): Unit = MindboxInternalCore.disposeFmsTokenSubscription(subscriptionId)

    /**
     * Returns date of FMS token saving
     */
    fun getFmsTokenSaveDate(): String = MindboxInternalCore.getFmsTokenSaveDate()

    /**
     * Returns SDK version
     */
    fun getSdkVersion(): String = MindboxInternalCore.getSdkVersion()

    /**
     * Subscribe to gets deviceUUID used by SDK
     *
     * @param subscription - invocation function with deviceUUID
     * @return String identifier of subscription
     * @see disposeDeviceUuidSubscription
     */
    fun subscribeDeviceUuid(
        subscription: (String) -> Unit
    ): String = MindboxInternalCore.subscribeDeviceUuid(subscription)

    /**
     * Removes deviceUuid subscription if it is no longer necessary
     *
     * @param subscriptionId - identifier of the subscription to remove
     */
    fun disposeDeviceUuidSubscription(
        subscriptionId: String
    ): Unit = MindboxInternalCore.disposeDeviceUuidSubscription(subscriptionId)

    /**
     * Updates FMS token for SDK
     * Call it from onNewToken on messaging service
     *
     * @param context used to initialize the main tools
     * @param token - token of FMS
     */
    fun updateFmsToken(
        context: Context,
        token: String
    ): Unit = MindboxInternalCore.updateFmsToken(context, token)

    /**
     * Creates and deliveries event of "Push delivered". Recommended call this method from
     * background thread.
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     */
    fun onPushReceived(
        context: Context,
        uniqKey: String
    ): Unit = MindboxInternalCore.onPushReceived(context, uniqKey)

    /**
     * Creates and deliveries event of "Push clicked". Recommended call this method from background
     * thread.
     *
     * @param context used to initialize the main tools
     * @param uniqKey - unique identifier of push notification
     * @param buttonUniqKey - unique identifier of push notification button
     */
    fun onPushClicked(
        context: Context,
        uniqKey: String,
        buttonUniqKey: String?
    ): Unit = onPushClicked(context, uniqKey, buttonUniqKey)

    /**
     * Creates and deliveries event of "Push clicked".
     * Recommended to be used with Mindbox SDK pushes with [handleRemoteMessage] method.
     * Intent should contain "uniq_push_key" and "uniq_push_button_key" (optionally) in order to work correctly
     * Recommended call this method from background thread.
     *
     * @param context used to initialize the main tools
     * @param intent - intent recieved in app component
     *
     * @return true if Mindbox SDK recognises push intent as Mindbox SDK push intent
     *         false if Mindbox SDK cannot find critical information in intent
     */
    fun onPushClicked(
        context: Context,
        intent: Intent
    ): Boolean = MindboxInternalCore.onPushClicked(context, intent)


    /**
     * Initializes the SDK for further work.
     * We recommend calling it in onCreate on an application class
     *
     * @param context used to initialize the main tools
     * @param configuration contains the data that is needed to connect to the Mindbox
     */
    fun init(
        context: Context,
        configuration: MindboxConfiguration
    ) {
        val validatedConfiguration = validateConfiguration(configuration)
        MindboxInternalCore.init(context, validatedConfiguration)
    }

    /**
     * Send track visit event after link or push was clicked for [Activity] with launchMode equals
     * "singleTop" or "singleTask" or if a client used the [Intent.FLAG_ACTIVITY_SINGLE_TOP] or
     * [Intent.FLAG_ACTIVITY_NEW_TASK]
     * flag when calling {@link #startActivity}.
     *
     * @param intent new intent for activity, which was received in [Activity.onNewIntent] method
     */
    fun onNewIntent(intent: Intent?) = MindboxInternalCore.onNewIntent(intent)

    /**
     * Specifies log level for Mindbox
     *
     * @param level - is used for showing Mindbox logs starts from [Level]. Default
     * is [Level.INFO]. [Level.NONE] turns off all logs.
     */
    fun setLogLevel(level: Level): Unit = MindboxInternalCore.setLogLevel(level)

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBody [T] which extends [OperationBodyInternal] and will be send as event json body of operation.
     */
    @Deprecated("Used Mindbox.executeAsyncOperation with OperationBodyRequestBase")
    fun <T : OperationBody> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ): Unit = MindboxInternalCore.executeAsyncOperation(context, operationSystemName, operationBody)

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     */
    fun <T : OperationBodyRequestBase> executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T
    ): Unit = MindboxInternalCore.executeAsyncOperation(context, operationSystemName, operationBody)

    /**
     * Creates and deliveries event with specified name and body. Recommended call this method from
     * background thread.
     *
     * @param context current context is used
     * @param operationSystemName the name of asynchronous operation
     * @param operationBodyJson event json body of operation.
     */
    fun executeAsyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String
    ): Unit =
        MindboxInternalCore.executeAsyncOperation(context, operationSystemName, operationBodyJson)

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     * @param onSuccess Callback for response typed [OperationResponse] that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    fun <T : OperationBodyRequestBase> executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
        onSuccess: (OperationResponse) -> Unit,
        onError: (MindboxError) -> Unit
    ): Unit = MindboxInternalCore.executeSyncOperation(
        context = context,
        operationSystemName = operationSystemName,
        operationBody = operationBody,
        classOfV = OperationResponse::class.java,
        onSuccess = onSuccess,
        onError = {
            onError(MindboxError.fromInternal(it))
        }
    )

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBody [T] which extends [OperationBodyRequestBase] and will be send as event json body of operation.
     * @param classOfV Class type for response object.
     * @param onSuccess Callback for response typed [V] which extends [OperationResponseBase] that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    fun <T : OperationBodyRequestBase, V : OperationResponseBase> executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBody: T,
        classOfV: Class<V>,
        onSuccess: (V) -> Unit,
        onError: (MindboxError) -> Unit
    ): Unit = MindboxInternalCore.executeSyncOperation(
        context = context,
        operationSystemName = operationSystemName,
        operationBody = operationBody,
        classOfV = classOfV,
        onSuccess = onSuccess,
        onError = {
            onError(MindboxError.fromInternal(it))
        }
    )

    /**
     * Creates and deliveries event synchronously with specified name and body.
     *
     * @param context current context is used
     * @param operationSystemName the name of synchronous operation
     * @param operationBodyJson event json body of operation.
     * @param onSuccess Callback that will be invoked for success response to a given request.
     * @param onError Callback for response typed [MindboxError] and will be invoked for error response to a given request.
     */
    fun executeSyncOperation(
        context: Context,
        operationSystemName: String,
        operationBodyJson: String,
        onSuccess: (String) -> Unit,
        onError: (MindboxError) -> Unit
    ): Unit = MindboxInternalCore.executeSyncOperation(
        context = context,
        operationSystemName = operationSystemName,
        operationBodyJson = operationBodyJson,
        onSuccess = onSuccess,
        onError = {
            onError(MindboxError.fromInternal(it))
        }
    )

    /**
     * Handles only Mindbox notification message from [FirebaseMessagingService].
     *
     * @param context context used for Mindbox initializing and push notification showing
     * @param message the [RemoteMessage] received from Firebase
     * @param channelId the id of channel for Mindbox pushes
     * @param channelName the name of channel for Mindbox pushes
     * @param pushSmallIcon icon for push notification as drawable resource
     * @param channelDescription the description of channel for Mindbox pushes. Default is null
     * @param activities map (url mask) -> (Activity class). When clicked on push or button with url, corresponding activity will be opened
     *        Currently supports '*' character - indicator of zero or more numerical, alphabetic and punctuation characters
     *        e.g. mask "https://sample.com/" will match only "https://sample.com/" link
     *        whereas mask "https://sample.com/\u002A" will match
     *        "https://sample.com/", "https://sample.com/foo", "https://sample.com/foo/bar", "https://sample.com/foo?bar=baz" and other masks
     * @param defaultActivity default activity to be opened if url was not found in [activities]
     *
     * @return true if notification is Mindbox push and it's successfully handled, false otherwise.
     */
    fun handleRemoteMessage(
        context: Context,
        message: RemoteMessage?,
        channelId: String,
        channelName: String,
        @DrawableRes pushSmallIcon: Int,
        defaultActivity: Class<out Activity>,
        channelDescription: String? = null,
        activities: Map<String, Class<out Activity>>? = null
    ): Boolean = MindboxInternalCore.handleRemoteMessage(
        context,
        message,
        channelId,
        channelName,
        pushSmallIcon,
        defaultActivity,
        channelDescription,
        activities
    )

    /**
     * Retrieves url from intent generated by notification manager
     *
     * @param intent an intent sent by SDK and received in BroadcastReceiver
     * @return url associated with the push intent or null if there is none
     */
    fun getUrlFromPushIntent(intent: Intent?): String? =
        MindboxInternalCore.getUrlFromPushIntent(intent)

    private fun validateConfiguration(configuration: MindboxConfiguration): MindboxConfigurationInternal {
        val validationErrors = SdkValidation.validateConfiguration(
            domain = configuration.domain,
            endpointId = configuration.endpointId,
            previousDeviceUUID = configuration.previousDeviceUUID,
            previousInstallationId = configuration.previousInstallationId
        )

        return if (validationErrors.isEmpty()) {
            configuration
        } else {
            if (validationErrors.any(SdkValidation.Error::critical)) {
                throw InitializeMindboxException(validationErrors.toString())
            }
            MindboxLoggerInternal.e(this, "Invalid configuration parameters found: $validationErrors")
            val isDeviceIdError = validationErrors.contains(SdkValidation.Error.INVALID_DEVICE_ID)
            val isInstallationIdError = validationErrors.contains(SdkValidation.Error.INVALID_INSTALLATION_ID)
            configuration.copy(
                previousDeviceUUID = if (isDeviceIdError) "" else configuration.previousDeviceUUID,
                previousInstallationId = if (isInstallationIdError) "" else configuration.previousInstallationId
            )
        }
    }

}
