package cloud.mindbox.mobile_sdk.models

import com.google.gson.Gson
import org.junit.Assert.assertTrue
import org.junit.Test

/** The field is required by the backend: it must appear in every body, empty set included. */
class TrackingIdsSerializationTest {

    private val gson = Gson()

    private fun updateData(ids: List<TrackingId>) = UpdateData(
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
    fun `the field cannot be left out — every body has to carry it`() {
        // The model has no default for `ids`, so a new construction site cannot forget it and
        // silently ship a body the backend would read as an erase.
        val json = gson.toJson(updateData(emptyList()))

        assertTrue(json.contains("\"ids\""))
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
