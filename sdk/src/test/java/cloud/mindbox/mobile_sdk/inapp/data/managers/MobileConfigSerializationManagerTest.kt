package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.di.modules.DataModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.MobileConfigSerializationManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.TreeTargetingDto
import cloud.mindbox.mobile_sdk.models.operation.response.FormDto
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseBlank
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class MobileConfigSerializationManagerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    private lateinit var mobileConfigSerializationManager: MobileConfigSerializationManager
    private val gson = DataModule(mockk(relaxed = true), mockk(relaxed = true)).gson

    @MockK
    private lateinit var fakeGson: Gson

    @Before
    fun onTestStart() {
        mobileConfigSerializationManager = MobileConfigSerializationManagerImpl(gson)
        every {
            fakeGson.fromJson(any<String>(), TreeTargetingDto::class.java)
        } throws Error("errorMessage")
        every {
            fakeGson.fromJson(any<String>(), InAppConfigResponseBlank::class.java)
        } throws Error("errorMessage")
        every {
            fakeGson.fromJson(any<String>(), FormDto::class.java)
        } throws Error("errorMessage")
    }

    @Test
    fun `deserialize to config dto blank success`() {
        val successJson = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": 1,
                    |"max": null
                  |},
                  |"targeting": {
                    |"${'$'}type": "true"
                  |},
                  |"form": {
                    |"variants": [
                      |{
                        |"imageUrl": "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                        |"redirectUrl": "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                        |"intentPayload": "123",
                        |"${'$'}type": "simpleImage"
                      |}
                    |]
                  |}
                |}
              |]
            |}
        """.trimMargin()
        val expectedResult = InAppConfigStub.getConfigResponseBlank().copy(
            inApps = listOf(
                InAppStub.getInAppDtoBlank().copy(
                    id = "040810aa-d135-49f4-8916-7e68dcc61c71",
                    sdkVersion = InAppStub.getSdkVersion().copy(minVersion = 1, maxVersion = null),
                    targeting = JsonObject().apply {
                        addProperty("${'$'}type", "true")
                    },
                    form = JsonObject().apply {
                        add("variants", JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty(
                                    "imageUrl",
                                    "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg"
                                )
                                addProperty(
                                    "redirectUrl",
                                    "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71"
                                )
                                addProperty("${'$'}type", "simpleImage")
                                addProperty("intentPayload", "123")
                            })
                        })
                    }
                )
            )
        )
        val actualResult = mobileConfigSerializationManager.deserializeToConfigDtoBlank(successJson)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to config dto blank empty string`() {
        val invalidJson = ""
        assertNull(mobileConfigSerializationManager.deserializeToConfigDtoBlank(invalidJson))
    }

    @Test
    fun `deserialize to config dto blank invalid json`() {
        val invalidJson = "invalidJson"
        assertNull(mobileConfigSerializationManager.deserializeToConfigDtoBlank(invalidJson))
    }

    @Test
    fun `deserialize to config dto blank throws error`() {
        mobileConfigSerializationManager = MobileConfigSerializationManagerImpl(fakeGson)
        val invalidJson = "invalidJson"
        assertNull(mobileConfigSerializationManager.deserializeToConfigDtoBlank(invalidJson))
    }

    @Test
    fun `deserialize to inApp targetingDto success`() {
        val expectedResult = InAppStub.getTargetingTrueNodeDto().copy(type = "true")
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "true")
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to in app targeting city dto success`() {
        val expectedResult = InAppStub.getTargetingCityNodeDto()
            .copy(type = "city", kind = "positive", listOf("123"))
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "city")
                addProperty("kind", "positive")
                add("ids", JsonArray().apply {
                    add("123")
                })
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to in app targeting country dto success`() {
        val expectedResult = InAppStub.getTargetingCountryNodeDto()
            .copy(type = "country", kind = "positive", listOf("123"))
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "country")
                addProperty("kind", "positive")
                add("ids", JsonArray().apply {
                    add("123")
                })
            })
        assertEquals(expectedResult, actualResult)
    }


    @Test
    fun `deserialize to in app targeting region dto success`() {
        val expectedResult = InAppStub.getTargetingRegionNodeDto()
            .copy(type = "region", kind = "positive", listOf("123"))
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "region")
                addProperty("kind", "positive")
                add("ids", JsonArray().apply {
                    add("123")
                })
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to in app segment dto success`() {
        val expectedResult = InAppStub.getTargetingSegmentNodeDto().copy(
            type = "segment",
            kind = "positive",
            segmentExternalId = "123",
            segmentationExternalId = "213",
            segmentationInternalId = "222"
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "segment")
                addProperty("kind", "positive")
                addProperty("segmentExternalId", "123")
                addProperty("segmentationExternalId", "213")
                addProperty("segmentationInternalId", "222")
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to in app operation dto success`() {
        val expectedResult = InAppStub.getTargetingOperationNodeDto().copy(
            type = "apiMethodCall",
            systemName = "test"
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "apiMethodCall")
                addProperty("systemName", "test")
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to inapp view product category dto success`() {
        val expectedResult = InAppStub.viewProductCategoryNodeDto.copy(
            type = "viewProductCategoryId",
            kind = "substring",
            value = "test"
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "viewProductCategoryId")
                addProperty("kind", "substring")
                addProperty("value", "test")
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to inapp view product category in dto success`() {
        val expectedResult = InAppStub.viewProductCategoryInNodeDto.copy(
            type = "viewProductCategoryIdIn",
            kind = "any",
            values = listOf(
                InAppStub.viewProductCategoryInValueDto.copy(
                    id = "id",
                    externalId = "externalId",
                    externalSystemName = "externalSystemName",
                )
            )
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "viewProductCategoryIdIn")
                addProperty("kind", "any")
                add("values", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("id", "id")
                        addProperty("externalId", "externalId")
                        addProperty("externalSystemName", "externalSystemName")
                    })
                })
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to in app intersection dto success`() {
        val expectedResult = InAppStub.getTargetingIntersectionNodeDto().copy(
            type = "and",
            nodes = listOf(InAppStub.getTargetingTrueNodeDto().copy(type = "true"))
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "and")
                add("nodes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("${'$'}type", "true")
                    })
                })
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to in app union dto success`() {
        val expectedResult = InAppStub.getTargetingUnionNodeDto().copy(
            type = "or",
            nodes = listOf(InAppStub.getTargetingTrueNodeDto().copy(type = "true"))
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "or")
                add("nodes", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("${'$'}type", "true")
                    })
                })
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to inapp view product dto success`() {
        val expectedResult = InAppStub.getTargetingViewProductNodeDto().copy(
            type = "viewProductId",
            kind = "substring",
            value = "test"
        )

        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "viewProductId")
                addProperty("kind", "substring")
                addProperty("value", "test")
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to inapp view product segment dto success`() {
        val expectedResult = InAppStub.getTargetingViewProductSegmentNodeDto().copy(
            type = "viewProductSegment",
            kind = "positive",
            segmentExternalId = "segmentExternalId",
            segmentationInternalId = "segmentationInternalId",
            segmentationExternalId = "segmentationExternalId"
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
                addProperty("${'$'}type", "viewProductSegment")
                addProperty("kind", "positive")
                addProperty("segmentExternalId", "segmentExternalId")
                addProperty("segmentationInternalId", "segmentationInternalId")
                addProperty("segmentationExternalId", "segmentationExternalId")
            })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to inApp targetingDto invalid json object`() {
        assertNull(mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject()))
    }

    @Test
    fun `deserialize to inApp targetingDto null`() {
        assertNull(mobileConfigSerializationManager.deserializeToInAppTargetingDto(null))
    }

    @Test
    fun `deserialize to inApp targetingDto throws error`() {
        mobileConfigSerializationManager = MobileConfigSerializationManagerImpl(fakeGson)
        assertNull(mobileConfigSerializationManager.deserializeToInAppTargetingDto(JsonObject().apply {
            addProperty("${'$'}type", "true")
        }))
    }

    @Test
    fun `deserialize to modal window inApp form dto success`() {
        val color = "#000000"
        val lineWidth = 4.0
        val type = "closeButton"
        val expectedResult = InAppStub.getFormDto().copy(
            variants = listOf(
                InAppStub.getModalWindowDto().copy(
                    type = "modal", content = InAppStub.getContentDto().copy(
                        background = InAppStub.getBackgroundDto().copy(layers = listOf(InAppStub.getImageLayerDto().copy(
                            action = InAppStub.getRedirectUrlActionDto().copy(
                                intentPayload = "123", type = "redirectUrl",
                                value = "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71"
                            ), source = InAppStub.getUrlSourceDto().copy(
                                type = "url", value = "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg"
                            ), type = "image"
                        ))), elements = listOf(InAppStub.getCloseButtonElementDto().copy(
                            color = color, lineWidth = lineWidth, position = InAppStub.getElementPositionDto().copy(
                                margin = InAppStub.getElementMarginDto().copy(
                                    bottom = 0.03,
                                    kind = "proportion",
                                    left = 0.03,
                                    right = 0.03,
                                    top = 0.03
                                )
                            ), size = InAppStub.getElementSizeDto().copy(
                                height = 24.0,
                                kind = "dp",
                                width = 24.0
                            ), type = type
                        ))
                    ),
                )
            )
        )
        val actualResult = mobileConfigSerializationManager.deserializeToInAppFormDto(JsonObject().apply {
            add("variants", JsonArray().apply {
                val variantObject = JsonObject().apply {
                    addProperty("${"$"}type", "modal")
                    add("content", JsonObject().apply {
                        add("background", JsonObject().apply {
                            add("layers", JsonArray().apply {
                                val imageLayerObject = JsonObject().apply {
                                    add("action", JsonObject().apply {
                                        addProperty("intentPayload", "123")
                                        addProperty("${"$"}type", "redirectUrl")
                                        addProperty("value", "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71")
                                    })
                                    add("source", JsonObject().apply {
                                        addProperty("${"$"}type", "url")
                                        addProperty("value", "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg")
                                    })
                                    addProperty("${"$"}type", "image")
                                }
                                add(imageLayerObject)
                            })
                        })
                        add("elements", JsonArray().apply {
                            val elementObject = JsonObject().apply {
                                addProperty("color", color)
                                addProperty("lineWidth", lineWidth)
                                add("position", JsonObject().apply {
                                    add("margin", JsonObject().apply {
                                        addProperty("bottom", 0.03)
                                        addProperty("kind", "proportion")
                                        addProperty("left", 0.03)
                                        addProperty("right", 0.03)
                                        addProperty("top", 0.03)
                                    })
                                })
                                add("size", JsonObject().apply {
                                    addProperty("height", 24.0)
                                    addProperty("kind", "dp")
                                    addProperty("width", 24.0)
                                })
                                addProperty("${"$"}type", type)
                            }
                            add(elementObject)
                        })
                    })
                }
                add(variantObject)
            })
        })
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `deserialize to inApp formDto invalid json object`() {
        assertNull(mobileConfigSerializationManager.deserializeToInAppFormDto(JsonObject()))
    }

    @Test
    fun `deserialize to inApp formDto null`() {
        assertNull(mobileConfigSerializationManager.deserializeToInAppFormDto(null))
    }

    @Test
    fun `deserialize to inApp formDto throws error with unknown json`() {
        mobileConfigSerializationManager = MobileConfigSerializationManagerImpl(fakeGson)
        assertNull(mobileConfigSerializationManager.deserializeToInAppFormDto(JsonObject().apply {
            add("variants", JsonArray().apply {
                val variantObject = JsonObject().apply {
                    addProperty("${"$"}type", "modal")
                    add("content", JsonObject().apply {
                        add("background", JsonObject().apply {
                            add("layers", JsonArray().apply {
                                val imageLayerObject = JsonObject().apply {
                                    add("action", JsonObject().apply {
                                        addProperty("intentPayload", "123")
                                        addProperty("${"$"}type", "redirectUrl")
                                        addProperty("value", "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71")
                                    })
                                    add("source", JsonObject().apply {
                                        addProperty("${"$"}type", "url")
                                        addProperty("value", "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg")
                                    })
                                    addProperty("${"$"}type", "image")
                                }
                                add(imageLayerObject)
                            })
                        })
                        add("elements", JsonArray().apply {
                            val elementObject = JsonObject().apply {
                                addProperty("color", 22)
                                addProperty("lineWidth", "a")
                                add("position", JsonObject().apply {
                                    add("margin", JsonObject().apply {
                                        addProperty("bottom", 0.03)
                                        addProperty("kind", "proportion")
                                        addProperty("left", 0.03)
                                        addProperty("right", 0.03)
                                        addProperty("top", 0.03)
                                    })
                                })
                                add("size", JsonObject().apply {
                                    addProperty("height", 24.0)
                                    addProperty("kind", "dp")
                                    addProperty("width", 24.0)
                                })
                                addProperty("${"$"}type", "qwe")
                            }
                            add(elementObject)
                        })
                    })
                }
                add(variantObject)
            })
        }))
    }

    @Test
    fun `deserialize to inApp formDto throws error`() {
        mobileConfigSerializationManager = MobileConfigSerializationManagerImpl(fakeGson)
        assertNull(mobileConfigSerializationManager.deserializeToInAppFormDto(JsonObject().apply {
            add("variants", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty(
                        "imageUrl",
                        "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg"
                    )
                    addProperty(
                        "redirectUrl",
                        "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71"
                    )
                    addProperty("${'$'}type", "simpleImage")
                    addProperty("intentPayload", "123")
                })
            })
        }))
    }


}