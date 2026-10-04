package vn.aow.monika.diag

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.util.Base64
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Chỉ chụp khi người dùng chọn Báo lỗi; ảnh JPEG có ngân sách riêng theo SOL-009 A. */
internal object UserGameReport {
    const val MAX_BODY = 60_000
    const val MAX_IMAGE = 24 * 1024

    fun encode(source: Bitmap): Diagnostics.ReportImage? {
        var width = source.width; var height = source.height
        val scale = minOf(1.0, 480.0 / maxOf(width, height))
        width = maxOf(1, (width * scale).toInt()); height = maxOf(1, (height * scale).toInt())
        while (width >= 1 && height >= 1) {
            val small = Bitmap.createScaledBitmap(source, width, height, true)
            try {
                for (quality in listOf(85, 65, 45)) {
                    val bytes = ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
                    if (bytes.size <= MAX_IMAGE) return Diagnostics.ReportImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP), width, height)
                }
            } finally { if (small !== source) small.recycle() }
            if (maxOf(width, height) <= 32) return null
            width = maxOf(1, width * 3 / 4); height = maxOf(1, height * 3 / 4)
        }
        return null
    }

    /** Trước lưu/gửi: binary base64 được kiểm JPEG, không xử lý như chuỗi log. */
    fun valid(image: Diagnostics.ReportImage?): Diagnostics.ReportImage? = runCatching {
        image ?: return null
        require(image.mime == "image/jpeg" && image.width > 0 && image.height > 0 && maxOf(image.width, image.height) <= 480)
        require(image.data.length <= 32768 && Regex("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?").matches(image.data))
        val bytes = Base64.decode(image.data, Base64.NO_WRAP)
        require(bytes.size <= MAX_IMAGE && Base64.encodeToString(bytes, Base64.NO_WRAP) == image.data)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth == image.width && bounds.outHeight == image.height && bounds.outMimeType == "image/jpeg")
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        decoded.recycle(); image
    }.getOrNull()

    private fun surface(view: View): SurfaceView? = when (view) {
        is SurfaceView -> view.takeIf { it.holder.surface.isValid }
        is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { surface(view.getChildAt(it)) }
        else -> null
    }

    suspend fun capture(activity: Activity): Diagnostics.ReportImage? = withTimeoutOrNull(1500) {
        suspendCancellableCoroutine { continuation ->
            val view = surface(activity.window.decorView)
            val width = view?.width ?: activity.window.decorView.width
            val height = view?.height ?: activity.window.decorView.height
            if (width <= 0 || height <= 0) { continuation.resume(null); return@suspendCancellableCoroutine }
            val scale = minOf(1.0, 480.0 / maxOf(width, height))
            val bitmap = Bitmap.createBitmap(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()), Bitmap.Config.ARGB_8888)
            val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
                try {
                    if (continuation.isActive) continuation.resume(if (result == PixelCopy.SUCCESS) encode(bitmap) else null)
                } finally { bitmap.recycle() }
            }
            try {
                val handler = Handler(Looper.getMainLooper())
                if (view != null) PixelCopy.request(view, bitmap, listener, handler)
                else PixelCopy.request(activity.window, bitmap, listener, handler)
            } catch (_: Exception) { bitmap.recycle(); if (continuation.isActive) continuation.resume(null) }
        }
    }

    /** PID đang chơi, đúng cửa sổ 60 giây, timeout 700ms; không đụng PID của app chính. */
    fun recentLog(pid: Int): List<String> = runCatching {
        val start = System.currentTimeMillis() / 1000.0 - 60
        val process = ProcessBuilder("logcat", "-d", "-v", "epoch", "--pid", pid.toString(), "-t", "4000").redirectErrorStream(true).start()
        val lines = java.util.Collections.synchronizedList(mutableListOf<String>())
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().useLines { seq -> seq.forEach { line ->
                val time = line.trimStart().substringBefore(' ').toDoubleOrNull()
                if (time != null && time >= start) synchronized(lines) { lines.add(line.take(300)); if (lines.size > 250) lines.removeAt(0) }
            } } }
        }.apply { isDaemon = true; start() }
        try { if (!process.waitFor(500, TimeUnit.MILLISECONDS)) process.destroyForcibly(); reader.join(200) }
        finally { process.destroy(); runCatching { process.inputStream.close() } }
        synchronized(lines) { lines.toList() }
    }.getOrDefault(emptyList())

    fun bounded(input: Diagnostics.Report, encode: (Diagnostics.Report) -> String): Diagnostics.Report {
        if (input.kind != "user") return input
        var r = input.copy(title = input.title.take(300), app = input.app.take(80), device = input.device.take(300),
            reason = input.reason.take(300), detail = input.detail.take(2000), env = input.env.take(2000),
            component = input.component.take(160), fingerprint = input.fingerprint.take(128),
            crumbs = input.crumbs.takeLast(40).map { it.take(300) }, log = input.log.takeLast(250).map { it.take(300) },
            session = input.session?.let { it.copy(kind=it.kind.take(20), core=it.core.take(60), coreInfo=it.coreInfo.take(200), game=it.game.take(160), system=it.system.take(60), stage=it.stage.take(40)) })
        while (encode(r).toByteArray(Charsets.UTF_8).size > MAX_BODY) {
            r = when {
                r.log.isNotEmpty() -> r.copy(log = r.log.drop(1))
                r.crumbs.isNotEmpty() -> r.copy(crumbs = r.crumbs.drop(1))
                r.detail.isNotEmpty() -> r.copy(detail = r.detail.take(r.detail.length / 2))
                r.env.isNotEmpty() -> r.copy(env = r.env.take(r.env.length / 2))
                else -> error("Báo cáo vượt ngân sách")
            }
        }
        return r
    }
}
