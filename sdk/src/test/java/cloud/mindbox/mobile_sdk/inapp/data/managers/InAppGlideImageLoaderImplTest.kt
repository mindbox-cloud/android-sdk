package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap
import cloud.mindbox.mobile_sdk.R
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageSizeStorage
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppContentFetchingError
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppGlideImageLoaderImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var context: Context

    @MockK
    private lateinit var resources: Resources

    @RelaxedMockK
    private lateinit var inAppImageSizeStorage: InAppImageSizeStorage

    @RelaxedMockK
    private lateinit var requestManager: RequestManager

    @RelaxedMockK
    private lateinit var requestBuilder: RequestBuilder<Drawable>

    private val testDispatcher = StandardTestDispatcher()
    private val listenerSlot = slot<RequestListener<Drawable>>()
    private lateinit var loader: InAppGlideImageLoaderImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val displayMetrics = DisplayMetrics().apply {
            widthPixels = 1080
            heightPixels = 1920
        }
        every { context.resources } returns resources
        every { resources.displayMetrics } returns displayMetrics
        every { context.getString(R.string.mindbox_inapp_fetching_timeout) } returns "3000"

        mockkStatic(Glide::class)
        every { Glide.with(any<Context>()) } returns requestManager
        every { requestManager.load(any<String>()) } returns requestBuilder
        every { requestBuilder.timeout(any()) } returns requestBuilder
        every { requestBuilder.diskCacheStrategy(any()) } returns requestBuilder
        every { requestBuilder.override(any<Int>(), any<Int>()) } returns requestBuilder
        every { requestBuilder.centerInside() } returns requestBuilder
        every { requestBuilder.listener(capture(listenerSlot)) } returns requestBuilder
        every { requestBuilder.preload(any<Int>(), any<Int>()) } returns mockk()

        loader = InAppGlideImageLoaderImpl(context, inAppImageSizeStorage)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    // region loadImage — success

    @Test
    fun `loadImage returns true when image loaded successfully`() = runTest {
        val bitmap = mockk<Bitmap> {
            every { width } returns 500
            every { height } returns 300
        }
        val drawable = mockk<Drawable>(relaxed = true)
        mockkStatic("androidx.core.graphics.drawable.DrawableKt")
        every { drawable.toBitmap(any(), any(), any()) } returns bitmap

        var result: Boolean? = null
        val job = launch { result = loader.loadImage("id1", URL) }

        runCurrent()
        listenerSlot.captured.onResourceReady(drawable, null, null, null, false)
        runCurrent()
        job.join()

        assertTrue(result == true)
    }

    @Test
    fun `loadImage stores image dimensions on success`() = runTest {
        val bitmap = mockk<Bitmap> {
            every { width } returns 500
            every { height } returns 300
        }
        val drawable = mockk<Drawable>(relaxed = true)
        mockkStatic("androidx.core.graphics.drawable.DrawableKt")
        every { drawable.toBitmap(any(), any(), any()) } returns bitmap

        val job = launch { runCatching { loader.loadImage("id1", URL) } }

        runCurrent()
        listenerSlot.captured.onResourceReady(drawable, null, null, null, false)
        runCurrent()
        job.join()

        verify(exactly = 1) { inAppImageSizeStorage.addSize("id1", URL, 500, 300) }
    }

    // endregion

    // region loadImage — failure

    @Test
    fun `loadImage throws InAppContentFetchingError when Glide reports failure`() = runTest {
        var thrownException: Throwable? = null
        val job = launch {
            runCatching { loader.loadImage("id1", URL) }.onFailure { thrownException = it }
        }

        runCurrent()
        listenerSlot.captured.onLoadFailed(mockk<GlideException>(), null, null, false)
        runCurrent()
        job.join()

        assertTrue(thrownException is InAppContentFetchingError)
    }

    @Test
    fun `loadImage throws InAppContentFetchingError when onResourceReady callback throws`() = runTest {
        val drawable = mockk<Drawable>(relaxed = true)
        mockkStatic("androidx.core.graphics.drawable.DrawableKt")
        every { drawable.toBitmap(any(), any(), any()) } throws RuntimeException("decode error")

        var thrownException: Throwable? = null
        val job = launch {
            runCatching { loader.loadImage("id1", URL) }.onFailure { thrownException = it }
        }

        runCurrent()
        listenerSlot.captured.onResourceReady(drawable, null, null, null, false)
        runCurrent()
        job.join()

        assertTrue(thrownException is InAppContentFetchingError)
    }

    // endregion

    // region loadImage — timeout

    @Test
    fun `loadImage throws InAppContentFetchingError when timeout expires`() = runTest {
        var thrownException: Throwable? = null
        val job = launch {
            runCatching { loader.loadImage("id1", URL) }.onFailure { thrownException = it }
        }

        advanceTimeBy(3001)
        job.join()

        assertTrue(thrownException is InAppContentFetchingError)
    }

    @Test
    fun `loadImage does not complete before timeout when no callback fires`() = runTest {
        var completed = false
        val job = launch {
            runCatching { loader.loadImage("id1", URL) }
            completed = true
        }

        advanceTimeBy(2999)
        assertFalse("Job must still be active before timeout", completed)

        job.cancel()
    }

    // endregion

    // region cancelLoading

    @Test
    fun `cancelLoading clears Glide request for given inAppId`() = runTest {
        val target = mockk<Target<Drawable>>(relaxed = true)
        every { requestBuilder.preload(any<Int>(), any<Int>()) } returns target

        val job = launch { runCatching { loader.loadImage("id1", URL) } }
        runCurrent()

        loader.cancelLoading("id1")

        verify { requestManager.clear(target) }
        job.cancel()
    }

    @Test
    fun `cancelLoading is called when coroutine is cancelled by timeout`() = runTest {
        val target = mockk<Target<Drawable>>(relaxed = true)
        every { requestBuilder.preload(any<Int>(), any<Int>()) } returns target

        val job = launch { runCatching { loader.loadImage("id1", URL) } }
        runCurrent()

        advanceTimeBy(3001)
        job.join()

        verify { requestManager.clear(target) }
    }

    // endregion

    private companion object {
        const val URL = "https://example.com/image.jpg"
    }
}
