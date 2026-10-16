package cloud.mindbox.mindbox_rustore

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushConverter
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage

@Suppress("unused")
public fun MindboxRuStore(projectId: String): MindboxRuStore = MindboxRuStore.apply {
    this.projectId = projectId
}

/***
 * Use factory function MindboxRuStore(projectId) to set up RuStore projectId.
 * Without it, the project id is read from the `ru.rustore.sdk.pushclient.project_id` meta-data.
 */
public object MindboxRuStore : MindboxPushService, MindboxPushConverter<RemoteMessage>() {

    internal var projectId: String = ""
        internal set(value) = run { field = value }

    override val tag: String = "RuStore"

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler): PushServiceHandler {
        return RuStoreServiceHandler(logger, exceptionHandler, projectId)
    }

    override fun toString(): String = tag

    override fun RemoteMessage.pushData(): Map<String, String> = data
}
