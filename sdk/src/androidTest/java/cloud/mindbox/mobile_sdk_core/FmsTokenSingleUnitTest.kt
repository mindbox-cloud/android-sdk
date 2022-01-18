package cloud.mindbox.mobile_sdk_core

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk_core.repository.MindboxDatabase
import org.junit.After
import org.junit.Assert
import org.junit.Test

class FmsTokenSingleUnitTest {

    @Test
    fun subscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        MindboxInternalCore.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        val configs = cloud.mindbox.mobile_sdk.MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        MindboxDatabase.isTestMode = true
        MindboxInternalCore.init(appContext, configs)

        Thread.sleep(10000)

        Assert.assertEquals(true, result?.isUuid() ?: true)
    }

    @Test
    fun unsubscribe_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        val subscribeId = MindboxInternalCore.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        MindboxInternalCore.disposeFmsTokenSubscription(subscribeId)

        Thread.sleep(3000)

        val configs = cloud.mindbox.mobile_sdk.MindboxConfiguration.Builder(appContext, "example.com", "someEndpoint").build()

        MindboxDatabase.isTestMode = true
        MindboxInternalCore.init(appContext, configs)

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        MindboxInternalCore.disposeFmsTokenSubscription("wrong_subscribe")
        MindboxInternalCore.disposeFmsTokenSubscription("")

        MindboxDatabase.isTestMode = true
        MindboxInternalCore.initComponents(InstrumentationRegistry.getInstrumentation().targetContext) //for cancel method after test
    }

    @Test
    fun subscribeIdGeneration_isCorrect() {
        val subscribeId = MindboxInternalCore.subscribeFmsToken { }

        MindboxInternalCore.disposeFmsTokenSubscription(subscribeId)

        Assert.assertEquals(true, subscribeId.isUuid())
    }

    @After
    fun clear() {
        clearPreferences()
    }

}
