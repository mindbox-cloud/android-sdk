package cloud.mindbox.mindbox_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.models.isUuid
import cloud.mindbox.mobile_sdk_core.MindboxInternalCore
import cloud.mindbox.mobile_sdk_core.clearPreferences
import cloud.mindbox.mobile_sdk_core.initCoreComponents
import cloud.mindbox.mobile_sdk_core.setDatabaseTestMode
import org.junit.After
import org.junit.Assert
import org.junit.Test

class DeviceUuidSingleUnitTest {

    @Test
    fun subscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result = ""

        MindboxInternalCore.subscribeDeviceUuid { deviceUuid ->
            result = deviceUuid
            println(deviceUuid)
        }

        val configs = cloud.mindbox.mobile_sdk.MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        setDatabaseTestMode(true)
        MindboxInternalCore.init(appContext, configs)

        Thread.sleep(10000)

        Assert.assertEquals(true, result.isUuid())
    }

    @Test
    fun unsubscribe_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result = ""

        val subscribeId = MindboxInternalCore.subscribeDeviceUuid { deviceUuid -> result = deviceUuid }

        MindboxInternalCore.disposeDeviceUuidSubscription(subscribeId)

        Thread.sleep(3000)

        val configs = cloud.mindbox.mobile_sdk.MindboxConfiguration.Builder(appContext, "example.com", "someEndpoint").build()

        setDatabaseTestMode(true)
        MindboxInternalCore.init(appContext, configs)

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        MindboxInternalCore.disposeDeviceUuidSubscription("wrong_subscribe")
        MindboxInternalCore.disposeDeviceUuidSubscription("")

        setDatabaseTestMode(true)
        initCoreComponents()
    }

    @Test
    fun subscribeIdGeneration_isCorrect() {
        val subscribeId = MindboxInternalCore.subscribeDeviceUuid { }

        MindboxInternalCore.disposeDeviceUuidSubscription(subscribeId)

        Assert.assertEquals(true, subscribeId.isUuid())
    }

    @After
    fun clear() {
        clearPreferences()
    }

}
