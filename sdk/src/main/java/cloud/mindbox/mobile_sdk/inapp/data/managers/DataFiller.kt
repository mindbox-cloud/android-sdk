package cloud.mindbox.mobile_sdk.inapp.data.managers

internal interface DataFiller<T> {

    fun fillData(item: T): T
}