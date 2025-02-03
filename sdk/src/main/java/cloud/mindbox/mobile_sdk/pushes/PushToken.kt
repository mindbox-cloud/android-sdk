package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.Stopwatch
import cloud.mindbox.mobile_sdk.utils.awaitAllWithTimeout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

private const val GET_PUSH_TOKEN_TIMEOUT = 5000L

internal data class PushToken(
    val provider: String,
    val token: String,
)

internal data class PrefPushToken(
    val token: String,
    val updateDate: Long,
)

internal typealias PushTokenMap = Map<String, String>
internal typealias PrefPushTokenMap = Map<String, PrefPushToken>

internal fun <K, V> Map<K, V>.toPreferences(): String =
    runCatching {
        Gson().toJson(this)
    }.getOrDefault("")

internal fun String?.toTokensMap(): PrefPushTokenMap =
    runCatching {
        val pushTokenMapType = object : TypeToken<PrefPushTokenMap>() {}.type
        Gson().fromJson(this, pushTokenMapType) as PrefPushTokenMap
    }.getOrDefault(emptyMap())

internal suspend fun getPushTokens(context: Context, previousToken: PushTokenMap): PushTokenMap =
    withContext(Mindbox.mindboxScope.coroutineContext) {
        val handlers = Mindbox.pushServiceHandlers
        if (handlers.isEmpty()) {
            return@withContext emptyMap()
        }
        Stopwatch.start(Stopwatch.GET_PUSH_TOKENS)
        handlers
            .map { handler ->
                async {
                    runCatching {
                        val provider = handler.notificationProvider
                        val token = handler.registerToken(context, previousToken[provider])
                        handler.notificationProvider to token
                    }.getOrElse {
                        Mindbox.logE("Failed to get push token from provider", it)
                        handler.notificationProvider to null
                    }
                }
            }
            .awaitAllWithTimeout(GET_PUSH_TOKEN_TIMEOUT)
            .mapNotNull { (provider, token) ->
                if (!token.isNullOrEmpty()) provider to token else null
            }
            .toMap()
            .also { tokens ->
                val duration = Stopwatch.stop(Stopwatch.GET_PUSH_TOKENS)
                mindboxLogI("Push tokens $tokens received in $duration")
            }
    }
