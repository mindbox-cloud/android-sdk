package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import cloud.mindbox.mobile_sdk.Mindbox
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

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

internal fun PrefPushTokenMap.toPreferences(): String =
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
        Mindbox.pushServiceHandlers
            .map { handler ->
                async {
                    val provider = handler.notificationProvider
                    val token = handler.registerToken(context, previousToken[provider])
                    handler.notificationProvider to token
                }
            }.awaitAll()
            .mapNotNull { (provider, token) ->
                if (!token.isNullOrEmpty()) provider to token else null
            }.toMap()
    }
