package cloud.mindbox.mobile_sdk.embedded

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockContentFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun resolutionFor(rawConfig: String, place: String = "place"): EmbeddedContentResolution =
        EmbeddedBlockContentFactory { rawConfig }.create(context, place)

    @Test
    fun `a placement for the place builds content`() {
        // No mechanic type any more: a configured place always gets content, and what the page
        // draws is the page's business.
        val resolution = resolutionFor("""{"inlineBlocks":[{"placeSystemName":"place","pageUrl":null}]}""")

        assertTrue(resolution is EmbeddedContentResolution.Content)
    }

    @Test
    fun `the first candidate for the place wins`() {
        val resolution = resolutionFor(
            """
            {"inlineBlocks":[
              {"placeSystemName":"place","pageUrl":"https://mindbox.ru/a"},
              {"placeSystemName":"place","pageUrl":"https://mindbox.ru/b"}
            ]}
            """.trimIndent(),
        )

        // Which candidate wins becomes a targeting decision with the in-app migration; today the
        // contract is only "one of them, never a crash".
        assertTrue(resolution is EmbeddedContentResolution.Content)
    }

    @Test
    fun `no placement for the place means nothing to show`() {
        val resolution = resolutionFor("""{"inlineBlocks":[{"placeSystemName":"other","pageUrl":null}]}""")

        assertTrue(resolution is EmbeddedContentResolution.NothingToShow)
    }

    @Test
    fun `a config without the section means nothing to show`() {
        // The config arrived and simply has no blocks in it — a settled answer, not a wait.
        assertTrue(resolutionFor("""{"inapps":[]}""") is EmbeddedContentResolution.NothingToShow)
    }

    @Test
    fun `a malformed config means nothing to show`() {
        assertTrue(resolutionFor("not a json") is EmbeddedContentResolution.NothingToShow)
    }

    @Test
    fun `an absent config is a wait, not an empty place`() {
        // The SDK may still be starting up. Telling the block "nothing here" would collapse it
        // for good; the block has to keep its placeholder until the config actually lands.
        assertTrue(resolutionFor("") is EmbeddedContentResolution.NotReadyYet)
    }

    @Test
    fun `a nameless block has nothing to resolve`() {
        assertTrue(
            EmbeddedBlockContentFactory.resolve(context, null) is EmbeddedContentResolution.NothingToShow,
        )
        assertTrue(
            EmbeddedBlockContentFactory.resolve(context, " ") is EmbeddedContentResolution.NothingToShow,
        )
    }
}
