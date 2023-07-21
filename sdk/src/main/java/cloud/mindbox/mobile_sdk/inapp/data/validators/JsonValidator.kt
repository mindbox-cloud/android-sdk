package cloud.mindbox.mobile_sdk.inapp.data.validators


internal class JsonValidator : Validator<String?> {

    override fun isValid(item: String?): Boolean {
        if (item.isNullOrBlank()) return false
        val jsonRegex = "^\\s*(\\{.*\\}|\\[.*])\\s*$"
        return item.trim().matches(jsonRegex.toRegex())
    }
}