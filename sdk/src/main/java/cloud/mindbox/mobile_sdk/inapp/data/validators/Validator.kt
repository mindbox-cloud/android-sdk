package cloud.mindbox.mobile_sdk.inapp.data.validators

internal interface Validator<T> {
    fun isValid(item: T): Boolean
}
