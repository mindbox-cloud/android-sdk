package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class InAppInteractorImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @OverrideMockKs
    private lateinit var inAppInteractor: InAppInteractorImpl

    @Test
    fun `config has only targeting in-apps`() {
        var rez = true
        inAppInteractor.getConfigWithTargeting(InAppConfigStub.getConfig()
            .copy(inApps = listOf(
                InAppStub.getInApp().copy(targeting = InAppStub.getInApp().targeting?.copy(
                    segmentation = null,
                    segment = null)),
                InAppStub.getInApp().copy(targeting = InAppStub.getInApp().targeting?.copy(
                    segmentation = "abc",
                    segment = null)),
                InAppStub.getInApp().copy(targeting = InAppStub.getInApp().targeting?.copy(
                    segmentation = null,
                    segment = "xb")),
                InAppStub.getInApp().copy(targeting = InAppStub.getInApp().targeting?.copy(
                    segmentation = "abc",
                    segment = "xb"))
            ))).inApps.forEach { inApp ->
            inApp.targeting?.apply {
                if (segmentation == null || segment == null) {
                    rez = false
                }
            }
        }
        assertTrue(rez)
    }

    @Test
    fun `in-app has no targeting`() {
        assertFalse(inAppInteractor.validateInAppTargeting(InAppStub.getInApp()
            .copy(targeting = null)))
    }

    @Test
    fun `in-app has no segmentation`() {
        assertFalse(inAppInteractor.validateInAppTargeting(InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(
                segmentation = null))))
    }

    @Test
    fun `in-app has no segment`() {
        assertFalse(inAppInteractor.validateInAppTargeting(InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(
                segment = null))))
    }

    @Test
    fun `customer has no segmentation`() {
        assertFalse(inAppInteractor.validateSegmentation(InAppStub.getInApp(),
            SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                        null)
                ))))
    }

    @Test
    fun `customer is in segmentation`() {
        assertTrue(inAppInteractor.validateSegmentation(InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(segment = "123")),
            SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                        "123")
                ))))
    }

    @Test
    fun `customer is not in segmentation`() {
        assertFalse(inAppInteractor.validateSegmentation(InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(segment = "1234")),
            SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                    ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                        "123")
                ))))
    }

    @Test
    fun `in-app version is lower than required`() {
        assertFalse(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(maxVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1)))
    }

    @Test
    fun `in-app version is higher than required`() {
        assertFalse(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(minVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1)))
    }

    @Test
    fun `in-app version is out of range`() {
        assertFalse(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(minVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1,
                maxVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1)))
    }

    @Test
    fun `in-app version no min version`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(minVersion = null)))
    }

    @Test
    fun `in-app version no max version`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(minVersion = null)))
    }

    @Test
    fun `in-app version no limitations`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(minVersion = null, maxVersion = null)))

    }

    @Test
    fun `in-app version is in range`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.getInApp()
            .copy(minVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1,
                maxVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1)))
    }

}