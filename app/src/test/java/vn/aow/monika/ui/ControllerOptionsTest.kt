package vn.aow.monika.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.Prefs
import vn.aow.monika.ui.controls.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ControllerOptionsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @After fun reset() {
        Prefs(context).apply { controllerOptions = ControllerOptions(); padScale = 1f; padOpacity = .65f }
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
    }

    @Test fun settingsSurviveNewPrefsAndResetWithoutTouchingGameLayout() {
        val prefs = Prefs(context)
        prefs.padScale = 1.2f; prefs.padOpacity = .4f
        val settings = ControllerOptions(HapticLevel.LIGHT, false, 1.3f, .5f, false)
        prefs.controllerOptions = settings
        assertEquals(settings, Prefs(context).controllerOptions)
        Prefs(context).controllerOptions = ControllerOptions()
        assertEquals(ControllerOptions(), prefs.controllerOptions)
        assertEquals(1.2f, prefs.padScale, .001f)
        assertEquals(.4f, prefs.padOpacity, .001f)
    }

    @Test fun invalidStorageIsBoundedAndUnknownHapticFallsBack() {
        context.getSharedPreferences("monika", Context.MODE_PRIVATE).edit()
            .putFloat("controller_size", Float.NaN).putFloat("controller_opacity", -9f)
            .putString("controller_haptic", "future-level").commit()
        val p = Prefs(context).controllerOptions
        assertEquals(1f, p.size, .001f); assertEquals(.2f, p.opacity, .001f)
        assertEquals(HapticLevel.MEDIUM, p.haptic)
        assertEquals(1.4f, ControllerOptions(size = 99f).bounded().size, .001f)
    }

    @Test fun systemTouchSettingAndOffSuppressFeedback() {
        var calls = 0
        val view = object : View(context) {
            override fun performHapticFeedback(feedbackConstant: Int): Boolean { calls++; return true }
        }
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 0)
        HapticLevel.entries.forEach { controllerHaptic(view, it) }
        assertEquals(0, calls)
        Settings.System.putInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
        controllerHaptic(view, HapticLevel.OFF)
        assertEquals(0, calls)
        view.isHapticFeedbackEnabled = false
        HapticLevel.entries.forEach { controllerHaptic(view, it) }
        assertEquals(0, calls)
    }

    @Test fun dpadDiagonalsAndStickDeadzoneKeepDirectionContract() {
        assertEquals(setOf(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_UP), directionKeys(50f, -50f, 150f))
        assertTrue(directionKeys(10f, 10f, 150f).isEmpty())
        assertEquals(setOf(android.view.KeyEvent.KEYCODE_DPAD_DOWN), directionKeys(0f, 50f, 150f))
        assertEquals(0f, stickValue(.119f), 0f)
        assertEquals(.12f, stickValue(.12f), 0f)
        assertEquals(-1f, stickValue(-2f), 0f)
    }
}
