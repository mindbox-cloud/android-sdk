package cloud.mindbox.mobile_sdk

import androidx.annotation.WorkerThread
import cloud.mindbox.mobile_sdk.logger.mindboxLogI
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job

internal object InitializeLock {

    private val map: MutableMap<State, CompletableDeferred<Unit>> = mutableMapOf(
        State.SAVE_MINDBOX_CONFIG to CompletableDeferred(),
        State.APP_STARTED to CompletableDeferred()
    )

    @WorkerThread
    internal suspend fun await(state: State) {
        State.values().filter { state >= it }
            .sortedBy { it.ordinal }
            .mapNotNull { map[it] }
            .onEach {
                it.await()
            }
    }

    internal fun complete(state: State) {
        map[state]?.complete(Unit)
        mindboxLogI("State $state completed")
    }

    internal fun reset(state: State) {
        map[state]?.complete(Unit)
        map[state] = CompletableDeferred()
        mindboxLogI("State $state is reset")
    }

    internal enum class State {
        MIGRATION,
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
