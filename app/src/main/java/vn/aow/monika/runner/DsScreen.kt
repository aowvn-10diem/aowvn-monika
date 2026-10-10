package vn.aow.monika.runner

import vn.aow.monika.config.CoreScreen
import vn.aow.monika.config.ScreenChoice

/**
 * V78c: mục "Màn hình" trong Cài đặt Monika (bố cục · tỉ lệ · khoảng cách của lõi nhiều màn hình, vd. NDS).
 * Chỉ là lớp mỏng trên tùy chọn lõi: đọc giá trị hiện tại từ [CoreOptions] (lựa chọn tay → mặc định config → mặc định mục)
 * và ghi lại đúng khóa libretro, nên khớp với bảng "Tùy chọn giả lập" trong game và có hiệu lực khi mở game tiếp theo.
 */
object DsScreen {
    /** Trần an toàn cho thanh kéo khoảng cách dù config (có thể từ xa) ghi số lớn. */
    const val GAP_LIMIT = 256

    fun gapMax(screen: CoreScreen): Int = screen.gapMax.coerceIn(0, GAP_LIMIT)

    /** Lựa chọn hiện tại của người chơi. [gap] tính bằng điểm ảnh. */
    data class State(val layout: ScreenChoice, val ratio: ScreenChoice?, val gap: Int)

    /** [options] = tùy chọn đang hiệu lực của lõi: mặc định trong config đè bằng lựa chọn tay đã lưu. */
    fun current(screen: CoreScreen, options: Map<String, String>): State {
        val layout = pick(screen.layouts, options[screen.layoutKey], screen.defaultLayout)
        val ratio = if (screen.ratioKey.isBlank()) null else pick(screen.ratios, options[screen.ratioKey], screen.defaultRatio)
        val gap = if (screen.gapKey.isBlank() || gapMax(screen) < 1) 0
        else (options[screen.gapKey]?.toIntOrNull() ?: screen.defaultGap).coerceIn(0, gapMax(screen))
        return State(layout ?: ScreenChoice("", "", ""), ratio, gap)
    }

    /** Có hiện lựa chọn tỉ lệ không (bố cục đang chọn dùng tỉ lệ, và lõi có khóa tỉ lệ). */
    fun showsRatio(screen: CoreScreen, state: State): Boolean = state.layout.usesRatio && screen.ratioKey.isNotBlank() && screen.ratios.isNotEmpty()

    fun showsGap(screen: CoreScreen): Boolean = screen.gapKey.isNotBlank() && gapMax(screen) >= 1

    /** Khóa → giá trị cần ghi vào tùy chọn lõi khi người chơi chọn [choice] (bố cục hoặc tỉ lệ). */
    fun valueOf(key: String, choice: ScreenChoice): Pair<String, String> = key to choice.value

    fun gapValue(screen: CoreScreen, px: Int): Pair<String, String> = screen.gapKey to px.coerceIn(0, gapMax(screen)).toString()

    /** Các khóa mục này quản lý (để "Về mặc định" xóa lựa chọn tay). */
    fun keys(screen: CoreScreen): List<String> = listOf(screen.layoutKey, screen.ratioKey, screen.gapKey).filter { it.isNotBlank() }

    private fun pick(choices: List<ScreenChoice>, value: String?, defaultId: String): ScreenChoice? =
        choices.firstOrNull { it.value == value } ?: choices.firstOrNull { it.id == defaultId } ?: choices.firstOrNull()
}
