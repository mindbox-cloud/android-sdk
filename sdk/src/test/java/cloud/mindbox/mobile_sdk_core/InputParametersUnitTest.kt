package cloud.mindbox.mobile_sdk_core

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
        val rightCase = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = rightDomainParameter,
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(0, rightCase.size)
    }

    @Test
    fun mandatoryInputParameters_isCorrect() {
        val rightCase = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = rightDomainParameter,
            endpointId = rightEndpointParameter,
            previousDeviceUUID = "",
            previousInstallationId = ""
        )
        assertEquals(0, rightCase.size)
    }

    @Test
    fun noPreviousInstallationId_isCorrect() {
        val rightCase = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = rightDomainParameter,
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = ""
        )
        assertEquals(0, rightCase.size)
    }

    @Test
    fun noPreviousDeviceUuid_isCorrect() {
        val rightCase = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = rightDomainParameter,
            endpointId = rightEndpointParameter,
            previousDeviceUUID = "",
            previousInstallationId = rightUuidParameter
        )
        assertEquals(0, rightCase.size)
    }

    @Test
    fun deviceUuid_isWrong() {
        wrongUuidParameters.forEach { wrongUuid ->
            val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
                domain = rightDomainParameter,
                endpointId = rightEndpointParameter,
                previousDeviceUUID = wrongUuid,
                previousInstallationId = rightUuidParameter
            )
            assertEquals(1, errors.size)
            assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_DEVICE_ID, errors[0])
        }
    }

    @Test
    fun installationId_isWrong() {
        wrongUuidParameters.forEach { wrongUuid ->
            val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
                domain = rightDomainParameter,
                endpointId = rightEndpointParameter,
                previousDeviceUUID = rightUuidParameter,
                previousInstallationId = wrongUuid
            )
            assertEquals(1, errors.size)
            assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_INSTALLATION_ID, errors[0])
        }
    }

    @Test
    fun domain_isEmpty() {
        val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[0],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors.size)
        assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.EMPTY_DOMAIN, errors[0])
    }

    @Test
    fun domain_startsWithHttps() {
        val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[1],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors.size)
        assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_FORMAT_DOMAIN, errors[0])
    }

    @Test
    fun domain_endsWithSlash() {
        val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[2],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors.size)
        assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_FORMAT_DOMAIN, errors[0])
    }

    @Test
    fun domain_startsWithHttpsAndEndsWithSlash() {
        val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[3],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors.size)
        assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_FORMAT_DOMAIN, errors[0])
    }

    @Test
    fun domain_InvalidFormat() {
        val errors4 = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[4],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors4.size)
        assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_DOMAIN, errors4[0])

        val errors5 = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
            domain = wrongDomainParameter[5],
            endpointId = rightEndpointParameter,
            previousDeviceUUID = rightUuidParameter,
            previousInstallationId = rightUuidParameter
        )
        assertEquals(1, errors5.size)
        assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.INVALID_DOMAIN, errors5[0])
    }

    @Test
    fun endpoint_isWrong() {
        emptyEndpointParameters.forEach { wrongEndpoint ->
            val errors = cloud.mindbox.mobile_sdk.models.SdkValidation.validateConfiguration(
                rightDomainParameter,
                wrongEndpoint,
                rightUuidParameter,
                rightUuidParameter
            )
            assertEquals(1, errors.size)
            assertEquals(cloud.mindbox.mobile_sdk.models.SdkValidation.Error.EMPTY_ENDPOINT, errors[0])
        }
    }
}