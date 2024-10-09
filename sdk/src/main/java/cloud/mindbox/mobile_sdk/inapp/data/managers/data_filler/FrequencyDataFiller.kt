package cloud.mindbox.mobile_sdk.inapp.data.managers.data_filler

import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto

internal class FrequencyDataFiller : DataFiller<FrequencyDto?> {
    override fun fillData(item: FrequencyDto?): FrequencyDto = item ?: FrequencyDto.FrequencyOnceDto("once", kind = "lifetime")
}
