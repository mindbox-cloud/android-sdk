package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import android.app.Application
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test


class AbtestsMobileConfigSerializationManagerTest {

    private val manager: MobileConfigSerializationManagerImpl by mindboxInject {
        mobileConfigSerializationManager as MobileConfigSerializationManagerImpl
    }

    private val context = mockk<Application>(relaxed = true) {
        every { applicationContext } returns this
    }

    @Before
    fun onTestStart() {
        MindboxDI.init(context)
    }

    @Test
    fun `test ABTestsConfig should parse successfully`() {
        val json = getJson("ConfigParsing/ABTests/ABTestsConfig.json")
        val config = manager.deserializeAbtests(json)!!

        assertEquals(2, config.size)

        config.forEach { abTest ->
            assertNotNull(abTest.salt)
            assertNotNull(abTest.sdkVersion)
            assertNotNull(abTest.variants)
        }
    }

    @Test
    fun `test ABTestsConfig with IdError should throw decoding error`() {
        val json = getJson("ConfigParsing/ABTests/ABTestsIdConfigError.json")
        val config = manager.deserializeAbtests(json)

        assertNull(config)
    }

    @Test
    fun `test ABTestsConfig with IdTypeError should throw decoding error`() {
        val json = getJson("ConfigParsing/ABTests/ABTestsIdConfigTypeError.json")
        val config = manager.deserializeAbtests(json)
        // Type of `id` is Int instead of String
        assertNull(config)
    }

    @Test
    fun `test ABTestsConfig with SdkVersionError should set to Null corrupted data`() {
        val json = getJson("ConfigParsing/ABTests/ABTestsSdkVersionConfigError.json")
        val config = manager.deserializeAbtests(json)!!

        config.forEach { abTest ->
            if (abTest.id == "94CD824A-59AA-4937-9E0E-089895A0DB6F") {
                assertNull(abTest.sdkVersion)
            } else {
                assertNotNull(abTest.sdkVersion)
            }
            assertNotNull(abTest.salt)
            assertNotNull(abTest.variants)
        }
    }

    @Test
    fun `test ABTestsConfig with SdkVersionTypeError should throw decoding error`() {
        val json = getJson("ConfigParsing/ABTests/ABTestsSdkVersionConfigTypeError.json")
        val config = manager.deserializeAbtests(json)
        // Type of `sdkVersion` is Int instead of SdkVersion
        assertNull(config)
    }
}

