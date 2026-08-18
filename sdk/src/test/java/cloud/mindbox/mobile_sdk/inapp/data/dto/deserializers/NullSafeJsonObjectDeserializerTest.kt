package cloud.mindbox.mobile_sdk.inapp.data.dto.deserializers

import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NullSafeJsonObjectDeserializerTest {

    private val deserializer = NullSafeJsonObjectDeserializer()

    private fun deserialize(raw: String): JsonObject? =
        deserializer.deserialize(JsonParser.parseString(raw), JsonObject::class.java, null)

    @Test
    fun `json object is returned as is`() {
        val result = deserialize("""{"${'$'}type":"directCall","nested":{"a":1}}""")

        assertEquals("directCall", result?.get("\$type")?.asString)
        assertEquals(1, result?.getAsJsonObject("nested")?.get("a")?.asInt)
    }

    @Test
    fun `empty json object is returned as is`() {
        assertEquals(JsonObject(), deserialize("{}"))
    }

    @Test
    fun `explicit json null reads as null`() {
        assertNull(deserializer.deserialize(JsonNull.INSTANCE, JsonObject::class.java, null))
    }

    @Test
    fun `number reads as null`() {
        assertNull(deserialize("42"))
    }

    @Test
    fun `string reads as null`() {
        assertNull(deserialize("\"tomorrow\""))
    }

    @Test
    fun `boolean reads as null`() {
        assertNull(deserialize("true"))
    }

    @Test
    fun `array reads as null`() {
        assertNull(deserialize("""[{"a":1}]"""))
    }

    @Test
    fun `missing element reads as null`() {
        assertNull(deserializer.deserialize(null, JsonObject::class.java, null))
    }
}
