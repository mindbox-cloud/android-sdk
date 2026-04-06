package cloud.mindbox.mobile_sdk.inapp.presentation.view

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import cloud.mindbox.mobile_sdk.annotations.InternalMindboxApi
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import cloud.mindbox.mobile_sdk.utils.loggingRunCatching
import org.json.JSONObject

internal enum class HapticImpactStyle { Light, Medium, Heavy }

internal sealed class HapticRequest {
    object Selection : HapticRequest()

    data class Impact(val style: HapticImpactStyle) : HapticRequest()

    data class Pattern(val events: List<HapticPatternEvent>) : HapticRequest()
}

/**
 * Represents a single haptic pattern event.
 *
 * @param time Start time of the event relative to the beginning of the pattern, in milliseconds.
 * @param duration Duration of the vibration, in milliseconds.
 * @param intensity Normalized intensity in range [0.0, 1.0].
 * @param sharpness Normalized sharpness in range [0.0, 1.0].
 *
 * Note: On Android, [sharpness] is currently parsed for compatibility with the
 * cross‑platform schema but is not applied when generating vibration effects.
 * Changes to this parameter will not affect the resulting haptic feedback on Android.
 */
internal data class HapticPatternEvent(
    val time: Long,
    val duration: Long,
    val intensity: Float,
    val sharpness: Float,
)

internal object HapticConstants {
    const val KEY_TYPE = "type"
    const val KEY_STYLE = "style"
    const val KEY_PATTERN = "pattern"
    const val KEY_TIME = "time"
    const val KEY_DURATION = "duration"
    const val KEY_INTENSITY = "intensity"
    const val KEY_SHARPNESS = "sharpness"

    const val TYPE_SELECTION = "selection"
    const val TYPE_IMPACT = "impact"
    const val TYPE_PATTERN = "pattern"

    const val STYLE_LIGHT = "light"
    const val STYLE_MEDIUM = "medium"
    const val STYLE_HEAVY = "heavy"
    const val STYLE_SOFT = "soft"
    const val STYLE_RIGID = "rigid"

    const val SELECTION_FALLBACK_DURATION_MS = 20L
    const val TRANSIENT_DURATION_MS = 10L
}

@OptIn(InternalMindboxApi::class)
internal fun parseHapticRequest(payload: String?): HapticRequest {
    if (payload.isNullOrBlank() || payload == BridgeMessage.EMPTY_PAYLOAD) {
        return HapticRequest.Selection
    }
    return loggingRunCatching(defaultValue = HapticRequest.Selection) {
        val json = JSONObject(payload)
        when (json.optString(HapticConstants.KEY_TYPE, HapticConstants.TYPE_SELECTION)) {
            HapticConstants.TYPE_IMPACT -> {
                val styleStr: String = json.optString(HapticConstants.KEY_STYLE)
                HapticRequest.Impact(style = parseImpactStyle(styleStr))
            }
            HapticConstants.TYPE_PATTERN -> HapticRequest.Pattern(events = parsePatternEvents(json))
            else -> HapticRequest.Selection
        }
    }
}

private fun parseImpactStyle(style: String): HapticImpactStyle = when (style) {
    HapticConstants.STYLE_LIGHT, HapticConstants.STYLE_SOFT -> HapticImpactStyle.Light
    HapticConstants.STYLE_HEAVY, HapticConstants.STYLE_RIGID -> HapticImpactStyle.Heavy
    else -> HapticImpactStyle.Medium
}

private fun parsePatternEvents(json: JSONObject): List<HapticPatternEvent> {
    val array = json.optJSONArray(HapticConstants.KEY_PATTERN) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        loggingRunCatching(defaultValue = null) {
            val item = array.getJSONObject(index)
            HapticPatternEvent(
                time = item.optLong(HapticConstants.KEY_TIME, 0L),
                duration = item.optLong(HapticConstants.KEY_DURATION, 0L),
                intensity = item.optDouble(HapticConstants.KEY_INTENSITY, 1.0).toFloat(),
                sharpness = item.optDouble(HapticConstants.KEY_SHARPNESS, 0.0).toFloat(),
            )
        }
    }
}

internal interface HapticFeedbackExecutor {
    fun execute(request: HapticRequest)

    fun cancel()
}

internal class HapticFeedbackExecutorImpl(
    private val context: Context,
) : HapticFeedbackExecutor {

    override fun execute(request: HapticRequest) {
        loggingRunCatching {
            when (request) {
                is HapticRequest.Selection -> executeSelection()
                is HapticRequest.Impact -> executeImpact(request.style)
                is HapticRequest.Pattern -> executePattern(request.events)
            }
        }
    }

    override fun cancel() {
        loggingRunCatching {
            resolveVibrator()?.cancel()
        }
    }

    @Suppress("DEPRECATION")
    private fun executeSelection() {
        val vibrator: Vibrator = resolveVibrator() ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                vibrator.vibrate(VibrationEffect.createOneShot(HapticConstants.SELECTION_FALLBACK_DURATION_MS, 85))
            else ->
                vibrator.vibrate(HapticConstants.SELECTION_FALLBACK_DURATION_MS)
        }
    }

    @Suppress("DEPRECATION")
    private fun executeImpact(style: HapticImpactStyle) {
        val vibrator: Vibrator = resolveVibrator() ?: return
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                val effectId: Int = when (style) {
                    HapticImpactStyle.Light -> VibrationEffect.EFFECT_TICK
                    HapticImpactStyle.Medium -> VibrationEffect.EFFECT_CLICK
                    HapticImpactStyle.Heavy -> VibrationEffect.EFFECT_HEAVY_CLICK
                }
                vibrator.vibrate(VibrationEffect.createPredefined(effectId))
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                val (durationMs, amplitude) = impactParams(style)
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
            }
            else ->
                vibrator.vibrate(impactLegacyDuration(style))
        }
    }

    @Suppress("DEPRECATION")
    private fun executePattern(events: List<HapticPatternEvent>) {
        if (events.isEmpty()) return
        val vibrator: Vibrator = resolveVibrator() ?: return
        mindboxLogI("[Haptic] pattern events=${events.size}")
        val (timings, amplitudes) = buildWaveform(events)
        if (timings.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings.toLongArray(), -1)
        }
    }

    private fun buildWaveform(events: List<HapticPatternEvent>): Pair<List<Long>, List<Int>> {
        val sorted: List<HapticPatternEvent> = events.sortedBy { it.time }
        val timings: MutableList<Long> = mutableListOf()
        val amplitudes: MutableList<Int> = mutableListOf()
        var currentTime = 0L
        for (event in sorted) {
            val effectiveDuration: Long =
                if (event.duration > 0) event.duration else HapticConstants.TRANSIENT_DURATION_MS
            val amplitude: Int = (event.intensity * 255).toInt().coerceIn(0, 255)
            val gap: Long = event.time - currentTime
            if (gap > 0) {
                timings.add(gap)
                amplitudes.add(0)
            } else if (timings.isEmpty()) {
                timings.add(0)
                amplitudes.add(0)
            }
            timings.add(effectiveDuration)
            amplitudes.add(amplitude)
            currentTime = event.time + effectiveDuration
        }
        return timings to amplitudes
    }

    @Suppress("DEPRECATION")
    private fun resolveVibrator(): Vibrator? {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        return vibrator?.takeIf { it.hasVibrator() }
    }

    private fun impactParams(style: HapticImpactStyle): Pair<Long, Int> = when (style) {
        HapticImpactStyle.Light -> 20L to 85
        HapticImpactStyle.Medium -> 40L to 180
        HapticImpactStyle.Heavy -> 60L to 255
    }

    private fun impactLegacyDuration(style: HapticImpactStyle): Long = when (style) {
        HapticImpactStyle.Light -> 20L
        HapticImpactStyle.Medium -> 40L
        HapticImpactStyle.Heavy -> 60L
    }
}
