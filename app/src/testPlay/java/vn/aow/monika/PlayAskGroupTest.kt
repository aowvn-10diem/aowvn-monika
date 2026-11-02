package vn.aow.monika

import android.app.Activity
import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.community.AskGroup
import vn.aow.monika.play.PlayLinks
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class PlayAskGroupTest {
    @Before fun initGraph() {
        AppGraph.init(ApplicationProvider.getApplicationContext())
    }

    @Test fun configuredGroupReceivesReadableImageAndQuestionThroughChooser() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val shot = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val group = AppGraph.config.current.community.facebookGroup
            assertTrue(group.isNotBlank())
            AskGroup.ask(activity, shot, "Fixture Game", "Fixture System")

            val chooser = shadowOf(activity).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("image/png", send.type)
            assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains("Fixture Game (Fixture System)"))
            assertTrue(send.getStringExtra(Intent.EXTRA_TEXT)!!.contains(group))
            val image = requireNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", image.scheme)
            assertEquals(BuildConfig.APPLICATION_ID + ".files", image.authority)
            assertEquals(image, send.clipData!!.getItemAt(0).uri)
            assertEquals(image, chooser.clipData!!.getItemAt(0).uri)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertTrue(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            activity.contentResolver.openInputStream(image)!!.use {
                val decoded = requireNotNull(BitmapFactory.decodeStream(it))
                assertEquals(2, decoded.width)
                assertEquals(2, decoded.height)
                decoded.recycle()
            }
            val clipboard = activity.getSystemService(ClipboardManager::class.java)!!
            assertTrue(clipboard.primaryClip!!.getItemAt(0).text.contains("Fixture Game"))
        } finally { shot.recycle() }
    }

    @Test fun noScreenshotStillOpensConfiguredGroup() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        AskGroup.ask(activity, null, "Fixture Game", "Fixture System")
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(AppGraph.config.current.community.facebookGroup, intent.dataString)
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test fun unsupportedLinkCannotStartImageSharing() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val file = File(activity.filesDir, "fixture.png").apply { writeBytes(byteArrayOf(1)) }
        PlayLinks.open(activity, "https://example.test/download", file, "Fixture")
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun textOnlyFallbackDoesNotGrantFileAccess() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        PlayLinks.share(activity, null, "Fixture question")
        val chooser = shadowOf(activity).nextStartedActivity
        val send = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals("text/plain", send.type)
        assertEquals("Fixture question", send.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(send.hasExtra(Intent.EXTRA_STREAM))
        assertEquals(0, send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // Android có thể tự thêm ClipData text/plain; không mục nào được chứa URI tệp.
        send.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) assertNull(clip.getItemAt(i).uri)
        }
    }
}
