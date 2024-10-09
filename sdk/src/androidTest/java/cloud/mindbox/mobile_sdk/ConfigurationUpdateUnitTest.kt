package cloud.mindbox.mobile_sdk

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import cloud.mindbox.mobile_sdk.managers.DbManager
import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.operation.Ids
import cloud.mindbox.mobile_sdk.models.operation.request.*
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ConfigurationUpdateUnitTest {

    @Before
    fun init() {
        setDatabaseTestMode(true)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val configs = MindboxConfiguration.Builder(
            appContext,
            "mindbox-pushok.umbrellait.tech",
            "app-with-hub-Android",
        )
            .shouldCreateCustomer(true)
            .build()

        initMindbox(appContext, configs)
    }

    @After
    fun clear() {
        DbManager.removeAllEventsFromQueue()
        clearPreferences()
    }

    @Test
    fun uuid_does_not_change_after_updating_configuration() {
        Mindbox.subscribeDeviceUuid { deviceUUID ->
            updateConfig()
            Mindbox.subscribeDeviceUuid { newUuid ->
                Assert.assertTrue("First uuid is not empty", deviceUUID.isNotEmpty())
                Assert.assertTrue("New uuid is not empty", newUuid.isNotEmpty())
                Assert.assertEquals("both uuids are equal", newUuid, deviceUUID)
            }
        }
    }

    @Test
    fun instance_id_changing_after_updating_configuration() {
        val instanceId = MindboxPreferences.instanceId

        updateConfig()

        Assert.assertNotEquals(instanceId, MindboxPreferences.instanceId)
    }

    @Test
    fun app_info_equal_to_1_after_updating_configuration() {
        updateConfig()

        Assert.assertEquals(1, MindboxPreferences.infoUpdatedVersion)
    }

    @Test
    fun scc_config_will_not_change_after_re_initialization() {
        val instanceId = MindboxPreferences.instanceId

        MindboxPreferences.isFirstInitialize = false
        updateConfig(true)

        Assert.assertEquals(instanceId, MindboxPreferences.instanceId)
    }

    @Test
    fun events_are_empty_after_updating_configuration() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        repeat(10) {
            val bodyRequest = OperationBodyRequest(
                recommendation = createRecommendationRequest(
                    limit = 732852493 + it,
                    area = "1345ff$it",
                    category = "156$it",
                )
            )

            Mindbox.executeAsyncOperation(appContext, "categoryReco", bodyRequest)
        }

        updateConfig()

        val events = DbManager.getFilteredEvents()
            .filter { it.eventType !is EventType.AppInstalled }
            .filter { it.eventType !is EventType.AppInstalledWithoutCustomer }
            .filter { it.eventType !is EventType.TrackVisit }

        Assert.assertTrue(events.isEmpty())
    }

    private fun updateConfig(shouldCreateCustomer: Boolean = false) {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        var (domain, endpoint) = "" to ""

        if (shouldCreateCustomer) {
            domain = "mindbox-pushok.umbrellait.tech"
            endpoint = "app-with-hub-Android"
        } else {
            domain = "api.mindbox.ru"
            endpoint = "mpush-test-Android"
        }

        val newConfigs = MindboxConfiguration.Builder(appContext, domain, endpoint)
            .shouldCreateCustomer(shouldCreateCustomer)
            .build()

        initMindbox(appContext, newConfigs)
    }

    private fun initMindbox(context: Context, configuration: MindboxConfiguration) {
        runBlocking(Dispatchers.Main) {
            Mindbox.init(context, configuration, listOf())
            delay(3_000)
        }
    }

    private fun createRecommendationRequest(
        limit: Int,
        area: String,
        category: String? = null,
        product: String? = null,
    ) = RecommendationRequest(
        limit = limit,
        area = AreaRequest(Ids(ids = mapOf("externalId" to area))),
        productCategory = category?.let { ProductCategoryRequest(Ids(ids = mapOf("website" to it))) },
        product = product?.let { ProductRequest(Ids(ids = mapOf("website" to it))) },
    )
}
