package cloud.mindbox.mobile_sdk.inapp.data

import android.content.Context
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseStub
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

internal class InAppRepositoryImplTest {

    @MockK
    private lateinit var inAppMessageMapper: InAppMessageMapper

    @MockK
    private lateinit var gson: Gson

    @MockK
    private lateinit var context: Context

    @OverrideMockKs
    private lateinit var inAppRepository: InAppRepositoryImpl

    @Before
    fun onTestStart() {
        MockKAnnotations.init(this)
        mockkObject(MindboxPreferences)
    }

    @Test
    fun `shown inApp ids is not empty and is a valid json`() {
        every { MindboxPreferences.shownInAppIds } returns "[&quot;71110297-58ad-4b3c-add1-60df8acb9e5e&quot;,&quot;ad487f74-924f-44f0-b4f7-f239ea5643c5&quot;]"
        val tmp = MindboxPreferences.shownInAppIds
        { inAppRepository.shownInApps }.shouldNotThrow()
        verify(exactly = 1) {
            gson.fromJson(tmp,
                object : TypeToken<HashSet<String>>() {}.type)
        }
    }

    @Test
    fun `shown inApp ids empty`() {
        every { MindboxPreferences.shownInAppIds } returns ""
        val tmp = MindboxPreferences.shownInAppIds
        inAppRepository.shownInApps
        verify(exactly = 0) {
            gson.fromJson(tmp,
                object : TypeToken<HashSet<String>>() {}.type)
        }
    }

    @Test
    fun `shown inApp ids is not empty and is not a json`() {
        every { MindboxPreferences.shownInAppIds } returns "123"
        { inAppRepository.shownInApps }.shouldNotThrow()
    }

    private fun (() -> Any?).shouldNotThrow() = try {
        invoke()
    } catch (ex: Exception) {
        throw Error("expected not to throw!", ex)
    }

    @Test
    fun `simple image response mapping success test`() {
        val rez =
            inAppRepository.deserializeConfigToConfigDto(InAppConfigResponseStub.getConfigResponseJson())
        assertTrue((rez != null) && (rez.inApps!!.first().form?.variants!!.first() is PayloadDto.SimpleImage))
    }

    @Test
    fun `simple image response mapping error test`() {
        assertNull(inAppRepository.deserializeConfigToConfigDto(InAppConfigResponseStub.getConfigResponseErrorJson())
        )
    }

    @Test
    fun `simple image response mapping empty string test`() {
        assertNull(inAppRepository.deserializeConfigToConfigDto("")
        )
    }

    @Test
    fun `simple image response mapping malformed test`() {
        assertNull(inAppRepository.deserializeConfigToConfigDto(InAppConfigResponseStub.getConfigResponseMalformedJson()))
    }


}