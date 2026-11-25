package cloud.mindbox.mindbox_rustore

import org.junit.Assert.assertSame
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MindboxRuStoreManifestTest {

    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    private fun pushProviderClassNames(): List<String> {
        val manifest = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        val metaData = manifest.getElementsByTagName("meta-data")
        return (0 until metaData.length)
            .map { metaData.item(it) as Element }
            .filter { it.getAttributeNS(androidNamespace, "name").startsWith("cloud.mindbox.push.provider.") }
            .map { it.getAttributeNS(androidNamespace, "value") }
    }

    @Test
    fun `the manifest leads the SDK to the MindboxRuStore object`() {
        val className = pushProviderClassNames().single()

        val pushService = Class.forName(className).getDeclaredField("INSTANCE").get(null)

        assertSame(MindboxRuStore, pushService)
    }
}
