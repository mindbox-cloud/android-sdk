package cloud.mindbox.mindbox_rustore

import android.app.Application
import android.content.Context
import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.rustore.sdk.pushclient.RuStorePushClient

class RuStoreServiceHandlerTest {

    private val application = mockk<Application>()
    private val context = mockk<Context> {
        every { applicationContext } returns application
    }
    private val exceptionHandler = object : ExceptionHandler() {
        override fun handle(exception: Throwable) = throw exception
    }

    @After
    fun tearDown() {
        MindboxRuStore.projectId = ""
        unmockkAll()
    }

    @Test
    fun `a handler created before the project id is set starts RuStore with that id`() = runTest {
        val projectId = slot<String>()
        mockkObject(RuStorePushClient)
        every { RuStorePushClient.init(any(), capture(projectId), any(), any(), any(), any(), any()) } just runs
        val handler = MindboxRuStore.getServiceHandler(mockk<MindboxLogger>(relaxed = true), exceptionHandler)

        MindboxRuStore("project-id")
        handler.initService(context)

        assertEquals("project-id", projectId.captured)
    }
}
