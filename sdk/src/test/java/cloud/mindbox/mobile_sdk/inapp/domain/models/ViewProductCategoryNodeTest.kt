package cloud.mindbox.mobile_sdk.inapp.domain.models

import android.content.Context
import app.cash.turbine.test
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.domain.InAppEventManagerImpl
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.managers.InAppEventManager
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declare
import org.koin.test.mock.declareMock

@OptIn(ExperimentalCoroutinesApi::class)
class ViewProductCategoryNodeTest : KoinTest {

    private lateinit var mobileConfigRepository: MobileConfigRepository

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule)
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
    fun `filter appStartup event`() = runTest {
        declare<InAppEventManager> { InAppEventManagerImpl() }
        assertFalse(InAppStub.viewProductCategoryNode.filterEvent(InAppEventType.AppStartup))
    }

    @Test
    fun `filter ordinal event`() = runTest {
        declare<InAppEventManager> { InAppEventManagerImpl() }
        assertTrue(
            InAppStub.viewProductCategoryNode.filterEvent(mockk<InAppEventType.OrdinalEvent>())
        )
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
            assertFalse(InAppStub.viewProductCategoryNode.checkTargeting())
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
            assertFalse(InAppStub.viewProductCategoryNode.checkTargeting())
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), body)

        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.SUBSTRING)

        listOf(
            stub.copy(value = "ategoryrandomnam"),
            stub.copy(value = "CATEGORYRANDOMNAME"),
            stub.copy(value = "a"),
            stub.copy(value = "CategoryRandomNameShop"),
            stub.copy(value = "Shop")
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "x"),
            stub.copy(value = "CategoryRandomNameX")
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertFalse(it.toString(), it.checkTargeting())
            }
    }

    @Test
    fun `checkTargeting notSubstring`() = runTest {
        every { MindboxKoin.koin } returns getKoin()
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

        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), body)
        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.NOT_SUBSTRING)

        val stub1 = stub.copy(value = "CATEGORYRANDOMNAME1")
        val mock = spyk(stub1, recordPrivateCalls = true)
        every { mock getProperty "lastEvent" } returns InAppEventType.OrdinalEvent(
            EventType.SyncOperation(
                "viewCategory"
            ), body
        )

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAME1"),
            stub.copy(value = "CATEGORYRANDOMNAMESHOP"),
            stub.copy(value = "x"),
            stub.copy(value = "shop"),
            stub.copy(value = " ")
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAME"),
            stub.copy(value = "random"),
            stub.copy(value = "a"),
            stub.copy(value = "ategoryRandomnam")
        ).map { it.spykLastEvent(event) }
            .onEach { targeting ->
                assertFalse(targeting.toString(), targeting.checkTargeting())
            }
    }

    @Test
    fun `checkTargeting startWith`() = runTest {
        every { MindboxKoin.koin } returns getKoin()
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), body)

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAME"),
            stub.copy(value = "CATEGORYRANDOMNAMESHOP"),
            stub.copy(value = "c"),
            stub.copy(value = "cA"),
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "CATEGORYRANDOMNAMESHOP1"),
            stub.copy(value = "ategoryrandomname"),
            stub.copy(value = "a"),
            stub.copy(value = "ategoryRandomnam")
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertFalse(it.toString(), it.checkTargeting())
            }

    }

    @Test
    fun `checkTargeting endWith`() = runTest {
        every { MindboxKoin.koin } returns getKoin()
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), body)
        val stub = InAppStub.viewProductCategoryNode.copy(kind = KindSubstring.ENDS_WITH)

        listOf(
            stub.copy(value = "NAme"),
            stub.copy(value = "SHOP"),
            stub.copy(value = "CategoryRandomName"),
            stub.copy(value = "CategoryRandomNameShop"),
            stub.copy(value = "e"),
            stub.copy(value = "p"),
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertTrue(it.toString(), it.checkTargeting())
            }

        listOf(
            stub.copy(value = "1"),
            stub.copy(value = "1CategoryRandomName"),
            stub.copy(value = "x"),
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertFalse(it.toString(), it.checkTargeting())
            }
    }

    private fun ViewProductCategoryNode.spykLastEvent(event: InAppEventType.OrdinalEvent): ViewProductCategoryNode {
        return spyk(this, recordPrivateCalls = true).also {
            every { it getProperty "lastEvent" } returns event
        }
    }
}