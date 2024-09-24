package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import com.google.gson.JsonElement
import com.google.gson.JsonParser

fun Any.getJson(path: String): JsonElement {
    return javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader()
        .use { it.readText() }
        .let { JsonParser.parseString(it) }
}
