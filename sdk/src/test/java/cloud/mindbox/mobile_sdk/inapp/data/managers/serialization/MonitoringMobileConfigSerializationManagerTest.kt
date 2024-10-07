package cloud.mindbox.mobile_sdk.inapp.data.managers.serialization

import android.app.Application
import cloud.mindbox.mobile_sdk.di.MindboxDI
import cloud.mindbox.mobile_sdk.di.mindboxInject
import cloud.mindbox.mobile_sdk.inapp.data.managers.MobileConfigSerializationManagerImpl
import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test


class MonitoringMobileConfigSerializationManagerTest {

    private val manager: MobileConfigSerializationManagerImpl by mindboxInject {
        mobileConfigSerializationManager as MobileConfigSerializationManagerImpl
    }

    private val context = mockk<Application>(relaxed = true) {
        every { applicationContext } returns this
    }

    @Before
    fun onTestStart() {
        MindboxDI.init(context)
    }

    @Test
    fun monitoringConfig_shouldParseSuccessfully() {
        // Correct config
        val json = getJson("ConfigParsing/Monitoring/MonitoringConfig.json")
        val config = manager.deserializeMonitoring(json)!!

        assertEquals(2, config.logs?.size)
    }

    @Test
    fun monitoringConfig_withLogsError_shouldSetMonitoringToNull() {
        // Key is `logsTests` instead of `logs`
        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsError.json")
        val config = manager.deserializeMonitoring(json)

        assertNull(config?.logs)
    }

    @Test
    fun monitoringConfig_withLogsTypeError_shouldSetMonitoringToNull() {
        // Type of `logs` is Int instead of
        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsTypeError.json")
        val config = manager.deserializeMonitoring(json)

        assertNull(config?.logs)
    }

    @Test
    fun monitoringConfig_withLogsOneElementError_shouldParseSuccessfullyRemainsElements() {
        // Type of `requestId` is Int instead of String
        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsOneElementError.json")
        val config = manager.deserializeMonitoring(json)

        assertEquals(1, config?.logs?.size)
    }

    @Test
    fun monitoringConfig_withLogsOneElementTypeError_shouldParseSuccessfullyRemainsElements() {
        // Type of `requestId` is Int instead of String
        mockkObject(MindboxLoggerImpl)
        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsOneElementTypeError.json")
        val config = manager.deserializeMonitoring(json)!!

        assertEquals(1, config.logs?.size)
        verify(exactly = 1) { MindboxLoggerImpl.e(any(),"Failed to parse logs block", any()) }
    }

    @Test
    fun monitoringConfig_withLogsTwoElementsError_shouldParseSuccessfullyRemainsElements() {
        // Key is `request` instead `requestId` and key is `device` instead of `deviceUUID`
        mockkObject(MindboxLoggerImpl)

        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsTwoElementsError.json")
        val config = manager.deserializeMonitoring(json)!!

        assertEquals(0, config.logs?.size)
        verify(exactly = 2) { MindboxLoggerImpl.e(any(),"Failed to parse logs block", any()) }
    }

    @Test
    fun monitoringConfig_withLogsTwoElementsTypeError_shouldParseSuccessfullyRemainsElements() {
        // Type of `requestId` is Int instead of String and type of `from` is Object instead of `String`
        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsTwoElementsTypeError.json")
        val config = manager.deserializeMonitoring(json)!!

        assertEquals(0, config.logs?.size)
    }

    @Test
    fun monitoringConfig_withLogsTwoElementsMixedError_shouldParseSuccessfullyRemainsElements() {
        // Type of `requestId` is Int instead of String and key is `fromTest` instead of `from`
        val json = getJson("ConfigParsing/Monitoring/MonitoringLogsElementsMixedError.json")
        val config = manager.deserializeMonitoring(json)

        assertEquals(0, config?.logs?.size)
    }
}

