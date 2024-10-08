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
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProductCategoryNodeTest {

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
        assertTrue(InAppStub.viewProductCategoryNode.hasOperationNode())
    }

    @Test
    fun `hasGeoNode always false`() {
        assertFalse(InAppStub.viewProductCategoryNode.hasGeoNode())
    }

    @Test
    fun `hasSegmentationNode always false`() {
        assertFalse(InAppStub.viewProductCategoryNode.hasSegmentationNode())
    }

    @Test
    fun `getOperationsSet return viewCategory`() = runTest {
        assertEquals(
            setOf("TestSystemNameCategory"),
            InAppStub.viewProductCategoryNode.getOperationsSet()
        )
    }

    @Test
    fun `checkTargeting after AppStartup`() = runTest {
        MindboxEventManager.eventFlow.resetReplayCache()
        MindboxEventManager.eventFlow.emit(InAppEventType.AppStartup)
        MindboxEventManager.eventFlow.test {
            assertFalse(
                InAppStub.viewProductCategoryNode.checkTargeting(
                    TestTargetingData("viewCategory", null)
                )
            )
            awaitItem()
        }
    }

    @Test
    fun `checkTargeting after event with empty body`() = runTest {
        MindboxEventManager.eventFlow.resetReplayCache()
        MindboxEventManager.eventFlow.emit(
            InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), null)
        )
        MindboxEventManager.eventFlow.test {
            assertFalse(
                InAppStub.viewProductCategoryNode.checkTargeting(
                    TestTargetingData("viewCategory", null)
                )
            )
            awaitItem()
        }
    }

    @Test
    fun `checkTargeting substring`() = runTest {
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
        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.SUBSTRING)

        listOf(
            stub.copy(value = "ategoryrandomnam"),
            stub.copy(value = "CATEGORYRANDOMNAME"),
            stub.copy(value = "a"),
            stub.copy(value = "CategoryRandomNameShop"),
            stub.copy(value = "Shop")
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "x"),
            stub.copy(value = "CategoryRandomNameX")
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }

    @Test
    fun `checkTargeting notSubstring`() = runTest {
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
        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.NOT_SUBSTRING)

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAME1"),
            stub.copy(value = "CATEGORYRANDOMNAMESHOP"),
            stub.copy(value = "x"),
            stub.copy(value = "shop"),
            stub.copy(value = " ")
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAME"),
            stub.copy(value = "random"),
            stub.copy(value = "a"),
            stub.copy(value = "ategoryRandomnam")
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }

    @Test
    fun `checkTargeting startWith`() = runTest {
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

        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.STARTS_WITH)
        val data = TestTargetingData("viewCategory", body)

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAME"),
            stub.copy(value = "CATEGORYRANDOMNAMESHOP"),
            stub.copy(value = "c"),
            stub.copy(value = "cA"),
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAMESHOP1"),
            stub.copy(value = "ategoryrandomname"),
            stub.copy(value = "a"),
            stub.copy(value = "ategoryRandomnam")
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }

    @Test
    fun `checkTargeting endWith`() = runTest {
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

        MindboxEventManager.eventFlow.resetReplayCache()
        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.ENDS_WITH)
        val data = TestTargetingData("viewCategory", body)

        listOf(
            stub.copy(value = "NAme"),
            stub.copy(value = "SHOP"),
            stub.copy(value = "CategoryRandomName"),
            stub.copy(value = "CategoryRandomNameShop"),
            stub.copy(value = "e"),
            stub.copy(value = "p"),
        ).onEach { node ->
            assertTrue(node.toString(), node.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "1"),
            stub.copy(value = "1CategoryRandomName"),
            stub.copy(value = "x"),
        ).onEach { node ->
            assertFalse(node.toString(), node.checkTargeting(data))
        }
    }
}
