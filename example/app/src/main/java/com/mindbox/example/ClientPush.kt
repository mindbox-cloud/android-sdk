package com.mindbox.example

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class ClientPush(
    val messageId: String,
    val title: String,
    val text: String,
    val clickUrl: String?,
    val imageUrl: String?,
    val payload: String?,
    val buttons: List<ClientPushButton>,
)

data class ClientPushButton(
    val text: String,
    val url: String?,
    val uniqueKey: String,
)

fun parseClientPush(data: Map<String, String>): ClientPush? {
    val messageId = data["messageId"]?.takeIf { it.isNotBlank() } ?: return null
    return ClientPush(
        messageId = messageId,
        title = data["title"].orEmpty(),
        text = data["text"].orEmpty(),
        clickUrl = data["clickUrl"]?.takeIf { it.isNotBlank() },
        imageUrl = data["imageUrl"]?.takeIf { it.isNotBlank() },
        payload = data["payload"],
        buttons = parseButtons(data["buttons"]),
    )
}

private fun parseButtons(json: String?): List<ClientPushButton> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching {
        JsonParser.parseString(json).asJsonArray.mapNotNull { element ->
            val button = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val uniqueKey = button.string("uniqueKey")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ClientPushButton(
                text = button.string("text").orEmpty(),
                url = button.string("url")?.takeIf { it.isNotBlank() },
                uniqueKey = uniqueKey,
            )
        }
    }.getOrDefault(emptyList())
}

private fun JsonObject.string(name: String): String? =
    get(name)?.takeIf(JsonElement::isJsonPrimitive)?.asString
