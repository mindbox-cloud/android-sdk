package cloud.mindbox.mobile_sdk.inapp.data.managers

internal interface DefaultDataFiller<T> {

    fun fillData(item: T): T
}