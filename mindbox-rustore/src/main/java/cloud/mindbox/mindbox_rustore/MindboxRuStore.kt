package cloud.mindbox.mindbox_rustore

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage

@Suppress("unused")
fun MindboxRuStore(projectId: String): MindboxRuStore = MindboxRuStoreInternal.apply {
    this.projectId = projectId
}

/***
 * Use factory function MindboxRuStore(projectId) to create an instance of this class.
 */
abstract class MindboxRuStore : MindboxPushService by MindboxRuStoreInternal {

    companion object {
        /**
         * Checks if [RemoteMessage] is sent with Mindbox
         * Returns true if it is or false otherwise
         **/
        fun isMindboxPush(remoteMessage: RemoteMessage?): Boolean {
            TODO()
        }

        /**
         * Converts [RemoteMessage] to [MindboxRemoteMessage]
         * Use this method to get mindbox push-notification data
         * It is encouraged to use this method inside try/catch block
         * @throws JsonSyntaxException – if remote message can't be parsed
         **/
        fun convertToMindboxRemoteMessage(remoteMessage: RemoteMessage?): MindboxRemoteMessage? {
            TODO()
        }
    }

    override val tag: String = "RuStore"

    var projectId: String = ""
        internal set(value) = run { field = value }
}

internal object MindboxRuStoreInternal : MindboxRuStore() {

    override fun toString(): String = tag

    override fun getServiceHandler(logger: MindboxLogger, exceptionHandler: ExceptionHandler): PushServiceHandler {
        return RuStoreServiceHandler(logger, exceptionHandler, projectId)
    }
}
