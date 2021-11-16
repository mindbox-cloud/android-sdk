package cloud.mindbox.mobile_sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class InputParametersUnitTest {

    private val wrongDomainParameter = arrayListOf(
        "",
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
        val rightCase = SdkValidation.validateConfiguration(
            domain = rightDomainParameter,
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(0, rightCase.size)

    }

    @Test
    fun deviceUuid_isWrong() {
        wrongUuidParameters.forEach { wrongUuid ->
            val errors = SdkValidation.validateConfiguration(
                domain = rightDomainParameter,
                endpointId = rightEndpointParameter,
                previousDeviceUUID = wrongUuid,
                previousInstallationId = rightUuidParameter
            )
            assertEquals(1, errors.size)
            assertEquals(SdkValidation.ERROR_INVALID_DEVICE_ID, errors[0])
        }
    }

    @Test
    fun installationId_isWrong() {
        wrongUuidParameters.forEach { wrongUuid ->
            val errors = SdkValidation.validateConfiguration(
                domain = rightDomainParameter,
                endpointId = rightEndpointParameter,
                previousDeviceUUID = rightUuidParameter,
                previousInstallationId = wrongUuid
            )
            assertEquals(1, errors.size)
            assertEquals(SdkValidation.ERROR_INVALID_INSTALLATION_ID, errors[0])
        }
    }

    @Test
    fun domain_isWrong() {
        val errors0 = SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[0],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors0.size)
        assertEquals(SdkValidation.ERROR_EMPTY_DOMAIN, errors0[0])

        val errors1 = SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[1],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors1.size)
        assertEquals(SdkValidation.ERROR_INVALID_FORMAT_DOMAIN, errors1[0])

        val errors2 = SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[2],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors2.size)
        assertEquals(SdkValidation.ERROR_INVALID_FORMAT_DOMAIN, errors2[0])

        val errors3 = SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[3],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors3.size)
        assertEquals(SdkValidation.ERROR_INVALID_FORMAT_DOMAIN, errors3[0])

        val errors4 = SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[4],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors4.size)
        assertEquals(SdkValidation.ERROR_INVALID_DOMAIN, errors4[0])

        val errors5 = SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[5],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors5.size)
        assertEquals(SdkValidation.ERROR_INVALID_DOMAIN, errors5[0])
    }

    @Test
    fun endpoint_isWrong() {
        emptyEndpointParameters.forEach { wrongEndpoint ->
            val errors = SdkValidation.validateConfiguration(
                rightDomainParameter,
                wrongEndpoint,
                rightUuidParameter,
                rightUuidParameter
            )
            assertEquals(1, errors.size)
            assertEquals(SdkValidation.ERROR_EMPTY_ENDPOINT, errors[0])
        }
    }
}