package cloud.mindbox.mobile_sdk.inapp.data.validators

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlValidatorTest {

    private val xmlValidator: XmlValidator = XmlValidator()

    @Test
    fun `valid xml string`() {
        val xmlString =
            "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><custDtl><name>abc</name><mobNo>9876543210</mobNo></custDtl>"
        assertTrue(xmlValidator.isValid(xmlString))
    }

    @Test
    fun `empty string`() {
        val xmlString = ""
        assertFalse(xmlValidator.isValid(xmlString))
    }

    @Test
    fun `null string`() {
        assertFalse(xmlValidator.isValid(null))
    }

    @Test
    fun `inValid xml string`() {
        val xmlString =
            "<?xml vers123ion='1.0' encoding='UTF-8' standalone='yes'?><custDtl><name>abc</name><mobNo>9876543210</mobNo></custDtl>"
        assertFalse(xmlValidator.isValid(xmlString))
    }

    @Test
    fun `valid xml string with line breaks`() {
        val xmlString =
            """
                <?xml version='1.0' encoding='UTF-8' standalone='yes'?>
                <custDtl>
                	<name>abc</name>
                	<mobNo>9876543210</mobNo>
                </custDtl>
            """.trimIndent()
        assertTrue(xmlValidator.isValid(xmlString))
    }

    @Test
    fun `inValid xml string with line breaks`() {
        val xmlString =
            """
                <?xml versi12222on='1.0' encoding='UTF-8' standalone='yes'?>
                <custDtl>
                	<name>abc</name>
                	<mobNo>9876543210</mobNo>
                </custDtl>
            """.trimIndent()
        assertFalse(xmlValidator.isValid(xmlString))
    }
}
