package cloud.mindbox.mobile_sdk.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import cloud.mindbox.mobile_sdk.managers.DbManager.EVENTS_TABLE_NAME
import com.google.gson.reflect.TypeToken
import java.util.*

@Entity(tableName = EVENTS_TABLE_NAME)
internal data class Event(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0L,
    val eventType: EventType,
    val transactionId: String = UUID.randomUUID().toString(),
    val enqueueTimestamp: Long = System.currentTimeMillis(), // date of event creating
    val additionalFields: HashMap<String, String>? = null,
    val body: String? = null //json
)

internal sealed class EventType(val operation: String, val endpoint: String) {

    companion object {

        private const val APP_INSTALLED_ORDINAL = 0
        private const val APP_INFO_UPDATED_ORDINAL = 1
        private const val PUSH_DELIVERED_ORDINAL = 2
        private const val PUSH_CLICKED_ORDINAL = 3
        private const val TRACK_VISIT_ORDINAL = 4
        private const val ASYNC_OPERATION_ORDINAL = 5
        private const val APP_INSTALLED_WITHOUT_CUSTOMER = 6

        fun typeToken(ordinal: Int) = when (ordinal) {
            APP_INSTALLED_ORDINAL -> object : TypeToken<AppInstalled>() {}
            APP_INSTALLED_WITHOUT_CUSTOMER -> object : TypeToken<AppInstalledWithoutCustomer>() {}
            APP_INFO_UPDATED_ORDINAL -> object : TypeToken<AppInfoUpdated>() {}
            PUSH_DELIVERED_ORDINAL -> object : TypeToken<PushDelivered>() {}
            PUSH_CLICKED_ORDINAL -> object : TypeToken<PushClicked>() {}
            TRACK_VISIT_ORDINAL -> object : TypeToken<TrackVisit>() {}
            ASYNC_OPERATION_ORDINAL -> object : TypeToken<AsyncOperation>() {}
            else -> throw IllegalArgumentException("Unknown TypeToken for $ordinal EventType ordinal")
        }

    }

    object AppInstalled : EventType("MobilePush.ApplicationInstalled", "/v3/operations/async")

    object AppInstalledWithoutCustomer :
        EventType("MobilePush.ApplicationInstalledWithoutCustomer", "/v3/operations/async")

    object AppInfoUpdated : EventType("MobilePush.ApplicationInfoUpdated", "/v3/operations/async")

    object PushDelivered : EventType("", "/mobile-push/delivered")

    object PushClicked : EventType("MobilePush.TrackClick", "/v3/operations/async")

    object TrackVisit : EventType("TrackVisit", "/v1.1/customer/mobile-track-visit")

    internal class AsyncOperation(operation: String) : EventType(operation, "/v3/operations/async")

    fun ordinal() = when (this) {
        is AppInstalled -> APP_INSTALLED_ORDINAL
        is AppInstalledWithoutCustomer -> APP_INSTALLED_WITHOUT_CUSTOMER
        is AppInfoUpdated -> APP_INFO_UPDATED_ORDINAL
        is PushDelivered -> PUSH_DELIVERED_ORDINAL
        is PushClicked -> PUSH_CLICKED_ORDINAL
        is TrackVisit -> TRACK_VISIT_ORDINAL
        is AsyncOperation -> ASYNC_OPERATION_ORDINAL
    }

}

internal enum class EventParameters(val fieldName: String) {
    UNIQ_KEY("uniqKey")
}
