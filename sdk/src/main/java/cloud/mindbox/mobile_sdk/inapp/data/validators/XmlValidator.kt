package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

internal class XmlValidator : Validator<String?> {

    override fun isValid(item: String?): Boolean {
        runCatching {
            SAXParserFactory.newInstance().newSAXParser().xmlReader.parse(
                InputSource(
                    StringReader(item)
                )
            )
        }.onSuccess {
            return true
        }.onFailure {
            return false
        }
        return false
    }
}