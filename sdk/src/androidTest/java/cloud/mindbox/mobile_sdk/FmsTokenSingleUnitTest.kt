package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.models.isUuid
import org.junit.After
import org.junit.Assert
import org.junit.Test

class FmsTokenSingleUnitTest {

    @Test
    fun subscribe_isCorrectReturn() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        Mindbox.subscribePushToken { pushToken ->
            result = pushToken
        }

        val configs = MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()

        setDatabaseTestMode(true)
        Mindbox.init(appContext, configs, listOf())

        Thread.sleep(10000)

        Assert.assertEquals(true, result?.isUuid() ?: true)
    }

    @Test
    fun unsubscribe_isCorrect() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        var result: String? = ""

        val subscribeId = Mindbox.subscribePushToken { pushToken ->
            result = pushToken
        }

        Mindbox.disposePushTokenSubscription(subscribeId)

        Thread.sleep(3000)

        val configs = MindboxConfiguration.Builder(appContext, "example.com", "someEndpoint")
            .build()

        setDatabaseTestMode(true)
        Mindbox.init(appContext, configs, listOf())

        Thread.sleep(5000)

        Assert.assertEquals("", result)
    }

    @Test
    fun wrongUnsubscribe_isCorrect() {
        Mindbox.disposePushTokenSubscription("wrong_subscribe")
        Mindbox.disposePushTokenSubscription("")

        setDatabaseTestMode(true)
        initCoreComponents()
    }

    @Test
    fun subscribeIdGeneration_isCorrect() {
        val subscribeId = Mindbox.subscribePushToken { }

        Mindbox.disposePushTokenSubscription(subscribeId)

        Assert.assertEquals(true, subscribeId.isUuid())
    }

    @After
    fun clear() {
        clearPreferences()
    }

}
