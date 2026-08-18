package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.models.EventType
import cloud.mindbox.mobile_sdk.models.InAppEventType
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class InAppEventManagerTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @OverrideMockKs
    private lateinit var inAppEventManager: InAppEventManagerImpl

    @Test
    fun `validate app startup inApp event`() {
        assertTrue(inAppEventManager.isValidInAppEvent(InAppEventType.AppStartup))
    }

    @Test
    fun `validate custom async event`() {
        assertTrue(
            inAppEventManager.isValidInAppEvent(
                InAppEventType.OrdinalEvent(
                    EventType.AsyncOperation(
                        ""
                    )
                )
            )
        )
    }

    @Test
    fun `validate custom sync event`() {
        assertTrue(
            inAppEventManager.isValidInAppEvent(
                InAppEventType.OrdinalEvent(
                    EventType.SyncOperation(
                        ""
                    )
                )
            )
        )
    }

    @Test
    fun `validate system event`() {
        assertFalse(inAppEventManager.isValidInAppEvent(InAppEventType.OrdinalEvent(EventType.AppInstalledWithoutCustomer)))
    }

    @Test
    fun `embedded place request is not a valid in-app event`() {
        // MOBILE-333: block content is resolved by pull/push through the blocks controller;
        // the place signal must never reach the overlay conveyor (the MOBILE-324 bypass is gone).
        assertFalse(
            inAppEventManager.isValidInAppEvent(
                InAppEventType.EmbeddedPlaceRequested(placeSystemName = "main-screen-top"),
            ),
        )
    }
}
