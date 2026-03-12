package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.models.convertToIso8601String
import cloud.mindbox.mobile_sdk.models.toTimestamp
import cloud.mindbox.mobile_sdk.pushes.PrefPushToken
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class MigrationManagerTest {

    @Before
    fun setUp() {
        mockkObject(MindboxPreferences)
        mockkObject(MindboxDI)
        every { MindboxDI.appModule } returns mockk(relaxed = true) {
            every { gson } returns Gson()
        }
    }

    @Test
    fun `run method properly updates MindboxPreferences shownInApps`() = runTest {
        val gson: Gson = mockk()
        val oldShownInAppIds = setOf("app1", "app2")
        val oldShownInAppIdsString = Gson().toJson(oldShownInAppIds)
        val expectedNewShownInApps = mapOf("app2" to 0L, "app1" to 0L)
        val expectedNewMapString = Gson().toJson(expectedNewShownInApps)
        val expectedShownInappsWithListShown = mapOf("app2" to listOf(0L), "app1" to listOf(0L))
        val expectedShownInappsWithListShowString = Gson().toJson(expectedShownInappsWithListShown)
        every {
            MindboxPreferences.shownInAppIds
        } returns oldShownInAppIdsString
        every { MindboxPreferences.shownInApps } returns expectedNewMapString
        every { MindboxPreferences.shownInApps = any() } just runs
        every {
            gson.fromJson<String>(
                MindboxPreferences.shownInAppIds,
                object : TypeToken<HashSet<String>>() {}.type
            ) ?: emptySet<String>()
        } returns oldShownInAppIds
        every {
            gson.toJson(
                expectedNewShownInApps,
                object : TypeToken<HashMap<String, List<Long>>>() {}.type
            )
        } returns expectedNewMapString

        val mm = MigrationManager(mockk())
        every { MindboxPreferences.versionCode } returns 2
        mm.migrateAll()
        coVerify(exactly = 1) {
            MindboxPreferences.shownInApps = expectedNewMapString
        }
        coVerify(exactly = 1) {
            MindboxPreferences.shownInApps = expectedShownInappsWithListShowString
        }
    }

    @Test
    fun `version2150 saves minimum push token timestamp as first initialization time`() = runTest {
        val expectedTimestamp = 1000L
        every { MindboxPreferences.versionCode } returns 3
        every { MindboxPreferences.firstInitializationTime } returns null
        every { MindboxPreferences.pushTokens } returns mapOf(
            "FCM" to PrefPushToken("tokenFCM", expectedTimestamp),
            "HMS" to PrefPushToken("tokenHMS", 2000L),
        )
        every { MindboxPreferences.firstInitializationTime = any() } just runs
        every { MindboxPreferences.versionCode = any() } just runs

        MigrationManager(mockk()).migrateAll()

        verify(exactly = 1) {
            MindboxPreferences.firstInitializationTime = expectedTimestamp
                .toTimestamp()
                .convertToIso8601String()
        }
    }

    @Test
    fun `version2150 does not override existing first initialization time`() = runTest {
        every { MindboxPreferences.versionCode } returns 3
        every { MindboxPreferences.firstInitializationTime } returns "2025-01-10T07:40:00Z"
        every { MindboxPreferences.versionCode = any() } just runs

        MigrationManager(mockk()).migrateAll()

        verify(exactly = 0) {
            MindboxPreferences.firstInitializationTime = any()
        }
    }

    @Test
    fun `version2150 uses current time when no push tokens available`() = runTest {
        every { MindboxPreferences.versionCode } returns 3
        every { MindboxPreferences.firstInitializationTime } returns null
        every { MindboxPreferences.pushTokens } returns emptyMap()
        every { MindboxPreferences.firstInitializationTime = any() } just runs
        every { MindboxPreferences.versionCode = any() } just runs

        MigrationManager(mockk()).migrateAll()

        verify(exactly = 1) {
            MindboxPreferences.firstInitializationTime = any()
        }
    }

    @Test
    fun `version2150 filters out zero push token timestamps`() = runTest {
        val expectedTimestamp = 5000L
        every { MindboxPreferences.versionCode } returns 3
        every { MindboxPreferences.firstInitializationTime } returns null
        every { MindboxPreferences.pushTokens } returns mapOf(
            "FCM" to PrefPushToken("tokenFCM", 0L),
            "HMS" to PrefPushToken("tokenHMS", expectedTimestamp),
        )
        every { MindboxPreferences.firstInitializationTime = any() } just runs
        every { MindboxPreferences.versionCode = any() } just runs

        MigrationManager(mockk()).migrateAll()

        verify(exactly = 1) {
            MindboxPreferences.firstInitializationTime = expectedTimestamp
                .toTimestamp()
                .convertToIso8601String()
        }
    }
}
