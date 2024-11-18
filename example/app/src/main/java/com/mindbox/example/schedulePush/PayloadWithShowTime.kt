package com.mindbox.example.schedulePush

import android.annotation.SuppressLint
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/***
 * Method for getting info from Mindbox push about show time and tolerance
 *
 * example of payload:
 * { "showTimeGTM": "2024-12-31T23:55:00+0000", "toleranceMinutes": 5 }
 */
@SuppressLint("SimpleDateFormat")
fun MindboxRemoteMessage.getPayloadWithShowTime(): PayloadWithShowTime? =
    this.payload?.let {
        runCatching {
            val json = JsonParser.parseString(it).asJsonObject
            PayloadWithShowTime(
                showTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXX").parse(json.get("showTimeGTM").asString!!)!!,
                toleranceMinutes = json.get("toleranceMinutes").asLong.toDuration(DurationUnit.MINUTES)
            )
        }.getOrNull()
    }

data class PayloadWithShowTime(
    val showTime: Date,
    val toleranceMinutes: Duration
)
