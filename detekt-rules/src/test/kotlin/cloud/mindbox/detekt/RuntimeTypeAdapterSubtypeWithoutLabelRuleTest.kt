package cloud.mindbox.detekt

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import io.gitlab.arturbosch.detekt.test.lintWithContext
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeTypeAdapterSubtypeWithoutLabelRuleTest {

    private val sdkFactory = """
        package cloud.mindbox.mobile_sdk.utils
        class RuntimeTypeAdapterFactory<T> {
            fun registerSubtype(type: Class<out T>): RuntimeTypeAdapterFactory<T> = this
            fun registerSubtype(type: Class<out T>, label: String): RuntimeTypeAdapterFactory<T> = this
        }
    """.trimIndent()

    private fun compileAndLint(code: String): List<Finding> =
        RuntimeTypeAdapterSubtypeWithoutLabelRule(TestConfig()).lintWithContext(env, code, sdkFactory)

    @Test
    fun `reports the one argument overload`() {
        val findings = compileAndLint(
            """
            import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
            open class Base
            class Child : Base()
            fun test(f: RuntimeTypeAdapterFactory<Base>) = f.registerSubtype(Child::class.java)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `accepts an explicit label`() {
        val findings = compileAndLint(
            """
            import cloud.mindbox.mobile_sdk.utils.RuntimeTypeAdapterFactory
            open class Base
            class Child : Base()
            fun test(f: RuntimeTypeAdapterFactory<Base>) = f.registerSubtype(Child::class.java, "child")
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `ignores a same-named factory from another package`() {
        val findings = compileAndLint(
            """
            package com.example
            class RuntimeTypeAdapterFactory { fun registerSubtype(type: Class<*>) = this }
            class Child
            fun test(f: RuntimeTypeAdapterFactory) = f.registerSubtype(Child::class.java)
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `ignores registerSubtype on other classes`() {
        val findings = compileAndLint(
            """
            class OtherRegistry { fun registerSubtype(type: Class<*>) = Unit }
            class Child
            fun test(r: OtherRegistry) = r.registerSubtype(Child::class.java)
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports nothing without type resolution`() {
        val findings = RuntimeTypeAdapterSubtypeWithoutLabelRule(TestConfig())
            .lint("fun test(f: Any) = f.registerSubtype(Any::class.java)")
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
