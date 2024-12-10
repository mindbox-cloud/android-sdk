package cloud.mindbox.mindbox_rustore

import cloud.mindbox.mobile_sdk.logger.MindboxLogger
import cloud.mindbox.mobile_sdk.pushes.MindboxPushService
import cloud.mindbox.mobile_sdk.pushes.PushServiceHandler
import cloud.mindbox.mobile_sdk.utils.ExceptionHandler

@Suppress("unused")
fun MindboxRuStore(projectId: String): MindboxRuStore = MindboxRuStoreInternal.apply {
    this.projectId = projectId
}

/***
 * Use factory function MindboxRuStore(projectId) to create an instance of this class.
 */
abstract class MindboxRuStore : MindboxPushService {
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
