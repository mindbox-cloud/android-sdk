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
class ViewProductNodeTest {

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
        assertTrue(InAppStub.viewProductNode.hasOperationNode())
    }

    @Test
    fun `hasGeoNode always false`() {
        assertFalse(InAppStub.viewProductNode.hasGeoNode())
    }

    @Test
    fun `hasSegmentationNode always false`() {
        assertFalse(InAppStub.viewProductNode.hasSegmentationNode())
    }

    @Test
    fun `checkTargeting after AppStartup`() = runTest {
        MindboxEventManager.eventFlow.resetReplayCache()
        MindboxEventManager.eventFlow.emit(InAppEventType.AppStartup)
        MindboxEventManager.eventFlow.test {
            assertFalse(InAppStub.viewProductNode.checkTargeting( TestTargetingData("viewProduct", null)))
            awaitItem()
        }
    }

    @Test
    fun `checkTargeting after event with empty body`() = runTest {
        MindboxEventManager.eventFlow.resetReplayCache()
        MindboxEventManager.eventFlow.emit(
            InAppEventType.OrdinalEvent(EventType.SyncOperation("viewProduct"), null)
        )
        MindboxEventManager.eventFlow.test {
            assertFalse(
                InAppStub.viewProductNode.checkTargeting(
                    TestTargetingData("viewProduct", null)
                )
            )
            awaitItem()
        }
    }

    @Test
    fun `checkTargeting substring`() = runTest {
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
        val data = TestTargetingData("viewProduct", body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.SUBSTRING)

        listOf(
            stub.copy(value = "roductrandomnam"),
            stub.copy(value = "roductRANDOMNAME"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandomNameShop"),
            stub.copy(value = "Shop")
        ).onEach {
            assertTrue(it.toString(), it.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "x"),
            stub.copy(value = "ProductRandomNameX")
        ).onEach {
            assertFalse(it.toString(), it.checkTargeting(data))
        }
    }

    @Test
    fun `checkTargeting notSubstring`() = runTest {
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

        val data = TestTargetingData("viewProduct", body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.NOT_SUBSTRING)

        listOf(
            stub.copy(value = "ProductRANDOMNAME1"),
            stub.copy(value = "ProductRANDOMNAMESHOP"),
            stub.copy(value = "x"),
            stub.copy(value = "shop"),
            stub.copy(value = " ")
        ).onEach {
            assertTrue(it.toString(), it.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "ProductRANDOMNAME"),
            stub.copy(value = "random"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandomnam")
        ).onEach { targeting ->
            assertFalse(targeting.toString(), targeting.checkTargeting(data))
        }
    }

    @Test
    fun `checkTargeting startWith`() = runTest {
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

        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.STARTS_WITH)
        val data = TestTargetingData("viewProduct", body)

        listOf(
            stub.copy(value = "ProductRANDOM"),
            stub.copy(value = "ProductRANDOMNAM"),
            stub.copy(value = "p"),
            stub.copy(value = "pR"),
        ).onEach {
            assertTrue(it.toString(), it.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "ProductRANDOMNAMESHOP1"),
            stub.copy(value = "Categoryrandomname"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandosnam")
        ).onEach {
            assertFalse(it.toString(), it.checkTargeting(data))
        }

    }

    @Test
    fun `checkTargeting endWith`() = runTest {
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

        MindboxEventManager.eventFlow.resetReplayCache()
        val data = TestTargetingData("viewProduct", body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.ENDS_WITH)

        listOf(
            stub.copy(value = "NAme"),
            stub.copy(value = "SHOP"),
            stub.copy(value = "ProductRandomName"),
            stub.copy(value = "ProductRandomNameShop"),
            stub.copy(value = "e"),
            stub.copy(value = "p"),
        ).onEach {
            assertTrue(it.toString(), it.checkTargeting(data))
        }

        listOf(
            stub.copy(value = "1"),
            stub.copy(value = "1ProductRandomName"),
            stub.copy(value = "x"),
        ).onEach {
            assertFalse(it.toString(), it.checkTargeting(data))
        }
    }

    @Test
    fun `getOperationsSet return viewProduct`() = runTest {
        assertEquals(
            setOf("TestSystemNameProduct"),
            InAppStub.viewProductNode.getOperationsSet()
        )
    }

}