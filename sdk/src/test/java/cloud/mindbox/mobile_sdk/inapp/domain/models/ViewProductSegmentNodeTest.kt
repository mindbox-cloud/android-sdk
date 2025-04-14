package cloud.mindbox.mobile_sdk.inapp.domain.models

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.data.managers.SessionStorageManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.ProductSegmentationResponseStub
import com.google.gson.Gson
import io.mockk.*
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProductSegmentNodeTest {

    private val mockkMobileConfigRepository: MobileConfigRepository = mockk {
        every {
            runBlocking { getOperations() }
        } returns mapOf(
            OperationName.VIEW_PRODUCT to OperationSystemName("TestSystemNameProduct"),
            OperationName.VIEW_CATEGORY to OperationSystemName("TestSystemNameCategory"),
        )
    }

    private val mockkInAppSegmentationRepository: InAppSegmentationRepository = mockk()
    private val sessionStorageManager = mockk<SessionStorageManager>()

    @get:Rule
    val mockkRule = MockKRule(this)

    @Before
    fun onTestStart() = runTest {
        mockkObject(MindboxEventManager)
        MindboxEventManager.eventFlow.resetReplayCache()

        // mockk 'by mindboxInject { }'
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk {
            every { mobileConfigRepository } returns mockkMobileConfigRepository
            every { inAppSegmentationRepository } returns mockkInAppSegmentationRepository
            every { gson } returns Gson()
        }
    }

    @Test
    fun `hasOperationNode always true`() {
        assertTrue(InAppStub.viewProductSegmentNode.hasOperationNode())
    }

    @Test
    fun `hasGeoNode always false`() {
        assertFalse(InAppStub.viewProductSegmentNode.hasGeoNode())
    }

    @Test
    fun `hasSegmentationNode always false`() {
        assertFalse(InAppStub.viewProductSegmentNode.hasSegmentationNode())
    }

    @Test
    fun `check targeting positive success`() = runTest {
        val productSegmentation =
            setOf(
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
            mockkInAppSegmentationRepository.getProductSegmentations("website:ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.POSITIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "segmentExternalId"
        )

        val data = TestTargetingData("viewProduct", body)
        assertTrue(stub.checkTargeting(data))
    }

    @Test
    fun `check targeting negative success`() = runTest {
        val productSegmentation =
            setOf(
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
            mockkInAppSegmentationRepository.getProductSegmentations("website:ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.NEGATIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "otherSegmentExternalId"
        )

        val data = TestTargetingData("viewProduct", body)
        assertTrue(stub.checkTargeting(data))
    }

    @Test
    fun `check targeting negative error`() = runTest {
        val productSegmentation =
            setOf(
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
            mockkInAppSegmentationRepository.getProductSegmentations("website:ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.NEGATIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "segmentExternalId"
        )

        val data = TestTargetingData("viewProduct", body)
        assertFalse(stub.checkTargeting(data))
    }

    @Test
    fun `check targeting positive error`() = runTest {
        val productSegmentation =
            setOf(
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
            mockkInAppSegmentationRepository.getProductSegmentations("website:ProductRandomName")
        } returns productSegmentation

        val stub = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.POSITIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "otherSegmentExternalId"
        )

        val data = TestTargetingData("viewProduct", body)
        assertFalse(stub.checkTargeting(data))
    }

    @Test
    fun `getOperationsSet return viewProduct`() = runTest {
        assertEquals(
            setOf("TestSystemNameProduct"),
            InAppStub.viewProductSegmentNode.getOperationsSet()
        )
    }

    @Test
    fun `fetchTargetingInfo should only fetch segmentation for unprocessed or error products`() = runTest {
        val processedProducts = hashMapOf(
            "website:successProduct" to ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS,
            "website:errorProduct" to ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        )
        every { sessionStorageManager.processedProductSegmentations } returns processedProducts
        every { mockkInAppSegmentationRepository.getProductSegmentationFetched("website:successProduct") } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_SUCCESS
        every { mockkInAppSegmentationRepository.getProductSegmentationFetched("website:errorProduct") } returns ProductSegmentationFetchStatus.SEGMENTATION_FETCH_ERROR
        every { mockkInAppSegmentationRepository.getProductSegmentationFetched("website:newProduct") } returns ProductSegmentationFetchStatus.SEGMENTATION_NOT_FETCHED

        val successProductBody = """{
            "viewProduct": {
                "product": {
                    "ids": {
                        "website": "successProduct"
                    }
                }
            }
        }""".trimIndent()

        val errorProductBody = """{
            "viewProduct": {
                "product": {
                    "ids": {
                        "website": "errorProduct"
                    }
                }
            }
        }""".trimIndent()

        val newProductBody = """{
            "viewProduct": {
                "product": {
                    "ids": {
                        "website": "newProduct"
                    }
                }
            }
        }""".trimIndent()

        val node = InAppStub.viewProductSegmentNode.copy(
            type = "",
            kind = Kind.POSITIVE,
            segmentationExternalId = "segmentationExternalId",
            segmentExternalId = "segmentExternalId"
        )
        node.fetchTargetingInfo(TestTargetingData("viewProduct", successProductBody))
        coVerify(exactly = 0) { mockkInAppSegmentationRepository.fetchProductSegmentation(any()) }

        node.fetchTargetingInfo(TestTargetingData("viewProduct", errorProductBody))
        coVerify(exactly = 1) {
            mockkInAppSegmentationRepository.fetchProductSegmentation(
                Pair("website", "errorProduct")
            )
        }

        node.fetchTargetingInfo(TestTargetingData("viewProduct", newProductBody))
        coVerify(exactly = 1) {
            mockkInAppSegmentationRepository.fetchProductSegmentation(
                Pair("website", "newProduct")
            )
        }
    }
}
