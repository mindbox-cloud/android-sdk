package cloud.mindbox.mobile_sdk.pushes

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import io.mockk.every
import io.mockk.mockk

internal fun packageManagerWithMetaData(vararg metaData: Pair<String, String>): PackageManager {
    val bundle = mockk<Bundle> {
        every { keySet() } returns metaData.map { it.first }.toSet()
        metaData.forEach { (key, value) -> every { getString(key) } returns value }
    }
    val applicationInfo = ApplicationInfo().apply { this.metaData = bundle }
    return mockk { every { getApplicationInfo(any<String>(), any<Int>()) } returns applicationInfo }
}

internal fun contextWith(packageManager: PackageManager): Context = mockk {
    every { this@mockk.packageManager } returns packageManager
    every { packageName } returns "com.example.app"
}

internal fun contextWithMetaData(vararg metaData: Pair<String, String>): Context =
    contextWith(packageManagerWithMetaData(*metaData))
