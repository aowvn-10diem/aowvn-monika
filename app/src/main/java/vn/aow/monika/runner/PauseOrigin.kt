package vn.aow.monika.runner

import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.SystemClock

/**
 * Mốc `t0` của phép đo pause/destroy (#350/#318, `docs/loi/melondsds-anr-1313.md` §5.2).
 *
 * `Application.ActivityLifecycleCallbacks.onActivityPrePaused` (API 29+) được phát **trước** thân
 * `Activity.onPause()`, nên `t0→t1` mới thấy được phần thời gian tiêu **trước** `pause()` của view
 * (chờ `GLSurfaceView.onPause()`). API 26–28 không có callback này: không đăng ký, `t1` tự là gốc
 * và crumb ghi rõ "t0 không đo được".
 *
 * Chỉ đọc đồng hồ cho đúng activity được truyền vào; không đổi luồng vòng đời, không giữ Activity
 * sau khi [unregister].
 */
internal object PauseOrigin {
    /** Trả callback đã đăng ký (cần cho [unregister]), hoặc `null` khi máy không hỗ trợ. */
    fun register(owner: Activity, onMark: (label: String, at: Long) -> Unit): Application.ActivityLifecycleCallbacks? =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) null else createQ(owner, onMark)

    /** Tách riêng để `onActivityPrePaused` (API 29) không bị lint coi là gọi API mới ở máy cũ. */
    @TargetApi(Build.VERSION_CODES.Q)
    private fun createQ(owner: Activity, onMark: (label: String, at: Long) -> Unit): Application.ActivityLifecycleCallbacks? {
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPrePaused(activity: Activity) {
                if (activity !== owner) return
                runCatching { onMark("t0", SystemClock.elapsedRealtime()) }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        return runCatching {
            owner.application.registerActivityLifecycleCallbacks(callbacks)
            callbacks
        }.getOrNull()
    }

    fun unregister(owner: Activity, callbacks: Application.ActivityLifecycleCallbacks?) {
        if (callbacks == null) return
        runCatching { owner.application.unregisterActivityLifecycleCallbacks(callbacks) }
    }
}
