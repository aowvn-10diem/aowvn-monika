package vn.aow.monika.runner

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tay cầm ảo đơn giản (bản khung): D-pad, A/B/X/Y, L/R, Select/Start.
 * Phần trống trong suốt không chặn chạm, nên màn hình cảm ứng NDS vẫn dùng được.
 * Sau này có thể thay bằng thư viện RadialGamePad cho đẹp hơn.
 */
@SuppressLint("ViewConstructor")
class VirtualPad(
    context: Context,
    private val send: (action: Int, keyCode: Int) -> Unit,
) : FrameLayout(context) {

    private val size = (52 * resources.displayMetrics.density).toInt()

    init {
        addView(row(key("L", KeyEvent.KEYCODE_BUTTON_L1)), lp(Gravity.TOP or Gravity.START))
        addView(row(key("R", KeyEvent.KEYCODE_BUTTON_R1)), lp(Gravity.TOP or Gravity.END))
        addView(
            grid(
                null, key("▲", KeyEvent.KEYCODE_DPAD_UP), null,
                key("◀", KeyEvent.KEYCODE_DPAD_LEFT), null, key("▶", KeyEvent.KEYCODE_DPAD_RIGHT),
                null, key("▼", KeyEvent.KEYCODE_DPAD_DOWN), null,
            ),
            lp(Gravity.BOTTOM or Gravity.START)
        )
        addView(
            grid(
                null, key("X", KeyEvent.KEYCODE_BUTTON_X), null,
                key("Y", KeyEvent.KEYCODE_BUTTON_Y), null, key("A", KeyEvent.KEYCODE_BUTTON_A),
                null, key("B", KeyEvent.KEYCODE_BUTTON_B), null,
            ),
            lp(Gravity.BOTTOM or Gravity.END)
        )
        addView(
            row(key("SELECT", KeyEvent.KEYCODE_BUTTON_SELECT), key("START", KeyEvent.KEYCODE_BUTTON_START)),
            lp(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        )
    }

    private fun lp(gravity: Int) = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, gravity).apply {
        val m = (12 * resources.displayMetrics.density).toInt()
        setMargins(m, m, m, m)
    }

    private fun row(vararg keys: TextView) = LinearLayout(context).apply {
        keys.forEach { addView(it, LinearLayout.LayoutParams(if (it.text.length > 1) size * 2 else size, size).apply { marginEnd = 8 }) }
    }

    private fun grid(vararg cells: TextView?) = GridLayout(context).apply {
        columnCount = 3
        cells.forEach { cell ->
            addView(cell ?: TextView(context), GridLayout.LayoutParams().apply { width = size; height = size })
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun key(label: String, keyCode: Int) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        textSize = if (label.length > 1) 11f else 18f
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = size / 2f
            setColor(0x55FFFFFF)
        }
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { v.alpha = 0.5f; send(KeyEvent.ACTION_DOWN, keyCode) }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { v.alpha = 1f; send(KeyEvent.ACTION_UP, keyCode) }
            }
            true
        }
    }
}
