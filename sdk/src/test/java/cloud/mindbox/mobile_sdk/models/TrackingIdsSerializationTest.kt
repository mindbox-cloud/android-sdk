package cloud.mindbox.mobile_sdk.models

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `[]` erases the stored set on the backend, a missing field leaves it untouched. */
class TrackingIdsSerializationTest {

    private val gson = Gson()

    private fun updateData(ids: List<TrackingId>?) = UpdateData(
        isNotificationsEnabled = true,
        instanceId = "instance",
        version = 1,
        tokens = emptyList(),
        trackingIds = ids,
    )

    @Test
    fun `an empty payload serializes as an empty array`() {
        val json = gson.toJson(updateData(emptyList()))

        assertTrue(json.contains("\"ids\":[]"))
    }

    @Test
    fun `identifiers serialize with the Mindbox provider vocabulary`() {
        val json = gson.toJson(
            updateData(
                listOf(
                    TrackingId("google", "38400000-8cf0-11bd-b23e-10b96e40000d"),
                    TrackingId("huawei", "1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809"),
                )
            )
        )

        assertTrue(
            json.contains(
                "\"ids\":[" +
                    "{\"type\":\"google\",\"value\":\"38400000-8cf0-11bd-b23e-10b96e40000d\"}," +
                    "{\"type\":\"huawei\",\"value\":\"1a2b3c4d-5e6f-7081-92a3-b4c5d6e7f809\"}]"
            )
        )
    }

    @Test
    fun `a null set is left out of the body, so the backend keeps what it has`() {
        val json = gson.toJson(updateData(null))

        assertFalse(json.contains("\"ids\""))
    }

    @Test
    fun `the regular operations carry no set by default`() {
        val json = gson.toJson(
            UpdateData(
                isNotificationsEnabled = true,
                instanceId = "instance",
                version = 1,
                tokens = emptyList(),
            )
        )

        assertFalse(json.contains("\"ids\""))
    }

    @Test
    fun `installed body carries the same field`() {
        val json = gson.toJson(
            InitData(
                installationId = "",
                externalDeviceUUID = "",
                isNotificationsEnabled = true,
                subscribe = true,
                instanceId = "instance",
                ianaTimeZone = null,
                tokens = emptyList(),
                trackingIds = emptyList(),
            )
        )

        assertTrue(json.contains("\"ids\":[]"))
    }
}
