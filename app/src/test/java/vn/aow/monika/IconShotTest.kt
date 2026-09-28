package vn.aow.monika

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.aow.monika.ui.TestApp
import java.io.File

/** Vẽ icon app (nền + chữ M Portal) ra PNG để soát bằng mắt: app/build/screenshots/icon-*.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class IconShotTest {
    @Test fun renderIcon() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val size = 432
        fun draw(vararg ids: Int): Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bmp ->
            val c = Canvas(bmp)
            ids.forEach { id -> ContextCompat.getDrawable(ctx, id)!!.apply { setBounds(0, 0, size, size) }.draw(c) }
        }
        val dir = File("build/screenshots").apply { mkdirs() }
        val full = draw(R.drawable.monika_adaptive_background, R.drawable.monika_adaptive_foreground)
        File(dir, "icon-adaptive.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val mono = draw(R.drawable.monika_monochrome)
        File(dir, "icon-mono.png").outputStream().use { mono.compress(Bitmap.CompressFormat.PNG, 100, it) }
        // Khe "portal": giữa chân phải, ngay dưới vai, phải trong suốt (thấy nền) — còn thân chân thì có nét.
        fun at(x: Int, y: Int) = android.graphics.Color.alpha(mono.getPixel((x * 0.48 + 216 - 256 * 0.48).toInt(), (y * 0.48 + 216 - 247 * 0.48).toInt()))
        assertTrue("khe portal phải trống", at(428, 179) < 40)
        assertTrue("chân phải có nét", at(428, 300) > 200)
        assertTrue("vai phải có nét", at(428, 120) > 200)
        assertTrue("giữa chữ M trống", at(256, 330) < 40)
    }
}
