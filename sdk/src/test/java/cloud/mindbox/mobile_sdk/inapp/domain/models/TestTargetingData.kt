package cloud.mindbox.mobile_sdk.inapp.domain.models

class TestTargetingData(
    override val triggerEventName: String,
    override val operationBody: String? = null
) : TargetingData.OperationBody, TargetingData.OperationName