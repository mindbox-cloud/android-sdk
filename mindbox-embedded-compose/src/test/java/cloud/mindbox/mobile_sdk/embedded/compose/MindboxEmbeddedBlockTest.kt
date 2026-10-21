package cloud.mindbox.mobile_sdk.embedded.compose

import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
import cloud.mindbox.mobile_sdk.Mindbox
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockLoadingStrategy
import cloud.mindbox.mobile_sdk.embedded.MindboxEmbeddedBlockView
import cloud.mindbox.mobile_sdk.logger.Level
import java.time.Duration
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

    private fun showHostWindow() {
        compose.waitForIdle()
        dispatchWindowVisibility(compose.activity.window.decorView, View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    @Test
    fun `a placeholder block keeps its frame and stays silent while the SDK is not initialized`() {
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier
                    .height(120.dp)
                    .testTag("block"),
                loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
                onLoad = { events.add("load") },
                onFail = { events.add("fail") },
            )
        }
        showHostWindow()

        compose.onNodeWithTag("block").assertExists()
        compose.onNodeWithTag("block").assertHeightIsEqualTo(120.dp)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a block waiting hidden is zero height from its very first frame`() {
        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier
                    .height(120.dp)
                    .testTag("block"),
            )
        }
        compose.waitForIdle()

        compose.onNodeWithTag("block").assertHeightIsEqualTo(0.dp)
    }

    @Test
    fun `the strategy and the animation flag reach the view and are read once`() {
        val strategy = mutableStateOf(MindboxEmbeddedBlockLoadingStrategy.HIDDEN)
        val animates = mutableStateOf(false)

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                loadingStrategy = strategy.value,
                animatesReveal = animates.value,
            )
        }
        showHostWindow()

        val view = requireNotNull(findBlockView(compose.activity.window.decorView))
        assertEquals(MindboxEmbeddedBlockLoadingStrategy.HIDDEN, view.loadingStrategy)
        assertEquals(false, view.animatesReveal)

        ShadowLog.clear()
        compose.runOnUiThread {
            strategy.value = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER
            animates.value = true
        }
        showHostWindow()

        assertEquals(MindboxEmbeddedBlockLoadingStrategy.HIDDEN, view.loadingStrategy)
        assertEquals(false, view.animatesReveal)
        assertTrue(
            ShadowLog.getLogs().any { log ->
                log.msg.contains("fixed when the block is created")
            },
        )
    }

    @Test
    fun `a place with no name collapses the composable frame, not only the view inside it`() {
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "",
                modifier = Modifier
                    .height(120.dp)
                    .testTag("block"),
                onEmpty = { events.add("empty") },
                onFail = { reason -> events.add("fail:$reason") },
            )
        }
        showHostWindow()

        compose.onNodeWithTag("block").assertHeightIsEqualTo(0.dp)
        assertEquals(listOf("empty"), events)
    }

    @Test
    fun `no config within the budget fails the block with a network error`() {
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier
                    .height(120.dp)
                    .testTag("block"),
                timeoutMs = 50,
                onEmpty = { events.add("empty") },
                onFail = { reason -> events.add("fail:$reason") },
            )
        }
        showHostWindow()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(60))
        showHostWindow()

        assertEquals(listOf("fail:networkError"), events)
        compose.onNodeWithTag("block").assertHeightIsEqualTo(0.dp)
    }

    @Test
    fun `custom slots are accepted and the block still resolves`() {
        val events = mutableListOf<String>()

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
                onFail = { events.add("fail") },
                placeholder = { Box(Modifier.fillMaxSize().testTag("custom-placeholder")) },
                error = { Box(Modifier.fillMaxSize().testTag("custom-error")) },
            )
        }
        showHostWindow()

        compose.onNodeWithTag("custom-placeholder").assertExists()
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a slot that appears after the first composition still reaches the block`() {
        val withPlaceholder = mutableStateOf(false)

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                loadingStrategy = MindboxEmbeddedBlockLoadingStrategy.PLACEHOLDER,
                placeholder = if (withPlaceholder.value) {
                    { Box(Modifier.fillMaxSize().testTag("custom-placeholder")) }
                } else {
                    null
                },
            )
        }
        showHostWindow()
        compose.onNodeWithTag("custom-placeholder").assertDoesNotExist()

        compose.runOnUiThread { withPlaceholder.value = true }
        showHostWindow()

        compose.onNodeWithTag("custom-placeholder").assertExists()
    }

    @Test
    fun `a budget changed after creation is ignored, and said out loud`() {
        val timeout = mutableStateOf<Long?>(5_000)

        compose.setContent {
            MindboxEmbeddedBlock(
                placeSystemName = "main-screen-top",
                modifier = Modifier.height(120.dp),
                timeoutMs = timeout.value,
            )
        }
        showHostWindow()
        assertTrue(timeoutWarnings().isEmpty())

        compose.runOnUiThread { timeout.value = 60_000 }
        showHostWindow()

        val said = timeoutWarnings()
        assertEquals(1, said.size)
        assertTrue(said.single().contains("timeoutMs=60000"))
        assertTrue(said.single().contains("keeps 5000"))

        compose.runOnUiThread { timeout.value = 60_000 }
        showHostWindow()
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
        showHostWindow()
        compose.runOnUiThread { compose.activity.setTitle("recompose") }
        showHostWindow()

        assertTrue(timeoutWarnings().isEmpty())
    }

    @Test
    fun `leaving the composition lets the block go through releaseOrRetain, not release, and with nothing loaded it releases`() {
        Mindbox.setLogLevel(Level.INFO)
        val show = mutableStateOf(true)

        compose.setContent {
            if (show.value) {
                MindboxEmbeddedBlock(
                    placeSystemName = "main-screen-top",
                    modifier = Modifier.height(120.dp),
                )
            }
        }
        showHostWindow()
        show.value = false
        showHostWindow()

        val messages = ShadowLog.getLogs().mapNotNull { log -> log.msg }
        assertTrue(messages.any { message -> message.contains("Let go by the host wrapper, nothing to keep") })
    }

    private fun timeoutWarnings(): List<String> = ShadowLog.getLogs()
        .filter { log -> log.msg?.contains("was given timeoutMs=") == true }
        .map { log -> log.msg }

    private fun findBlockView(root: View): MindboxEmbeddedBlockView? {
        if (root is MindboxEmbeddedBlockView) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findBlockView(root.getChildAt(index))?.let { return it }
        }
        return null
    }
}
