package cloud.mindbox.mobile_sdk.inapp.data.repositories

import cloud.mindbox.mobile_sdk.di.dataModule
import cloud.mindbox.mobile_sdk.di.domainModule
import cloud.mindbox.mobile_sdk.di.monitoringModule
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.verify
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.inject

class InAppRepositoryTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(dataModule, monitoringModule, domainModule)
    }

    private val inAppRepository: InAppRepositoryImpl by inject()

    @Test
    fun `shown inApp ids is not empty and is a valid json`() {
        val testHashSet = hashSetOf(
            "71110297-58ad-4b3c-add1-60df8acb9e5e",
            "ad487f74-924f-44f0-b4f7-f239ea5643c5"
        )
        every { MindboxPreferences.shownInAppIds } returns
                "[\"71110297-58ad-4b3c-add1-60df8acb9e5e\",\"ad487f74-924f-44f0-b4f7-f239ea5643c5\"]"
        assertTrue(inAppRepository.getShownInApps().containsAll(testHashSet))
    }

    @Test
    fun `shownInApp ids returns null`() {
        every { MindboxPreferences.shownInAppIds } returns "a"
        assertNotNull(inAppRepository.getShownInApps())
    }

    @Test
    fun `shown inApp ids empty`() {
        val expectedIds = hashSetOf<String>()
        every { MindboxPreferences.shownInAppIds } returns ""
        val actualIds = inAppRepository.getShownInApps()
        assertTrue(expectedIds.containsAll(actualIds))
    }

    @Test
    fun `shown inApp ids is not empty and is not a json`() {
        every { MindboxPreferences.shownInAppIds } returns "123"
        val expectedResult = hashSetOf<String>()
        val actualResult = inAppRepository.getShownInApps()
        assertTrue(actualResult.containsAll(expectedResult))
    }

    @Test
    fun `save shown inApp success`() {
        val expectedJson = """
            |["123","456"]
        """.trimMargin()
        every { MindboxPreferences.shownInAppIds } returns "[123]"
        inAppRepository.saveShownInApp("456")
        verify(exactly = 1) {
            MindboxPreferences.shownInAppIds = expectedJson
        }
    }
}