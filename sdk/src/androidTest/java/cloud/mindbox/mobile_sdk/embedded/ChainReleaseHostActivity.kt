package cloud.mindbox.mobile_sdk.embedded

import android.os.Bundle
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity

/** A bare screen for the chain-release test: the block goes into [container]. */
class ChainReleaseHostActivity : FragmentActivity() {

    lateinit var container: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = FrameLayout(this)
        setContentView(container)
    }
}
