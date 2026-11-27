package cloud.mindbox.mobile_sdk.models

import android.os.Build

internal enum class PushDeliverySchedule(val offsets: List<Milliseconds>) {
    FCM(offsets = offsets(0L, 3_000L, 6_000L, 15_000L)),
    HMS(offsets = offsets(0L, 2_000L, 4_000L)),
    DEFAULT(offsets = offsets(0L, 3_000L, 6_000L));

    val interval: Milliseconds = Milliseconds(
        offsets.zipWithNext { previous, next -> next.interval - previous.interval }.minOrNull() ?: 0L
    )

    companion object {
        private const val FCM_PROVIDER = "FCM"
        private const val HMS_PROVIDER = "HCM"

        fun of(provider: String?, sdkInt: Int): PushDeliverySchedule = when (provider) {
            FCM_PROVIDER -> if (sdkInt >= Build.VERSION_CODES.S) FCM else DEFAULT
            HMS_PROVIDER -> HMS
            else -> DEFAULT
        }
    }
}

private fun offsets(vararg offset: Long): List<Milliseconds> = offset.map(::Milliseconds)
