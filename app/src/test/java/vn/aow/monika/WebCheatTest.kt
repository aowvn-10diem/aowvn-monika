package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.cheats.WebCheatController
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class WebCheatTest {
    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    /** WebView trả kết quả JS dạng chuỗi JSON đã bọc thêm 1 lớp (đúng như evaluateJavascript). */
    private fun quoted(json: String) = org.json.JSONObject.quote(json)

    @Test fun bootScriptShipsInAssets() {
        val js = app.assets.open("cheats/web-boot.js").bufferedReader().readText()
        assertTrue("M.detect = " in js && "M.speed = " in js && "Game_BattlerBase" in js)
    }

    @Test fun detectsEngineAndSpeed() {
        val sent = mutableListOf<String>()
        val c = WebCheatController(app) { js, done ->
            sent += js
            done(if ("detect" in js) quoted("""{"engine":"mv","flags":{"god":1,"exp":5},"speed":2}""") else quoted("""{"ok":true,"msg":"ok"}"""))
        }
        c.show()
        assertEquals("mv", c.engine)
        assertEquals(1.0, c.flags["god"]!!, 0.0)
        assertEquals(2.0, c.speed, 0.0)
        c.action("gold")
        assertTrue(sent.any { "__M.action('gold')" in it })
        assertEquals("ok", c.message)
    }
}
