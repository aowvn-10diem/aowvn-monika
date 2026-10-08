package vn.aow.monika.diag

import android.content.Context
import android.media.AudioManager
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Build
import android.os.Process
import vn.aow.monika.AppGraph
import vn.aow.monika.BuildConfig
import vn.aow.monika.ui.theme.PerformanceTier
import vn.aow.monika.ui.theme.detectTier
import java.io.File
import java.util.concurrent.TimeUnit

/** Chỉ dữ liệu kỹ thuật; không tên tài khoản/đường dẫn. GL đọc đúng luồng renderer, cache theo app. */
internal object GameEnvironment {
    private fun cache(c: Context) = c.getSharedPreferences("diag-gl-${BuildConfig.VERSION_CODE}", Context.MODE_PRIVATE)
    /**
     * V56: GLSurfaceView chạy sự kiện `queueEvent` TRƯỚC khi tạo EGL context. Gọi `glGetString` lúc chưa có context hiện hành
     * thì trên Android 17 (libGLES) deref con trỏ null → SIGSEGV `fault addr 0xdd0` (Test Lab K2, `GameEnvironment.cacheGl`).
     * Chỉ đọc GL khi luồng này đã có context; chưa có thì bỏ qua (lần chạy sau sẽ cache).
     */
    internal fun glContextCurrent(): Boolean = runCatching {
        val ctx: android.opengl.EGLContext? = EGL14.eglGetCurrentContext()
        ctx != null && ctx != EGL14.EGL_NO_CONTEXT
    }.getOrDefault(false)

    fun cacheGl(
        c: Context, source: String = "libretro",
        hasContext: () -> Boolean = { glContextCurrent() },
        read: (Int) -> String? = { GLES20.glGetString(it) },
    ) {
        if (!hasContext()) return
        val renderer = read(GLES20.GL_RENDERER).orEmpty()
        val version = read(GLES20.GL_VERSION).orEmpty()
        if (renderer.isNotBlank() && version.isNotBlank()) cache(c).edit()
            .putString("gl", Diagnostics.scrub(c, "cache($source): $renderer / $version").take(300)).apply()
    }
    fun capture(c: Context): String {
        val cfg = runCatching { AppGraph.config.current.configVersion.toString() }.getOrDefault("[CHƯA KIỂM]")
        val tier = runCatching { PerformanceTier.valueOf(AppGraph.prefs.motionMode) }.getOrElse { detectTier(c) }.name
        val packs = File(c.filesDir, "packs").listFiles().orEmpty().filter { it.isDirectory }.sortedBy { it.name }
            .mapNotNull { d -> File(d, "version").takeIf { it.isFile }?.let { "${d.name}=${it.readText().trim().take(80)}" } }.joinToString(",")
        return Diagnostics.scrub(c, "process64=${Process.is64Bit()} · arch=${System.getProperty("os.arch")} · ABIs=${Build.SUPPORTED_ABIS.joinToString(",")} · nativeBridge=${bridge()} · configVersion=$cfg · PerformanceTier=$tier · packs=[$packs]")
    }
    private fun bridge(): String = runCatching {
        val process = ProcessBuilder("getprop", "ro.dalvik.vm.native.bridge").start()
        try {
            if (!process.waitFor(300, TimeUnit.MILLISECONDS)) { process.destroyForcibly(); "[CHƯA KIỂM]" }
            else process.inputStream.bufferedReader().use { it.readText().trim().ifBlank { "[CHƯA KIỂM]" } }.take(80)
        } finally { process.destroy() }
    }.getOrDefault("[CHƯA KIỂM]")
    fun current(c: Context): String {
        val gl = cache(c).getString("gl", null) ?: "[CHƯA KIỂM]"
        val audio = runCatching {
            val a = c.getSystemService(AudioManager::class.java)
            "musicVolume=${a.getStreamVolume(AudioManager.STREAM_MUSIC)}/${a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)} · musicMuted=${a.isStreamMute(AudioManager.STREAM_MUSIC)} · audioOutputs=${a.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.distinct().joinToString(",")}" 
        }.getOrDefault("audio=[CHƯA KIỂM]")
        return Diagnostics.scrub(c, "GL=$gl · $audio")
    }
}
