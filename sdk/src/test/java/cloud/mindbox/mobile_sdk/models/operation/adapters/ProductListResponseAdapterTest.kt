package cloud.mindbox.mobile_sdk.models.operation.adapters

import cloud.mindbox.mobile_sdk.models.operation.response.CatalogProductListResponse
import cloud.mindbox.mobile_sdk.models.operation.response.ProductListItemResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ProductListResponseAdapter].
 *
 * The adapter dispatches on the JSON token type:
 *  - BEGIN_ARRAY  → List<ProductListItemResponse>
 *  - BEGIN_OBJECT → CatalogProductListResponse
 *  - NULL         → null
 */
class ProductListResponseAdapterTest {

    private val adapter = ProductListResponseAdapter()

    private fun read(json: String): Any? {
        val reader = com.google.gson.stream.JsonReader(java.io.StringReader(json))
        return adapter.read(reader)
    }

    private fun write(value: Any?): String {
        val sw = java.io.StringWriter()
        val writer = com.google.gson.stream.JsonWriter(sw)
        adapter.write(writer, value)
        writer.flush()
        return sw.toString()
    }

    // region read – array input

    @Test
    fun `read - JSON array deserializes to list of ProductListItemResponse`() {
        val result = read("""[{"count":2.0,"pricePerItem":99.9},{"count":1.0}]""")
        assertNotNull(result)
        assertTrue(result is List<*>)
        @Suppress("UNCHECKED_CAST")
        val list = result as List<ProductListItemResponse>
        assertEquals(2, list.size)
        assertEquals(2.0, list[0].count)
        assertEquals(99.9, list[0].pricePerItem)
    }

    @Test
    fun `read - empty JSON array deserializes to empty list`() {
        val result = read("[]")
        assertNotNull(result)
        assertTrue(result is List<*>)
        assertTrue((result as List<*>).isEmpty())
    }

    // endregion

    // region read – object input

    @Test
    fun `read - JSON object deserializes to CatalogProductListResponse`() {
        val result = read("""{"processingStatus":"Success","items":[]}""")
        assertNotNull(result)
        assertTrue(result is CatalogProductListResponse)
        val catalog = result as CatalogProductListResponse
        assertNotNull(catalog.items)
        assertTrue(catalog.items!!.isEmpty())
    }

    @Test
    fun `read - empty JSON object deserializes to CatalogProductListResponse`() {
        val result = read("""{}""")
        assertNotNull(result)
        assertTrue(result is CatalogProductListResponse)
    }

    // endregion

    // region read – null input

    @Test
    fun `read - JSON null returns null`() {
        val result = read("null")
        assertNull(result)
    }

    // endregion

    // region write

    @Test
    fun `write - list serialized as JSON array`() {
        val list = listOf(ProductListItemResponse(count = 3.0))
        val json = write(list)
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains(""""count":3.0"""))
    }

    @Test
    fun `write - CatalogProductListResponse serialized as JSON object`() {
        val catalog = CatalogProductListResponse(items = emptyList())
        val json = write(catalog)
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun `write - null serialized as JSON null`() {
        val json = write(null)
        assertEquals("null", json)
    }

    // endregion

    // region round-trip

    @Test
    fun `round-trip - list of items preserved`() {
        val original = listOf(
            ProductListItemResponse(count = 1.0, price = 50.0),
            ProductListItemResponse(count = 2.0, price = 100.0),
        )
        val json = write(original)
        val restored = read(json)

        assertTrue(restored is List<*>)
        @Suppress("UNCHECKED_CAST")
        val list = restored as List<ProductListItemResponse>
        assertEquals(2, list.size)
        assertEquals(1.0, list[0].count)
        assertEquals(2.0, list[1].count)
    }

    @Test
    fun `round-trip - CatalogProductListResponse preserved`() {
        val original = CatalogProductListResponse(items = emptyList())
        val json = write(original)
        val restored = read(json)
        assertTrue(restored is CatalogProductListResponse)
    }

    // endregion
}
