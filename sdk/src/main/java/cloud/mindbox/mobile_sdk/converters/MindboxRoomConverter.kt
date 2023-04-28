package cloud.mindbox.mobile_sdk.converters

import androidx.room.TypeConverter
import cloud.mindbox.mobile_sdk.models.EventType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

internal object MindboxRoomConverter {

    private val gson by lazy { Gson() }

    @TypeConverter
    @JvmStatic
    fun stringToHashMap(value: String): HashMap<String, String>? {
        return gson.fromJson(value, object : TypeToken<HashMap<String, String>?>() {}.type)
    }

    @TypeConverter
    @JvmStatic
    fun hashMapToString(
        value: HashMap<String, String>?
    ): String = if (value == null) "" else gson.toJson(value)

    @TypeConverter
    @JvmStatic
    fun stringToEventType(value: String): EventType {
        val ordinal = value.substringBefore(";", "-1").toInt()
        val json = value.substringAfter(";", "")
        return gson.fromJson(json, EventType.typeToken(ordinal).type)
    }

    @TypeConverter
    @JvmStatic
    fun eventTypeToString(
        value: EventType?
    ): String = value?.let { "${it.ordinal()};${gson.toJson(it)}" } ?: ""

}
