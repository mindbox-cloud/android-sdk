package cloud.mindbox.mobile_sdk

import cloud.mindbox.mobile_sdk.models.ValidationError
import org.junit.Assert.assertEquals
import org.junit.Test

class InputParametersUnitTest {

    private val wrongDomainParameter = arrayListOf(
        "https://api.mindbox.ru",
        "api.mindbox.ru/",
        "https://api.mindbox.ru/",
        "hgkkjhhv",
        "4854-t789")

    private val wrongUuidParameters = arrayListOf(
        "ларалтка ыфдво",
        "7659d 79",
        "jkdkj jkdck",
        "?hjd?/()$%^&@#"
    )

    private val emptyEndpointParameters = arrayListOf(
        " ",
        ""
    )

    private val rightDomainParameter = "198.246.176.1"//"api.mindbox.ru"
    private val rightEndpointParameter = "some_endpoint"
    private val rightUuidParameter = "31f08aa0-494a-11eb-b378-0242ac130002"

    @Test
    fun inputParameters_isCorrect() {
        val rightCase =
            ValidationError()
                .apply {
                    validateFields(
                        rightDomainParameter,
                        rightEndpointParameter,
                        rightUuidParameter,
                        rightUuidParameter
                    )
                }
        assertEquals(0, rightCase.messages?.size)

    }

    @Test
    fun deviceUuid_isWrong() {
        wrongUuidParameters.forEach { parameter ->
            val errors =
                ValidationError()
                    .apply {
                        validateFields(
                            rightDomainParameter,
                            rightEndpointParameter,
                            parameter,
                            rightUuidParameter
                        )
                    }
            assertEquals(1, errors.messages?.size)
        }
    }

    @Test
    fun installationId_isWrong() {
        wrongUuidParameters.forEach { parameter ->
            val errors =
                ValidationError()
                    .apply {
                        validateFields(
                            rightDomainParameter,
                            rightEndpointParameter,
                            rightUuidParameter,
                            parameter
                        )
                    }
            assertEquals(1, errors.messages?.size)
        }
    }

    @Test
    fun domain_isWrong() {
        wrongDomainParameter.forEach { parameter ->
            val errors = ValidationError()
                .apply { validateFields(parameter, rightEndpointParameter, rightUuidParameter, rightUuidParameter) }
            assertEquals(1, errors.messages?.size)
        }
    }

    @Test
    fun endpoint_isWrong() {
        emptyEndpointParameters.forEach { parameter ->
            val errors =
                ValidationError()
                    .apply {
                        validateFields(
                            rightDomainParameter,
                            parameter,
                            rightUuidParameter,
                            rightUuidParameter
                        )
                    }
            assertEquals(1, errors.messages?.size)
        }
    }
}