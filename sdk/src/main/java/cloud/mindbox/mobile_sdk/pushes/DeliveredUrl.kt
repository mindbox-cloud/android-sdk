package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.encode
import cloud.mindbox.mobile_sdk.isHttpsUrl

@JvmInline
internal value class DeliveredUrl private constructor(val value: String) {

    fun withQueryParameter(name: String, parameterValue: String): String {
        val base = value.substringBefore('#')
        val fragment = value.substring(base.length)
        val encodedName = name.encode()
        val query = base.substringAfter('?', missingDelimiterValue = "")
        val keptParameters = if (query.isEmpty()) {
            emptyList()
        } else {
            query.split('&').filter { it.substringBefore('=') != encodedName }
        }
        val newQuery = (keptParameters + "$encodedName=${parameterValue.encode()}").joinToString("&")
        return "${base.substringBefore('?')}?$newQuery$fragment"
    }

    companion object {
        fun of(raw: String?): DeliveredUrl? = raw?.trim()
            ?.takeIf { it.isHttpsUrl() }
            ?.let(::DeliveredUrl)
    }
}
