package vn.aow.monika.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import java.io.File

/**
 * Chụp màn hình vào app/build/screenshots/<tên>.png để kiểm bằng mắt.
 * Vẽ thẳng decorView ra bitmap (captureToImage của Compose hay treo trên Robolectric).
 */
fun AndroidComposeTestRule<*, out ComponentActivity>.shot(name: String) {
    waitForIdle()
    val view = activity.window.decorView
    val bmp = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    runOnUiThread { view.draw(Canvas(bmp)) }
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
