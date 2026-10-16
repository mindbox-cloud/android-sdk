package cloud.mindbox.mobile_sdk.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.managers.SharedPreferencesManager
import cloud.mindbox.mobile_sdk.models.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class MindboxPreferencesSoftResetTest {

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().apply()
        SharedPreferencesManager.with(context)
    }

    @Test
    fun `softReset clears the min interval start together with the show history`() {
        MindboxPreferences.shownInApps = """{"inapp-1":[1700000000000]}"""
        MindboxPreferences.lastInappChangeStateTime = Timestamp(1_700_000_000_000L)

        MindboxPreferences.softReset()

        assertEquals("", MindboxPreferences.shownInApps)
        assertEquals(Timestamp.ZERO, MindboxPreferences.lastInappChangeStateTime)
    }
}
