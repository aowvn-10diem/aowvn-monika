// Dựa trên SoftwareKeyboard.kt của Azahar Emulator Project (GPLv2 hoặc mới hơn).
// Aow Monika: hộp nhập chữ do Monika vẽ ([AzaharBridge.keyboard]); native gọi hàm này ở luồng giả lập và chờ kết quả.
package org.citra.citra_emu.applets

import androidx.annotation.Keep
import java.io.Serializable
import vn.aow.monika.azahar.AzaharBridge

@Keep
object SoftwareKeyboard {
    @JvmStatic
    fun execute(config: KeyboardConfig): KeyboardData {
        if (config.buttonConfig == ButtonConfig.NONE) return KeyboardData(0, "")
        return AzaharBridge.keyboard(config)
    }

    @Suppress("unused")
    @JvmStatic
    fun showError(error: String) {
        AzaharBridge.alert("Bàn phím", error, false)
    }

    @Suppress("FunctionName")
    external fun ValidateInput(text: String): ValidationError

    // / Tương ứng Frontend::ButtonConfig
    interface ButtonConfig {
        companion object {
            const val SINGLE = 0 // / Ok
            const val DUAL = 1 // / Cancel | Ok
            const val TRIPLE = 2 // / Cancel | I Forgot | Ok
            const val NONE = 3
        }
    }

    // / Tương ứng Frontend::ValidationError (native tra theo TÊN)
    enum class ValidationError {
        None, ButtonOutOfRange, MaxDigitsExceeded, AtSignNotAllowed, PercentNotAllowed, BackslashNotAllowed,
        ProfanityNotAllowed, CallbackFailed, FixedLengthRequired, MaxLengthExceeded, BlankInputNotAllowed, EmptyInputNotAllowed
    }

    @Keep
    class KeyboardConfig : Serializable {
        var buttonConfig = 0
        var maxTextLength = 0
        var multilineMode = false
        var hintText: String? = null
        lateinit var buttonText: Array<String>
    }

    // / Tương ứng Frontend::KeyboardData
    class KeyboardData(var button: Int, var text: String)
}
