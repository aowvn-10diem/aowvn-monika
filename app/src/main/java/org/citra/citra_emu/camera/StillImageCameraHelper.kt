// Dựa trên StillImageCameraHelper.kt của Azahar Emulator Project (GPLv2 hoặc mới hơn).
// Aow Monika: chưa hỗ trợ camera giả (chọn ảnh) — trả về null để native dùng hình trống.
package org.citra.citra_emu.camera

import android.graphics.Bitmap
import androidx.annotation.Keep

object StillImageCameraHelper {
    @Suppress("unused")
    @Keep
    @JvmStatic
    fun openFilePicker(): String? = null

    @Suppress("unused")
    @Keep
    @JvmStatic
    fun loadImageFromFile(uri: String?, width: Int, height: Int): Bitmap? = null
}
