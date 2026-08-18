package cloud.mindbox.mobile_sdk.embedded

import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import java.util.concurrent.ConcurrentHashMap

/**
 * The debug overrides of block content, keyed by place system name.
 *
 * Set from the host app's QA code and read by the content factory on the main thread at every
 * attach, so the map has to survive being written from one thread while another reads it.
 *
 * The override sits in the place of the config, so the whole path below it — the page, the bridge,
 * the container's waiting budget — works for real; only the source of the block content changes.
 */
internal object EmbeddedBlockContentOverrides {

    private val overrides = ConcurrentHashMap<String, MindboxEmbeddedBlockDebug.Content>()

    fun set(content: MindboxEmbeddedBlockDebug.Content, placeSystemName: String) {
        overrides[placeSystemName] = content
        mindboxLogI(
            "[EmbeddedBlock] Debug override is ON for place '$placeSystemName': ${content.describe()}",
        )
    }

    fun remove(placeSystemName: String) {
        if (overrides.remove(placeSystemName) == null) return
        mindboxLogI("[EmbeddedBlock] Debug override is OFF for place '$placeSystemName'")
    }

    fun removeAll() {
        if (overrides.isEmpty()) return
        overrides.clear()
        mindboxLogI("[EmbeddedBlock] All debug overrides are OFF")
    }

    fun contentFor(placeSystemName: String): MindboxEmbeddedBlockDebug.Content? =
        overrides[placeSystemName]

    private fun MindboxEmbeddedBlockDebug.Content.describe(): String = when (this) {
        is MindboxEmbeddedBlockDebug.Content.Url -> url
        is MindboxEmbeddedBlockDebug.Content.Html -> "inline html"
        is MindboxEmbeddedBlockDebug.Content.Empty -> "empty"
    }
}
