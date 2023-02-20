package cloud.mindbox.mobile_sdk.inapp.data.managers

import cloud.mindbox.mobile_sdk.di.dataModule
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import kotlin.test.assertNull

internal class MobileConfigSerializationManagerTest : KoinTest {

    private lateinit var mobileConfigSerializationManager: MobileConfigSerializationManager

    private val gson: Gson by inject()

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var fakeGson: Gson

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
    }

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
    fun `deserialize to inApp form dto success`() {
        val expectedResult = InAppStub.getFormDto().copy(
            variants = listOf(
                InAppStub.getPayloadSimpleImage().copy(
                    type = "simpleImage",
                    imageUrl = "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                    redirectUrl = "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                    intentPayload = "123"

                )
            )
        )
        val actualResult =
            mobileConfigSerializationManager.deserializeToInAppFormDto(JsonObject().apply {
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