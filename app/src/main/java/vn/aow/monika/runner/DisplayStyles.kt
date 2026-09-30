package vn.aow.monika.runner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import vn.aow.monika.config.CoreDisplay
import vn.aow.monika.config.DisplayStyle

/** Chọn/xoay vòng kiểu hiển thị và tính cỡ co giãn số nguyên. Toàn hàm thuần → test được trên JVM. */
object DisplayStyles {
    /** Kiểu đang dùng: [saved] (lựa chọn của người chơi) nếu còn trong config, không thì mặc định, không thì kiểu đầu tiên. */
    fun resolve(display: CoreDisplay?, saved: String?): Pair<String, DisplayStyle>? {
        val d = display?.takeIf { it.styles.isNotEmpty() } ?: return null
        val key = saved?.takeIf { it in d.styles } ?: d.default.takeIf { it in d.styles } ?: d.styles.keys.first()
        return key to d.styles.getValue(key)
    }

    fun next(display: CoreDisplay, current: String): String {
        val keys = display.styles.keys.toList()
        return keys[(keys.indexOf(current) + 1) % keys.size]
    }

    /**
     * Bội số nguyên lớn nhất để hình gốc ([nativeW]×[nativeH]) vừa khung [availW]×[availH]. Trả 0 (= lấp đầy kiểu thường) khi
     * không vừa hoặc hình chỉ chiếm dưới [minFill] chiều chật của khung — viền đen quá to thì không đáng.
     */
    fun integerScale(availW: Int, availH: Int, nativeW: Int, nativeH: Int, minFill: Float = 0.82f): Int {
        if (nativeW <= 0 || nativeH <= 0 || availW <= 0 || availH <= 0) return 0
        val n = minOf(availW / nativeW, availH / nativeH)
        if (n < 1) return 0
        val fill = maxOf(n * nativeW / availW.toFloat(), n * nativeH / availH.toFloat())
        return if (fill >= minFill) n else 0
    }

    /** Lưới LCD chỉ có nghĩa từ 3× (2× thì 1 điểm ảnh gốc chỉ có 2 điểm ảnh máy, vạch lưới ăn mất nửa hình). */
    fun gridScaleOk(scale: Int) = scale >= 3
}

/**
 * Lưới điểm ảnh LCD: bản "gạch" scale×scale điểm ảnh máy phủ lặp lên hình đã co giãn số nguyên.
 * Mỗi điểm ảnh gốc của game = 1 ô; khe tối nằm ở mép phải + mép dưới ô (1 điểm ảnh máy, 2 từ 6×), kèm 1 điểm ảnh "vai" mờ hơn
 * cho khe mềm như màn LCD thật. Chỉ là lớp đen có độ trong suốt → không đổi màu, chỉ làm tối khe.
 */
object LcdGrid {
    fun gap(scale: Int) = if (scale >= 6) 2 else 1

    /** Điểm ảnh ARGB của gạch (scale*scale). scale < 3 hoặc strength ≤ 0 → toàn trong suốt. */
    fun tile(scale: Int, strength: Float): IntArray {
        val out = IntArray(maxOf(scale, 1) * maxOf(scale, 1))
        if (scale < 3 || strength <= 0f) return out
        val s = strength.coerceIn(0f, 1f)
        val g = gap(scale)
        fun edge(i: Int): Float = when {
            i >= scale - g -> 1f                 // khe
            i == scale - g - 1 && scale >= 4 -> SHOULDER // vai mềm
            else -> 0f
        }
        for (y in 0 until scale) for (x in 0 until scale) {
            val dx = edge(x); val dy = edge(y)
            val a = (1f - (1f - dx) * (1f - dy)) * s
            out[y * scale + x] = (Math.round(a * 255f).coerceIn(0, 255)) shl 24 // đen, chỉ có kênh alpha
        }
        return out
    }

    private const val SHOULDER = 0.22f
}

/** Khung phủ lưới LCD: đặt đúng chồng lên khung game (cùng LayoutParams). Không nhận chạm. */
class LcdGridView(context: Context) : View(context) {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private var tileScale = 0
    private var tileStrength = 0f

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** [scale] < 3 hoặc [strength] ≤ 0 → ẩn. */
    fun set(scale: Int, strength: Float) {
        if (!DisplayStyles.gridScaleOk(scale) || strength <= 0f) { visibility = GONE; paint.shader = null; tileScale = 0; tileStrength = 0f; return }
        if (scale == tileScale && strength == tileStrength && visibility == VISIBLE) return
        val bmp = Bitmap.createBitmap(LcdGrid.tile(scale, strength), scale, scale, Bitmap.Config.ARGB_8888)
        paint.shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        tileScale = scale; tileStrength = strength
        visibility = VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (tileScale > 0) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
