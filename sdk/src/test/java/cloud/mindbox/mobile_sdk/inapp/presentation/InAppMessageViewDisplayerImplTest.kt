package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import io.mockk.impl.annotations.InjectMockKs
import kotlinx.coroutines.runBlocking
import org.junit.Test

internal class InAppMessageViewDisplayerImplTest {

    @InjectMockKs
    private lateinit var inAppMessageViewDisplayerImpl: InAppMessageViewDisplayer

    //тест под удаление
    /*@Test
    fun `show in-app messages test`() {
        runBlocking {
            whenever(inAppMessageViewDisplayerImpl.showInAppMessage(any(),
                any(),
                any())).thenCallRealMethod()
            inAppMessageViewDisplayerImpl.showInAppMessage(any(), any(), any())
        }
    }*/
}