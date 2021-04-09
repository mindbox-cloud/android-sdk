package cloud.mindbox.mobile_sdk

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.models.Event
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.UpdateData
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.AfterClass
import org.junit.Assert
import org.junit.Test
import java.util.*

class VersioningUnitTest {
    private val eventList = Collections.synchronizedList(ArrayList<UpdateData>())

    companion object {
        @AfterClass
        @JvmStatic
        fun clearData() {

            clearPreferences()
        }
    }

    @Test
    fun generatedData_isCorrect() {

        Hawk.init(InstrumentationRegistry.getInstrumentation().targetContext)


        val scope = CoroutineScope(Dispatchers.Default)

        scope.launch {
            val coroutines =
                1.rangeTo(1000).map {
                    launch {
                        createEvent()
                    }
                }

            coroutines.forEach { corotuine ->
                corotuine.join() // wait for all coroutines to finish their jobs.
            }
        }

        Thread.sleep(40000)

        val result = eventsIsUniq()

        Assert.assertEquals(true, result)
    }

    private fun createEvent() {
        val event = UpdateData(
            token = UUID.randomUUID().toString(),
            isTokenAvailable = true,
            isNotificationsEnabled = true,
            version = MindboxPreferences.infoUpdatedVersion
        )

        eventList.add(event)
    }

    private fun eventsIsUniq(): Boolean {
        val idList = ArrayList<Int>()
        var result = true

        eventList.forEach { event ->
            if (idList.contains(event.version)) {
                result = false
            } else {
                idList.add(event.version)
            }
        }
        return result
    }

}