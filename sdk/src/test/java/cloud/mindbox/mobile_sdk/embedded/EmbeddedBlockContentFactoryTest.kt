package cloud.mindbox.mobile_sdk.embedded

import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedBlockWebViewHolder
import cloud.mindbox.mobile_sdk.models.InAppStub
import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockContentFactoryTest {

    @Test
    fun `resolved embedded content becomes a block webview holder`() {
        val provider = EmbeddedBlockContentFactory.createProvider(
            ApplicationProvider.getApplicationContext(),
            InAppStub.getEmbedded(),
            startTick = Milliseconds(0L),
        )

        assertTrue(provider is EmbeddedBlockWebViewHolder)
    }

    @Test
    fun `content without a webview layer builds nothing`() {
        val provider = EmbeddedBlockContentFactory.createProvider(
            ApplicationProvider.getApplicationContext(),
            InAppStub.getEmbedded().copy(layers = emptyList()),
            startTick = Milliseconds(0L),
        )

        assertNull(provider)
    }
}
