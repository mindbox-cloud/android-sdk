package cloud.mindbox.mobile_sdk.inapp.domain.models

import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppSegmentationRepository
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
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
class ViewProductCategoryInNodeTest {

    private val mockkMobileConfigRepository: MobileConfigRepository = mockk {
        every {
            runBlocking { getOperations() }
        } returns mapOf(
            OperationName.VIEW_PRODUCT to OperationSystemName("TestSystemNameProduct"),
            OperationName.VIEW_CATEGORY to OperationSystemName("TestSystemNameCategory"),
        )
    }

    private val mockkInAppSegmentationRepository: InAppSegmentationRepository = mockk()

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
        assertTrue(InAppStub.viewProductCategoryInNode.hasOperationNode())
    }

    @Test
    fun `hasGeoNode always false`() {
        assertFalse(InAppStub.viewProductCategoryInNode.hasGeoNode())
    }

    @Test
    fun `hasSegmentationNode always false`() {
        assertFalse(InAppStub.viewProductCategoryInNode.hasSegmentationNode())
    }

    @Test
    fun `getOperationsSet return viewCategory`() = runTest {
        assertEquals(
            setOf("TestSystemNameCategory"),
            InAppStub.viewProductCategoryInNode.getOperationsSet()
        )
    }

    @Test
    fun `checkTargeting after AppStartup`() = runTest {
        MindboxEventManager.eventFlow.emit(InAppEventType.AppStartup)
        MindboxEventManager.eventFlow.test {
            awaitItem()
            assertFalse(InAppStub.viewProductCategoryInNode.checkTargeting(
                TestTargetingData("viewCategory", null)
            ))
        }
    }

    @Test
    fun `checkTargeting after event with empty body`() = runTest {
        MindboxEventManager.eventFlow.emit(
            InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), null)
        )
        MindboxEventManager.eventFlow.test {
            awaitItem()
            assertFalse(InAppStub.viewProductCategoryInNode.checkTargeting(
                TestTargetingData("viewCategory", null)
            ))
        }
    }

    @Test
    fun `checkTargeting any`() = runTest {
        val body = """{
              "viewProductCategory": {
                "productCategory": {
                  "ids": {
                    "website": "CategoryRandomName",
                    "shop": "CategoryRandomNameShop"
                  }
                }
              }
            }""".trimIndent()
        val data = TestTargetingData("viewCategory", body)
        val stub = InAppStub.viewProductCategoryInNode.copy(kind = KindAny.ANY)

        listOf(
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "categoryrandomname"
                    ),
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "shop",
                        externalId = "CATEGORYRANDOMNAMESHOP"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "CAtegoryRAndomNAme"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "shop",
                        externalId = "CategoryRandomNameShop"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "categoryrandomname"
                    ),
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "wEbsitE",
                        externalId = "Error"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "wEbsitE",
                        externalId = "Error"
                    ),
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "WEBSITE",
                        externalId = "categoryrandomname"
                    ),
                )
            )
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website1",
                        externalId = "CategoryRandomName"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "CategoryRandomName1"
                    )
                )
            ),
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }


    @Test
    fun `checkTargeting any with same externalSystemName`() = runTest {
        val body = """{
              "viewProductCategory": {
                "productCategory": {
                  "ids": {
                    "website": "123",
                    "WEBSITE": "456"
                  }
                }
              }
            }""".trimIndent()
        val data = TestTargetingData("viewCategory", body)
        val stub = InAppStub.viewProductCategoryInNode.copy(kind = KindAny.ANY)

        listOf(
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "Website",
                        externalId = "123"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "456",
                        externalSystemName = "weBsitE",
                        externalId = "456"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "Website",
                        externalId = "123"
                    ),
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "Website",
                        externalId = "456"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "123"
                    ),
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "wEbsitE",
                        externalId = "Error"
                    )
                )
            )
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website1",
                        externalId = "123"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "2"
                    )
                )
            ),
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }

    @Test
    fun `checkTargeting none`() = runTest {
        val body = """{
              "viewProductCategory": {
                "productCategory": {
                  "ids": {
                    "website": "CategoryRandomName",
                    "shop": "CategoryRandomNameShop"
                  }
                }
              }
            }""".trimIndent()

        val data = TestTargetingData("viewCategory", body)
        val stub = InAppStub.viewProductCategoryInNode.copy(kind = KindAny.NONE)

        listOf(
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website1",
                        externalId = "CategoryRandomName"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "CategoryRandomName1"
                    )
                )
            ),
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "Random"
                    ),
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "shop",
                        externalId = "CategoryRandomNameShop"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "123",
                        externalSystemName = "website",
                        externalId = "CategoryRandomName"
                    )
                )
            ),
            stub.copy(
                values = listOf(
                    ViewProductCategoryInNode.Value(
                        id = "124",
                        externalSystemName = "shop",
                        externalId = "CategoryRandomNameShop"
                    )
                )
            ),
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }
}