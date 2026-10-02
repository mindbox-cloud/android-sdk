package cloud.mindbox.detekt

import io.github.detekt.test.utils.createEnvironment
import io.gitlab.arturbosch.detekt.api.Finding
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.compileAndLintWithContext
import io.gitlab.arturbosch.detekt.test.lint
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnjustifiedReflectionRuleTest {

    private fun compileAndLint(code: String): List<Finding> =
        UnjustifiedReflectionRule(TestConfig()).compileAndLintWithContext(env, code)

    @Test
    fun `reports Class forName`() {
        val findings = compileAndLint("""fun test(): Class<*> = Class.forName("com.example.Main")""")
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("java.lang.Class.forName"))
    }

    @Test
    fun `reports declared member lookups`() {
        val findings = compileAndLint(
            """
            fun test() {
                String::class.java.getDeclaredField("value")
                String::class.java.getDeclaredMethod("length")
                String::class.java.getMethod("length")
            }
            """.trimIndent()
        )
        assertEquals(3, findings.size)
    }

    @Test
    fun `reports getField and getDeclaredConstructor`() {
        val findings = compileAndLint(
            """
            fun test() {
                StringBuilder::class.java.getField("x")
                StringBuilder::class.java.getDeclaredConstructor()
            }
            """.trimIndent()
        )
        assertEquals(2, findings.size)
    }

    @Test
    fun `reports the Kotlin property form once`() {
        val findings = compileAndLint(
            """
            fun test(clazz: Class<*>) {
                clazz.declaredFields
                clazz.declaredMethods
                clazz.declaredConstructors
            }
            """.trimIndent()
        )
        assertEquals(3, findings.size)
        assertTrue(findings.first().message.contains("java.lang.Class.getDeclaredFields"))
    }

    @Test
    fun `reports public member lookups`() {
        val findings = compileAndLint(
            """
            fun test(clazz: Class<*>) {
                clazz.getConstructor()
                clazz.methods
                clazz.fields
                clazz.constructors
            }
            """.trimIndent()
        )
        assertEquals(
            listOf(
                "java.lang.Class.getConstructor",
                "java.lang.Class.getMethods",
                "java.lang.Class.getFields",
                "java.lang.Class.getConstructors",
            ),
            findings.map { finding -> finding.message.substringBefore(" ") }
        )
    }

    @Test
    fun `reports a reflective call exactly once`() {
        val findings = compileAndLint("""fun test(clazz: Class<*>) = clazz.getDeclaredFields()""")
        assertEquals(1, findings.size)
    }

    @Test
    fun `ignores non-reflective synthetic properties`() {
        val findings = compileAndLint("""fun test(clazz: Class<*>) = clazz.simpleName + clazz.name""")
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports ClassLoader loadClass and ServiceLoader load`() {
        val findings = compileAndLint(
            """
            fun test(loader: ClassLoader) {
                loader.loadClass("com.example.Main")
                java.util.ServiceLoader.load(Runnable::class.java)
            }
            """.trimIndent()
        )
        assertEquals(2, findings.size)
    }

    @Test
    fun `reports function references`() {
        val findings = compileAndLint(
            """
            fun test(names: List<String>, classes: List<Class<*>>) {
                names.map(Class::forName)
                classes.map(Class<*>::getDeclaredFields)
            }
            """.trimIndent()
        )
        assertEquals(
            listOf("java.lang.Class.forName", "java.lang.Class.getDeclaredFields"),
            findings.map { finding -> finding.message.substringBefore(" ") }
        )
    }

    @Test
    fun `reports an inherited lookup called on a subclass`() {
        val findings = compileAndLint(
            """
            fun test(loader: ClassLoader) = (loader as java.net.URLClassLoader).loadClass("com.example.Main")
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertTrue(findings.first().message.contains("java.lang.ClassLoader.loadClass"))
    }

    @Test
    fun `reports an overriding lookup`() {
        val findings = compileAndLint(
            """
            class MyLoader : ClassLoader() {
                override fun loadClass(name: String): Class<*> = super.loadClass(name)
            }
            fun test(loader: MyLoader) = loader.loadClass("com.example.Main")
            """.trimIndent()
        )
        assertEquals(2, findings.size)
    }

    @Test
    fun `ignores unrelated functions with the same name`() {
        val findings = compileAndLint(
            """
            object Registry { fun forName(name: String): String = name }
            fun test() = Registry.forName("x")
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `respects an explicit suppression`() {
        val findings = compileAndLint(
            """
            fun test(): Class<*> = run {
                @Suppress("UnjustifiedReflection")
                Class.forName("com.example.Main")
            }
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `reports nothing without type resolution`() {
        val findings = UnjustifiedReflectionRule(TestConfig()).lint("""fun test() = Class.forName("x")""")
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
