package cloud.mindbox.mobile_sdk.pushes

import cloud.mindbox.mobile_sdk.encode
import cloud.mindbox.mobile_sdk.isHttpsUrl

@JvmInline
internal value class DeliveredUrl private constructor(val value: String) {

    fun withQueryParameter(name: String, parameterValue: String): String {
        val separator = if ('?' in value) "&" else "?"
        return "$value$separator${name.encode()}=${parameterValue.encode()}"
    }

    companion object {
        fun of(raw: String?): DeliveredUrl? = raw?.trim()
            ?.takeIf { it.isHttpsUrl() }
            ?.let(::DeliveredUrl)
    }
}
