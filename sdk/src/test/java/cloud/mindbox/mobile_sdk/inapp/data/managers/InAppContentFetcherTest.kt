package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.InAppImageLoader
import io.mockk.mockk
import org.junit.Test

internal class InAppContentFetcherTest {

    private val inAppImageLoader: InAppImageLoader = mockk()

    private val inAppContentFetcher = InAppContentFetcherImpl(inAppImageLoader)

    @Test
    fun `fetching content of modal window success`() { }
}
