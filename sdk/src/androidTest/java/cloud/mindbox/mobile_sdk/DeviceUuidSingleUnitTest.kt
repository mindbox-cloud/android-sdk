package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
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

        setDatabaseTestMode(true)
        Mindbox.init(appContext, configs, listOf())

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

        setDatabaseTestMode(true)
        Mindbox.init(appContext, configs, listOf())

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        Mindbox.disposeDeviceUuidSubscription("wrong_subscribe")
        Mindbox.disposeDeviceUuidSubscription("")

        setDatabaseTestMode(true)
        initCoreComponents()
    }

    @After
    fun clear() {
        clearPreferences()
    }
}
