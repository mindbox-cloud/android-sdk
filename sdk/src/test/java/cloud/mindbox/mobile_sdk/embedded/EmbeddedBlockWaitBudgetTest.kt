package cloud.mindbox.mobile_sdk.embedded

import android.os.Handler
import android.os.Looper
import cloud.mindbox.mobile_sdk.models.Milliseconds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class EmbeddedBlockWaitBudgetTest {

    @Test
    fun `a crashing expiry callback is contained`() {
        var fired = 0
        val budget = EmbeddedBlockWaitBudget(Milliseconds(50L), Handler(Looper.getMainLooper())) {
            fired++
            error("expiry boom")
        }

        budget.armIfNeeded()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(51L))

        assertEquals(1, fired)
    }
}
