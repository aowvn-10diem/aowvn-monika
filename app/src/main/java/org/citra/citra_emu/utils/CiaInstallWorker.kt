// Dựa trên CiaInstallWorker.kt của Azahar Emulator Project (GPLv2 hoặc mới hơn).
// Aow Monika: chỉ giữ hàm mà mã native tra cứu lúc nạp thư viện; cài .cia chưa hỗ trợ.
package org.citra.citra_emu.utils

import androidx.annotation.Keep

@Keep
class CiaInstallWorker {
    @Keep
    fun setProgressCallback(max: Int, progress: Int) {}
}
