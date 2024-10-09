package cloud.mindbox.mobile_sdk.inapp.data.validators

import com.google.gson.JsonParser

internal class JsonValidator : Validator<String?> {

    override fun isValid(item: String?): Boolean {
        if (item.isNullOrBlank()) return false

        return runCatching {
            JsonParser.parseString(item).let { it.isJsonObject || it.isJsonArray }
        }.getOrElse { false }
    }
}
