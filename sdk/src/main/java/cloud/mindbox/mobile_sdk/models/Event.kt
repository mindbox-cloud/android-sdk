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
    // date of event creating
    val enqueueTimestamp: Long = System.currentTimeMillis(),
    val additionalFields: HashMap<String, String>? = null,
    // json
    val body: String? = null,
)

internal sealed class EventType(val operation: String, val endpoint: String) {

    companion object {

        private const val APP_INSTALLED_ORDINAL = 0
        private const val APP_INFO_UPDATED_ORDINAL = 1

        // use it when returning a delivered operation, it is not used now
        private const val PUSH_DELIVERED_ORDINAL = 2
        private const val PUSH_CLICKED_ORDINAL = 3
        private const val TRACK_VISIT_ORDINAL = 4
        private const val ASYNC_OPERATION_ORDINAL = 5
        private const val SYNC_OPERATION_ORDINAL = 6
        private const val APP_INSTALLED_WITHOUT_CUSTOMER = 7
        private const val APP_KEEP_A_LIVE = 8

        fun typeToken(ordinal: Int) = when (ordinal) {
            APP_INSTALLED_ORDINAL -> object : TypeToken<AppInstalled>() {}
            APP_INSTALLED_WITHOUT_CUSTOMER -> object : TypeToken<AppInstalledWithoutCustomer>() {}
            APP_INFO_UPDATED_ORDINAL -> object : TypeToken<AppInfoUpdated>() {}
            APP_KEEP_A_LIVE -> object : TypeToken<AppKeepalive>() {}
            PUSH_CLICKED_ORDINAL -> object : TypeToken<PushClicked>() {}
            TRACK_VISIT_ORDINAL -> object : TypeToken<TrackVisit>() {}
            ASYNC_OPERATION_ORDINAL -> object : TypeToken<AsyncOperation>() {}
            SYNC_OPERATION_ORDINAL -> object : TypeToken<SyncOperation>() {}
            else -> throw IllegalArgumentException("Unknown TypeToken for $ordinal EventType ordinal")
        }
    }

    data object AppInstalled : EventType("MobilePush.ApplicationInstalled", "/v3/operations/async")

    data object AppInstalledWithoutCustomer :
        EventType("MobilePush.ApplicationInstalledWithoutCustomer", "/v3/operations/async")

    data object AppInfoUpdated : EventType("MobilePush.ApplicationInfoUpdated", "/v3/operations/async")

    data object AppKeepalive : EventType("MobilePush.ApplicationKeepalive", "/v3/operations/async")

    data object PushClicked : EventType("MobilePush.TrackClick", "/v3/operations/async")

    data object TrackVisit : EventType("TrackVisit", "/v1.1/customer/mobile-track-visit")

    internal class AsyncOperation(operation: String) : EventType(operation, "/v3/operations/async")

    internal class SyncOperation(operation: String) : EventType(operation, "/v3/operations/sync")

    fun ordinal() = when (this) {
        is AppInstalled -> APP_INSTALLED_ORDINAL
        is AppInstalledWithoutCustomer -> APP_INSTALLED_WITHOUT_CUSTOMER
        is AppInfoUpdated -> APP_INFO_UPDATED_ORDINAL
        is AppKeepalive -> APP_KEEP_A_LIVE
        is PushClicked -> PUSH_CLICKED_ORDINAL
        is TrackVisit -> TRACK_VISIT_ORDINAL
        is AsyncOperation -> ASYNC_OPERATION_ORDINAL
        is SyncOperation -> SYNC_OPERATION_ORDINAL
    }
}

internal sealed class InAppEventType(val name: String) {
    data object AppStartup : InAppEventType("appStartup")

    class OrdinalEvent(val eventType: EventType, val body: String? = null) : InAppEventType(eventType.operation)
}
