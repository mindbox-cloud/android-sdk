package cloud.mindbox.detekt

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnmonitoredGsonWrapperRuleTest {

    @Test
    fun `reports function that wraps fromJson under a new name`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson
            import com.google.gson.reflect.TypeToken

            fun <T> deserializeModel(json: String, clazz: Class<T>): T =
                Gson().fromJson(json, clazz)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertEquals("UnmonitoredGsonWrapper", findings.first().id)
        assertTrue(findings.first().message.contains("deserializeModel"))
    }

    @Test
    fun `reports generic function that wraps toJson under a new name`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            fun <T> serializePayload(payload: T): String = Gson().toJson(payload)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("serializePayload"))
    }

    @Test
    fun `does not report non-generic function wrapping Gson`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            data class Config(val key: String)
            fun serializeConfig(config: Config): String = Gson().toJson(config)
            """.trimIndent()
        )
        // Non-generic: explicit type Config is already detected by GsonMissingSerializedName.
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report function already monitored by GsonMissingSerializedName`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            fun <T> toJson(obj: T): String = Gson().toJson(obj)
            fun <T> fromJson(json: String, clazz: Class<T>): T = Gson().fromJson(json, clazz)
            fun <T> toJsonTyped(obj: T): String = Gson().toJson(obj)
            fun <T> fromJsonTyped(json: String, clazz: Class<T>): T = Gson().fromJson(json, clazz)
            fun <T> operationBodyJson(obj: T): String = Gson().toJson(obj)
            fun <T> convertJsonToBody(json: String, clazz: Class<T>): T = Gson().fromJson(json, clazz)
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports internal generic function wrapping Gson`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            internal fun <T> loadModel(json: String, clazz: Class<T>): T = Gson().fromJson(json, clazz)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("loadModel"))
    }

    @Test
    fun `does not report private function wrapping Gson`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            class Repo {
                private fun loadInternal(json: String): Any = Gson().fromJson(json, Any::class.java)
            }
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report override function wrapping Gson`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            class GeoSerializer : GeoSerializationManager {
                override fun deserializeToGeoTargeting(json: String): Any =
                    Gson().fromJson(json, Any::class.java)
            }
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report function that does not call Gson`() {
        val findings = rule.lint(
            """
            import com.fasterxml.jackson.databind.ObjectMapper

            fun deserializeWithJackson(json: String): Any =
                ObjectMapper().readValue(json, Any::class.java)
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports generic extension function on Gson receiver`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            fun <T> Gson.parseConfig(json: String, clazz: Class<T>): T = fromJson(json, clazz)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("parseConfig"))
    }

    @Test
    fun `does not report non-generic extension function on Gson receiver`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            data class Config(val key: String)
            fun Gson.parseConfig(json: String): Config = fromJson(json, Config::class.java)
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports outer generic function when Gson call is inside a nested lambda`() {
        val findings = rule.lint(
            """
            import com.google.gson.Gson

            fun <T> buildDeserializer(clazz: Class<T>): () -> T = {
                Gson().fromJson("{}", clazz)
            }
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("buildDeserializer"))
    }

    private val rule = UnmonitoredGsonWrapperRule(config = TestConfig())
}
