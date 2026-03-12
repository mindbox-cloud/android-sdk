package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class WebViewLocalStateStoreTest {

    companion object {
        private const val LOCAL_STATE_FILE_NAME: String = "mindbox_webview_local_state"
    }

    private lateinit var context: Context
    private lateinit var store: WebViewLocalStateStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(LOCAL_STATE_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().apply()
        SharedPreferencesManager.with(context)
        MindboxPreferences.localStateVersion = 1
        store = WebViewLocalStateStore(context)
    }

    @Test
    fun `getState returns default version and empty data when storage is empty`() {
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        assertEquals(1, actualResponse.getInt("version"))
        assertEquals(0, actualResponse.getJSONObject("data").length())
    }

    @Test
    fun `get with specific keys returns only requested keys`() {
        store.initState("""{"data":{"key1":"value1","key2":"value2"},"version":2}""")
        val actualResponse: JSONObject = store.getState("""{"data":["key1"]}""").toJsonObject()
        assertEquals(2, actualResponse.getInt("version"))
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertEquals("value1", actualData.getString("key1"))
        assertFalse(actualData.has("key2"))
    }

    @Test
    fun `get with empty keys returns all stored keys`() {
        store.setState("""{"data":{"key1":"value1","key2":"value2"}}""")
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertEquals(2, actualData.length())
        assertEquals("value1", actualData.getString("key1"))
        assertEquals("value2", actualData.getString("key2"))
    }

    @Test
    fun `get returns current version from preferences`() {
        MindboxPreferences.localStateVersion = 5
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        assertEquals(5, actualResponse.getInt("version"))
    }

    @Test
    fun `setState updates values and removes fields with null`() {
        store.initState("""{"data":{"key1":"value1","key2":"value2"},"version":3}""")
        store.setState("""{"data":{"key1":"updated","key2":null,"key3":"value3"}}""")
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        assertEquals(3, actualResponse.getInt("version"))
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertEquals("updated", actualData.getString("key1"))
        assertFalse(actualData.has("key2"))
        assertEquals("value3", actualData.getString("key3"))
    }

    @Test
    fun `initState returns error when requested version is lower than current`() {
        store.initState("""{"data":{"key":"value"},"version":5}""")
        val actualError: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            store.initState("""{"data":{"key":"next"},"version":0}""")
        }
        assertTrue(actualError.message?.contains("Version must be greater than 0") == true)
    }

    @Test
    fun `initState returns error when data field is missing`() {
        val actualError: Exception = assertThrows(Exception::class.java) {
            store.initState("""{"version":2}""")
        }
        assertTrue(actualError.message?.isNotBlank() == true)
    }

    @Test
    fun `initState stores version in sdk preferences`() {
        store.initState("""{"data":{"key":"value"},"version":7}""")
        assertEquals(7, MindboxPreferences.localStateVersion)
    }

    @Test
    fun `setState stores each data key as separate preference key`() {
        store.setState("""{"data":{"firstKey":"firstValue","secondKey":"secondValue"}}""")
        val localStatePreferences = context.getSharedPreferences(LOCAL_STATE_FILE_NAME, Context.MODE_PRIVATE)
        assertEquals("firstValue", localStatePreferences.getString("firstKey", null))
        assertEquals("secondValue", localStatePreferences.getString("secondKey", null))
        assertFalse(localStatePreferences.contains("local_state_data_json"))
    }

    @Test
    fun `get missing keys excludes absent keys from response`() {
        store.initState("""{"data":{"existing":"value"},"version":2}""")
        val actualResponse: JSONObject = store.getState("""{"data":["existing","missing"]}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertTrue(actualData.has("existing"))
        assertTrue(actualData.has("missing"))
        assertEquals(2, actualData.length())
    }

    @Test
    fun `setState returns only affected keys`() {
        store.initState("""{"data":{"oldKey":"oldValue"},"version":4}""")
        val actualResponse: JSONObject = store.setState("""{"data":{"newKey":"newValue"}}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertTrue(actualData.has("newKey"))
        assertFalse(actualData.has("oldKey"))
    }

    @Test
    fun `setState does not change version`() {
        store.initState("""{"data":{"key":"value"},"version":8}""")
        val actualResponse: JSONObject = store.setState("""{"data":{"key":"updated"}}""").toJsonObject()
        assertEquals(8, actualResponse.getInt("version"))
        assertEquals(8, MindboxPreferences.localStateVersion)
    }

    @Test
    fun `initState merges with existing data`() {
        store.setState("""{"data":{"base":"base-value","keep":"keep-value"}}""")
        store.initState("""{"data":{"base":"updated-base","added":"added-value"},"version":3}""")
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertEquals("updated-base", actualData.getString("base"))
        assertEquals("keep-value", actualData.getString("keep"))
        assertEquals("added-value", actualData.getString("added"))
    }

    @Test
    fun `initState rejects negative version`() {
        val actualError: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            store.initState("""{"data":{"key":"value"},"version":-1}""")
        }
        assertTrue(actualError.message?.contains("Version must be greater than 0") == true)
    }

    @Test
    fun `initState rejects zero version`() {
        val actualError: IllegalArgumentException = assertThrows(IllegalArgumentException::class.java) {
            store.initState("""{"data":{"key":"value"},"version":0}""")
        }
        assertTrue(actualError.message?.contains("Version must be greater than 0") == true)
    }

    @Test
    fun `initState does not write version when rejected`() {
        store.initState("""{"data":{"key":"value"},"version":6}""")
        assertThrows(IllegalArgumentException::class.java) {
            store.initState("""{"data":{"key":"next"},"version":-10}""")
        }
        assertEquals(6, MindboxPreferences.localStateVersion)
    }

    @Test
    fun `full flow init set get works correctly`() {
        store.initState("""{"data":{"k1":"v1"},"version":5}""")
        store.setState("""{"data":{"k2":"v2","k1":"v1-updated"}}""")
        val actualResponse: JSONObject = store.getState("""{"data":["k1","k2"]}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertEquals("v1-updated", actualData.getString("k1"))
        assertEquals("v2", actualData.getString("k2"))
        assertEquals(5, actualResponse.getInt("version"))
    }

    @Test
    fun `set null then get returns removed key as empty`() {
        store.setState("""{"data":{"keyToDelete":"value"}}""")
        store.setState("""{"data":{"keyToDelete":null}}""")
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertFalse(actualData.has("keyToDelete"))
    }

    @Test
    fun `initState removes key when value is null`() {
        store.setState("""{"data":{"keyToDelete":"value"}}""")
        store.initState("""{"data":{"keyToDelete":null},"version":2}""")
        val actualResponse: JSONObject = store.getState("""{"data":[]}""").toJsonObject()
        val actualData: JSONObject = actualResponse.getJSONObject("data")
        assertFalse(actualData.has("keyToDelete"))
    }

    private fun String.toJsonObject(): JSONObject = JSONObject(this)
}
