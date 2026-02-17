package cloud.mindbox.mobile_sdk.inapp.presentation

import cloud.mindbox.mobile_sdk.di.MindboxDI
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before

internal class InAppMessageViewDisplayerImplTest {

    private lateinit var displayer: InAppMessageViewDisplayerImpl

    @Before
    fun setUp() {
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { gson } returns Gson()
        }
        displayer = InAppMessageViewDisplayerImpl(mockk())
    }

    @After
    fun tearDown() {
        unmockkAll()
    }
}
