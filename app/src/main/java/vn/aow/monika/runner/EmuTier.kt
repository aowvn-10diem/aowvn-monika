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

        /**
         * Mức theo **phần cứng** (RAM, số nhân, low-RAM), không xét tiết kiệm pin.
         * Hàm thuần để test. RAM tính bằng byte theo `MemoryInfo.totalMem` (máy "4 GB" báo khoảng 3,6–3,9 GiB).
         */
        fun classifyHardware(totalMemBytes: Long, cores: Int, lowRamDevice: Boolean): EmuTier {
            val gb = totalMemBytes / GIB
            return when {
                lowRamDevice || gb < 3.5 || cores < 6 -> LITE
                gb >= 7.0 && cores >= 8 -> FULL
                else -> MID
            }
        }

        /** Mức hiệu năng để chọn bộ tùy chọn lõi: tiết kiệm pin thì máy nhả xung CPU → hạ 1 bậc để khỏi giật. */
        fun classify(totalMemBytes: Long, cores: Int, lowRamDevice: Boolean, powerSave: Boolean): EmuTier {
            val base = classifyHardware(totalMemBytes, cores, lowRamDevice)
            return if (powerSave) when (base) { FULL -> MID; else -> LITE } else base
        }

        /** [override]: "lite"/"mid"/"full" do người chơi chọn trong Cài đặt; khác đi = tự đoán. */
        fun detect(context: Context, override: String? = null): EmuTier {
            entries.firstOrNull { it.key == override }?.let { return it }
            val save = runCatching { context.getSystemService(PowerManager::class.java).isPowerSaveMode }.getOrDefault(false)
            return measure(context) { mem, cores, lowRam -> classify(mem, cores, lowRam, save) }
        }

        /**
         * Như [detect] nhưng **bỏ qua tiết kiệm pin** — dùng cho quyết định theo *danh tính máy*
         * (vd. `System/is_new_3ds` của Azahar): bật tiết kiệm pin chỉ giảm xung CPU, không biến
         * máy New 3DS thành máy 3DS thường. Lựa chọn của người chơi ([override]) vẫn thắng.
         */
        fun detectHardware(context: Context, override: String? = null): EmuTier {
            entries.firstOrNull { it.key == override }?.let { return it }
            return measure(context) { mem, cores, lowRam -> classifyHardware(mem, cores, lowRam) }
        }

        private inline fun measure(context: Context, f: (Long, Int, Boolean) -> EmuTier): EmuTier {
            val am = context.getSystemService(ActivityManager::class.java)
            val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            return f(mem.totalMem, Runtime.getRuntime().availableProcessors(), am.isLowRamDevice)
        }
    }
}
