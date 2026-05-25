package cloud.mindbox.detekt

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GsonSerializedNameRuleTest {

    private val stubs = """
        fun fromJson(json: String, clazz: Class<*>): Any = error("stub")
        fun <T> fromJson(json: String, clazz: Class<T>): T = error("stub")
        fun toJson(obj: Any?): String = error("stub")
        fun <T> toJsonTyped(obj: T): String = error("stub")
        fun <T> fromJsonTyped(json: String, clazz: Class<T>): T = error("stub")
        fun <T> operationBodyJson(obj: T): String = error("stub")
        fun convertJsonToBody(json: String, clazz: Class<*>): Any = error("stub")
        abstract class TypeToken<T>
        annotation class SerializedName(val value: String)
    """.trimIndent()

    private fun compileAndLint(code: String): List<Finding> =
        GsonSerializedNameRule(TestConfig()).compileAndLintWithContext(env, code)

    @Test
    fun `reports class passed via class literal to fromJson`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(val name: String)
            fun test(json: String): Any = fromJson(json, MyDto::class.java)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.name"))
    }

    @Test
    fun `reports class passed as explicit type argument to fromJson`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(val name: String)
            fun test(json: String): MyDto = fromJson<MyDto>(json, MyDto::class.java)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.name"))
    }

    @Test
    fun `reports class passed as first argument to toJson`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(val name: String)
            fun test() = toJson(MyDto("x"))
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.name"))
    }

    @Test
    fun `reports class when toJson called with this inside the class`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(val name: String) {
                fun serialize() = toJson(this)
            }
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.name"))
    }

    @Test
    fun `reports class passed via operationBodyJson`() {
        val findings = compileAndLint(
            """
            $stubs
            data class RequestBody(val action: String)
            fun test() = operationBodyJson(RequestBody("click"))
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("RequestBody.action"))
    }

    @Test
    fun `reports class passed via fromJsonTyped class literal`() {
        val findings = compileAndLint(
            """
            $stubs
            data class ResponseBody(val status: String)
            fun test(json: String): ResponseBody = fromJsonTyped(json, ResponseBody::class.java)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("ResponseBody.status"))
    }

    @Test
    fun `reports class passed via convertJsonToBody`() {
        val findings = compileAndLint(
            """
            $stubs
            data class EventBody(val type: String)
            fun test(json: String): Any = convertJsonToBody(json, EventBody::class.java)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("EventBody.type"))
    }

    @Test
    fun `reports class used as direct TypeToken generic argument`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(val name: String)
            val token = object : TypeToken<MyDto>() {}
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.name"))
    }

    @Test
    fun `reports class nested inside List in TypeToken`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(val name: String)
            val token = object : TypeToken<List<MyDto>>() {}
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.name"))
    }

    @Test
    fun `reports transitive field type missing annotation`() {
        val findings = compileAndLint(
            """
            $stubs
            data class Inner(val value: Int)
            data class Outer(val inner: Inner)
            fun test() = toJson(Outer(Inner(1)))
            """.trimIndent()
        )
        assertEquals(2, findings.size)
        assertTrue(findings.any { it.message.contains("Outer.inner") })
        assertTrue(findings.any { it.message.contains("Inner.value") })
    }

    @Test
    fun `reports class nested inside generic List field transitively`() {
        val findings = compileAndLint(
            """
            $stubs
            data class Item(val id: Int)
            data class Page(val items: List<Item>)
            fun test() = toJson(Page(emptyList()))
            """.trimIndent()
        )
        assertTrue(findings.any { it.message.contains("Page.items") })
        assertTrue(findings.any { it.message.contains("Item.id") })
    }

    @Test
    fun `reports missing annotation when class has partial SerializedName contract`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(
                @SerializedName("name") val name: String,
                val age: Int
            )
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("MyDto.age"))
    }

    @Test
    fun `does not report data class not involved in any Gson call`() {
        val findings = compileAndLint(
            """
            $stubs
            data class Config(val key: String, val value: Int)
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report when all fields have SerializedName`() {
        val findings = compileAndLint(
            """
            $stubs
            data class MyDto(
                @SerializedName("name") val name: String,
                @SerializedName("age") val age: Int
            )
            fun test() = toJson(MyDto("x", 1))
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report non-data class passed to toJson`() {
        val findings = compileAndLint(
            """
            $stubs
            class NotADataClass(val name: String)
            fun test() = toJson(NotADataClass("x"))
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `does not report stdlib types like String or List`() {
        val findings = compileAndLint(
            """
            $stubs
            fun test() = toJson(listOf("a", "b"))
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    companion object {
        private val environmentWrapper = createEnvironment()
        private val env get() = environmentWrapper.env

        @AfterClass @JvmStatic
        fun tearDown() {
            environmentWrapper.dispose()
        }
    }
}
