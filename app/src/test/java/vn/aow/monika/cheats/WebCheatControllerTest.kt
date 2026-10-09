package vn.aow.monika.cheats

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.ui.TestApp

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class WebCheatControllerTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** Bridge giả: ghi lại đoạn JS gửi đi và trả lời theo tên hàm `__M.*`. */
    private class FakeBridge {
        val sent = mutableListOf<String>()
        var detectReply = """{"ok":true,"engine":"rpgmv","flags":{"god":1,"noclip":0},"speed":2}"""
        var toggleReply = """{"ok":true}"""
        var tyranoReply = """{"ok":true,"vars":[{"scope":"f","key":"gold","value":100},{"scope":"sf","key":"it's","value":2}]}"""
        var varReply = """{"ok":true,"name":"hp","value":42}"""

        val eval: (String, (String?) -> Unit) -> Unit = { js, done ->
            sent += js
            done(
                when {
                    "__M.detect()" in js -> detectReply
                    "__M.toggle(" in js -> toggleReply
                    "__M.tyranoVars()" in js -> tyranoReply
                    "__M.getVar(" in js -> varReply
                    else -> "null"
                },
            )
        }
    }

    @Test fun refreshReadsEngineFlagsAndSpeed() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.refresh()
        assertEquals("rpgmv", c.engine)
        assertEquals(mapOf("god" to 1.0, "noclip" to 0.0), c.flags)
        assertEquals(2.0, c.speed, 0.0)
        assertTrue(bridge.sent.none { "tyranoVars" in it })
    }

    @Test fun refreshAcceptsResultWrappedAsJsonString() {
        val bridge = FakeBridge().apply { detectReply = "\"{\\\"ok\\\":true,\\\"engine\\\":\\\"rpgmv\\\"}\"" }
        val c = WebCheatController(app, bridge.eval)
        c.refresh()
        assertEquals("rpgmv", c.engine)
    }

    @Test fun speedIsKeptWhenReportedValueIsNotPositive() {
        val bridge = FakeBridge().apply { detectReply = """{"ok":true,"engine":"none","speed":0}""" }
        val c = WebCheatController(app, bridge.eval)
        c.refresh()
        assertEquals(1.0, c.speed, 0.0)
        assertEquals("none", c.engine)
    }

    @Test fun showOpensPanelAndRefreshes() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.show()
        assertTrue(c.open)
        assertEquals("rpgmv", c.engine)
        c.close()
        assertFalse(c.open)
    }

    @Test fun tyranoEngineLoadsVariablesAndKeepsQuotedKeys() {
        val bridge = FakeBridge().apply { detectReply = """{"ok":true,"engine":"tyrano"}""" }
        val c = WebCheatController(app, bridge.eval)
        c.refresh()
        assertEquals(listOf("f", "sf"), c.tyrano.map { it.scope })
        assertEquals(listOf("gold", "it's"), c.tyrano.map { it.key })
        assertEquals(listOf(100.0, 2.0), c.tyrano.map { it.value })
    }

    @Test fun tyranoAddAndMulEscapeKeyAndSendNewValue() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.tyranoAdd(WebCheatController.TVar("sf", "it's", 2.0), 3.0)
        assertTrue(bridge.sent.any { "__M.tyranoSet('sf','it\\'s',5.0)" in it })
        c.tyranoMul(WebCheatController.TVar("f", "gold", 100.0), 0.5)
        assertTrue(bridge.sent.any { "__M.tyranoSet('f','gold',50.0)" in it })
    }

    @Test fun toggleSendsFlagAndRefreshesWhenOk() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.setToggle("god", true)
        assertTrue(bridge.sent.any { "__M.toggle('god',1)" in it })
        assertTrue(bridge.sent.any { "__M.detect()" in it })
    }

    @Test fun failedToggleShowsMessageAndDoesNotRefresh() {
        val bridge = FakeBridge().apply { toggleReply = """{"ok":false,"msg":"Trò chơi này không hỗ trợ"}""" }
        val c = WebCheatController(app, bridge.eval)
        c.setToggle("god", false)
        assertTrue(bridge.sent.any { "__M.toggle('god',0)" in it })
        assertEquals("Trò chơi này không hỗ trợ", c.message)
        assertTrue(bridge.sent.none { "__M.detect()" in it })
    }

    @Test fun chooseSpeedStoresValueAndSendsIt() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.chooseSpeed(2.5)
        assertEquals(2.5, c.speed, 0.0)
        assertTrue(bridge.sent.any { "__M.speed(2.5)" in it })
    }

    @Test fun actionMultAndSwitchSendArguments() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.action("heal", 2)
        c.action("kill")
        c.setMult("exp", 3)
        c.setSwitch(5, true)
        assertTrue(bridge.sent.any { "__M.action('heal',2)" in it })
        assertTrue(bridge.sent.any { "__M.action('kill')" in it })
        assertTrue(bridge.sent.any { "__M.mult('exp',3)" in it })
        assertTrue(bridge.sent.any { "__M.setSwitch(5,true)" in it })
    }

    @Test fun readVarFormatsNameAndValueOrFailure() {
        val bridge = FakeBridge()
        val c = WebCheatController(app, bridge.eval)
        c.readVar(3)
        assertEquals("#3 hp = 42", c.varInfo)
        bridge.varReply = """{"ok":false}"""
        c.readVar(4)
        assertEquals("Không đọc được", c.varInfo)
    }
}
