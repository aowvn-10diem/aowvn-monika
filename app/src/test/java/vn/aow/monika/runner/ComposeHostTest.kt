package vn.aow.monika.runner

import android.app.Activity
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.findViewTreeOnBackPressedDispatcherOwner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ComposeHostTest {
    @Test fun plainActivityGetsBackOwnerAndDestroysCallbacks() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val host = ComposeHost(activity)
        host.attach(root) {}
        val owner = requireNotNull(root.findViewTreeOnBackPressedDispatcherOwner())
        assertSame(host, owner)
        var calls = 0
        host.resume()
        owner.onBackPressedDispatcher.addCallback(host, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { calls++ }
        })
        owner.onBackPressedDispatcher.onBackPressed()
        assertEquals(1, calls)
        host.destroy()
        assertFalse(owner.onBackPressedDispatcher.hasEnabledCallbacks())
        controller.pause().stop().destroy()
    }
}
