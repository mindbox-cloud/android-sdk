package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.models.MindboxResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class InputParametersUnitTest {

    private val wrongDomainParameter = arrayListOf(
        "https://api.mindbox.ru",
        "api.mindbox.ru/",
        "https://api.mindbox.ru/",
    )

    private val wrongDeviceIdParameters = arrayListOf(
        "ларалтка ыфдво",
        "7659d 79",
        "jkdkj jkdck",
        "?hjd?/()$%^&@#"
    )

    private val emptyEndpointParameters = arrayListOf(
        " ",
        ""
    )

    private val rightDomainParameter = "api.mindbox.ru"
    private val rightEndpointParameter = "some_endpoint"
    private val rightDeviceIdParameter = "31f08aa0-494a-11eb-b378-0242ac130002"

    @Test
    fun inputParameters_isCorrect() {

        wrongDeviceIdParameters.forEach { parameter ->
            val errors =
                MindboxResponse.ValidationError()
                    .apply {
                        validateFields(
                            rightDomainParameter,
                            rightEndpointParameter,
                            parameter
                        )
                    }
            assertEquals(1, errors.messages.size)
        }

        emptyEndpointParameters.forEach { parameter ->
            val errors =
                MindboxResponse.ValidationError()
                    .apply {
                        validateFields(
                            rightDomainParameter,
                            parameter,
                            rightDeviceIdParameter
                        )
                    }
            assertEquals(1, errors.messages.size)
        }

        wrongDomainParameter.forEach { parameter ->
            val errors = MindboxResponse.ValidationError()
                .apply { validateFields(parameter, rightEndpointParameter, rightDeviceIdParameter) }
            assertEquals(1, errors.messages.size)
        }

        val rightCase =
            MindboxResponse.ValidationError()
                .apply {
                    validateFields(
                        rightDomainParameter,
                        rightEndpointParameter,
                        rightDeviceIdParameter
                    )
                }
        assertEquals(0, rightCase.messages.size)

    }
}