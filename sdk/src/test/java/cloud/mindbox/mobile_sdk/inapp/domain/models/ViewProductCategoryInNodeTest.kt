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
class ViewProductCategoryInNodeTest : KoinTest {

    private lateinit var mobileConfigRepository: MobileConfigRepository
    private lateinit var inAppEventManagerImpl: InAppEventManager

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
    fun `filter appStartup event`() = runTest {
        declare<InAppEventManager> { InAppEventManagerImpl() }
        assertFalse(InAppStub.viewProductCategoryInNode.filterEvent(InAppEventType.AppStartup))
    }

    @Test
    fun `filter ordinal event`() = runTest {
        declare<InAppEventManager> { InAppEventManagerImpl() }
        assertTrue(
            InAppStub.viewProductCategoryInNode.filterEvent(mockk<InAppEventType.OrdinalEvent>())
        )
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
            assertFalse(InAppStub.viewProductCategoryInNode.checkTargeting())
        }
    }

    @Test
    fun `checkTargeting after event with empty body`() = runTest {
        MindboxEventManager.eventFlow.emit(
            InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), null)
        )
        MindboxEventManager.eventFlow.test {
            awaitItem()
            assertFalse(InAppStub.viewProductCategoryInNode.checkTargeting())
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
        val event = InAppEventType.OrdinalEvent(EventType.SyncOperation("viewCategory"), body)
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
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertTrue(it.toString(), it.checkTargeting())
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
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertFalse(it.toString(), it.checkTargeting())
            }
    }

    @Test
    fun `checkTargeting none`() = runTest {
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
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertTrue(it.toString(), it.checkTargeting())
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
        ).map { it.spykLastEvent(event) }
            .onEach {
                assertFalse(it.toString(), it.checkTargeting())
            }
    }

    private fun ViewProductCategoryInNode.spykLastEvent(event: InAppEventType.OrdinalEvent): ViewProductCategoryInNode {
        return spyk(this, recordPrivateCalls = true).also {
            every { it getProperty "lastEvent" } returns event
        }
    }
}