package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.pushes.PushConverter
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.SingleInitDelegate
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackingIdTypesOfTest {

    private fun handler(trackingIdType: String?): PushServiceHandler =
        mockk<PushServiceHandler>().also { every { it.trackingIdType } returns trackingIdType }

    private val firebase = handler("google")
    private val huawei = handler("huawei")
    private val ruStore = handler(null)

    private fun passed(vararg lists: List<PushConverter>): SingleInitDelegate<PushConverter> =
        SingleInitDelegate<PushConverter>().also { delegate ->
            var converters: List<PushConverter> by delegate
            lists.forEach { converters = it }
        }

    @Test
    fun `nothing passed to init yet leaves the configuration unknown`() {
        assertNull(trackingIdTypesOf(configured = passed(), live = emptyList()))
    }

    @Test
    fun `an empty list passed to init configures no tracking id types`() {
        assertEquals(emptySet<String>(), trackingIdTypesOf(configured = passed(emptyList()), live = emptyList()))
    }

    @Test
    fun `an empty list passed after the push services keeps their types`() {
        val types = trackingIdTypesOf(configured = passed(listOf(firebase), emptyList()), live = emptyList())

        assertEquals(setOf("google"), types)
    }

    @Test
    fun `every passed push service with a tracking id is configured`() {
        val types = trackingIdTypesOf(configured = passed(listOf(firebase, huawei, ruStore)), live = emptyList())

        assertEquals(setOf("google", "huawei"), types)
    }

    @Test
    fun `a RuStore-only integration configures no tracking id types`() {
        val types = trackingIdTypesOf(configured = passed(listOf(ruStore)), live = listOf(ruStore))

        assertEquals(emptySet<String>(), types)
    }

    @Test
    fun `a service filtered out as unavailable at startup stays configured`() {
        val types = trackingIdTypesOf(configured = passed(listOf(firebase)), live = emptyList())

        assertEquals(setOf("google"), types)
    }

    @Test
    fun `a live handler counts even if it came from another init call`() {
        val types = trackingIdTypesOf(configured = passed(listOf(firebase)), live = listOf(firebase, huawei))

        assertEquals(setOf("google", "huawei"), types)
    }

    @Test
    fun `live handlers alone make the configuration known`() {
        assertEquals(setOf("huawei"), trackingIdTypesOf(configured = passed(), live = listOf(huawei)))
    }
}
