// Dựa trên DiskShaderCacheProgress.kt của Azahar Emulator Project (GPLv2 hoặc mới hơn).
// Aow Monika: tiến độ nạp shader chuyển cho giao diện Monika thay vì ViewModel của Azahar.
package org.citra.citra_emu.utils

import androidx.annotation.Keep
import vn.aow.monika.azahar.AzaharBridge

@Keep
object DiskShaderCacheProgress {
    @JvmStatic
    fun loadProgress(stage: LoadCallbackStage, progress: Int, max: Int, obj: String) =
        AzaharBridge.shaderProgress(stage, progress, max, obj)

    // Tương ứng VideoCore::LoadCallbackStage
    enum class LoadCallbackStage { Prepare, Decompile, Build, Complete }
}
