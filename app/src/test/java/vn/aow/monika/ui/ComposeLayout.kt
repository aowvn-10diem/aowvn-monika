package vn.aow.monika.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import org.robolectric.Shadows.shadowOf

/**
 * Coordinate the two schedulers in a Robolectric manual-clock fixture. Android
 * callbacks can enqueue snapshot writes after the last Compose frame. Advancing
 * only Compose misses those writes; switching to automatic global idleness can
 * instead keep pumping frames while Android still needs a traversal.
 *
 * Drain current Android work, notify Compose, run a frame (including Compose's
 * measure/layout hook), then drain the resulting Android work. Do not advance
 * Android timers to their end or wait for every unrelated global frame awaiter.
 * The real layout/semantics predicate must still succeed within two wall seconds.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.awaitComposeLayout(
    condition: () -> Boolean,
) {
    check(!mainClock.autoAdvance) { "This helper requires the fixture's manual clock" }
    waitUntil(timeoutMillis = 2_000) {
        runOnUiThread {
            shadowOf(Looper.getMainLooper()).idle()
            Snapshot.sendApplyNotifications()
        }
        mainClock.advanceTimeByFrame()
        runOnUiThread { shadowOf(Looper.getMainLooper()).idle() }
        condition()
    }
}
