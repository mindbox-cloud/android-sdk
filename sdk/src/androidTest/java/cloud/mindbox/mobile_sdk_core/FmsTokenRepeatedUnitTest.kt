package cloud.mindbox.mobile_sdk_core

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk_core.repository.MindboxDatabase
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
            val configs = cloud.mindbox.mobile_sdk.MindboxConfiguration.Builder(appContext, "epi.ru", "some").build()
            MindboxDatabase.isTestMode = true
            MindboxInternalCore.init(appContext, configs)
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

        MindboxInternalCore.subscribeFmsToken { fmsToken ->
            result = fmsToken
        }

        Thread.sleep(3000)

        Assert.assertEquals(true, result?.isUuid() ?: true)
    }

}
