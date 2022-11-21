package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.inapp.domain.InAppMessageViewDisplayer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

internal class InAppMessageViewDisplayerImplTest {

    @Mock
    private lateinit var inAppMessageViewDisplayerImpl: InAppMessageViewDisplayer

    //тест под удаление
    @Test
    fun `show in-app messages test`() {
        runBlocking {
            whenever(inAppMessageViewDisplayerImpl.showInAppMessage(any(),
                any(),
                any())).thenCallRealMethod()
            inAppMessageViewDisplayerImpl.showInAppMessage(any(), any(), any())
        }
    }
}