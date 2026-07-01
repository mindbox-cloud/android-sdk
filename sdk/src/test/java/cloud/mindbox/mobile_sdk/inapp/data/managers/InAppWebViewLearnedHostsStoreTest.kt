package cloud.mindbox.mobile_sdk.inapp.data.managers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class InAppWebViewLearnedHostsStoreTest {

    private val store = InAppWebViewLearnedHostsStore()

    @Before
    fun setUp() {
        SharedPreferencesManager.with(ApplicationProvider.getApplicationContext<Context>())
        SharedPreferencesManager.deleteAll()
    }

    @Test
    fun `hosts are empty by default`() {
        assertTrue(store.hosts("Some.Endpoint").isEmpty())
    }

    @Test
    fun `merge persists per endpoint and roundtrips`() {
        store.merge("Endpoint.A", listOf("a.mindbox.ru", "b.mindbox.ru"))
        store.merge("Endpoint.B", listOf("c.mindbox.ru"))

        assertEquals(listOf("a.mindbox.ru", "b.mindbox.ru"), store.hosts("Endpoint.A"))
        assertEquals(listOf("c.mindbox.ru"), store.hosts("Endpoint.B"))
    }

    @Test
    fun `merge is newest first deduplicated and capped`() {
        store.merge("Endpoint.A", (1..10).map { index -> "old$index.ru" })
        store.merge("Endpoint.A", listOf("new1.ru", "old1.ru", "new2.ru"))

        val hosts = store.hosts("Endpoint.A")
        assertEquals(12, hosts.size)
        assertEquals(listOf("new1.ru", "old1.ru", "new2.ru", "old1.ru").distinct().take(3), hosts.take(3))
        assertTrue(hosts.contains("old9.ru"))
    }

    @Test
    fun `merge ignores blank input`() {
        store.merge("Endpoint.A", listOf(" ", ""))
        store.merge("", listOf("host.ru"))

        assertTrue(store.hosts("Endpoint.A").isEmpty())
        assertTrue(store.hosts("").isEmpty())
    }
}
