// Dựa trên CiaInstallWorker.kt của Azahar Emulator Project (GPLv2 hoặc mới hơn).
// Aow Monika: bỏ WorkManager/thông báo của Azahar; tiến độ chuyển cho [AzaharBridge.installProgress].
// Mã native gọi installCIA trên đối tượng này và gọi ngược setProgressCallback(II)V.
package org.citra.citra_emu.utils

import androidx.annotation.Keep
import org.citra.citra_emu.NativeLibrary
import vn.aow.monika.azahar.AzaharBridge

@Keep
class CiaInstallWorker {
    @Keep
    fun setProgressCallback(max: Int, progress: Int) = AzaharBridge.installProgress(max, progress)

    /** Đường dẫn bắt đầu bằng "!" = đường dẫn tệp thật (không phải URI). */
    external fun installCIA(path: String): NativeLibrary.InstallStatus
}
