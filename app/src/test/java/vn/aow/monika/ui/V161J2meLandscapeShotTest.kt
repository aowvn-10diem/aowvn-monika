package vn.aow.monika.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.playsoftware.j2meloader.AowKeyPalette
import ru.playsoftware.j2meloader.AowLandscapePad

/**
 * V161: ảnh trước/sau bố cục J2ME khi máy NGANG — phím chia hai mép, khung game ở giữa giữ tỉ lệ.
 *
 * Ảnh ra `app/build/screenshots/` (cùng quy ước `Shots.kt`, CI gom vào artifact `anh-chup-giao-dien`).
 *
 * NGUỒN ẢNH — nói thật: ảnh do test vẽ bằng `android.graphics.Canvas` từ CHÍNH các hàm/hằng sản xuất
 * (`AowLandscapePad.band/innerFrame/fit/keySize/keyAlpha`, `AowKeyPalette`), tức đúng số đo mà
 * `Canvas.updateSize` và `VirtualKeyboard` dùng. KHÔNG phải ảnh chụp emulator, và cũng không phải
 * `VirtualKeyboard.paint`: Robolectric 4.16 không nạp được runtime `javax.microedition` (xem ghi chú ở
 * `JavaRmsBackupTest`), nên lớp vẽ bàn phím thật không dựng được trong unit test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [34])
class V161J2meLandscapeShotTest {
    private val screenW = 800
    private val screenH = 360
    private val captionH = 56
    private val density = 1f

    /** Padding 0: bố cục mặc định. */
    @Test
    fun ngangPadding0() {
        val shot = render(padding = 0, before = false)
        assertEquals("tâm ảnh phải trùng tâm khung", shot.frameCenterX, shot.imageRect.centerX(), 1f)
        save(shot, "V161-ngang-padding-0")
    }

    /** Padding 16: ca từng bị lệch tâm — sau khi sửa, tâm ảnh trùng tâm khung. */
    @Test
    fun ngangPadding16() {
        val shot = render(padding = 16, before = false)
        assertEquals("tâm ảnh phải trùng tâm khung", shot.frameCenterX, shot.imageRect.centerX(), 1f)
        save(shot, "V161-ngang-padding-16")
    }

    /** Cùng padding 16 nhưng dựng bằng công thức CŨ (bề rộng lấy cả band) để thấy đúng lỗi lệch phải. */
    @Test
    fun ngangPadding16Truoc() {
        val shot = render(padding = 16, before = true)
        val delta = shot.imageRect.centerX() - shot.frameCenterX
        assertTrue("công thức cũ phải lệch phải đúng bằng padding, lệch thực tế $delta", delta >= 15f)
        save(shot, "V161-ngang-padding-16-truoc")
    }

    private class Shot(val bitmap: Bitmap, val imageRect: RectF, val frameCenterX: Float)

    private fun render(padding: Int, before: Boolean): Shot {
        val bitmap = Bitmap.createBitmap(screenW, screenH + captionH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(0x10, 0x14, 0x1A))

        val band = AowLandscapePad.band(screenW.toFloat(), screenH.toFloat(), density)
        val inner = AowLandscapePad.innerFrame(screenW.toFloat(), screenH.toFloat(), density, padding)
        val offX = band[0].toInt()
        val offY = band[1].toInt()
        // Trước khi sửa: bề rộng lấy cả band (rộng hơn 2*padding), rồi mới cộng padding lúc căn giữa.
        val frameW = if (before) (band[2] - band[0]).toInt() else inner[0]
        val frameH = inner[1]

        val fit = AowLandscapePad.fit(240, 320, frameW, frameH)
        val onX = (frameW - fit[0]) / 2 + padding + offX
        val onY = (frameH - fit[1]) / 2 + padding + offY
        val imageRect = RectF(
            onX.toFloat(), onY.toFloat(), (onX + fit[0]).toFloat(), (onY + fit[1]).toFloat(),
        )

        drawFakeGame(canvas, imageRect)
        drawKeyClusters(canvas, band)
        drawGuides(canvas, band, imageRect)
        drawCaption(canvas, padding, before, band, imageRect)

        return Shot(bitmap, imageRect, (band[0] + band[2]) / 2f)
    }

    /** Hai cụm phím đúng cỡ/vị trí/độ mờ của sản xuất: bám mép DƯỚI, ngoài khung game (như `resetLayout`). */
    private fun drawKeyClusters(canvas: Canvas, band: FloatArray) {
        val size = AowLandscapePad.keySize(screenW.toFloat(), screenH.toFloat(), density)
        if (size <= 0f) return
        val edge = AowLandscapePad.EDGE_DP * density
        val rowH = size * AowLandscapePad.ROW_SCALE_Y
        val alpha = AowLandscapePad.KEY_ALPHA

        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AowKeyPalette.face(true, false)
            this.alpha = alpha
        }
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = AowKeyPalette.OUTER_RIM
            this.alpha = alpha
        }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size * 0.42f
            textAlign = Paint.Align.CENTER
            color = AowKeyPalette.label(true, false)
        }

        // Khay phím bán trong suốt hai bên khung game (như paintMonikaSkin).
        val panel = Paint().apply {
            color = AowKeyPalette.PAD_SURFACE
            this.alpha = 0x40
        }
        canvas.drawRect(0f, 0f, band[0], screenH.toFloat(), panel)
        canvas.drawRect(band[2], 0f, screenW.toFloat(), screenH.toFloat(), panel)

        fun key(col: Int, colStart: Float, row: Int, rows: Int, label: String) {
            val left = colStart + col * size
            val top = screenH - edge - (rows - row) * rowH
            val rect = RectF(left + 2f, top + 2f, left + size - 2f, top + rowH - 2f)
            canvas.drawRoundRect(rect, size * 0.18f, size * 0.18f, face)
            canvas.drawRoundRect(rect, size * 0.18f, size * 0.18f, rim)
            canvas.drawText(label, rect.centerX(), rect.centerY() + size * 0.15f, ink)
        }

        // Trái: bàn số 3x4, bám mép dưới-trái (SCREEN INT_SOUTHWEST như resetLayout).
        val numbers = listOf("*", "0", "#", "7", "8", "9", "4", "5", "6", "1", "2", "3")
        for (row in 0 until 4) {
            for (col in 0 until 3) {
                key(col, edge, row, 4, numbers[row * 3 + col])
            }
        }

        // Phải: 3x3 bám mép dưới-phải — L ^ R / < OK > / (trống) v Menu.
        val rightStart = screenW - edge - 3 * size
        val arrows = listOf("L", "^", "R", "<", "OK", ">")
        for (row in 0 until 2) {
            for (col in 0 until 3) {
                key(col, rightStart, row, 3, arrows[row * 3 + col])
            }
        }
        key(1, rightStart, 2, 3, "v")
        key(2, rightStart, 2, 3, "M")
    }

    /** Nội dung game giả: nền + lưới + chữ + đường tâm, đủ để nhìn ra ảnh có nằm giữa khung hay không. */
    private fun drawFakeGame(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(0x1E, 0x4A, 0x6B)
        canvas.drawRect(rect, paint)

        paint.color = Color.rgb(0x2E, 0x6A, 0x94)
        var y = rect.top + 40f
        while (y < rect.bottom) {
            canvas.drawRect(rect.left, y, rect.right, y + 2f, paint)
            y += 40f
        }
        var x = rect.left + 40f
        while (x < rect.right) {
            canvas.drawRect(x, rect.top, x + 2f, rect.bottom, paint)
            x += 40f
        }

        paint.color = Color.WHITE
        paint.textSize = 16f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("GAME J2ME 240x320", rect.centerX(), rect.centerY() - 6f, paint)
        paint.color = Color.rgb(0xFF, 0xD5, 0x4F)
        paint.strokeWidth = 1f
        canvas.drawLine(rect.centerX(), rect.top, rect.centerX(), rect.bottom, paint)
        canvas.drawLine(rect.left, rect.centerY(), rect.right, rect.centerY(), paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(0x9F, 0xE8, 0xFF)
        canvas.drawRect(rect, paint)
    }

    /** Vạch tâm khung (vàng), mép khung (xám) và tâm ảnh (xanh) để đối chiếu bằng mắt. */
    private fun drawGuides(canvas: Canvas, band: FloatArray, imageRect: RectF) {
        val paint = Paint().apply { strokeWidth = 1f }
        paint.color = Color.rgb(0x9A, 0xA0, 0xA6)
        canvas.drawLine(band[0], 60f, band[0], screenH - 4f, paint)
        canvas.drawLine(band[2], 60f, band[2], screenH - 4f, paint)
        paint.color = Color.rgb(0xFF, 0xD5, 0x4F)
        canvas.drawLine((band[0] + band[2]) / 2f, 60f, (band[0] + band[2]) / 2f, screenH - 4f, paint)
        paint.color = Color.rgb(0x8C, 0xE9, 0x9A)
        canvas.drawLine(imageRect.centerX(), 60f, imageRect.centerX(), screenH - 4f, paint)
    }

    /** Nhãn nói rõ TRƯỚC/SAU và nguồn ảnh — không để người đọc tưởng là ảnh emulator. */
    private fun drawCaption(canvas: Canvas, padding: Int, before: Boolean, band: FloatArray, imageRect: RectF) {
        canvas.drawRect(
            0f, screenH.toFloat(), screenW.toFloat(), (screenH + captionH).toFloat(),
            Paint().apply { color = Color.rgb(0x05, 0x07, 0x0A) },
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            textAlign = Paint.Align.LEFT
        }
        val title = if (before) {
            "TRUOC khi sua (cong thuc cu, bo qua padding) - screenPadding=$padding"
        } else {
            "SAU khi sua - screenPadding=$padding"
        }
        paint.color = if (before) Color.rgb(0xFF, 0x8A, 0x80) else Color.rgb(0x8C, 0xE9, 0x9A)
        canvas.drawText(title, 8f, screenH + 18f, paint)

        paint.color = Color.rgb(0xC8, 0xD3, 0xDE)
        paint.textSize = 10f
        canvas.drawText(
            "Anh do test Robolectric ve tu ham san xuat AowLandscapePad/AowKeyPalette - KHONG phai anh emulator.",
            8f, screenH + 34f, paint,
        )
        val bandCenter = (band[0] + band[2]) / 2f
        val imageCenter = imageRect.centerX()
        canvas.drawText(
            "tam khung x=%.0f | tam anh x=%.1f | lech %.1f px"
                .format(bandCenter, imageCenter, imageCenter - bandCenter),
            8f, screenH + 48f, paint,
        )
    }

    private fun save(shot: Shot, name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            shot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
