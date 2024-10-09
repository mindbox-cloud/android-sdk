package cloud.mindbox.mobile_sdk.utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import org.junit.Assert.*
import org.junit.Test

class NullableTypAdapterFactoryTest {

    private val gson by lazy {
        GsonBuilder()
            .registerTypeAdapter(String::class.java, StrictStringAdapter())
            .create()
    }

    @Test
    fun parse_null_when_nonnullable_type_is_null() {
        val result = gson.fromJson(
            """{
                "field1": null,
                "field2": null
            }
            """.trimIndent(),
            StubObject::class.java
        )

        assertNull(result.field1)
        assertNull(result.field2)

        assertThrows(NullPointerException::class.java) {
            result.copy()
        }
    }

    @Test
    fun parse_success() {
        val result = gson.fromJson(
            """{
                "field1": "string1",
                "field2": "string2"
            }""".trimIndent(), StubObject::class.java
        )

        assertEquals(StubObject("string1", "string2"), result)
    }

    @Test
    fun parse_success_nullable() {
        val result = gson.fromJson(
            """{
                "field1": "string1",
                "field2": null
            }""".trimIndent(), StubObject::class.java
        )

        assertEquals(StubObject("string1", null), result)
    }

    @Test
    fun parse_throw_error_when_json_corrupted() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson(
                """{
                "field1": "string1"
                "field2": "string2"
            }
                """.trimIndent(),
                StubObject::class.java
            )
        }
    }

    @Test
    fun throw_exception_when_parse_not_string_type_int() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson(
                """{
                "field1": 123,
                "field2": null
            }
                """.trimIndent(),
                StubObject::class.java
            )
        }
    }

    @Test
    fun throw_exception_when_parse_not_string_type_object() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson(
                """{
                "field1": "123",
                "field2": {}
            }
                """.trimIndent(),
                StubObject::class.java
            )
        }
    }

    @Test
    fun throw_exception_when_parse_not_string_type_array() {
        assertThrows(JsonParseException::class.java) {
            gson.fromJson(
                """{
                "field1": [],
                "field2": null
            }
                """.trimIndent(),
                StubObject::class.java
            )
        }
    }

    data class StubObject(
        val field1: String,
        val field2: String?,
    )
}
