package cloud.mindbox.mobile_sdk

import androidx.annotation.WorkerThread
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import kotlinx.coroutines.Job
import java.util.concurrent.CountDownLatch

internal object InitializeLock {

    private val map: MutableMap<State, CountDownLatch> = mutableMapOf(
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
        mindboxLogI("State $state completed")
    }
    
    internal fun reset(state: State) {
        map[state]?.countDown()
        map[state] = CountDownLatch(1)
        mindboxLogI("State $state is reset")
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
        }
    }
}