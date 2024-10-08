package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test

class FmsTokenRepeatedUnitTest {

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
        var result: String? = ""

        Thread.sleep(5000)

        Mindbox.subscribePushToken { pushToken ->
            result = pushToken
        }

        Thread.sleep(3000)

        Assert.assertEquals(true, result?.isUuid() ?: true)
    }
}
