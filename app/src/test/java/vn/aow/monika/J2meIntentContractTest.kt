package vn.aow.monika

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.playsoftware.j2meloader.J2meRuntime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class J2meIntentContractTest {
    @Test fun internalLaunchRemainsExplicitWithReadGrant() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://fixture.example/game.jar")
        val intent = J2meRuntime.openGameIntent(context, uri)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("ru.woesss.j2me.installer.MonikaLaunchActivity", intent.component!!.className)
        assertEquals(context.packageName, intent.component!!.packageName)
        assertEquals(uri, intent.data)
        assertEquals("application/java-archive", intent.type)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
