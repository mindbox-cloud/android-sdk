package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import org.junit.After
import org.junit.Assert
import org.junit.Test

class DeviceUuidUnitTest {

    @Test
    fun deviceUuidSubscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val tag = "device_uuid_subscribe_tag"

        var result = ""

        Mindbox.subscribeDeviceUuid(appContext) { deviceUuid ->
            result = deviceUuid
            println(deviceUuid)
            Assert.assertEquals(true, deviceUuid.isUuid())
        }

        val configs = MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        Mindbox.init(appContext, configs)

        Thread.sleep(10000)

        Assert.assertEquals(true, result.isUuid())
    }

    @After
    fun clear() {
        MindboxPreferences.clear()
        DbManager.removeConfiguration()
    }
}