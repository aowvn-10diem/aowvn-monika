package vn.aow.monika.notify

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import vn.aow.monika.feed.Post
import vn.aow.monika.ui.MainActivity
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class NotifierTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Before fun setUp() {
        shadowOf(context).grantPermissions("android.permission.POST_NOTIFICATIONS")
        Notifier.createChannels(context)
    }

    @Test fun determinateProgressShowsPercentInTitle() {
        val n = Notifier.progress(context, "Giải nén", "game.zip", 40)
        assertEquals("Giải nén 40%", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(40, n.extras.getInt(Notification.EXTRA_PROGRESS))
        assertEquals(100, n.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertFalse(n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
        assertTrue((n.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test fun indeterminateProgressHasPlainTitle() {
        val n = Notifier.progress(context, "Giải nén", "game.zip")
        assertEquals("Giải nén", n.extras.getString(Notification.EXTRA_TITLE))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE))
    }

    @Test fun newPostNotificationShowsTitleAndOpensThatPost() {
        val post = Post("42", "Game Việt hóa mới", "2026-10-08", "https://aow.vn/p/x.html", listOf("rpg"), null, "")
        Notifier.newPost(context, post)
        val n = shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification("42".hashCode())
        assertEquals("Bài mới trên AowVN", n.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Game Việt hóa mới", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val open = shadowOf(n.contentIntent).savedIntent
        assertEquals(ComponentName(context, MainActivity::class.java), open.component)
        assertEquals("42", open.getStringExtra(MainActivity.EXTRA_POST_ID))
    }
}
