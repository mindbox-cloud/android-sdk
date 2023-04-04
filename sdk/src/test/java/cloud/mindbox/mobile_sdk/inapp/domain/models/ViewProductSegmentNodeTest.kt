package cloud.mindbox.mobile_sdk.inapp.domain.models

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.di.domainModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.ProductSegmentationResponseStub
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.assertFalse
import kotlin.test.assertTrue


@OptIn(ExperimentalCoroutinesApi::class)
class ViewProductSegmentNodeTest : KoinTest {

    private lateinit var mobileConfigRepository: MobileConfigRepository

    private lateinit var inAppSegmentationRepository: InAppSegmentationRepository

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule, domainModule)
        androidContext(mockkClass(Context::class))
    }

    @get:Rule
    val mockProvider = MockProviderRule.create { clazz ->
        mockkClass(clazz)
    }

    @get:Rule
    val mockkRule = MockKRule(this)


    @Before
    fun onTestStart() = runTest {
        mockkObject(MindboxKoin)
        mockkObject(MindboxEventManager)
        every { MindboxKoin.koin } returns getKoin()
        mobileConfigRepository = declareMock()
        inAppSegmentationRepository = declareMock()
        every {
            runBlocking { mobileConfigRepository.getOperations() }
        } returns mapOf(
            OperationName.VIEW_PRODUCT to OperationSystemName("TestSystemNameProduct"),
            OperationName.VIEW_CATEGORY to OperationSystemName("TestSystemNameCategory"),
        )
        MindboxEventManager.eventFlow.resetReplayCache()
    }

    @Test
    fun `hasOperationNode always true`() {
        Assert.assertTrue(InAppStub.viewProductSegmentNode.hasOperationNode())
    }

    @Test
    fun `hasGeoNode always false`() {
        Assert.assertFalse(InAppStub.viewProductSegmentNode.hasGeoNode())
    }

    @Test
    fun `hasSegmentationNode always false`() {
        Assert.assertFalse(InAppStub.viewProductSegmentNode.hasSegmentationNode())
    }

    @Test
    fun `check targeting positive success`() = runTest {

        val productSegmentation =
            ProductSegmentationResponseStub.getProductSegmentationResponseWrapper()
                .copy(
                    productSegmentations = listOf(
                        ProductSegmentationResponseStub.getProductResponse().copy(
                            productList = listOf(
                                ProductSegmentationResponseStub.getProductSegmentationsResponse()
                                    .copy(
                                        segmentationExternalId = "segmentationExternalId",
                                        segmentExternalId = "segmentExternalId"
                                    )
                            )
                        )

                    )
                )
        val body = """{
              "viewProduct": {
                "product": {
                  "ids": {
                    "website": "ProductRandomName",
                    "shop": "ProductRandomNameShop"
                  }
                }
              }
            }""".trimIndent()

        every {
            inAppSegmentationRepository.getProductSegmentation("ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.POSITIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "segmentExternalId"
        )

        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        assertTrue(stub.checkTargeting(data))
    }

    @Test
    fun `check targeting negative success`() = runTest {
        val productSegmentation =
            ProductSegmentationResponseStub.getProductSegmentationResponseWrapper()
                .copy(
                    productSegmentations = listOf(
                        ProductSegmentationResponseStub.getProductResponse().copy(
                            productList = listOf(
                                ProductSegmentationResponseStub.getProductSegmentationsResponse()
                                    .copy(
                                        segmentationExternalId = "segmentationExternalId",
                                        segmentExternalId = "segmentExternalId"
                                    )
                            )
                        )

                    )
                )
        val body = """{
              "viewProduct": {
                "product": {
                  "ids": {
                    "website": "ProductRandomName",
                    "shop": "ProductRandomNameShop"
                  }
                }
              }
            }""".trimIndent()

        every {
            inAppSegmentationRepository.getProductSegmentation("ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.NEGATIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "otherSegmentExternalId"
        )

        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        assertTrue(stub.checkTargeting(data))
    }

    @Test
    fun `check targeting negative error`() = runTest {
        val productSegmentation =
            ProductSegmentationResponseStub.getProductSegmentationResponseWrapper()
                .copy(
                    productSegmentations = listOf(
                        ProductSegmentationResponseStub.getProductResponse().copy(
                            productList = listOf(
                                ProductSegmentationResponseStub.getProductSegmentationsResponse()
                                    .copy(
                                        segmentationExternalId = "segmentationExternalId",
                                        segmentExternalId = "segmentExternalId"
                                    )
                            )
                        )

                    )
                )
        val body = """{
              "viewProduct": {
                "product": {
                  "ids": {
                    "website": "ProductRandomName",
                    "shop": "ProductRandomNameShop"
                  }
                }
              }
            }""".trimIndent()

        every {
            inAppSegmentationRepository.getProductSegmentation("ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.NEGATIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "segmentExternalId"
        )

        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        assertFalse(stub.checkTargeting(data))
    }

    @Test
    fun `check targeting positive error`() = runTest {
        val productSegmentation =
            ProductSegmentationResponseStub.getProductSegmentationResponseWrapper()
                .copy(
                    productSegmentations = listOf(
                        ProductSegmentationResponseStub.getProductResponse().copy(
                            productList = listOf(
                                ProductSegmentationResponseStub.getProductSegmentationsResponse()
                                    .copy(
                                        segmentationExternalId = "segmentationExternalId",
                                        segmentExternalId = "segmentExternalId"
                                    )
                            )
                        )

                    )
                )
        val body = """{
              "viewProduct": {
                "product": {
                  "ids": {
                    "website": "ProductRandomName",
                    "shop": "ProductRandomNameShop"
                  }
                }
              }
            }""".trimIndent()

        every {
            inAppSegmentationRepository.getProductSegmentation("ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.POSITIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "otherSegmentExternalId"
        )

        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        assertFalse(stub.checkTargeting(data))
    }

    @Test
    fun `getOperationsSet return viewProduct`() = runTest {
        Assert.assertEquals(
            setOf("TestSystemNameProduct"),
            InAppStub.viewProductSegmentNode.getOperationsSet()
        )
    }
}