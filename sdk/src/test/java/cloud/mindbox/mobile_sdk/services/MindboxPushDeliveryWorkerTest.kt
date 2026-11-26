package cloud.mindbox.mobile_sdk.services

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import cloud.mindbox.mobile_sdk.MindboxWorkerFactory
import cloud.mindbox.mobile_sdk.managers.PushDeliveryWorkerDelegate
import cloud.mindbox.mobile_sdk.utils.mockLogger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.runs
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MindboxPushDeliveryWorkerTest {

    private val context: Context = mockk(relaxed = true) {
        every { applicationContext } returns this
    }

    @Before
    fun setUp() {
        mockLogger()
        mockkConstructor(PushDeliveryWorkerDelegate::class)
        coEvery { anyConstructed<PushDeliveryWorkerDelegate>().send(any(), any()) } just runs
        coEvery { anyConstructed<PushDeliveryWorkerDelegate>().sendQueue(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun worker(input: Data) = MindboxPushDeliveryWorker(
        context,
        mockk<WorkerParameters>(relaxed = true) { every { inputData } returns input },
    )

    @Test
    fun `worker sends the confirmation of the row from its input`() = runTest {
        val result = worker(MindboxPushDeliveryWorker.inputData(7L)).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { anyConstructed<PushDeliveryWorkerDelegate>().send(context, 7L) }
        coVerify(exactly = 0) { anyConstructed<PushDeliveryWorkerDelegate>().sendQueue(any(), any(), any()) }
    }

    @Test
    fun `worker without a row id sends the queue with the 429 and failure counts from its input`() = runTest {
        val result = worker(MindboxPushDeliveryWorker.queueInputData(throttles = 2, failures = 3)).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 1) { anyConstructed<PushDeliveryWorkerDelegate>().sendQueue(context, 2, 3) }
        coVerify(exactly = 0) { anyConstructed<PushDeliveryWorkerDelegate>().send(any(), any()) }
    }

    @Test
    fun `unexpected error fails the work instead of crashing the process`() = runTest {
        coEvery { anyConstructed<PushDeliveryWorkerDelegate>().send(any(), any()) } throws IllegalStateException("db is closed")
        coEvery { anyConstructed<PushDeliveryWorkerDelegate>().sendQueue(any(), any(), any()) } throws IllegalStateException("db is closed")

        assertEquals(ListenableWorker.Result.failure(), worker(MindboxPushDeliveryWorker.inputData(7L)).doWork())
        assertEquals(ListenableWorker.Result.failure(), worker(Data.EMPTY).doWork())
    }

    @Test
    fun `error that is not an exception also fails the work`() = runTest {
        coEvery { anyConstructed<PushDeliveryWorkerDelegate>().send(any(), any()) } throws NoClassDefFoundError("missing class")

        assertEquals(ListenableWorker.Result.failure(), worker(MindboxPushDeliveryWorker.inputData(7L)).doWork())
    }

    @Test
    fun `stopped worker passes the cancellation on`() = runTest {
        coEvery { anyConstructed<PushDeliveryWorkerDelegate>().send(any(), any()) } throws CancellationException("stopped")
        val worker = worker(MindboxPushDeliveryWorker.inputData(7L))

        assertThrows(CancellationException::class.java) { kotlinx.coroutines.runBlocking { worker.doWork() } }
    }

    @Test
    fun `custom worker factory creates the push delivery worker and the event worker`() {
        val parameters = mockk<WorkerParameters>(relaxed = true)

        assertTrue(
            MindboxWorkerFactory.createWorker(context, MindboxPushDeliveryWorker::class.java.name, parameters)
                is MindboxPushDeliveryWorker
        )
        assertTrue(
            MindboxWorkerFactory.createWorker(context, MindboxOneTimeEventWorker::class.java.name, parameters)
                is MindboxOneTimeEventWorker
        )
        assertNull(MindboxWorkerFactory.createWorker(context, "com.client.OtherWorker", parameters))
    }
}
