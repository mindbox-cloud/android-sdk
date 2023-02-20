package cloud.mindbox.mobile_sdk.inapp.data.repositories

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.di.domainModule
import cloud.mindbox.mobile_sdk.di.monitoringModule
import cloud.mindbox.mobile_sdk.inapp.data.mapper.InAppMapper
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.validators.InAppValidator
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import cloud.mindbox.mobile_sdk.monitoring.data.validators.MonitoringValidator
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

// also tests InAppMessageMapper
internal class MobileConfigRepositoryTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule, monitoringModule, domainModule)
    }

    private val inAppMapper: InAppMapper by inject()
    private val inAppValidatorImpl: InAppValidator by inject()
    private val monitoringValidator: MonitoringValidator by inject()
    private lateinit var mobileConfigRepository: MobileConfigRepository

    @Before
    fun onTestStart() {
        mobileConfigRepository = MobileConfigRepositoryImpl(
            inAppMapper = inAppMapper,
            context = mockk(),
            inAppValidator = inAppValidatorImpl,
            monitoringValidator = monitoringValidator,
            mobileConfigSerializationManager = mockk()
        )
        mockkObject(MindboxPreferences)
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
            .copy(
                inApps = listOf(
                    InAppStub.getInApp()
                        .copy(
                            id = "040810aa-d135-49f4-8916-7e68dcc61c71",
                            minVersion = 1,
                            maxVersion = null,
                            targeting = InAppStub.getTargetingTrueNode().copy(type = "true"),
                            form = InAppStub.getInApp().form.copy(
                                variants = listOf(
                                    InAppStub.getSimpleImage()
                                        .copy(
                                            type = "simpleImage",
                                            imageUrl = "https://bipbap.ru/wp-content/uploads/2017/06/4-5.jpg",
                                            redirectUrl = "https://mpush-test.mindbox.ru/inapps/040810aa-d135-49f4-8916-7e68dcc61c71",
                                            intentPayload = "123"
                                        )
                                )
                            )
                        )
                )
            )

        val flow: MutableSharedFlow<String> = MutableSharedFlow()
        every { MindboxPreferences.inAppConfigFlow }.answers { flow }
        runBlocking {
            mobileConfigRepository.listenInAppsSection().test {
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
            mobileConfigRepository.listenInAppsSection().test {
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
            mobileConfigRepository.listenInAppsSection().test {
                flow.emit(json)
                val inApps = awaitItem()
                assertEquals(1, inApps?.size)
                assertEquals(validInAppId, inApps?.first()?.id)
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
            mobileConfigRepository.listenInAppsSection().test {
                flow.emit(errorJson)
                assertEquals(expectedResult, awaitItem())
            }
        }
    }
}