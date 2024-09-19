package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

class DeviceUuidRepeatedUnitTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun init() {
            val appContext = InstrumentationRegistry.getInstrumentation().targetContext
            val configs = MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()
            setDatabaseTestMode(true)
            Mindbox.init(appContext, configs, listOf())
        }

        @AfterClass
        @JvmStatic
        fun clear() {
            clearPreferences()
        }
    }

    @Test
    fun secondaryGetting_isCorrect() {
        var result = ""

        Thread.sleep(5000)

        Mindbox.subscribeDeviceUuid { deviceUuid -> result = deviceUuid }

        Thread.sleep(1000)

        Assert.assertEquals(true, result.isUuid())
    }

}
