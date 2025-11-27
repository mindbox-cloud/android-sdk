package cloud.mindbox.mobile_sdk.utils

import cloud.mindbox.mobile_sdk.logger.MindboxLoggerImpl
import cloud.mindbox.mobile_sdk.repository.MindboxPreferences
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs

internal fun mockPreferencesConfigSetter() {
    mockkObject(MindboxPreferences)
    every {
        MindboxPreferences setProperty "inAppConfig" value any<String>()
    } just runs
}

internal fun mockLogger() {
    mockkObject(MindboxLoggerImpl)
    every { MindboxLoggerImpl.w(any(), any()) } just runs
    every { MindboxLoggerImpl.w(any(), any(), any()) } just runs
    every { MindboxLoggerImpl.e(any(), any()) } just runs
    every { MindboxLoggerImpl.e(any(), any(), any()) } just runs
    every { MindboxLoggerImpl.d(any(), any()) } just runs
    every { MindboxLoggerImpl.i(any(), any()) } just runs
}
