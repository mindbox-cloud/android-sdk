package cloud.mindbox.mobile_sdk

import androidx.annotation.WorkerThread
import cloud.mindbox.mobile_sdk.logger.mindboxLogD
import kotlinx.coroutines.Job
import java.util.concurrent.CountDownLatch

internal object InitializeLock {

    private val map: Map<State, CountDownLatch> = mapOf(
        State.SAVE_MINDBOX_CONFIG to CountDownLatch(1),
        State.APP_STARTED to CountDownLatch(1)
    )

    @WorkerThread
    internal fun await(state: State) {
        State.values().filter { state >= it }
            .sortedBy { it.ordinal }
            .mapNotNull { map[it] }
            .onEach {
                it.await()
            }
    }

    internal fun complete(state: State) {
        map[state]?.countDown()
    }

    internal enum class State {
        SAVE_MINDBOX_CONFIG,
        APP_STARTED
    }
}

internal fun Job.initState(state: InitializeLock.State): Job {
    return this.apply {
        invokeOnCompletion {
            InitializeLock.complete(state)
            InitializeLock.mindboxLogD("State $state completed")
        }
    }
}