package cloud.mindbox.mobile_sdk.pushes

import org.junit.Assert.assertEquals
import org.junit.Test

class PushTokenTest {

    @Test
    fun `convert PushTokenMap to preferences correctly`() {
        val oneToken: PushTokenMap = mapOf("provider" to "token")
        assertEquals("{\"provider\":\"token\"}", oneToken.toPreferences())

        val threeTokens: PushTokenMap = mapOf(
            "provider1" to "token1",
            "provider2" to "token2",
            "provider3" to "token3",
        )
        assertEquals("{\"provider1\":\"token1\",\"provider2\":\"token2\",\"provider3\":\"token3\"}", threeTokens.toPreferences())

        val noToken: PushTokenMap = mapOf()
        assertEquals("{}", noToken.toPreferences())
    }

    @Test
    fun `convert preferences tokens to PushTokenMap correctly`() {
        val oneToken = "{\"provider\":\"token\"}"
        assertEquals(mapOf("provider" to "token"), oneToken.toTokensMap())

        val threeTokens = "{\"provider1\":\"token1\",\"provider2\":\"token2\",\"provider3\":\"token3\"}"
        assertEquals(
            mapOf(
                "provider1" to "token1",
                "provider2" to "token2",
                "provider3" to "token3",
            ),
            threeTokens.toTokensMap()
        )
    }

    @Test
    fun `convert preferences tokens to empty PushTokenMap`() {
        listOf(
            "{",
            "[]",
            "token",
            "{\"token\"}",
            "{\"provider\":{\"token\"}",
            "",
            "{}",
        ).onEach { json ->
            assertEquals(mapOf<String, String>(), json.toTokensMap())
        }
    }
}
