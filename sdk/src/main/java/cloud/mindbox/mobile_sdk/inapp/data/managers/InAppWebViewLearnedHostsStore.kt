package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import org.json.JSONArray

/**
 * Persists https hosts actually observed during webview in-app shows (per endpoint).
 * The mobile config only reveals the bootstrap hosts; the heavy ones (image CDNs)
 * are discovered at show time and fed to the next launch's preconnect prewarm.
 */
internal class InAppWebViewLearnedHostsStore {

    companion object {
        private const val KEY_PREFIX = "MBInAppWebViewLearnedHosts."
        private const val MAX_HOSTS = 12
    }

    fun hosts(endpointId: String): List<String> = loggingRunCatching(defaultValue = emptyList()) {
        val raw = SharedPreferencesManager.getString(key(endpointId))
            ?.takeIf { it.isNotBlank() }
            ?: return@loggingRunCatching emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).takeIf { host -> host.isNotBlank() }
        }
    }

    /**
     * Newest-first merge capped at [MAX_HOSTS] so one weird show can't flood the list.
     * Synchronized: merges arrive from evaluate callbacks/coroutines of concurrent closes,
     * and an unsynchronized read-modify-write would silently drop one close's hosts.
     */
    @Synchronized
    fun merge(endpointId: String, observedHosts: List<String>): Unit = loggingRunCatching {
        if (endpointId.isBlank()) return@loggingRunCatching
        val incoming = observedHosts.map(String::trim).filter(String::isNotBlank)
        if (incoming.isEmpty()) return@loggingRunCatching
        val merged = (incoming + hosts(endpointId)).distinct().take(MAX_HOSTS)
        SharedPreferencesManager.put(key(endpointId), JSONArray(merged).toString())
    }

    private fun key(endpointId: String): String = KEY_PREFIX + endpointId
}
