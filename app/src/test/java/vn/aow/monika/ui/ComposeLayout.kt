package vn.aow.monika.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
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
 * Robolectric does not deliver a real draw pass: explicitly traverse the decor
 * after each frame, so layout-driven state (LazyRow, removed children) can settle.
 * The real layout/semantics predicate must still succeed within two wall seconds.
 */
internal fun AndroidComposeTestRule<*, out ComponentActivity>.awaitComposeLayout(
    condition: () -> Boolean,
) {
    check(!mainClock.autoAdvance) { "This helper requires the fixture's manual clock" }
    val decor = activity.window.decorView
    val bitmap = Bitmap.createBitmap(
        decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888,
    )
    try {
        waitUntil(timeoutMillis = 2_000) {
            runOnUiThread {
                shadowOf(Looper.getMainLooper()).idle()
                Snapshot.sendApplyNotifications()
            }
            mainClock.advanceTimeByFrame()
            runOnUiThread {
                shadowOf(Looper.getMainLooper()).idle()
                // A frozen Compose clock cannot complete Android's draw traversal.
                // Match the existing window bounds, then perform the missing pass.
                decor.measure(
                    View.MeasureSpec.makeMeasureSpec(bitmap.width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(bitmap.height, View.MeasureSpec.EXACTLY),
                )
                decor.layout(0, 0, bitmap.width, bitmap.height)
                decor.draw(Canvas(bitmap))
                Snapshot.sendApplyNotifications()
            }
            condition()
        }
    } finally {
        bitmap.recycle()
    }
}
