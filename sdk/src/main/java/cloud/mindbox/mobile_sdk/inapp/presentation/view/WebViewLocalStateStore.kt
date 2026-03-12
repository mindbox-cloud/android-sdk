package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class WebViewLocalStateStore(
    context: Context
) {
    companion object {
        private const val LOCAL_STATE_FILE_NAME: String = "mindbox_webview_local_state"
        private const val FIELD_DATA: String = "data"
        private const val FIELD_VERSION: String = "version"
    }

    private val localStatePreferences: SharedPreferences =
        context.getSharedPreferences(LOCAL_STATE_FILE_NAME, Context.MODE_PRIVATE)

    fun getState(payload: String): String {
        val requestedKeys: JSONArray = JSONObject(payload).optJSONArray(FIELD_DATA) ?: JSONArray()
        val keys: List<String> = (0..<requestedKeys.length()).map { i -> requestedKeys.getString(i) }
        val savedData: Map<String, String?> = localStatePreferences.all.mapValues { it.value?.toString() }

        return buildResponse(
            data = savedData
                .takeIf { keys.isEmpty() }
                ?: keys.associateWith { key -> savedData[key] }
        )
    }

    fun setState(payload: String): String {
        val jsonData: JSONObject = JSONObject(payload).getJSONObject(FIELD_DATA)
        val dataToSet = jsonData.toMap()

        localStatePreferences.edit {
            dataToSet.forEach { (key, value) ->
                value?.let { putString(key, value) }
                    ?: remove(key)
            }
        }

        return buildResponse(data = dataToSet)
    }

    fun initState(payload: String): String {
        val version: Int = JSONObject(payload).getInt(FIELD_VERSION)
        require(version > 0) { "Version must be greater than 0" }

        MindboxPreferences.localStateVersion = version

        return setState(payload = payload)
    }

    private fun JSONObject.toMap(): Map<String, String?> {
        val keysIterator: Iterator<String> = this.keys()
        val resultMap: MutableMap<String, String?> = mutableMapOf()
        while (keysIterator.hasNext()) {
            val key: String = keysIterator.next()
            val value: Any? = this.opt(key)
            if (value == null || value == JSONObject.NULL) {
                resultMap[key] = null
            } else {
                resultMap[key] = value.toString()
            }
        }
        return resultMap
    }

    private fun buildResponse(data: Map<String, String?>): String {
        val responseObject: JSONObject = JSONObject()
            .put(FIELD_DATA, JSONObject(data))
            .put(FIELD_VERSION, MindboxPreferences.localStateVersion)
        return responseObject.toString()
    }
}
