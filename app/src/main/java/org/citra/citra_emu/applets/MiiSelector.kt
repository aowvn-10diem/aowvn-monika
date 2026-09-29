// Dựa trên MiiSelector.kt của Azahar Emulator Project (GPLv2 hoặc mới hơn).
// Aow Monika: hộp chọn Mii do Monika vẽ ([AzaharBridge.miiSelect]).
package org.citra.citra_emu.applets

import androidx.annotation.Keep
import java.io.Serializable
import vn.aow.monika.azahar.AzaharBridge

@Keep
object MiiSelector {
    @JvmStatic
    fun execute(config: MiiSelectorConfig): MiiSelectorData = AzaharBridge.miiSelect(config)

    @Keep
    class MiiSelectorConfig : Serializable {
        var enableCancelButton = false
        var title: String? = null
        var initiallySelectedMiiIndex: Long = 0
        lateinit var miiNames: Array<String>
    }

    class MiiSelectorData(var returnCode: Long, var index: Int)
}
