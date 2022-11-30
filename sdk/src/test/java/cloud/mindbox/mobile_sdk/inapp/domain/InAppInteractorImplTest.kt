package cloud.mindbox.mobile_sdk.inapp.domain

import cloud.mindbox.mobile_sdk.MindboxConfiguration
import cloud.mindbox.mobile_sdk.inapp.presentation.InAppMessageManagerImpl
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import cloud.mindbox.mobile_sdk.models.operation.response.InAppConfigStub
import com.android.volley.VolleyError
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.impl.annotations.OverrideMockKs
import io.mockk.junit4.MockKRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class InAppInteractorImplTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var mindboxConfiguration: MindboxConfiguration

    @MockK
    private lateinit var inAppRepository: InAppRepository

    @OverrideMockKs
    private lateinit var inAppInteractor: InAppInteractorImpl


    @Test
    fun `should choose in-app without targeting`() = runTest {
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(mindboxConfiguration, any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
        val expectedResult = InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(segmentation = null,
                segment = null))
        val actualResult = inAppInteractor.chooseInAppToShow(InAppConfigStub.getConfig()
            .copy(listOf(InAppStub.getInApp()
                .copy(targeting = InAppStub.getInApp().targeting?.copy(segmentation = null,
                    segment = null)),
                (InAppStub.getInApp()
                    .copy(targeting = InAppStub.getInApp().targeting?.copy(type = "asd",
                        "123",
                        segment = "456"))))),
            configuration = mindboxConfiguration)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `should choose in-app with targeting`() = runTest {
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(mindboxConfiguration, any())

        } returns SegmentationCheckInAppStub.getSegmentationCheckInApp()
            .copy(customerSegmentations = listOf(
                SegmentationCheckInAppStub.getCustomerSegmentation()
                    .copy(segmentation = SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.copy(
                        SegmentationCheckInAppStub.getCustomerSegmentation().segmentation?.ids?.copy(
                            "123")),
                        segment = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.copy(
                            ids = SegmentationCheckInAppStub.getCustomerSegmentation().segment?.ids?.copy(
                                "456")))))
        val expectedResult = InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(type = "asd",
                segmentation = "123",
                segment = "456"))
        val actualResult = inAppInteractor.chooseInAppToShow(InAppConfigStub.getConfig()
            .copy(listOf(InAppStub.getInApp()
                .copy(targeting = InAppStub.getInApp().targeting?.copy(type = "asd",
                    "123",
                    segment = "456")))),
            configuration = mindboxConfiguration)
        assertEquals(expectedResult, actualResult)
    }

    @Test
    fun `should return null if no in-apps present`() = runTest {
        val actualResult = inAppInteractor.chooseInAppToShow(InAppConfigStub.getConfig()
            .copy(listOf()),
            configuration = mindboxConfiguration)
        assertNull(actualResult)
    }

    @Test
    fun `should return null if network exception`() = runTest {
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(mindboxConfiguration, any())

        } throws VolleyError()
        val actualResult = inAppInteractor.chooseInAppToShow(InAppConfigStub.getConfig()
            .copy(listOf(InAppStub.getInApp()
                .copy(targeting = InAppStub.getInApp().targeting?.copy(type = "asd",
                    "123",
                    segment = "456")))),
            configuration = mindboxConfiguration)
        assertNull(actualResult)
    }

    @Test
    fun `should throw exception if non network error`() = runTest {
        every {
            inAppRepository.getShownInApps()
        } returns HashSet()
        coEvery {
            inAppRepository.fetchSegmentations(mindboxConfiguration, any())

        } throws Error()

        assertThrows(Error::class.java) {
            runBlocking {
                inAppInteractor.chooseInAppToShow(InAppConfigStub.getConfig()
                    .copy(listOf(InAppStub.getInApp()
                        .copy(targeting = InAppStub.getInApp().targeting?.copy(type = "asd",
                            "123",
                            segment = "456")))),
                    configuration = mindboxConfiguration)
            }
        }
    }

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
    fun `validate in-app was shown list is empty`() {
        every { inAppRepository.getShownInApps() } returns HashSet()
        assertTrue(inAppInteractor.validateInAppNotShown(InAppStub.getInApp()))
    }

    @Test
    fun `validate in-app was shown list isn't empty but does not contain current in-app id`() {
        every { inAppRepository.getShownInApps() } returns hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5")
        assertTrue(inAppInteractor.validateInAppNotShown(InAppStub.getInApp().copy(id = "123")))
    }

    @Test
    fun `validate in-app was shown list isn't empty and contains current in-app id`() {
        every { inAppRepository.getShownInApps() } returns hashSetOf("71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5")
        assertFalse(inAppInteractor.validateInAppNotShown(InAppStub.getInApp()
            .copy(id = "71110297-58ad-4b3c-add1-60df8acb9e5e")))
    }

    @Test
    fun `in-app has segmentation and segment`() {
        assertTrue(inAppInteractor.validateInAppTargeting(InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(segment = "213",
                segmentation = "345"))))
    }

    @Test
    fun `in-app has no segmentation and no segment`() {
        assertTrue(inAppInteractor.validateInAppTargeting(InAppStub.getInApp()
            .copy(targeting = InAppStub.getInApp().targeting?.copy(segment = null,
                segmentation = null))))
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
            .copy(maxVersion = null)))
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