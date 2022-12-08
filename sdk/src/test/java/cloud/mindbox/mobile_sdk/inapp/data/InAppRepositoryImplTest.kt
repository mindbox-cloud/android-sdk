package cloud.mindbox.mobile_sdk.inapp.data

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.inapp.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.domain.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.mapper.InAppMessageMapper
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

// also tests Gson RuntimeTypeAdapterFactory deserialization and InAppMessageMapper
@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppRepositoryImplTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
    }

    private val gson: Gson by inject()
    private val inAppMessageMapper: InAppMessageMapper by inject()
    private val inAppValidatorImpl: InAppValidator by inject()

    private lateinit var inAppRepository: InAppRepositoryImpl

    @Before
    fun onTestStart() {
        inAppRepository = InAppRepositoryImpl(
            inAppMapper = inAppMessageMapper,
            gson = gson,
            context = mockk(),
            inAppValidator = inAppValidatorImpl
        )
        mockkObject(MindboxPreferences)
    }

    @Test
    fun `shown inApp ids is not empty and is a valid json`() {
        val testHashSet = hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5")
        every { MindboxPreferences.shownInAppIds } returns
                "[\"71110297-58ad-4b3c-add1-60df8acb9e5e\",\"ad487f74-924f-44f0-b4f7-f239ea5643c5\"]"
        assertTrue(inAppRepository.getShownInApps().containsAll(testHashSet))
    }

    @Test
    fun `shownInApp ids returns null`() {
        every { MindboxPreferences.shownInAppIds } returns "a"
        assertNotNull(inAppRepository.getShownInApps())
    }

    @Test
    fun `shown inApp ids empty`() {
        val expectedIds = hashSetOf<String>()
        every { MindboxPreferences.shownInAppIds } returns ""
        val actualIds = inAppRepository.getShownInApps()
        assertTrue(expectedIds.containsAll(actualIds))
    }

    @Test
    fun `shown inApp ids is not empty and is not a json`() {
        every { MindboxPreferences.shownInAppIds } returns "123"
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppRepository.getShownInApps()
        assertTrue(actualResult.containsAll(expectedResult))
    }

    @Test
    fun `save shown inApp success`() {
        val expectedJson = """
            |["123","456"]
        """.trimMargin()
        every { MindboxPreferences.shownInAppIds } returns "[123]"
        inAppRepository.saveShownInApp("456")
        verify(exactly = 1) {
            MindboxPreferences.shownInAppIds = expectedJson
        }
    }

    @Test
    fun `simple image response mapping success test`() {
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

        val expectedResult = InAppConfigStub.getConfig()
            .copy(inApps = listOf(InAppStub.getInApp()
                .copy(id = "040810aa-d135-49f4-8916-7e68dcc61c71",
                    minVersion = 1,
                    maxVersion = null,
                    targeting = InAppStub.getTargetingTrueNode().copy(type = "true"),
                    form = InAppStub.getInApp().form.copy(variants = listOf(InAppStub.getSimpleImage()
                        .copy(
                            type = "simpleImage",
                            imageUrl = "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                            redirectUrl = "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                            intentPayload = "123"))))))

        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(successJson)
                assertEquals(expectedResult, awaitItem())
            }
        }
    }

    @Test
    fun `simple image response mapping invalid json test`() {
        val errorJson = "123"
        val expectedResult = InAppConfigStub.getConfig()
            .copy(inApps = listOf())

        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(errorJson)
                assertEquals(expectedResult, awaitItem())
            }
        }
    }

    @Test
    fun `inApp with unknown version and type and targeting filtered test`() {
        val unknownInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1
        val validInAppId = "040810aa-d135-49f4-8916-7e68dcc61c71"
        val json = """
                |{
                  |"inapps": [
                    |{
                      |"id": "11111111-d135-49f4-8916-7e68dcc61c70",
                      |"sdkVersion": {
                       |"min": 1,
                       |"max": null
                      |},
                      |"targeting": {
                        |"unknown": "unknown"
                      |},
                      |"form": {
                        |"variants": [
                          |{
                            |"unknown": "123",
                            |"${'$'}type": "unknownType"
                          |}
                        |]
                      |}
                    |},
                    |{
                      |"id": "11111111-d135-49f4-8916-7e68dcc61c71",
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
                            |"unknown": "123",
                            |"${'$'}type": "unknownType"
                          |}
                        |]
                      |}
                    |},
                    |{
                      |"id": "11111111-d135-49f4-8916-7e68dcc61c72",
                      |"sdkVersion": {
                        |"min": $unknownInAppVersion,
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
                    |},
                    |{
                      |"id": "$validInAppId",
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(1, config?.inApps?.size)
                assertEquals(validInAppId, config?.inApps?.first()?.id)
            }
        }
    }

    @Test
    fun `simple image response mapping empty string test`() {
        val errorJson = ""
        val expectedResult = InAppConfigStub.getConfig()
            .copy(inApps = listOf())

        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(errorJson)
                assertEquals(expectedResult, awaitItem())
            }
        }
    }

    @Test
    fun `in-app version is lower than required`() {
        val lowInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": 1,
                    |"max": $lowInAppVersion
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(true, config?.inApps.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `in-app version is higher than required`() {
        val highInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": $highInAppVersion,
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(true, config?.inApps.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `in-app version is out of range`() {
        val lowInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1
        val highInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": $highInAppVersion,
                    |"max": $lowInAppVersion
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(true, config?.inApps.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `in-app version no min version`() {
        val highInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": null,
                    |"max": $highInAppVersion
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(false, config?.inApps.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `in-app version no max version`() {
        val lowInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": $lowInAppVersion,
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(false, config?.inApps.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `in-app version no limitations`() {
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": null,
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(false, config?.inApps.isNullOrEmpty())
            }
        }
    }

    @Test
    fun `in-app version is in range`() {
        val lowInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1
        val highInAppVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1
        val json = """
            |{
              |"inapps": [
                |{
                  |"id": "040810aa-d135-49f4-8916-7e68dcc61c71",
                  |"sdkVersion": {
                    |"min": $lowInAppVersion,
                    |"max": $highInAppVersion
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
        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            inAppRepository.listenInAppConfig().test {
                flow.emit(json)
                val config = awaitItem()
                assertEquals(false, config?.inApps.isNullOrEmpty())
            }
        }
    }
}