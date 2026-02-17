package cloud.mindbox.mobile_sdk.inapp.data.mapper

import cloud.mindbox.mobile_sdk.inapp.data.dto.BackgroundDto
import cloud.mindbox.mobile_sdk.inapp.data.dto.PayloadDto
import cloud.mindbox.mobile_sdk.inapp.domain.models.InAppType
import cloud.mindbox.mobile_sdk.inapp.domain.models.Layer
import cloud.mindbox.mobile_sdk.inapp.domain.models.TreeTargeting
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.TimeSpan
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponse
import cloud.mindbox.mobile_sdk.models.operation.response.InAppDto
import org.junit.Assert.*
import org.junit.Test

class InAppMapperTest {

    @Test
    fun `mapToInAppConfig ids type to string`() {
        val mapper = InAppMapper()

        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = "id",
                        isPriority = false,
                        delayTime = null,
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.UnionNodeDto(
                            type = "or",
                            nodes = listOf(
                                TreeTargetingDto.CountryNodeDto(
                                    type = "country",
                                    kind = "positive",
                                    ids = listOf(1L, 2L, 3L),
                                ),
                                TreeTargetingDto.RegionNodeDto(
                                    type = "region",
                                    kind = "positive",
                                    ids = listOf(4L, 5L, 6L),
                                ),
                                TreeTargetingDto.CityNodeDto(
                                    type = "city",
                                    kind = "positive",
                                    ids = listOf(7L, 8L, 9L),
                                ),
                            ),
                        ),
                        form = null,
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )

        val targeting = result.inApps.first().targeting as TreeTargeting.UnionNode

        val country = targeting.nodes.first() as TreeTargeting.CountryNode
        assertTrue(country.ids.contains("1"))
        assertTrue(country.ids.contains("2"))
        assertTrue(country.ids.contains("3"))

        val region = targeting.nodes[1] as TreeTargeting.RegionNode
        assertTrue(region.ids.contains("4"))
        assertTrue(region.ids.contains("5"))
        assertTrue(region.ids.contains("6"))

        val city = targeting.nodes.last() as TreeTargeting.CityNode
        assertTrue(city.ids.contains("7"))
        assertTrue(city.ids.contains("8"))
        assertTrue(city.ids.contains("9"))
    }

    @Test
    fun `mapToInAppConfig handles isPriority correctly when isPriority true`() {
        val mapper = InAppMapper()

        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = "id",
                        isPriority = true,
                        delayTime = null,
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.UnionNodeDto(
                            type = "or",
                            nodes = listOf(
                                TreeTargetingDto.TrueNodeDto(
                                    type = ""
                                )
                            ),
                        ),
                        form = null,
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )
        assertTrue(result.inApps.first().isPriority)
    }

    @Test
    fun `mapToInAppConfig handles isPriority correctly when isPriority false`() {
        val mapper = InAppMapper()

        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = "id",
                        isPriority = false,
                        delayTime = null,
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.UnionNodeDto(
                            type = "or",
                            nodes = listOf(
                                TreeTargetingDto.TrueNodeDto(
                                    type = ""
                                )
                            ),
                        ),
                        form = null,
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )
        assertFalse(result.inApps.first().isPriority)
    }

    @Test
    fun `mapToInAppConfig handles delayTime correctly when delayTime not null`() {
        val mapper = InAppMapper()

        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = "id",
                        isPriority = false,
                        delayTime = TimeSpan.fromStringOrNull("00:00:30"),
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.UnionNodeDto(
                            type = "or",
                            nodes = listOf(
                                TreeTargetingDto.TrueNodeDto(
                                    type = ""
                                )
                            ),
                        ),
                        form = null,
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )
        assertEquals(result.inApps.first().delayTime?.interval, 30000L)
    }

    @Test
    fun `mapToInAppConfig handles delayTime correctly when delayTime is null`() {
        val mapper = InAppMapper()

        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = "id",
                        isPriority = false,
                        delayTime = null,
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.UnionNodeDto(
                            type = "or",
                            nodes = listOf(
                                TreeTargetingDto.TrueNodeDto(
                                    type = ""
                                )
                            ),
                        ),
                        form = null,
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )
        assertNull(result.inApps.first().delayTime)
    }

    @Test
    fun `mapToInAppConfig maps ModalWindowDto with webview layer to InAppType WebView`() {
        val mapper = InAppMapper()
        val inAppId = "webview-inapp-id"
        val webViewLayerDto = BackgroundDto.LayerDto.WebViewLayerDto(
            baseUrl = "https://inapp.local/popup",
            contentUrl = "https://inapp-dev.html",
            type = BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME,
            params = mapOf("formId" to "73379")
        )
        val modalWindowDto = PayloadDto.ModalWindowDto(
            content = PayloadDto.ModalWindowDto.ContentDto(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(webViewLayerDto)
                ),
                elements = null
            ),
            type = PayloadDto.ModalWindowDto.MODAL_JSON_NAME
        )
        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = inAppId,
                        isPriority = false,
                        delayTime = null,
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.TrueNodeDto(type = ""),
                        form = FormDto(variants = listOf(modalWindowDto))
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )
        val inApp = result.inApps.first()
        assertTrue(inApp.form.variants.first() is InAppType.WebView)
        val webView = inApp.form.variants.first() as InAppType.WebView
        assertEquals(inAppId, webView.inAppId)
        assertEquals(BackgroundDto.LayerDto.WebViewLayerDto.WEBVIEW_TYPE_JSON_NAME, webView.type)
        assertEquals(1, webView.layers.size)
        assertTrue(webView.layers.first() is Layer.WebViewLayer)
        val layer = webView.layers.first() as Layer.WebViewLayer
        assertEquals("https://inapp.local/popup", layer.baseUrl)
        assertEquals("https://inapp-dev.html", layer.contentUrl)
        assertEquals(mapOf("formId" to "73379"), layer.params)
    }

    @Test
    fun `mapToInAppConfig maps ModalWindowDto with image layer to InAppType ModalWindow`() {
        val mapper = InAppMapper()
        val inAppId = "modal-inapp-id"
        val modalWindowDto = InAppStub.getModalWindowDto().copy(
            content = InAppStub.getModalWindowContentDto().copy(
                background = InAppStub.getBackgroundDto().copy(
                    layers = listOf(InAppStub.getImageLayerDto())
                ),
                elements = emptyList()
            )
        )
        val result = mapper.mapToInAppConfig(
            InAppConfigResponse(
                inApps = listOf(
                    InAppDto(
                        id = inAppId,
                        isPriority = false,
                        delayTime = null,
                        frequency = FrequencyDto.FrequencyOnceDto(
                            type = "once",
                            kind = "lifetime",
                        ),
                        sdkVersion = null,
                        targeting = TreeTargetingDto.TrueNodeDto(type = ""),
                        form = FormDto(variants = listOf(modalWindowDto))
                    )
                ),
                monitoring = null,
                abtests = null,
                settings = null,
            )
        )
        val inApp = result.inApps.first()
        assertTrue(inApp.form.variants.first() is InAppType.ModalWindow)
        val modalWindow = inApp.form.variants.first() as InAppType.ModalWindow
        assertEquals(inAppId, modalWindow.inAppId)
        assertEquals(PayloadDto.ModalWindowDto.MODAL_JSON_NAME, modalWindow.type)
        assertEquals(1, modalWindow.layers.size)
        assertTrue(modalWindow.layers.first() is Layer.ImageLayer)
    }
}
