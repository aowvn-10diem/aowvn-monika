package vn.aow.monika.runner

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager

/**
 * Sức máy khi chạy giả lập → chọn bộ tùy chọn lõi trong config (`cores.<id>.perf.<lite|full>`).
 * Chỉ dựa vào RAM, số nhân và chế độ tiết kiệm pin (không đo FPS) — đủ để tránh đặt độ phân giải cao trên máy yếu.
 */
enum class EmuTier(val key: String) {
    LITE("lite"), MID("mid"), FULL("full");

    companion object {
        private const val GIB = 1024.0 * 1024 * 1024

        /** Hàm thuần để test. RAM tính bằng byte theo `MemoryInfo.totalMem` (máy "4 GB" báo khoảng 3,6–3,9 GiB). */
        fun classify(totalMemBytes: Long, cores: Int, lowRamDevice: Boolean, powerSave: Boolean): EmuTier {
            val gb = totalMemBytes / GIB
            val base = when {
                lowRamDevice || gb < 3.5 || cores < 6 -> LITE
                gb >= 7.0 && cores >= 8 -> FULL
                else -> MID
            }
            // Tiết kiệm pin: máy nhả xung CPU → hạ 1 bậc để khỏi giật.
            return if (powerSave) when (base) { FULL -> MID; else -> LITE } else base
        }

        /** [override]: "lite"/"mid"/"full" do người chơi chọn trong Cài đặt; khác đi = tự đoán. */
        fun detect(context: Context, override: String? = null): EmuTier {
            entries.firstOrNull { it.key == override }?.let { return it }
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val save = runCatching { context.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)
            return classify(mem.totalMem, Runtime.getRuntime().availableProcessors(), am.isLowRamDevice, save)
        }
    }
}
