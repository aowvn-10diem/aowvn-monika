package vn.aow.monika.community

import android.app.Activity
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.AppGraph
import vn.aow.monika.browser.InAppBrowserActivity
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class CommunityFlowsTest {
    @Test fun openUsesEmbeddedBrowserWhenTheCommunityAppIsMissing() {
        val app = ApplicationProvider.getApplicationContext<TestApp>()
        val url = "https://discord.gg/coverage-fixture"

        Community.open(app, url)

        val started = shadowOf(app).nextStartedActivity
        assertEquals(InAppBrowserActivity::class.java.name, started.component?.className)
        assertEquals(url, started.getStringExtra("url"))
    }

    @Test fun askGroupCopiesMessageAndAttachesScreenshotToConfiguredGroup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val gameName = "Fixture Game"
        val system = "Fixture System"
        val expectedNote = "Mọi người ơi, mình đang chơi $gameName ($system) trên Aow Monika và bị kẹt ở đoạn này, giúp mình với 🙏"
        val group = AppGraph.config.current.community.facebookGroup
        assertTrue(group.isNotBlank())

        AskGroup.ask(activity, bitmap, gameName, system)

        val clipboard = activity.getSystemService(ClipboardManager::class.java)!!
        assertEquals(expectedNote, clipboard.primaryClip!!.getItemAt(0).text.toString())
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(InAppBrowserActivity::class.java.name, started.component?.className)
        assertEquals(group, started.getStringExtra("url"))
        val attachment = File(started.getStringExtra("attach")!!)
        assertTrue(attachment.isFile)
        bitmap.recycle()
    }
}
