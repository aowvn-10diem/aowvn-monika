package vn.aow.monika.cheats

import com.swordfish.libretrodroid.GLRetroView

/** Áp mã cheat vào lõi libretro qua retro_cheat_set (lõi nào hỗ trợ thì có tác dụng, không thì bỏ qua êm). */
class LibretroCheats(private val view: () -> GLRetroView?) : CheatBackend {
    override fun apply(previous: List<CheatEntry>, list: List<CheatEntry>) {
        val v = view() ?: return
        previous.forEachIndexed { i, e -> if (e.enabled) runCatching { v.setCheat(i, false, e.code) } }
        list.forEachIndexed { i, e -> runCatching { v.setCheat(i, e.enabled, e.code) } }
    }
}
