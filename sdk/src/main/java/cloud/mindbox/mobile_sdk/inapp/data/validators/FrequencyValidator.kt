package cloud.mindbox.mobile_sdk.inapp.data.validators

import cloud.mindbox.mobile_sdk.equalsAny
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto.FrequencyPeriodicDto.Companion.FREQUENCY_UNIT_DAYS
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto.FrequencyPeriodicDto.Companion.FREQUENCY_UNIT_HOURS
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto.FrequencyPeriodicDto.Companion.FREQUENCY_UNIT_MINUTES
import cloud.mindbox.mobile_sdk.models.operation.response.FrequencyDto.FrequencyPeriodicDto.Companion.FREQUENCY_UNIT_SECONDS

internal class FrequencyValidator : Validator<FrequencyDto> {
    override fun isValid(item: FrequencyDto): Boolean {
        return when (item) {
            is FrequencyDto.FrequencyOnceDto -> {
                val isValid = (item.type == FREQUENCY_TYPE_ONCE && item.kind.equalsAny(ignoreCase = true, FREQUENCY_KIND_LIFETIME, FREQUENCY_KIND_SESSION))
                mindboxLogI("Current frequency is once and it's kind is ${item.kind}. It is valid = $isValid")
                isValid
            }

            is FrequencyDto.FrequencyPeriodicDto -> {
                val isValid = item.type == FREQUENCY_TYPE_PERIODIC && item.value > 0 && item.unit.equalsAny(ignoreCase = true,
                    FREQUENCY_UNIT_HOURS, FREQUENCY_UNIT_DAYS, FREQUENCY_UNIT_MINUTES, FREQUENCY_UNIT_SECONDS)
                mindboxLogI("Current frequency is periodic, it's unit is ${item.unit} and delay is ${item.value}. It is valid = $isValid")
                isValid
            }
        }
    }

    internal companion object {
        private const val FREQUENCY_TYPE_ONCE = "once"
        private const val FREQUENCY_TYPE_PERIODIC = "periodic"


        private const val FREQUENCY_KIND_LIFETIME = "lifetime"
        private const val FREQUENCY_KIND_SESSION = "session"
    }
}