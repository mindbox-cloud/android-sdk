package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

internal interface DataFiller<T> {

    fun fillData(item: T): T
}
