package cloud.mindbox.mobile_sdk.embedded

import androidx.test.core.app.ApplicationProvider
import cloud.mindbox.mobile_sdk.embedded.webview.EmbeddedBlockWebViewHolder
import cloud.mindbox.mobile_sdk.models.InAppStub
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockContentFactoryTest {

    @Test
    fun `resolved embedded content becomes a feed webview holder`() {
        val provider = EmbeddedBlockContentFactory.createProvider(
            ApplicationProvider.getApplicationContext(),
            InAppStub.getEmbedded(),
        )

        assertTrue(provider is EmbeddedBlockWebViewHolder)
    }

    @Test
    fun `content without a webview layer builds nothing`() {
        val provider = EmbeddedBlockContentFactory.createProvider(
            ApplicationProvider.getApplicationContext(),
            InAppStub.getEmbedded().copy(layers = emptyList()),
        )

        assertNull(provider)
    }
}
