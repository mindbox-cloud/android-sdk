package cloud.mindbox.mobile_sdk.inapp.domain.models

import android.content.Context
import cloud.mindbox.mobile_sdk.di.MindboxKoin
import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.inapp.domain.interfaces.repositories.InAppGeoRepository
import cloud.mindbox.mobile_sdk.models.GeoTargetingStub
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.SegmentationCheckInAppStub
import io.mockk.every
import io.mockk.junit4.MockKRule
import io.mockk.mockkClass
import io.mockk.mockkObject
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.mock.MockProviderRule
import org.koin.test.mock.declareMock
import kotlin.test.assertFalse

class TreeTargetingTest : KoinTest {

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

    private lateinit var inAppGeoRepository: InAppGeoRepository

    @Before
    fun onTestStart() {
        mockkObject(MindboxKoin)
        every { MindboxKoin.koin } returns getKoin()
        inAppGeoRepository = declareMock()
        every { inAppGeoRepository.getGeo() } returns GeoTargetingStub.getGeoTargeting()
            .copy(cityId = "123",
                regionId = "456",
                countryId = "789")
    }

    @Test
    fun `true targeting always true`() {
        assertTrue(InAppStub.getTargetingTrueNode().checkTargeting(emptyList()))
    }

    @Test
    fun `country targeting positive success check`() {
        assertTrue(InAppStub.getTargetingCountryNode()
            .copy(kind = Kind.POSITIVE, ids = listOf("789", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `country targeting positive error check`() {
        assertFalse(InAppStub.getTargetingCountryNode()
            .copy(kind = Kind.POSITIVE, ids = listOf("788", "456"))
            .checkTargeting(emptyList()))

    }

    @Test
    fun `country targeting negative error check`() {
        assertFalse(InAppStub.getTargetingCountryNode()
            .copy(kind = Kind.NEGATIVE, ids = listOf("789", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `country targeting negative success check`() {
        assertTrue(InAppStub.getTargetingCountryNode()
            .copy(kind = Kind.NEGATIVE, ids = listOf("788", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `region targeting positive success check`() {
        assertTrue(InAppStub.getTargetingRegionNode()
            .copy(kind = Kind.POSITIVE, ids = listOf("789", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `region targeting positive error check`() {
        assertFalse(InAppStub.getTargetingRegionNode()
            .copy(kind = Kind.POSITIVE, ids = listOf("788", "455"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `region targeting negative error check`() {
        assertFalse(InAppStub.getTargetingRegionNode()
            .copy(kind = Kind.NEGATIVE, ids = listOf("789", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `region targeting negative success check`() {
        assertTrue(InAppStub.getTargetingRegionNode()
            .copy(kind = Kind.NEGATIVE, ids = listOf("788", "455"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `city targeting positive success check`() {
        assertTrue(InAppStub.getTargetingCityNode()
            .copy(kind = Kind.POSITIVE, ids = listOf("123", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `city targeting positive error check`() {
        assertFalse(InAppStub.getTargetingCityNode()
            .copy(kind = Kind.POSITIVE, ids = listOf("788", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `city targeting negative error check`() {
        assertFalse(InAppStub.getTargetingCityNode()
            .copy(kind = Kind.NEGATIVE, ids = listOf("123", "456"))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `city targeting negative success check`() {
        assertTrue(InAppStub.getTargetingCityNode()
            .copy(kind = Kind.NEGATIVE, ids = listOf("788", "456"))
            .checkTargeting(emptyList()))
    }


    @Test
    fun `segment targeting positive success check`() {
        assertTrue(InAppStub.getTargetingSegmentNode()
            .copy(kind = Kind.POSITIVE, segmentationExternalId = "123", segmentExternalId = "234")
            .checkTargeting(listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(
                    segmentation = "123",
                    segment = "234"))))
    }

    @Test
    fun `segment targeting positive error check`() {
        assertFalse(InAppStub.getTargetingSegmentNode()
            .copy(kind = Kind.POSITIVE, segmentationExternalId = "123", segmentExternalId = "234")
            .checkTargeting(listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(
                    segmentation = "123",
                    segment = "233"))))
    }

    @Test
    fun `segment targeting negative error check`() {
        assertFalse(InAppStub.getTargetingSegmentNode()
            .copy(kind = Kind.NEGATIVE, segmentationExternalId = "123", segmentExternalId = "234")
            .checkTargeting(listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(
                    segmentation = "123",
                    segment = "234"))))
    }

    @Test
    fun `segment targeting negative success check`() {
        assertTrue(InAppStub.getTargetingSegmentNode()
            .copy(kind = Kind.NEGATIVE, segmentationExternalId = "123", segmentExternalId = "234")
            .checkTargeting(listOf(SegmentationCheckInAppStub.getCustomerSegmentation()
                .copy(
                    segmentation = "123",
                    segment = ""))))
    }

    @Test
    fun `segment targeting empty list positive check`() {
        assertFalse(InAppStub.getTargetingSegmentNode()
            .copy(kind = Kind.POSITIVE, segmentationExternalId = "123", segmentExternalId = "234")
            .checkTargeting(emptyList()))
    }

    @Test
    fun `segment targeting empty list negative check`() {
        assertFalse(InAppStub.getTargetingSegmentNode()
            .copy(kind = Kind.NEGATIVE, segmentationExternalId = "123", segmentExternalId = "234")
            .checkTargeting(emptyList()))
    }

    @Test
    fun `intersection targeting check both true`() {
        assertTrue(InAppStub.getTargetingIntersectionNode()
            .copy(nodes = listOf(InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                InAppStub.getTargetingRegionNode().copy(kind = Kind.POSITIVE, ids = listOf("456"))))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `intersection targeting check both false`() {
        assertFalse(InAppStub.getTargetingIntersectionNode()
            .copy(nodes = listOf(InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("234")),
                InAppStub.getTargetingCityNode().copy(kind = Kind.POSITIVE, ids = listOf("234"))))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `intersection targeting check one true one false`() {
        assertFalse(InAppStub.getTargetingIntersectionNode()
            .copy(nodes = listOf(InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                InAppStub.getTargetingCityNode().copy(kind = Kind.POSITIVE, ids = listOf("234"))))
            .checkTargeting(emptyList()))
    }


    @Test
    fun `union targeting check both true`() {
        assertTrue(InAppStub.getTargetingUnionNode()
            .copy(nodes = listOf(InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                InAppStub.getTargetingRegionNode().copy(kind = Kind.POSITIVE, ids = listOf("456"))))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `union targeting check both false`() {
        assertFalse(InAppStub.getTargetingUnionNode()
            .copy(nodes = listOf(InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("234")),
                InAppStub.getTargetingCityNode().copy(kind = Kind.POSITIVE, ids = listOf("234"))))
            .checkTargeting(emptyList()))
    }

    @Test
    fun `union targeting check one true one false`() {
        assertTrue(InAppStub.getTargetingUnionNode()
            .copy(nodes = listOf(InAppStub.getTargetingCityNode()
                .copy(kind = Kind.POSITIVE, ids = listOf("123")),
                InAppStub.getTargetingCityNode().copy(kind = Kind.POSITIVE, ids = listOf("234"))))
            .checkTargeting(emptyList()))
    }

}