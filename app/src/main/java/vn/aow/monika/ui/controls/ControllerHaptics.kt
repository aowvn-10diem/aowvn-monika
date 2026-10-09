package vn.aow.monika.ui.controls

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View

/** Một lần lúc nhấn, không lặp lúc giữ/nhả. Không vượt cài đặt rung chạm của Android. */
internal fun controllerHaptic(view: View, level: HapticLevel) {
    if (level == HapticLevel.OFF || !view.isHapticFeedbackEnabled ||
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
    @Suppress("DEPRECATION")
    val vibrator = view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    val vibrated = runCatching {
        if (vibrator?.hasVibrator() == true && vibrator.hasAmplitudeControl()) {
            vibrator.vibrate(VibrationEffect.createOneShot(level.durationMillis, level.amplitude))
            true
        } else false
    }.getOrDefault(false)
    if (!vibrated) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}
