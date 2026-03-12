package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import cloud.mindbox.mobile_sdk.BuildConfig
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.PermissionStatus
import cloud.mindbox.mobile_sdk.models.Configuration
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import cloud.mindbox.mobile_sdk.utils.Constants
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Locale
import kotlin.math.roundToInt
import android.content.res.Configuration as UiConfiguration

internal class DataCollector(
    private val appContext: Context,
    private val sessionStorageManager: SessionStorageManager,
    private val permissionManager: PermissionManager,
    private val configuration: Configuration,
    private val params: Map<String, String>,
    private val inAppInsets: InAppInsets,
    private val gson: Gson,
    private val inAppId: String,
) {

    private val providers: MutableMap<String, Provider> by lazy {
        mutableMapOf<String, Provider>(
            KEY_DEVICE_UUID to Provider.string(MindboxPreferences.deviceUuid),
            KEY_ENDPOINT_ID to Provider.string(configuration.endpointId),
            KEY_FIRST_INITIALIZATION_TIME to Provider.string(MindboxPreferences.firstInitializationTime),
            KEY_IN_APP_ID to Provider.string(inAppId),
            KEY_INSETS to createInsetsPayload(inAppInsets),
            KEY_LOCALE to Provider.string(resolveLocale()),
            KEY_OPERATION_NAME to Provider.string((sessionStorageManager.inAppTriggerEvent as? InAppEventType.OrdinalEvent)?.name),
            KEY_OPERATION_BODY to Provider.string((sessionStorageManager.inAppTriggerEvent as? InAppEventType.OrdinalEvent)?.body),
            KEY_PERMISSIONS to createPermissionsPayload(),
            KEY_PLATFORM to Provider.string(VALUE_PLATFORM),
            KEY_SDK_VERSION to Provider.string(BuildConfig.VERSION_NAME),
            KEY_SDK_VERSION_NUMERIC to Provider.string(Constants.SDK_VERSION_NUMERIC.toString()),
            KEY_THEME to Provider.string(resolveTheme()),
            KEY_TRACK_VISIT_SOURCE to Provider.string(sessionStorageManager.lastTrackVisitData?.source),
            KEY_TRACK_VISIT_REQUEST_URL to Provider.string(sessionStorageManager.lastTrackVisitData?.requestUrl),
            KEY_USER_VISIT_COUNT to Provider.string(MindboxPreferences.userVisitCount.toString()),
            KEY_VERSION to Provider.string(configuration.versionName),
        ).apply {
            params.forEach { (key, value) ->
                put(key, Provider.string(value))
            }
        }
    }

    companion object Companion {
        private const val KEY_DEVICE_UUID = "deviceUUID"
        private const val KEY_ENDPOINT_ID = "endpointId"
        private const val KEY_FIRST_INITIALIZATION_TIME = "firstInitializationDateTime"
        private const val KEY_IN_APP_ID = "inAppId"
        private const val KEY_INSETS = "insets"
        private const val KEY_LOCALE = "locale"
        private const val KEY_OPERATION_BODY = "operationBody"
        private const val KEY_OPERATION_NAME = "operationName"
        private const val KEY_PERMISSIONS = "permissions"
        private const val KEY_PERMISSIONS_STATUS = "status"
        private const val KEY_PERMISSIONS_CAMERA = "camera"
        private const val KEY_PERMISSIONS_LOCATION = "location"
        private const val KEY_PERMISSIONS_MICROPHONE = "microphone"
        private const val KEY_PERMISSIONS_NOTIFICATIONS = "notifications"
        private const val KEY_PERMISSIONS_PHOTO_LIBRARY = "photoLibrary"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_SDK_VERSION = "sdkVersion"
        private const val KEY_SDK_VERSION_NUMERIC = "sdkVersionNumeric"
        private const val KEY_THEME = "theme"
        private const val KEY_TRACK_VISIT_SOURCE = "trackVisitSource"
        private const val KEY_TRACK_VISIT_REQUEST_URL = "trackVisitRequestUrl"
        private const val KEY_USER_VISIT_COUNT = "userVisitCount"
        private const val KEY_VERSION = "version"
        private const val VALUE_PLATFORM = "android"
        private const val VALUE_THEME_DARK = "dark"
        private const val VALUE_THEME_LIGHT = "light"
    }

    internal fun interface Provider {
        fun get(): JsonElement?

        companion object {
            fun string(value: String?) = Provider {
                if (value.isNullOrBlank()) return@Provider null
                JsonPrimitive(value)
            }

            fun number(value: Number) = Provider {
                JsonPrimitive(value)
            }

            fun objectIntParams(vararg pairs: Pair<String, Int>) = Provider {
                JsonObject().apply {
                    pairs.forEach { (key, value) ->
                        addProperty(key, value)
                    }
                }
            }

            fun objectStringParams(vararg pairs: Pair<String, String?>) = Provider {
                JsonObject().apply {
                    pairs.forEach { (key, value) ->
                        addProperty(key, value)
                    }
                }
            }

            fun objectStringParams(map: Map<String, String>) = Provider {
                JsonObject().apply {
                    map.forEach { (key, value) ->
                        addProperty(key, value)
                    }
                }
            }
        }
    }

    internal fun get(): String {
        val payload = JsonObject()
        providers.forEach { (key, provider) ->
            provider.get()?.let { value ->
                payload.add(key, value)
            }
        }
        return gson.toJson(payload)
    }

    private fun createPermissionsPayload(): Provider {
        val map = mapOf(
            KEY_PERMISSIONS_CAMERA to permissionManager.getCameraPermissionStatus().value,
            KEY_PERMISSIONS_LOCATION to permissionManager.getLocationPermissionStatus().value,
            KEY_PERMISSIONS_MICROPHONE to permissionManager.getMicrophonePermissionStatus().value,
            KEY_PERMISSIONS_NOTIFICATIONS to permissionManager.getNotificationPermissionStatus().value,
            KEY_PERMISSIONS_PHOTO_LIBRARY to permissionManager.getPhotoLibraryPermissionStatus().value,
        ).filter { (_, value) -> value == PermissionStatus.GRANTED.value }

        return Provider {
            JsonObject().apply {
                map.forEach { (key, value) ->
                    add(key, JsonObject().apply { addProperty(KEY_PERMISSIONS_STATUS, value) })
                }
            }
        }
    }

    private fun resolveTheme(): String {
        val uiMode: Int = appContext.resources.configuration.uiMode
        val isDarkTheme: Boolean = (uiMode and UiConfiguration.UI_MODE_NIGHT_MASK) == UiConfiguration.UI_MODE_NIGHT_YES
        return if (isDarkTheme) VALUE_THEME_DARK else VALUE_THEME_LIGHT
    }

    private fun resolveLocale(): String {
        return Locale.getDefault().toLanguageTag().replace("-", "_")
    }

    private fun createInsetsPayload(insets: InAppInsets): Provider {
        val density: Float = appContext.resources.displayMetrics.density

        fun Int.toCssPixel(): Int = (this / density).roundToInt()

        return Provider {
            JsonObject().apply {
                addProperty(InAppInsets.BOTTOM, insets.bottom.toCssPixel())
                addProperty(InAppInsets.LEFT, insets.left.toCssPixel())
                addProperty(InAppInsets.RIGHT, insets.right.toCssPixel())
                addProperty(InAppInsets.TOP, insets.top.toCssPixel())
            }
        }
    }
}
