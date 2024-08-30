package cloud.mindbox.mobile_sdk.utils

import android.content.res.Resources
import android.os.Build
import cloud.mindbox.mobile_sdk.px

private const val EXPANDED_PUSH_IMAGE_HEIGHT_31_PLUS_TITLE_2_STRING = 250
private const val EXPANDED_PUSH_IMAGE_HEIGHT_31_WITH_BUTTONS_TITLE_2_STRING = 190
private const val EXPANDED_PUSH_IMAGE_HEIGHT_31_PLUS = 270
private const val EXPANDED_PUSH_IMAGE_HEIGHT_31_WITH_BUTTONS = 220
private const val EXPANDED_PUSH_IMAGE_HEIGHT_28_30 = 175
private const val EXPANDED_PUSH_IMAGE_HEIGHT_28_30_WITH_BUTTONS = 135
private const val EXPANDED_PUSH_IMAGE_HEIGHT_24_27 = 180
private const val EXPANDED_PUSH_IMAGE_HEIGHT_24_27_WITH_BUTTONS = 120
private const val EXPANDED_PUSH_IMAGE_HEIGHT_23_AND_LESS = 130
private const val MARGIN_ANDROID_30_AND_LESS = 32
private const val CHAR_COUNT_WHEN_TITLE_2_STRING = 35

internal val imageWidthInPixels: Int
    get() {
        return runCatching {
            val defaultWidth = Resources.getSystem().displayMetrics.widthPixels
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) defaultWidth - MARGIN_ANDROID_30_AND_LESS.px else defaultWidth
        }.getOrElse { 0 }
    }

fun getImageHeightWithoutButtonIxPixels(charCountInTitle: Int): Int {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (charCountInTitle <= CHAR_COUNT_WHEN_TITLE_2_STRING)
                EXPANDED_PUSH_IMAGE_HEIGHT_31_PLUS
            else EXPANDED_PUSH_IMAGE_HEIGHT_31_PLUS_TITLE_2_STRING
        Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.R -> EXPANDED_PUSH_IMAGE_HEIGHT_28_30
        Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.O_MR1 -> EXPANDED_PUSH_IMAGE_HEIGHT_24_27
        else -> EXPANDED_PUSH_IMAGE_HEIGHT_23_AND_LESS
    }.px
}

fun getImageHeightWithButtonIxPixels(charCountInTitle: Int): Int {
    return when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (charCountInTitle <= CHAR_COUNT_WHEN_TITLE_2_STRING)
                EXPANDED_PUSH_IMAGE_HEIGHT_31_WITH_BUTTONS
            else EXPANDED_PUSH_IMAGE_HEIGHT_31_WITH_BUTTONS_TITLE_2_STRING
        Build.VERSION.SDK_INT in Build.VERSION_CODES.P..Build.VERSION_CODES.R -> EXPANDED_PUSH_IMAGE_HEIGHT_28_30_WITH_BUTTONS
        Build.VERSION.SDK_INT in Build.VERSION_CODES.N..Build.VERSION_CODES.O_MR1 -> EXPANDED_PUSH_IMAGE_HEIGHT_24_27_WITH_BUTTONS
        else -> EXPANDED_PUSH_IMAGE_HEIGHT_23_AND_LESS
    }.px
}