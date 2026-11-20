package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler.FrequencyDataFiller
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.data.validators.FrequencyValidator
import cloud.mindbox.mobile_sdk.inapp.data.validators.TimeSpanPositiveValidator
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionState
import cloud.mindbox.mobile_sdk.models.operation.response.DisplayConditionsDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import cloud.mindbox.mobile_sdk.utils.setMindboxScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * One rule for both restriction fields: a missing or null value means the default,
 * a readable value is used as is, and anything the SDK cannot read drops only that in-app.
 */
@RunWith(Parameterized::class)
internal class InAppRestrictionFieldsTest(
    private val field: String,
    private val case: String,
    private val value: String?,
    private val expected: Expected,
) {

    internal sealed class Expected {
        data class Kept(val check: (InAppDto) -> Unit) : Expected() {
            override fun toString() = "kept"
        }

        data object Dropped : Expected() {
            override fun toString() = "dropped"
        }
    }

    private val gson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson
    private val serializationManager = MobileConfigSerializationManagerImpl(gson)
    private val originalMindboxScope = Mindbox.mindboxScope
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        setMindboxScope(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        setMindboxScope(originalMindboxScope)
    }

    @Test
    fun `restriction field follows the one rule and spares the other in-apps`() {
        val entry = value?.let { "\"$field\": $it," } ?: ""
        val config = """
            {"inapps":[
              {"id":"checked","isPriority":false,"sdkVersion":{"min":1,"max":null},$entry
               "targeting":{"${'$'}type":"true"},"form":{"variants":[]}},
              {"id":"neighbour","isPriority":false,"sdkVersion":{"min":1,"max":null},
               "targeting":{"${'$'}type":"true"},"form":{"variants":[]}}
            ]}
        """.trimIndent()

        val inApps = createRepository().getInApps(serializationManager.deserializeToConfigDtoBlank(config)).orEmpty()

        assertEquals("the neighbour in-app must survive", 1, inApps.count { it.id == "neighbour" })
        val checked = inApps.firstOrNull { it.id == "checked" }
        when (expected) {
            Expected.Dropped -> assertNull("the in-app must be dropped", checked)
            is Expected.Kept -> expected.check(checked ?: error("the in-app must be kept"))
        }
    }

    private fun createRepository() = MobileConfigRepositoryImpl(
        inAppMapper = InAppMapper(),
        timeSpanPositiveValidator = TimeSpanPositiveValidator(),
        inAppConfigTtlValidator = mockk(relaxed = true) { every { isValid(any()) } returns true },
        inAppValidator = mockk(relaxed = true) {
            every { validateInAppVersion(any()) } returns true
            every { validateInApp(any()) } answers { FrequencyValidator().isValid(firstArg<InAppDto>().frequency) }
        },
        mobileConfigSerializationManager = serializationManager,
        monitoringValidator = mockk(relaxed = true),
        abTestValidator = mockk(relaxed = true),
        operationNameValidator = mockk(relaxed = true),
        operationValidator = mockk(relaxed = true),
        gatewayManager = mockk(relaxed = true),
        defaultDataManager = mockk(relaxed = true) {
            every { fillFormData(any()) } answers { firstArg() }
            every { fillFrequencyData(any()) } answers { FrequencyDataFiller().fillData(firstArg()) }
        },
        ttlParametersValidator = mockk(relaxed = true),
        sessionStorageManager = mockk(relaxed = true) { every { state } returns SessionState() },
        mobileConfigSettingsManager = mockk(relaxed = true),
        integerPositiveValidator = mockk(relaxed = true),
        inappSettingsManager = mockk(relaxed = true),
        featureToggleManager = mockk(relaxed = true),
        inAppWebViewPrewarmManager = mockk(relaxed = true),
    )

    companion object {
        private const val FREQUENCY = "frequency"
        private const val DISPLAY_CONDITIONS = "displayConditions"

        private val onceLifetime = Expected.Kept { inApp ->
            assertEquals(FrequencyDto.FrequencyOnceDto("once", "lifetime"), inApp.frequency)
        }
        private val noConditions = Expected.Kept { inApp -> assertNull(inApp.displayConditions) }

        @JvmStatic
        @Parameterized.Parameters(name = "{0} {1} -> {3}")
        fun data(): Iterable<Array<Any?>> = listOf(
            arrayOf(FREQUENCY, "missing", null, onceLifetime),
            arrayOf(FREQUENCY, "null", "null", onceLifetime),
            arrayOf(
                FREQUENCY,
                "known type",
                """{"${'$'}type":"unlimited"}""",
                Expected.Kept { inApp -> assertEquals(FrequencyDto.FrequencyUnlimitedDto("unlimited"), inApp.frequency) },
            ),
            arrayOf(FREQUENCY, "known type with a broken kind", """{"${'$'}type":"once","kind":"forever"}""", Expected.Dropped),
            arrayOf(FREQUENCY, "unknown type", """{"${'$'}type":"everySecondTuesday"}""", Expected.Dropped),
            arrayOf(FREQUENCY, "miscased type", """{"${'$'}type":"Unlimited"}""", Expected.Dropped),
            arrayOf(FREQUENCY, "empty object", "{}", Expected.Dropped),
            arrayOf(FREQUENCY, "non-string type", """{"${'$'}type":42}""", Expected.Dropped),
            arrayOf(FREQUENCY, "string", "\"unlimited\"", Expected.Dropped),
            arrayOf(FREQUENCY, "number", "42", Expected.Dropped),
            arrayOf(FREQUENCY, "array", "[]", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "missing", null, noConditions),
            arrayOf(DISPLAY_CONDITIONS, "null", "null", noConditions),
            arrayOf(
                DISPLAY_CONDITIONS,
                "direct call",
                """{"${'$'}type":"directCall"}""",
                Expected.Kept { inApp -> assertEquals(DisplayConditionsDto.DirectCallDto("directCall"), inApp.displayConditions) },
            ),
            arrayOf(DISPLAY_CONDITIONS, "unknown type", """{"${'$'}type":"unknownCondition"}""", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "miscased type", """{"${'$'}type":"DirectCall"}""", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "empty object", "{}", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "non-string type", """{"${'$'}type":42}""", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "string", "\"directCall\"", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "number", "42", Expected.Dropped),
            arrayOf(DISPLAY_CONDITIONS, "array", "[]", Expected.Dropped),
        )
    }
}
