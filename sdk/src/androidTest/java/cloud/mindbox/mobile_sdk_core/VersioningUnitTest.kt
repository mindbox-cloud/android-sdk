package cloud.mindbox.mobile_sdk_core

import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk_core.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk_core.models.UpdateData
import cloud.mindbox.mobile_sdk_core.repository.MindboxPreferences
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
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        SharedPreferencesManager.with(appContext)

        val scope = CoroutineScope(Dispatchers.Default)
        MindboxPreferences.instanceId = IdentifierManager.generateRandomUuid()

        scope.launch {
            val coroutines =
                1.rangeTo(1000).map {
                    launch { createEvent() }
                }

            coroutines.forEach { coroutine -> coroutine.join() }
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
            instanceId = MindboxPreferences.instanceId,
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
