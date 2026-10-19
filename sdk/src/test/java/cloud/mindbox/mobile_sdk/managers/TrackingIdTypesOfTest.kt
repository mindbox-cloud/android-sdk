package cloud.mindbox.mobile_sdk.managers

import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
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

    @Test
    fun `nothing passed to init yet leaves the configuration unknown`() {
        assertNull(trackingIdTypesOf(configured = emptyList(), live = emptyList()))
    }

    @Test
    fun `every passed push service with a tracking id is configured`() {
        val types = trackingIdTypesOf(configured = listOf(firebase, huawei, ruStore), live = emptyList())

        assertEquals(setOf("google", "huawei"), types)
    }

    @Test
    fun `a RuStore-only integration configures no tracking id types`() {
        val types = trackingIdTypesOf(configured = listOf(ruStore), live = listOf(ruStore))

        assertEquals(emptySet<String>(), types)
    }

    @Test
    fun `a service filtered out as unavailable at startup stays configured`() {
        val types = trackingIdTypesOf(configured = listOf(firebase), live = emptyList())

        assertEquals(setOf("google"), types)
    }

    @Test
    fun `a live handler counts even if it came from another init call`() {
        // initPushServices and init passed different lists; handlers were built from the second one.
        val types = trackingIdTypesOf(configured = listOf(firebase), live = listOf(firebase, huawei))

        assertEquals(setOf("google", "huawei"), types)
    }
}
