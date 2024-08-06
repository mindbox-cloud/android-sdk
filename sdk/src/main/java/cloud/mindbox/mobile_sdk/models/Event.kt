package cloud.mindbox.mobile_sdk.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import cloud.mindbox.mobile_sdk.managers.DbManager.EVENTS_TABLE_NAME
import com.google.gson.reflect.TypeToken
import java.util.UUID

@Entity(tableName = EVENTS_TABLE_NAME)
internal data class Event(
    @PrimaryKey(autoGenerate = true) val uid: Long = 0L,
    val eventType: EventType,
    val transactionId: String = UUID.randomUUID().toString(),
    val enqueueTimestamp: Long = System.currentTimeMillis(), // date of event creating
    val additionalFields: HashMap<String, String>? = null,
    val body: String? = null, //json
)

internal sealed class EventType(val operation: String, val endpoint: String) {

    companion object {

        private const val APP_INSTALLED_ORDINAL = 0
        private const val APP_INFO_UPDATED_ORDINAL = 1
        private const val PUSH_DELIVERED_ORDINAL = 2
        private const val PUSH_CLICKED_ORDINAL = 3
        private const val TRACK_VISIT_ORDINAL = 4
        private const val ASYNC_OPERATION_ORDINAL = 5
        private const val SYNC_OPERATION_ORDINAL = 6
        private const val APP_INSTALLED_WITHOUT_CUSTOMER = 7

        fun typeToken(ordinal: Int) = when (ordinal) {
            APP_INSTALLED_ORDINAL -> object : TypeToken<AppInstalled>() {}
            APP_INSTALLED_WITHOUT_CUSTOMER -> object : TypeToken<AppInstalledWithoutCustomer>() {}
            APP_INFO_UPDATED_ORDINAL -> object : TypeToken<AppInfoUpdated>() {}
            PUSH_CLICKED_ORDINAL -> object : TypeToken<PushClicked>() {}
            TRACK_VISIT_ORDINAL -> object : TypeToken<TrackVisit>() {}
            ASYNC_OPERATION_ORDINAL -> object : TypeToken<AsyncOperation>() {}
            SYNC_OPERATION_ORDINAL -> object : TypeToken<SyncOperation>() {}
            else -> throw IllegalArgumentException("Unknown TypeToken for $ordinal EventType ordinal")
        }

    }

    object AppInstalled : EventType("MobilePush.ApplicationInstalled", "/v3/operations/async")

    object AppInstalledWithoutCustomer :
        EventType("MobilePush.ApplicationInstalledWithoutCustomer", "/v3/operations/async")

    object AppInfoUpdated : EventType("MobilePush.ApplicationInfoUpdated", "/v3/operations/async")

    object PushClicked : EventType("MobilePush.TrackClick", "/v3/operations/async")

    object TrackVisit : EventType("TrackVisit", "/v1.1/customer/mobile-track-visit")

    internal class AsyncOperation(operation: String) : EventType(operation, "/v3/operations/async")

    internal class SyncOperation(operation: String) : EventType(operation, "/v3/operations/sync")

    fun ordinal() = when (this) {
        is AppInstalled -> APP_INSTALLED_ORDINAL
        is AppInstalledWithoutCustomer -> APP_INSTALLED_WITHOUT_CUSTOMER
        is AppInfoUpdated -> APP_INFO_UPDATED_ORDINAL
        is PushClicked -> PUSH_CLICKED_ORDINAL
        is TrackVisit -> TRACK_VISIT_ORDINAL
        is AsyncOperation -> ASYNC_OPERATION_ORDINAL
        is SyncOperation -> SYNC_OPERATION_ORDINAL
    }

}

internal sealed class InAppEventType(val name: String) {
    object AppStartup : InAppEventType("appStartup")
    class OrdinalEvent(val eventType: EventType, val body: String? = null) : InAppEventType(eventType.operation)
}
