package vn.aow.monika.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vn.aow.monika.BuildConfig
import java.io.File

/**
 * Bắt lỗi khi chơi game — để sửa lõi / engine về sau.
 *
 * Vì sao cần: lõi giả lập là mã native (C/C++) do bên thứ ba viết; khi nó chết (SIGSEGV/SIGABRT) Android giết cả tiến trình
 * và KHÔNG có ngoại lệ Java nào để bắt. Cách làm:
 *  1. Game chạy ở tiến trình riêng ":game" → lõi chết không kéo sập Aow Monika.
 *  2. Trong lúc chơi ghi 1 file "phiên" (lõi + phiên bản lõi + game + giai đoạn + nhịp sống).
 *  3. Lần mở Monika kế tiếp: thấy phiên còn dang dở = game đã chết bất thường → hỏi Android lý do chết
 *     ([ApplicationExitInfo]: CRASH_NATIVE / ANR / LOW_MEMORY…, kèm tombstone), lấy log của tiến trình đã chết
 *     (logcat, gồm dòng "Fatal signal" của libc và log của lõi) → lưu thành báo cáo, hỏi người chơi gửi.
 *  4. Lỗi Java (kể cả ở tiến trình game) do [vn.aow.monika.CrashReporter] ghi vào cùng kho báo cáo.
 *
 * Báo cáo KHÔNG chứa đường dẫn file, email hay tên tài khoản; chỉ tên game, hệ máy, lõi, máy.
 */
object Diagnostics {

    @Serializable
    data class Session(
        val pid: Int,
        val startedAt: Long,
        /** "libretro" | "web" | "j2me"… */
        val kind: String,
        val core: String = "",
        /** Ghi chú phiên bản lõi (ngày tải, kích thước, Last-Modified) — lõi nightly đổi liên tục nên rất cần khi dò lỗi. */
        val coreInfo: String = "",
        val game: String = "",
        val system: String = "",
        /** "start" → "core-ready" → "view-created" → "first-frame" → … */
        val stage: String = "start",
        val stageAt: Long = 0,
        val lastAlive: Long = 0,
    )

    @Serializable
    data class Report(
        val id: Long,
        val time: Long,
        /** native | java | anr | lowmem | killed | unknown */
        val kind: String,
        val title: String,
        val app: String,
        val device: String,
        val session: Session? = null,
        val reason: String = "",
        val detail: String = "",
        val log: List<String> = emptyList(),
        val fromGame: Boolean = false,
        val seen: Boolean = false,
        val sent: Boolean = false,
        /** Thành phần gây lỗi: "pack:sevenzip", "engine:libretro", "engine:onsyuri", "app:ui"… (suy từ stack + vệt sự kiện). */
        val component: String = "",
        /** Dấu vân tay lỗi: cùng lỗi lặp lại thì gộp, tăng [count] thay vì đẻ thêm báo cáo. */
        val fingerprint: String = "",
        val count: Int = 1,
        /** Ảnh chụp máy lúc lỗi: RAM, heap, đĩa trống, mạng, nguồn. */
        val env: String = "",
        /** Vệt sự kiện ngay trước lỗi (mới nhất cuối). */
        val crumbs: List<String> = emptyList(),
    ) {
        fun toText(): String = scrub(null, buildString {
            appendLine("== Báo lỗi Aow Monika ==")
            appendLine(title)
            if (component.isNotBlank()) appendLine("Thành phần: $component${if (count > 1) " · lặp $count lần" else ""}")
            appendLine("Thời điểm: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US).format(time)}")
            appendLine("Bản app: $app")
            appendLine("Máy: $device")
            session?.let {
                appendLine("Loại: ${it.kind} · Hệ: ${it.system} · Lõi: ${it.core}")
                if (it.coreInfo.isNotBlank()) appendLine("Lõi tải: ${it.coreInfo}")
                appendLine("Game: ${it.game}")
                val played = (it.lastAlive - it.startedAt).coerceAtLeast(0) / 1000
                appendLine("Giai đoạn cuối: ${it.stage} · sống được ${played}s")
            }
            if (env.isNotBlank()) appendLine("Tình trạng máy: $env")
            if (reason.isNotBlank()) appendLine("Lý do (Android): $reason")
            if (detail.isNotBlank()) { appendLine(); appendLine(detail) }
            if (crumbs.isNotEmpty()) { appendLine(); appendLine("-- vệt sự kiện trước lỗi --"); crumbs.forEach(::appendLine) }
            if (log.isNotEmpty()) { appendLine(); appendLine("-- log --"); log.forEach(::appendLine) }
        })
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Báo cáo game chết chưa được người chơi xem → hộp thoại ở màn chính. */
    val pending = MutableStateFlow<Report?>(null)
    /** Đang mở màn "Nhật ký lỗi". */
    val logOpen = MutableStateFlow(false)
    /** Đổi khi kho báo cáo thay đổi. */
    val tick = MutableStateFlow(0)

    private fun dir(c: Context) = File(c.filesDir, "diag").apply { mkdirs() }
    private fun sessionFile(c: Context) = File(dir(c), "active-session.json")
    private fun reportsDir(c: Context) = File(dir(c), "reports").apply { mkdirs() }

    // ---------- Phiên chơi (chạy trong tiến trình game) ----------

    fun begin(c: Context, kind: String, core: String, coreInfo: String, game: String, system: String) {
        val now = System.currentTimeMillis()
        // Một lần mỗi phiên; ghi qua scrub, không thay định dạng Session cũ.
        runCatching { File(dir(c), "session-env.txt").writeText(GameEnvironment.capture(c)) }
        Breadcrumbs.add(c, "begin", "$kind · $core · $system")
        write(c, Session(Process.myPid(), now, kind, core, coreInfo, game, system, "start", now, now))
    }

    /** Cập nhật ghi chú bản lõi sau khi tải xong. */
    fun coreInfo(c: Context, info: String) {
        val s = read(c) ?: return
        write(c, s.copy(coreInfo = info))
    }

    fun stage(c: Context, stage: String) {
        val s = read(c) ?: return
        val now = System.currentTimeMillis()
        write(c, s.copy(stage = stage, stageAt = now, lastAlive = now))
        Breadcrumbs.add(c, "stage", stage)
    }

    /** Ghi 1 sự kiện vào vệt (gọn): Diagnostics.crumb(ctx, "pack", "tải sevenzip 45%"). */
    fun crumb(c: Context, tag: String, msg: String) = Breadcrumbs.add(c, tag, msg)

    /** Nhịp sống: biết game còn chạy tới lúc nào (Android cũ không cho hỏi lý do chết). */
    fun heartbeat(c: Context) {
        val s = read(c) ?: return
        write(c, s.copy(lastAlive = System.currentTimeMillis()))
    }

    /** Thoát bình thường → xóa phiên, không tạo báo cáo. */
    fun end(c: Context) { runCatching { sessionFile(c).delete(); File(dir(c), "session-env.txt").delete() } }

    private fun read(c: Context): Session? =
        runCatching { json.decodeFromString(Session.serializer(), sessionFile(c).readText()) }.getOrNull()

    private fun write(c: Context, s: Session) {
        runCatching {
            val tmp = File(dir(c), "active-session.tmp")
            tmp.writeText(scrubJson(c, json.encodeToJsonElement(Session.serializer(), s)).toString())
            tmp.renameTo(sessionFile(c))
        }
    }

    // ---------- Thu báo cáo (chạy trong tiến trình chính) ----------

    /**
     * Gọi lúc mở app (luồng nền). Nếu phiên trước chết bất thường → tạo báo cáo và đặt [pending].
     * Trả về báo cáo mới (nếu có).
     */
    fun collect(c: Context): Report? {
        val s = read(c) ?: return null
        if (s.pid == Process.myPid()) return null
        // Tiến trình game còn sống (mở lại Monika trong lúc đang chơi) → chưa phải chết.
        if (isAlive(c, s.pid)) return null
        sessionFile(c).delete()

        val exit = exitInfo(c, s.pid)
        val reason = exit?.let { describeReason(it) }.orEmpty()
        val kind = when (exit?.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
            ApplicationExitInfo.REASON_CRASH -> "java"
            ApplicationExitInfo.REASON_ANR -> "anr"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "lowmem"
            ApplicationExitInfo.REASON_USER_REQUESTED, ApplicationExitInfo.REASON_USER_STOPPED -> return null // người dùng vuốt tắt
            ApplicationExitInfo.REASON_EXIT_SELF -> return null
            null -> if (s.stage == "first-frame" || s.stage == "playing") "unknown" else "unknown"
            else -> "killed"
        }
        val trace = exit?.let { traceStrings(it) }.orEmpty()
        val whenDied = exit?.timestamp ?: s.lastAlive.takeIf { it > 0 } ?: System.currentTimeMillis()
        val log = logcat(s.pid, nativeDeathAt = whenDied.takeIf { kind == "native" })
        val detail = scrub(c, trace.joinToString("\n"))
        val crumbs = Breadcrumbs.read(c, s.pid)
        val r = record(c, Report(
            id = whenDied, time = whenDied, kind = kind,
            title = title(kind, s), app = appLine(), device = deviceLine(),
            session = s, reason = reason,
            detail = detail, log = log.map { scrub(c, it) }, fromGame = true,
            component = Components.of(c, s, detail + "\n" + crumbs.joinToString("\n")),
            env = envLine(c), crumbs = crumbs,
        ))
        Breadcrumbs.drop(c, s.pid)
        pending.value = r
        return r
    }

    private fun title(kind: String, s: Session): String {
        val what = "${s.game.ifBlank { "game" }} (${s.system.ifBlank { s.kind }}, lõi ${s.core.ifBlank { "?" }})"
        val phase = if (s.stage == "first-frame" || s.stage == "playing") "khi đang chơi" else "lúc đang khởi động"
        return when (kind) {
            "native" -> "Lõi giả lập bị sập $phase — $what"
            "java" -> "Lỗi chương trình $phase — $what"
            "anr" -> "Game bị treo (không phản hồi) $phase — $what"
            "lowmem" -> "Máy hết bộ nhớ, Android tắt game $phase — $what"
            "killed" -> "Game bị hệ thống tắt $phase — $what"
            else -> "Game dừng đột ngột $phase — $what"
        }
    }

    private fun isAlive(c: Context, pid: Int): Boolean =
        runCatching { c.getSystemService(ActivityManager::class.java).runningAppProcesses?.any { it.pid == pid } == true }.getOrDefault(false)

    private fun exitInfo(c: Context, pid: Int): ApplicationExitInfo? {
        if (Build.VERSION.SDK_INT < 30) return null
        return runCatching {
            c.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(c.packageName, pid, 1).firstOrNull()
        }.getOrNull()
    }

    private fun describeReason(e: ApplicationExitInfo): String {
        val name = when (e.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            else -> "code ${e.reason}"
        }
        return "$name (status ${e.status}) ${e.description.orEmpty()}".trim()
    }

    /** Tombstone của Android (protobuf) — không parse đầy đủ, chỉ nhặt các chuỗi đọc được: tên .so, hàm, tín hiệu. */
    private fun traceStrings(e: ApplicationExitInfo): List<String> {
        if (e.reason != ApplicationExitInfo.REASON_CRASH_NATIVE && e.reason != ApplicationExitInfo.REASON_ANR) return emptyList()
        return runCatching {
            val bytes = e.traceInputStream?.use { it.readNBytesCompat(400_000) } ?: return emptyList()
            // Android 12+: tombstone protobuf → đọc đúng trường (tín hiệu, luồng gây lỗi, ngăn xếp có build_id); không được thì nhặt chuỗi như trước.
            if (e.reason == ApplicationExitInfo.REASON_CRASH_NATIVE) TombstoneParser.parse(bytes)?.let { return TombstoneParser.toLines(it) }
            val out = LinkedHashSet<String>()
            val run = StringBuilder()
            fun flush() {
                if (run.length >= 6) {
                    val s = run.toString()
                    if (INTERESTING.containsMatchIn(s)) out += s.take(200)
                }
                run.setLength(0)
            }
            for (b in bytes) {
                val ch = b.toInt() and 0xff
                if (ch in 32..126) run.append(ch.toChar()) else flush()
            }
            flush()
            out.take(80).toList()
        }.getOrDefault(emptyList())
    }

    private val INTERESTING = Regex("""\.so|libretro|SIG[A-Z]+|signal|abort|Abort|assert|backtrace|::|Fatal|fault|SEGV|libc|libGLES|libEGL|vulkan""")

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val tmp = ByteArray(8192)
        while (buf.size() < max) {
            val n = read(tmp)
            if (n < 0) break
            buf.write(tmp, 0, n)
        }
        return buf.toByteArray()
    }

    /** Log của tiến trình [pid] (kể cả đã chết, còn trong bộ đệm logcat của máy): gồm log lõi + dòng "Fatal signal" của libc. */
    fun logcat(pid: Int, max: Int = 220, nativeDeathAt: Long? = null): List<String> = runCatching {
        // API 30: crash_dump ghi DEBUG bằng PID khác; epoch tránh đoán năm/múi giờ khi lọc ±5 giây.
        val debugAt = nativeDeathAt?.takeIf { Build.VERSION.SDK_INT == 30 }
        val p = ProcessBuilder("logcat", "-d", "-v", if (debugAt != null) "epoch" else "threadtime", "-t", "4000")
            .redirectErrorStream(true).start()
        val re = Regex("""^\S+\s+\S+\s+$pid\s""")
        val lines = p.inputStream.bufferedReader().useLines { seq ->
            if (debugAt != null) NativeCrashLogcat.select(seq, pid, debugAt, max)
            else seq.filter { re.containsMatchIn(it) }.toList()
        }
        runCatching { p.destroy() }
        lines.takeLast(max).map { it.take(300) }
    }.getOrDefault(emptyList())

    // ---------- Lỗi Java (ghi từ CrashReporter, ở bất kỳ tiến trình nào) ----------

    fun recordJavaCrash(c: Context, threadName: String, e: Throwable): Report {
        val s = read(c)?.takeIf { it.pid == Process.myPid() }
        val sw = java.io.StringWriter()
        e.printStackTrace(java.io.PrintWriter(sw))
        val stack = scrub(c, sw.toString())
        val now = System.currentTimeMillis()
        val comp = Components.of(c, s, stack)
        Breadcrumbs.add(c, "crash", "${e.javaClass.simpleName}: ${e.message.orEmpty().take(120)} [luồng $threadName]")
        val r = record(c, Report(
            id = now, time = now, kind = "java",
            title = if (s != null) title("java", s) else "Aow Monika gặp lỗi: ${e.javaClass.simpleName}${if (comp.isNotBlank()) " ($comp)" else ""}",
            app = appLine(), device = deviceLine(), session = s?.copy(lastAlive = now),
            reason = "Luồng $threadName", detail = stack.take(12_000),
            log = logcat(Process.myPid(), 80).map { scrub(c, it) }, fromGame = s != null,
            component = comp, env = envLine(c), crumbs = Breadcrumbs.read(c, Process.myPid()),
        ))
        if (s != null) end(c) // đã ghi báo cáo → lần sau đừng tạo thêm báo cáo "chết bất thường" cho cùng phiên
        return r
    }

    /**
     * Lỗi đã bắt được (không làm app chết) nhưng đáng ghi: tải/giải nén gói hỏng, engine không khởi động…
     * [component] dạng "pack:sevenzip" / "engine:onsyuri". Hiện trong Nhật ký lỗi, KHÔNG bật hộp thoại.
     */
    fun recordHandled(c: Context, component: String, what: String, e: Throwable? = null): Report {
        val now = System.currentTimeMillis()
        val stack = e?.let { val sw = java.io.StringWriter(); it.printStackTrace(java.io.PrintWriter(sw)); scrub(c, sw.toString()).take(8_000) }.orEmpty()
        Breadcrumbs.add(c, "error", "$component: $what")
        val s = read(c)?.takeIf { it.pid == Process.myPid() }
        return record(c, Report(
            id = now, time = now, kind = "handled",
            title = "Lỗi $component: $what", app = appLine(), device = deviceLine(), session = s,
            reason = what, detail = stack, log = emptyList(), fromGame = false,
            component = component, env = envLine(c), crumbs = Breadcrumbs.read(c, Process.myPid()),
        ))
    }

    /**
     * Lõi báo lỗi khi nạp game / không lên hình (tiến trình KHÔNG chết nên [collect] không thấy) → lập báo cáo từ phiên đang chạy.
     * Trả về báo cáo đã lưu.
     */
    fun recordCoreFailure(c: Context, what: String, detail: String): Report {
        val now = System.currentTimeMillis()
        val s = read(c)?.takeIf { it.pid == Process.myPid() }?.copy(lastAlive = now)
        val r = Report(
            id = now, time = now, kind = "core-error",
            title = "Lõi không chạy được game: $what — ${s?.game.orEmpty().ifBlank { "game" }} (${s?.system.orEmpty()}, lõi ${s?.core.orEmpty().ifBlank { "?" }})",
            app = appLine(), device = deviceLine(), session = s, reason = what, detail = scrub(c, detail),
            log = logcat(Process.myPid(), 120).map { scrub(c, it) }, fromGame = true,
            component = if (what.startsWith("pack:")) what else Components.of(c, s, detail),
            env = envLine(c), crumbs = Breadcrumbs.read(c, Process.myPid()),
        )
        return record(c, r)
    }

    /** Gửi ngầm (không clipboard) nếu người chơi không tắt và config `crash.autoSend` bật. Chạy ở luồng nền. */
    fun autoSend(c: Context, http: OkHttpClient, r: Report, endpoint: String, enabledByConfig: Boolean, enabledByUser: Boolean) {
        if (!enabledByConfig || !enabledByUser || endpoint.isBlank() || r.sent) return
        Thread {
            val ok = runCatching {
                http.newCall(Request.Builder().url(endpoint).post(reportJson(c, r).toRequestBody("application/json".toMediaType())).build())
                    .execute().use { it.isSuccessful }
            }.getOrDefault(false)
            if (ok) save(c, r.copy(sent = true))
        }.start()
    }

    // ---------- Gộp trùng, che thông tin riêng, chụp tình trạng máy ----------

    /** Lưu báo cáo mới; nếu cùng dấu vân tay với báo cáo trước (trong 30 báo cáo gần nhất) thì gộp: tăng đếm, giữ bản mới nhất. */
    private fun record(c: Context, r0: Report): Report {
        val safe = sanitized(c, r0)
        val fp = fingerprint(safe)
        val prev = list(c).firstOrNull { it.fingerprint == fp }
        val r = safe.copy(fingerprint = fp, count = (prev?.count ?: 0) + 1, sent = false, seen = prev?.seen == true && prev.count >= 3)
        prev?.let { File(reportsDir(c), "${it.id}.json").delete() }
        save(c, r)
        return r
    }

    /** Cùng loại + cùng thành phần + cùng 3 dòng stack đầu (bỏ số dòng/địa chỉ) → cùng lỗi. */
    internal fun fingerprint(r: Report): String {
        val frames = r.detail.lineSequence().map { it.trim() }
            .filter { it.startsWith("at ") || it.contains(".so") || it.contains("Exception") || it.contains("Error") }
            .map { it.replace(Regex("""\(.*?\)|0x[0-9a-fA-F]+|#\d+|\d+"""), "") }
            .take(3).joinToString("|")
        val key = "${r.kind}|${r.component}|${r.session?.core.orEmpty()}|${r.reason.take(60).replace(Regex("""\d+"""), "")}|$frames"
        return Integer.toHexString(key.hashCode())
    }

    private val PRIVATE_PATH = Regex("""(?i)(?:content|file)://[^\r\n"'<>]+|/(?:data/user/\d+|data/data|storage/emulated/\d+|sdcard)(?:/[^\r\n"'<>]*)?""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+(?:\.[\w-]+)+""")
    private val AUTH = Regex("""(?i)(authorization["']?\s*[:=]\s*["']?)(?:Bearer|Basic)\s+[^\s"',}]+""")
    private val SECRET = Regex("""(?i)(\b(?:token|key|password|passwd|authorization|secret|y))(["']?\s*[:=]\s*)(?:"[^"\r\n]*(?:"|(?=[\r\n])|$)|'[^'\r\n]*(?:'|(?=[\r\n])|$)|[^\s&"',}]+)""")

    /** Che trước khi cắt ngắn: đường dẫn có dấu cách/tiếng Việt và URI phải ẩn cả tên file. */
    internal fun scrub(c: Context?, text: String): String = text
        .replace(PRIVATE_PATH, "<đường-dẫn>")
        .replace(AUTH) { it.groupValues[1] + "<ẩn>" }
        .replace(SECRET) { it.groupValues[1] + it.groupValues[2] + "<ẩn>" }
        .replace(EMAIL, "<email>")

    /** Duyệt mọi chuỗi trong JSON, kể cả trường lồng nhau và trường mới thêm về sau. */
    private fun scrubJson(c: Context?, value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { scrubJson(c, it.value) })
        is JsonArray -> JsonArray(value.map { scrubJson(c, it) })
        is JsonPrimitive -> if (value.isString) JsonPrimitive(scrub(c, value.content)) else value
    }

    internal fun sanitized(c: Context?, r: Report): Report = json.decodeFromJsonElement(
        Report.serializer(), scrubJson(c, json.encodeToJsonElement(Report.serializer(), r))
    )

    internal fun reportJson(c: Context?, r: Report): String =
        scrubJson(c, json.encodeToJsonElement(Report.serializer(), r)).toString()

    /** RAM trống, heap, đĩa trống, mạng, pin — thường là nguyên nhân thật của lỗi tải/giải nén/hết bộ nhớ. */
    internal fun envLine(c: Context): String = runCatching {
        val mi = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val rt = Runtime.getRuntime()
        val mb = 1024L * 1024
        val disk = c.filesDir.usableSpace / mb
        val net = runCatching {
            val cm = c.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> "không mạng"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "4G/5G"
                else -> "mạng khác"
            }
        }.getOrDefault("?")
        "RAM trống ${mi.availMem / mb}/${mi.totalMem / mb} MB${if (mi.lowMemory) " (THẤP)" else ""} · heap ${(rt.totalMemory() - rt.freeMemory()) / mb}/${rt.maxMemory() / mb} MB · đĩa trống $disk MB · mạng $net · ${runCatching { File(dir(c), "session-env.txt").readText() }.getOrDefault("[CHƯA KIỂM]")} · ${GameEnvironment.current(c)}"
    }.getOrDefault("")

    /**
     * Quét lý do chết của CHÍNH tiến trình chính (và ":midlet"/":crash") do Android ghi lại: native crash, ANR, hết RAM…
     * — những lần chết không có ngoại lệ Java để CrashReporter bắt. Gọi lúc mở app; chỉ tạo báo cáo cho lần chết mới.
     */
    fun collectProcessDeaths(c: Context): List<Report> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        val stamp = File(dir(c), "last-scan.txt")
        val last = runCatching { stamp.readText().trim().toLong() }.getOrDefault(0L)
        val exits = runCatching { c.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(c.packageName, 0, 12) }.getOrDefault(emptyList())
        val out = ArrayList<Report>()
        var newest = last
        for (e in exits) {
            if (e.timestamp <= last) continue
            newest = maxOf(newest, e.timestamp)
            val kind = when (e.reason) {
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
                ApplicationExitInfo.REASON_ANR -> "anr"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "lowmem"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initfail"
                ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource"
                else -> continue // CRASH (Java) đã có CrashReporter; thoát bình thường / người dùng vuốt tắt thì bỏ qua
            }
            // Tiến trình game đã được [collect] xử lý (có phiên) → tránh báo hai lần.
            if (e.processName?.endsWith(":game") == true && File(reportsDir(c), "${e.timestamp}.json").exists()) continue
            val trace = scrub(c, traceStrings(e).joinToString("\n"))
            val crumbs = Breadcrumbs.read(c, e.pid)
            val proc = e.processName.orEmpty().substringAfter(':', "chính")
            val comp = Components.of(c, null, trace + "\n" + crumbs.joinToString("\n")).ifBlank { "app:$proc" }
            out += record(c, Report(
                id = e.timestamp, time = e.timestamp, kind = kind,
                title = when (kind) {
                    "native" -> "Mã native bị sập (tiến trình $proc) — $comp"
                    "anr" -> "App bị treo, không phản hồi (tiến trình $proc) — $comp"
                    "lowmem" -> "Máy hết bộ nhớ, Android tắt app (tiến trình $proc)"
                    else -> "App bị tắt: ${describeReason(e)} (tiến trình $proc)"
                },
                app = appLine(), device = deviceLine(), reason = describeReason(e), detail = trace,
                log = if (kind == "native" && Build.VERSION.SDK_INT == 30)
                    logcat(e.pid, nativeDeathAt = e.timestamp).map { scrub(c, it) } else emptyList(),
                component = comp, env = envLine(c), crumbs = crumbs,
            ))
            Breadcrumbs.drop(c, e.pid)
        }
        runCatching { stamp.writeText(newest.toString()) }
        return out
    }

    // ---------- Kho báo cáo ----------

    fun save(c: Context, r: Report) {
        runCatching {
            File(reportsDir(c), "${r.id}.json").writeText(reportJson(c, r))
            // Giữ 30 báo cáo mới nhất.
            reportsDir(c).listFiles()?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }
        }
        tick.value++
    }

    fun list(c: Context): List<Report> = reportsDir(c).listFiles().orEmpty()
        .sortedByDescending { it.name }
        .mapNotNull { f -> runCatching { sanitized(c, json.decodeFromString(Report.serializer(), f.readText())) }.getOrNull() }

    fun markSeen(c: Context, r: Report) { save(c, r.copy(seen = true)); if (pending.value?.id == r.id) pending.value = null }

    fun delete(c: Context, r: Report) { File(reportsDir(c), "${r.id}.json").delete(); tick.value++ }

    fun clear(c: Context) { reportsDir(c).deleteRecursively(); tick.value++ }

    /**
     * Gửi báo cáo: có địa chỉ nhận ([endpoint], cấu hình `crash.endpoint`) thì gửi thẳng (JSON, ẩn danh);
     * chưa có thì chép nội dung vào clipboard để dán vào nhóm / Discord của AowVN.
     * Trả true nếu đã gửi lên máy chủ.
     */
    fun send(c: Context, http: OkHttpClient, r: Report, endpoint: String): Boolean {
        val text = r.toText()
        if (endpoint.isBlank()) {
            c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Báo lỗi Aow Monika", text))
            return false
        }
        val ok = runCatching {
            http.newCall(Request.Builder().url(endpoint).post(reportJson(c, r).toRequestBody("application/json".toMediaType())).build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
        if (ok) save(c, r.copy(sent = true))
        else c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Báo lỗi Aow Monika", text))
        return ok
    }

    private fun appLine() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${if (BuildConfig.DEBUG) "debug" else "release"}"
    private fun deviceLine() =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.joinToString()} · ${Build.HARDWARE}/${socModel()}"

    private fun socModel(): String = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "?"
}
