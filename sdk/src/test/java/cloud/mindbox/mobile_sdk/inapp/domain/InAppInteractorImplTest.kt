package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.models.*
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigResponseStub
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@RunWith(MockitoJUnitRunner::class)
internal class InAppInteractorImplTest {

    @Mock
    private lateinit var inAppInteractor: InAppInteractorImpl

    @Before
    fun onTestStart() {
        runBlocking {
            whenever(inAppInteractor.validateInAppVersion(any())).thenCallRealMethod()
            whenever(inAppInteractor.validateSegmentation(any(), any())).thenCallRealMethod()
            whenever(inAppInteractor.validateInAppTargeting(any())).thenCallRealMethod()
            whenever(inAppInteractor.getConfigWithTargeting(any())).thenCallRealMethod()
        }
    }

    @Test
    fun `config has only targeting in-apps`() {
        var rez = true
        inAppInteractor.getConfigWithTargeting(InAppConfigResponseStub.getConfig()).inApps.forEach { inApp ->
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
        assertFalse(inAppInteractor.validateInAppTargeting(InAppStub.get()
            .copy(targeting = null)))
    }

    @Test
    fun `in-app has no segmentation`() {
        assertFalse(inAppInteractor.validateInAppTargeting(InAppStub.get()
            .copy(targeting = InAppStub.get().targeting!!.copy(
                segmentation = null))))
    }

    @Test
    fun `in-app has no segment`() {
        assertFalse(inAppInteractor.validateInAppTargeting(InAppStub.get()
            .copy(targeting = InAppStub.get().targeting!!.copy(
                segment = null))))
    }

    @Test
    fun `customer has no segmentation`() {
        assertFalse(inAppInteractor.validateSegmentation(InAppStub.get(),
            SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentInApp(IdsInApp(null)))))
    }

    @Test
    fun `customer is in segmentation`() {
        assertTrue(inAppInteractor.validateSegmentation(InAppStub.get(),
            SegmentationCheckInAppStub.getCustomerSegmentation()))
    }

    @Test
    fun `customer is not in segmentation`() {
        assertFalse(inAppInteractor.validateSegmentation(InAppStub.get(),
            SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(segment = SegmentInApp(IdsInApp("123456")))))
    }

    @Test
    fun `in-app version is lower than required`() {
        assertFalse(inAppInteractor.validateInAppVersion(InAppStub.get()
            .copy(maxVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1)))
    }

    @Test
    fun `in-app version is higher than required`() {
        assertFalse(inAppInteractor.validateInAppVersion(InAppStub.get()
            .copy(minVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1)))
    }

    @Test
    fun `in-app version is out of range`() {
        assertFalse(inAppInteractor.validateInAppVersion(InAppStub.get()
            .copy(minVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION + 1,
                maxVersion = InAppMessageManagerImpl.CURRENT_IN_APP_VERSION - 1)))
    }

    @Test
    fun `in-app version no min version`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.get().copy(minVersion = null)))
    }

    @Test
    fun `in-app version no max version`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.get().copy(minVersion = null)))
    }

    @Test
    fun `in-app version no limitations`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.get()
            .copy(minVersion = null, maxVersion = null)))

    }

    @Test
    fun `in-app version is in range`() {
        assertTrue(inAppInteractor.validateInAppVersion(InAppStub.get()))
    }

}