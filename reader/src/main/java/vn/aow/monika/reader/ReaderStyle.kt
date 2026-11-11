package vn.aow.monika.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle

/**
 * Bảng màu/chữ/bo góc màn đọc nhận từ app.
 * Mô-đun `:reader` không phụ thuộc `:app`, nên app truyền token `Monika.colors` /
 * `Monika.type` / `Radius` / `primaryGradient()` vào đây — màn đọc vẫn đúng bộ nhận diện
 * "Aow Monika" mà mô-đun không hard-code màu/cỡ.
 */
@Immutable
data class ReaderStyle(
    val background: Color,
    val surface: Color,
    val text: Color,
    val textSecondary: Color,
    val chip: Color,
    val chipText: Color,
    val accent: Color,
    val accentGradient: Brush,
    val onAccent: Color,
    /** Bo góc thẻ/khung (app truyền `Radius.large`). */
    val card: Shape,
    /** Bo tròn hoàn toàn (app truyền `Radius.pill`). */
    val pill: Shape,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val button: TextStyle,
)
