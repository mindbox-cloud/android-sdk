package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import org.junit.After
import org.junit.Assert
import org.junit.Test

class DeviceUuidSingleUnitTest {

    @Test
    fun subscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result = ""

        Mindbox.subscribeDeviceUuid { deviceUuid ->
            result = deviceUuid
            println(deviceUuid)
        }

        val configs = MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        MindboxDatabase.isTestMode = true
        Mindbox.init(appContext, configs)

        Thread.sleep(10000)

        Assert.assertEquals(true, result.isUuid())
    }

    @Test
    fun unsubscribe_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result = ""

        val subscribeId = Mindbox.subscribeDeviceUuid { deviceUuid -> result = deviceUuid }

        Mindbox.disposeDeviceUuidSubscription(subscribeId)

        Thread.sleep(3000)

        val configs = MindboxConfiguration.Builder(appContext, "example.com", "someEndpoint").build()

        MindboxDatabase.isTestMode = true
        Mindbox.init(appContext, configs)

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        Mindbox.disposeDeviceUuidSubscription("wrong_subscribe")
        Mindbox.disposeDeviceUuidSubscription("")

        MindboxDatabase.isTestMode = true
        Mindbox.initComponents(InstrumentationRegistry.getInstrumentation().targetContext) //for cancel method after test
    }

    @Test
    fun subscribeIdGeneration_isCorrect() {
        val subscribeId = Mindbox.subscribeDeviceUuid { }

        Mindbox.disposeDeviceUuidSubscription(subscribeId)

        Assert.assertEquals(true, subscribeId.isUuid())
    }

    @After
    fun clear() {
        clearPreferences()
    }

}
