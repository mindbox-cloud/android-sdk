package cloud.mindbox.mobile_sdk.inapp.data

import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseStub
import cloud.mindbox.mobile_sdk.models.operation.response.PayloadDto
import com.google.gson.JsonSyntaxException
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
class InAppRepositoryImplTest {

    @Mock
    private lateinit var inAppRepository: InAppRepositoryImpl


    @Before
    fun setUp() {
        whenever(inAppRepository.deserializeConfigToConfigDto(InAppConfigResponseStub.getConfigResponseJson())).thenCallRealMethod()
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

    @Test(expected = Error::class)
    fun `simple image response mapping malformed test`() {
        assertThrows(JsonSyntaxException::class.java) {
            inAppRepository.deserializeConfigToConfigDto(InAppConfigResponseStub.getConfigResponseMalformedJson())
        }
    }


}