package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.mockk.*
import org.junit.Before
import org.junit.Test

class MigrationManagerTest {

    @Before
    fun setUp() {
        mockkObject(MindboxPreferences)
    }

    @Test
    fun `run method properly updates MindboxPreferences shownInApps`() {
        val gson: Gson = mockk()
        val oldShownInAppIds = setOf("app1", "app2")
        val oldShownInAppIdsString = Gson().toJson(oldShownInAppIds)
        val expectedNewShownInApps = mapOf("app2" to 0L, "app1" to 0L)
        val expectedNewMapString = Gson().toJson(expectedNewShownInApps)
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
                object : TypeToken<HashMap<String, Long>>() {}.type
            )
        } returns expectedNewMapString

        val mm = MigrationManager(mockk())
        every { MindboxPreferences.versionCode } returns Constants.SDK_VERSION_CODE
        mm.migrateAll()

        verify(exactly = 1) {
            MindboxPreferences.shownInApps = expectedNewMapString
        }
        verify(exactly = 1) {
            MindboxPreferences.shownInAppIds = ""
        }
    }

}