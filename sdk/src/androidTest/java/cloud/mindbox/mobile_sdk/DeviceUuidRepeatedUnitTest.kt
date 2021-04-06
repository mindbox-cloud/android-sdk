package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
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
            Mindbox.init(appContext, configs)
        }

        @AfterClass
        @JvmStatic
        fun clear() {
            MindboxPreferences.clear()
            DbManager.removeConfiguration()
        }
    }

    @Test
    fun secondaryGetting_isCorrect() {
        var result = ""

        Thread.sleep(5000)

        Mindbox.subscribeDeviceUuid{ deviceUuid -> result = deviceUuid }

        Thread.sleep(1000)

        Assert.assertEquals(true, result.isUuid())
    }
}