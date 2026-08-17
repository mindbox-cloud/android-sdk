package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.domain.models.DisplayConditions
import cloud.mindbox.mobile_sdk.inapp.domain.models.Form
import cloud.mindbox.mobile_sdk.inapp.domain.models.InApp
import cloud.mindbox.mobile_sdk.models.InAppStub
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MOBILE-333 filters: place lookup, and cutting embedded/directCall out of the overlay
 * path.
 */
class EmbeddedFilteringManagerTest {

    private val manager = InAppFilteringManagerImpl(inAppRepository = mockk(relaxed = true))

    private fun embeddedInApp(id: String = "embedded-id", place: String = "main-screen-top"): InApp =
        InAppStub.getInApp().copy(
            id = id,
            form = Form(variants = listOf(InAppStub.getEmbedded().copy(inAppId = id, placeSystemName = place)))
        )

    private fun modalInApp(id: String = "modal-id"): InApp = InAppStub.getInApp().copy(id = id)

    @Test
    fun `embedded in-app is picked by its place`() {
        val embedded = embeddedInApp()
        val other = embeddedInApp(id = "other", place = "another-place")

        val result = manager.filterEmbeddedInAppsByPlace(listOf(embedded, other, modalInApp()), "main-screen-top")

        assertEquals(listOf(embedded), result)
    }

    @Test
    fun `place comparison trims whitespace on both sides`() {
        val embedded = embeddedInApp()

        val result = manager.filterEmbeddedInAppsByPlace(listOf(embedded), "  main-screen-top  ")

        assertEquals(listOf(embedded), result)
    }

    @Test
    fun `place comparison is case sensitive`() {
        val embedded = embeddedInApp(place = "Main-Screen-Top")

        val result = manager.filterEmbeddedInAppsByPlace(listOf(embedded), "main-screen-top")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `overlay path never sees in-apps with no overlay variant`() {
        val modal = modalInApp()

        val result = manager.filterOutNonOverlayInApps(listOf(embeddedInApp(), modal))

        assertEquals(listOf(modal), result)
    }

    @Test
    fun `mixed form stays on the overlay path thanks to its overlay variant`() {
        val mixed = InAppStub.getInApp().copy(
            id = "mixed",
            form = Form(
                variants = listOf(
                    InAppStub.getEmbedded().copy(inAppId = "mixed", placeSystemName = "main-screen-top"),
                    InAppStub.getModalWindow().copy(inAppId = "mixed")
                )
            )
        )

        val result = manager.filterOutNonOverlayInApps(listOf(mixed))

        assertEquals(listOf(mixed), result)
    }

    @Test
    fun `direct call in-apps are cut from a non-direct path`() {
        val direct = modalInApp(id = "direct").copy(displayConditions = DisplayConditions.DIRECT_CALL)
        val ordinary = modalInApp(id = "ordinary")
        val nullConditions = modalInApp(id = "null-conditions").copy(displayConditions = null)

        val result = manager.filterOutDirectCallInApps(listOf(direct, ordinary, nullConditions))

        assertEquals(listOf(ordinary, nullConditions), result)
    }
}
