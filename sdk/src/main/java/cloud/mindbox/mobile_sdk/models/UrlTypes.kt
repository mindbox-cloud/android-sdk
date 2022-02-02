package cloud.mindbox.mobile_sdk.models

internal enum class UrlQuery(val value: String) {
    ENDPOINT_ID("endpointId"),
    OPERATION("operation"),
    DEVICE_UUID("deviceUUID"),
    TRANSACTION_ID("transactionId"),
    DATE_TIME_OFFSET("dateTimeOffset"),
    UNIQ_KEY("uniqKey")
}