package cloud.mindbox.mobile_sdk.pushes

import org.junit.Assert.assertEquals
import org.junit.Test

class PushTokenTest {

    @Test
    fun `convert PushTokenMap to preferences correctly`() {
        val oneToken: PrefPushTokenMap = mapOf("provider" to PrefPushToken("token", 1L))
        assertEquals("{\"provider\":{\"token\":\"token\",\"updateDate\":1}}", oneToken.toPreferences())

        val threeTokens: PrefPushTokenMap = mapOf(
            "provider1" to PrefPushToken("token1", 1L),
            "provider2" to PrefPushToken("token2", 2L),
            "provider3" to PrefPushToken("token3", 3L),
        )
        assertEquals("{\"provider1\":{\"token\":\"token1\",\"updateDate\":1},\"provider2\":{\"token\":\"token2\",\"updateDate\":2},\"provider3\":{\"token\":\"token3\",\"updateDate\":3}}", threeTokens.toPreferences())

        val noToken: PrefPushTokenMap = mapOf()
        assertEquals("{}", noToken.toPreferences())
    }

    @Test
    fun `convert preferences tokens to PushTokenMap correctly`() {
        val oneToken = "{\"provider\":{\"token\":\"token\",\"updateDate\":1}}"
        assertEquals(mapOf("provider" to PrefPushToken("token", 1L)), oneToken.toTokensMap())

        val threeTokens = "{\"provider1\":{\"token\":\"token1\",\"updateDate\":1},\"provider2\":{\"token\":\"token2\",\"updateDate\":2},\"provider3\":{\"token\":\"token3\",\"updateDate\":3}}"
        assertEquals(
            mapOf(
                "provider1" to PrefPushToken("token1", 1L),
                "provider2" to PrefPushToken("token2", 2L),
                "provider3" to PrefPushToken("token3", 3L),
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
