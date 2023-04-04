package cloud.mindbox.mobile_sdk.inapp.domain.models

import android.content.Context
import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.di.domainModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.MobileConfigRepository
import cloud.mindbox.mobile_sdk.managers.MindboxEventManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockkClass
import io.mockk.mockkObject
import io.mockk.spyk
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

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProductNodeTest: KoinTest {

    private lateinit var mobileConfigRepository: MobileConfigRepository

    private lateinit var productSegmentationResponseWrapper: ProductSegmentationResponseWrapper


    private val inAppEventManager: InAppEventManager by inject()

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
    fun `filter appStartup event`() = runTest {
        Assert.assertFalse(InAppStub.viewProductNode.filterEvent(InAppEventType.AppStartup))
    }

    @Test
    fun `filter ordinal event`() = runTest {
        Assert.assertTrue(
            InAppStub.viewProductNode.filterEvent(
                spyk(
                    InAppEventType.OrdinalEvent(
                        EventType.SyncOperation(
                            ""
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `checkTargeting after AppStartup`() = runTest {
        MindboxEventManager.eventFlow.resetReplayCache()
        MindboxEventManager.eventFlow.emit(InAppEventType.AppStartup)
        MindboxEventManager.eventFlow.test {
            Assert.assertFalse(InAppStub.viewProductNode.checkTargeting())
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
            Assert.assertFalse(InAppStub.viewProductNode.checkTargeting())
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewProduct"), body)

        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.SUBSTRING)

        listOf(
            stub.copy(value = "roductrandomnam"),
            stub.copy(value = "roductRANDOMNAME"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandomNameShop"),
            stub.copy(value = "Shop")
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "x"),
            stub.copy(value = "ProductRandomNameX")
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertFalse(it.toString(), it.checkTargeting())
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

        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewProduct"), body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.NOT_SUBSTRING)

        val stub1 = stub.copy(value = "ProductRANDOMNAME1")
        val mock = spyk(stub1, recordPrivateCalls = true)
        every { mock getProperty "lastEvent" } returns InAppEventType.OrdinalEvent(
            EventType.SyncOperation(
                "viewProduct"
            ), body
        )

        listOf(
            stub.copy(value = "ProductRANDOMNAME1"),
            stub.copy(value = "ProductRANDOMNAMESHOP"),
            stub.copy(value = "x"),
            stub.copy(value = "shop"),
            stub.copy(value = " ")
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "ProductRANDOMNAME"),
            stub.copy(value = "random"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandomnam")
        ).map { it.spykLastEvent(event) }
            .onEach { targeting ->
                Assert.assertFalse(targeting.toString(), targeting.checkTargeting())
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewProduct"), body)

        listOf(
            stub.copy(value = "ProductRANDOM"),
            stub.copy(value = "ProductRANDOMNAM"),
            stub.copy(value = "p"),
            stub.copy(value = "pR"),
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "ProductRANDOMNAMESHOP1"),
            stub.copy(value = "Categoryrandomname"),
            stub.copy(value = "a"),
            stub.copy(value = "ProductRandosnam")
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertFalse(it.toString(), it.checkTargeting())
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewProduct"), body)
        val stub = InAppStub.viewProductNode.copy(kind = KindSubstring.ENDS_WITH)

        listOf(
            stub.copy(value = "NAme"),
            stub.copy(value = "SHOP"),
            stub.copy(value = "ProductRandomName"),
            stub.copy(value = "ProductRandomNameShop"),
            stub.copy(value = "e"),
            stub.copy(value = "p"),
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "1"),
            stub.copy(value = "1ProductRandomName"),
            stub.copy(value = "x"),
        ).map { it.spykLastEvent(event) }
            .onEach {
                Assert.assertFalse(it.toString(), it.checkTargeting())
            }
    }

    private fun ViewProductNode.spykLastEvent(event: InAppEventType.OrdinalEvent): ViewProductNode {
        return spyk(this, recordPrivateCalls = true).also {
            every { it getProperty "lastEvent" } returns event
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