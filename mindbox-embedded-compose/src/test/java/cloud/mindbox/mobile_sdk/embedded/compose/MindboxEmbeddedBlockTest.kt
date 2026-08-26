package cloud.mindbox.mobile_sdk.embedded.compose

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class MindboxEmbeddedBlockTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Puts the host window on screen so the block inside starts its content. */
    private fun settle() {
        compose.waitForIdle()
        dispatchWindowVisibility(compose.activity.window.decorView, View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    @Test
    fun `the block keeps its frame while the config has not arrived`() {
        // Nothing is initialized in this test process — the case a host hits when it composes a
        // screen before the SDK finished starting up. The block holds the height it was given
        // and stays silent: no config yet is not an empty place.
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier
                    .height(120.dp)
                    .testTag("block"),
                onLoad = { events.add("load") },
                onFail = { events.add("fail") },
            )
        }
        settle()

        compose.onNodeWithTag("block").assertExists()
        compose.onNodeWithTag("block").assertHeightIsEqualTo(120.dp)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a place with no name collapses the block to nothing`() {
        // A GONE child cannot shrink a Compose modifier by itself — the wrapper has to collapse
        // the frame, or the host layout keeps a hole where the block used to be.
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "",
                modifier = Modifier
                    .height(120.dp)
                    .testTag("block"),
                onFail = { events.add("fail") },
            )
        }
        settle()

        compose.onNodeWithTag("block").assertHeightIsEqualTo(0.dp)
        assertTrue(events.contains("fail"))
    }

    @Test
    fun `custom slots are accepted and the block still resolves`() {
        // Slot rendering itself is covered at the view level (setPlaceholderView/setErrorView);
        // here the wiring must simply survive slots being present.
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                onFail = { events.add("fail") },
                placeholder = { Box(Modifier.fillMaxSize().testTag("custom-placeholder")) },
                error = { Box(Modifier.fillMaxSize().testTag("custom-error")) },
            )
        }
        settle()

        compose.onNodeWithTag("custom-placeholder").assertExists()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a slot that appears after the first composition still reaches the block`() {
        // The factory runs once. A caller that decides on its slots later — after a flag loads,
        // after a theme resolves — would otherwise never get them installed at all.
        val withPlaceholder = mutableStateOf(false)

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                placeholder = if (withPlaceholder.value) {
                    { Box(Modifier.fillMaxSize().testTag("custom-placeholder")) }
                } else {
                    null
                },
            )
        }
        settle()
        compose.onNodeWithTag("custom-placeholder").assertDoesNotExist()

        compose.runOnUiThread { withPlaceholder.value = true }
        settle()

        compose.onNodeWithTag("custom-placeholder").assertExists()
    }

    @Test
    fun `default callbacks and slots are optional`() {
        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
            )
        }
        settle()
    }

    @Test
    fun `leaving the composition frees the block`() {
        // AndroidView's onRelease is the only teardown signal a composable gets — without it
        // every scroll past the block would leak a WebView.
        val shown = mutableStateOf(true)

        compose.setContent {
            if (shown.value) {
                MindboxEmbeddedBlock(
                    placeSystemName = "main-screen-top",
                    modifier = Modifier.height(120.dp).testTag("block"),
                )
            }
        }
        settle()
        compose.onNodeWithTag("block").assertExists()

        compose.runOnUiThread { shown.value = false }
        settle()

        compose.onNodeWithTag("block").assertDoesNotExist()
    }

    @Test
    fun `a budget changed after creation is ignored, and said out loud`() {
        // The factory runs once, so the budget is settled there. A value quietly dropped is the kind
        // of thing a host debugs against the clock — the View has no such trap, its timeout being a
        // constructor argument, but this composable takes the parameter on every recomposition.
        val timeout = mutableStateOf<Long?>(5_000)

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                timeoutMs = timeout.value,
            )
        }
        settle()
        assertTrue(timeoutWarnings().isEmpty())

        compose.runOnUiThread { timeout.value = 60_000 }
        settle()

        val said = timeoutWarnings()
        assertEquals(1, said.size)
        assertTrue(said.single().contains("timeoutMs=60000"))
        assertTrue(said.single().contains("keeps 5000"))

        // Said once per value, not once per frame: a recomposition on the same value adds nothing.
        compose.runOnUiThread { timeout.value = 60_000 }
        settle()
        assertEquals(1, timeoutWarnings().size)
    }

    @Test
    fun `a budget left alone says nothing`() {
        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                timeoutMs = 5_000,
            )
        }
        settle()
        compose.runOnUiThread { compose.activity.setTitle("recompose") }
        settle()

        assertTrue(timeoutWarnings().isEmpty())
    }

    private fun timeoutWarnings(): List<String> = ShadowLog.getLogs()
        .filter { log -> log.msg?.contains("was given timeoutMs=") == true }
        .map { log -> log.msg }
}
