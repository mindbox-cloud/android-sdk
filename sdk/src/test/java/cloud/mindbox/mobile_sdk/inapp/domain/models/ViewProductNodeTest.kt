package cloud.mindbox.mobile_sdk.inapp.domain.models

import android.content.Context
import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.di.domainModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.*
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
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProductNodeTest: KoinTest {

    private lateinit var mobileConfigRepository: MobileConfigRepository

    private lateinit var productSegmentationResponseWrapper: ProductSegmentationResponseWrapper

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
        Assert.assertTrue(InAppStub.viewProductNode.hasOperationNode())
    }

    @Test
    fun `hasGeoNode always false`() {
        Assert.assertFalse(InAppStub.viewProductNode.hasGeoNode())
    }

    @Test
    fun `hasSegmentationNode always false`() {
        Assert.assertFalse(InAppStub.viewProductNode.hasSegmentationNode())
    }

    @Test
    fun `checkTargeting after AppStartup`() = runTest {
        MindboxEventManager.eventFlow.resetReplayCache()
        MindboxEventManager.eventFlow.emit(InAppEventType.AppStartup)
        MindboxEventManager.eventFlow.test {
            Assert.assertFalse(InAppStub.viewProductNode.checkTargeting(mockk()))
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
            Assert.assertFalse(InAppStub.viewProductNode.checkTargeting(
                TreeTargetingTest.TestTargetingData("viewProduct", null))
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
        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.SUBSTRING)

        listOf(
            stub.copy(value = "roductrandomnam"),
            stub.copy(value = "roductRANDOMNAME"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandomNameShop"),
            stub.copy(value = "Shop")
        ).onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting(data))
            }

        listOf(
            stub.copy(value = "x"),
            stub.copy(value = "ProductRandomNameX")
        ).onEach {
                Assert.assertFalse(it.toString(), it.checkTargeting(data))
            }
    }

    @Test
    fun `checkTargeting notSubstring`() = runTest {
        every { MindboxKoin.koin } returns getKoin()
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

        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.NOT_SUBSTRING)

        listOf(
            stub.copy(value = "ProductRANDOMNAME1"),
            stub.copy(value = "ProductRANDOMNAMESHOP"),
            stub.copy(value = "x"),
            stub.copy(value = "shop"),
            stub.copy(value = " ")
        ).onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting(data))
            }

        listOf(
            stub.copy(value = "ProductRANDOMNAME"),
            stub.copy(value = "random"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandomnam")
        ).onEach { targeting ->
                Assert.assertFalse(targeting.toString(), targeting.checkTargeting(data))
            }
    }

    @Test
    fun `checkTargeting startWith`() = runTest {
        every { MindboxKoin.koin } returns getKoin()
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
        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)

        listOf(
            stub.copy(value = "ProductRANDOM"),
            stub.copy(value = "ProductRANDOMNAM"),
            stub.copy(value = "p"),
            stub.copy(value = "pR"),
        ).onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting(data))
            }

        listOf(
            stub.copy(value = "ProductRANDOMNAMESHOP1"),
            stub.copy(value = "Categoryrandomname"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandosnam")
        ).onEach {
                Assert.assertFalse(it.toString(), it.checkTargeting(data))
            }

    }

    @Test
    fun `checkTargeting endWith`() = runTest {
        every { MindboxKoin.koin } returns getKoin()
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
        val data = TreeTargetingTest.TestTargetingData("viewProduct", body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.ENDS_WITH)

        listOf(
            stub.copy(value = "NAme"),
            stub.copy(value = "SHOP"),
            stub.copy(value = "ProductRandomName"),
            stub.copy(value = "ProductRandomNameShop"),
            stub.copy(value = "e"),
            stub.copy(value = "p"),
        ).onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting(data))
            }

        listOf(
            stub.copy(value = "1"),
            stub.copy(value = "1ProductRandomName"),
            stub.copy(value = "x"),
        ).onEach {
                Assert.assertFalse(it.toString(), it.checkTargeting(data))
            }
    }

    @Test
    fun `getOperationsSet return viewProduct`() = runTest {
        Assert.assertEquals(
            setOf("TestSystemNameProduct"),
            InAppStub.viewProductNode.getOperationsSet()
        )
    }

}