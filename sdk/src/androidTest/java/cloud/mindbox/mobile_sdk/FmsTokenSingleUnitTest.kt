package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.repository.MindboxDatabase
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import org.junit.After
import org.junit.Assert
import org.junit.Test

class FmsTokenSingleUnitTest {

    @Test
    fun subscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        Mindbox.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        val configs = MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        MindboxDatabase.isTestMode = true
        Mindbox.init(appContext, configs)

        Thread.sleep(10000)

        Assert.assertEquals(true, result?.isUuid() ?: true)
    }

    @Test
    fun unsubscribe_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        val subscribeId = Mindbox.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        Mindbox.disposeFmsTokenSubscription(subscribeId)

        Thread.sleep(3000)

        val configs = MindboxConfiguration.Builder(appContext, "example.com", "someEndpoint").build()

        MindboxDatabase.isTestMode = true
        Mindbox.init(appContext, configs)

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        Mindbox.disposeFmsTokenSubscription("wrong_subscribe")
        Mindbox.disposeFmsTokenSubscription("")

        MindboxDatabase.isTestMode = true
        Mindbox.initComponents(InstrumentationRegistry.getInstrumentation().targetContext) //for cancel method after test
    }

    @Test
    fun subscribeIdGeneration_isCorrect() {
        val subscribeId = Mindbox.subscribeFmsToken { }

        Mindbox.disposeFmsTokenSubscription(subscribeId)

        Assert.assertEquals(true, subscribeId.isUuid())
    }

    @After
    fun clear() {
        clearPreferences()
    }

}
