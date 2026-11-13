package vn.aow.monika.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
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
import java.security.MessageDigest
import java.util.UUID

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
    data class ReportImage(val mime: String, val data: String, val width: Int, val height: Int)

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
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
        val image: ReportImage? = null,
        /** V101: ngữ cảnh chết; unknown là thiếu bằng chứng, không giả một Session. */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
        val core: String = "",
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
        val system: String = "",
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
        val stage: String = "",
        /**
         * V155: SHA-256 (hex thường) của chứng thư ký APK đang chạy, để phân biệt bản chính thức
         * với bản tự build. Rỗng/không có = không đọc được; không chặn việc gửi báo cáo.
         * `EncodeDefault.NEVER` giữ nguyên định dạng JSON cũ khi trường trống.
         */
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
        val certSha256: String = "",
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
    private fun deathSessionFile(c: Context, pid: Int) = File(File(dir(c), "sessions").apply { mkdirs() }, "$pid.json")
    private fun reportsDir(c: Context) = File(dir(c), "reports").apply { mkdirs() }

    /** Riêng từng lần khởi động tiến trình; Android lưu cùng ApplicationExitInfo. Không gửi token này. */
    private val processGeneration = UUID.randomUUID().toString()
    @Volatile private var startedSession: Long? = null
    @Serializable
    internal data class DeathSnapshot(val session: Session, val generation: String)
    internal fun deathProcessSummary(generation: String): ByteArray =
        "monika-death-context:$generation".toByteArray(Charsets.UTF_8)

    // ---------- Phiên chơi (chạy trong tiến trình game) ----------

    fun begin(c: Context, kind: String, core: String, coreInfo: String, game: String, system: String) {
        val now = System.currentTimeMillis()
        startedSession = now
        if (Build.VERSION.SDK_INT >= 30) runCatching {
            c.getSystemService(ActivityManager::class.java).setProcessStateSummary(deathProcessSummary(processGeneration))
        }
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

    /** Sao chép chẩn đoán timeout mềm; không lập báo cáo lỗi hay gửi mạng. */
    internal fun startupInfo(c: Context, detail: String): String = scrub(c,
        listOf("Chẩn đoán khởi động", appLine(), deviceLine(), envLine(c), detail,
            Breadcrumbs.read(c, Process.myPid()).joinToString("\n")).joinToString("\n"))

    /** Nhịp sống: biết game còn chạy tới lúc nào (Android cũ không cho hỏi lý do chết). */
    fun heartbeat(c: Context) {
        val s = read(c) ?: return
        write(c, s.copy(lastAlive = System.currentTimeMillis()))
    }

    /** Thoát bình thường → xóa phiên, không tạo báo cáo. */
    fun end(c: Context) { runCatching {
        startedSession = null
        sessionFile(c).delete(); deathSessionFile(c, Process.myPid()).delete()
        File(dir(c), "session-env.txt").delete()
    } }

    private fun read(c: Context): Session? =
        runCatching { json.decodeFromString(Session.serializer(), sessionFile(c).readText()) }.getOrNull()

    private fun write(c: Context, s: Session) {
        runCatching {
            val tmp = File(dir(c), "active-session.tmp")
            tmp.writeText(scrubJson(c, json.encodeToJsonElement(Session.serializer(), s)).toString())
            tmp.renameTo(sessionFile(c))
            // V101: riêng từng PID, không dùng phiên game khác để giải thích chết của main/midlet.
            if (s.pid != Process.myPid() || s.startedAt != startedSession) return@runCatching
            val target = deathSessionFile(c, s.pid)
            val snapshot = File(target.parentFile, "${s.pid}.tmp")
            snapshot.writeText(scrubJson(c, json.encodeToJsonElement(DeathSnapshot.serializer(), DeathSnapshot(s, processGeneration))).toString())
            snapshot.renameTo(target)
            target.parentFile?.listFiles()?.filter { it.extension == "json" }
                ?.sortedByDescending { it.lastModified() }?.drop(30)?.forEach { it.delete() }
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
        deathSessionFile(c, s.pid).delete()
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
        val detail = if (Build.VERSION.SDK_INT >= 30) " imp=${e.importance} pss=${e.pss shr 10}MB" else ""
        return "$name (status ${e.status}) ${e.description.orEmpty()}$detail".trim()
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

    // ---------- V112: mọi thao tác người dùng đều để lại log khi lỗi ----------

    /** Phạm vi của một thao tác người dùng; gọi [step] để ghi bước đang làm (vào vệt sự kiện và báo cáo lỗi). */
    class OpScope internal constructor(private val c: Context, val name: String) {
        @Volatile var stepName: String = ""
            private set
        fun step(s: String) { stepName = s; Breadcrumbs.add(c, "op", "$name: $s") }
    }

    /** Tên thao tác dùng chung để báo cáo `op-fail` gom đúng nhóm (khớp `component = "op:<tên>"`). */
    object Op {
        const val DOWNLOAD = "tai-xuong"
        const val EXTRACT = "giai-nen"
        const val IMPORT = "nhap-game"
        const val PATCH_ROM = "va-rom"
        const val INSTALL_PACK = "cai-goi-engine"
        const val OPEN_GAME = "mo-game"
        const val FETCH_CONFIG = "tai-cau-hinh"
        const val FETCH_FEED = "tai-feed"
    }

    /** Cùng thao tác + cùng chữ ký lỗi trong chừng này thời gian chỉ ghi/gửi 1 lần. */
    internal const val OP_THROTTLE_MS = 10 * 60_000L
    private const val OP_CRUMBS = 30
    internal var opClock: () -> Long = { System.currentTimeMillis() }
    private val opSeen = java.util.concurrent.ConcurrentHashMap<String, Long>()
    /** Gắn ở AppGraph: gửi ngầm báo cáo `op-fail` theo cấu hình/ý người chơi (cùng đường với lõi sập). */
    @Volatile internal var opSender: ((Context, Report) -> Unit)? = null
    internal fun resetOpThrottle() = opSeen.clear()
    private val opIdSeq = java.util.concurrent.atomic.AtomicLong(0)
    /** Mã báo cáo là mili-giây thật và không bao giờ trùng (id = tên file): hai lỗi cùng mili-giây không được ghi đè nhau. */
    private fun nextOpReportId(): Long {
        val t = System.currentTimeMillis()
        return opIdSeq.updateAndGet { maxOf(it + 1, t) }
    }

    /**
     * Chạy một thao tác người dùng; lỗi (trừ hủy coroutine) được ghi thành báo cáo `kind=op-fail` rồi trả về trong [Result]
     * — thay cho `runCatching` nuốt lỗi im lặng. [target] là tên file game (không đường dẫn, không link tải).
     * [expected] đánh dấu lỗi nằm trong luồng bình thường (vd. cần mật khẩu, thiếu gói giải nén mà mất mạng):
     * vẫn trả về trong [Result] nhưng không lập báo cáo — chỉ ghi một vệt để đọc log là biết.
     */
    fun <T> op(c: Context, name: String, target: String? = null, expected: (Throwable) -> Boolean = { false }, block: OpScope.() -> T): Result<T> {
        val scope = OpScope(c, name)
        return try { Result.success(scope.block()) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Throwable) { if (expected(e)) expectedCrumb(c, name, scope.stepName, e) else opFail(c, name, scope.stepName, e, target); Result.failure(e) }
    }

    /** Bản `suspend` của [op]. */
    suspend fun <T> opSuspend(c: Context, name: String, target: String? = null, expected: (Throwable) -> Boolean = { false }, block: suspend OpScope.() -> T): Result<T> {
        val scope = OpScope(c, name)
        return try { Result.success(scope.block()) } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Throwable) { if (expected(e)) expectedCrumb(c, name, scope.stepName, e) else opFail(c, name, scope.stepName, e, target); Result.failure(e) }
    }

    /** Lỗi thuộc luồng bình thường: không lập báo cáo, chỉ để lại vệt (cùng nhóm [op-fail] như ca lỗi mạng/hủy). */
    private fun expectedCrumb(c: Context, name: String, step: String, e: Throwable) {
        Breadcrumbs.add(c, "op-fail", "$name/${step.ifBlank { "-" }}: ${errorCode(e)} (mong đợi, không báo)")
    }

    /**
     * Ghi một lỗi thao tác đã tự bắt (nơi không bọc được bằng [op]). Trả null nếu trùng trong cửa sổ chống spam.
     * [expected] đánh dấu lỗi thuộc luồng bình thường (vd. người chơi chọn nhầm bản vá): chỉ ghi vệt, không lập báo cáo
     * — cùng quy ước với [op]/[opSuspend].
     */
    fun opFail(c: Context, name: String, step: String, e: Throwable, target: String? = null, expected: (Throwable) -> Boolean = { false }): Report? = runCatching {
        val code = errorCode(e)
        // Hủy (Activity rời màn, Worker bị dừng), lỗi mạng và lỗi đã khai báo [expected] là chuyện thường:
        // chỉ ghi vệt, không lập báo cáo.
        val skipReason = when {
            isCancellation(e) -> "hủy"
            isOffline(step, e) -> "lỗi mạng"
            expected(e) -> "mong đợi"
            else -> null
        }
        if (skipReason != null) {
            Breadcrumbs.add(c, "op-fail", "$name/${step.ifBlank { "-" }}: $code ($skipReason, không báo)")
            return@runCatching null
        }
        val key = "$name|$step|$code|${e.message.orEmpty().take(60).replace(Regex("""\d+"""), "")}"
        val now = opClock()
        var fresh = false
        // Nguyên tử: nhiều luồng cùng lỗi cùng lúc thì đúng 1 luồng thắng cửa sổ 10 phút.
        opSeen.compute(key) { _, last -> if (last == null || now - last >= OP_THROTTLE_MS) { fresh = true; now } else last }
        if (!fresh) {
            Breadcrumbs.add(c, "op-fail", "$name/${step.ifBlank { "-" }}: $code (trùng, đã ghi)")
            return@runCatching null
        }
        Breadcrumbs.add(c, "op-fail", "$name/${step.ifBlank { "-" }}: $code")
        val stack = java.io.StringWriter().also { e.printStackTrace(java.io.PrintWriter(it)) }.toString()
        val detail = buildString {
            append("thao tác: ").append(name).append('\n')
            append("bước: ").append(step.ifBlank { "-" }).append('\n')
            append("mã lỗi: ").append(code).append('\n')
            target?.takeIf { it.isNotBlank() }?.let { append("đối tượng: ").append(it.substringAfterLast('/')).append('\n') }
            append(stack)
        }.let { scrub(c, stripLinks(it)) }.take(8_000)
        val s = read(c)?.takeIf { it.pid == Process.myPid() }
        val id = nextOpReportId()
        val r = record(c, Report(
            id = id, time = id, kind = "op-fail",
            title = "Thao tác lỗi: $name${if (step.isBlank()) "" else " ($step)"} — $code", app = appLine(), device = deviceLine(), session = s,
            reason = "$name/${step.ifBlank { "-" }}: $code", detail = detail, fromGame = false,
            component = "op:$name", env = envLine(c), // Vệt cũ có thể chứa link (vd. ghi lúc tải) → lọc cả vệt: báo cáo/payload không bao giờ mang link tải.
            crumbs = Breadcrumbs.read(c, Process.myPid(), OP_CRUMBS).map { stripLinks(it) },
        ))
        opSender?.invoke(c, r)
        r
    }.getOrNull()

    /** Loại/mã lỗi gọn: tên lớp ngoại lệ (+ errno nếu có, + lớp nguyên nhân gốc nếu khác). */
    internal fun errorCode(e: Throwable): String {
        val root = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }.last()
        val errno = (e as? android.system.ErrnoException)?.errno ?: (root as? android.system.ErrnoException)?.errno
        return buildString {
            append(e.javaClass.simpleName.ifBlank { e.javaClass.name })
            errno?.let { append(" errno=").append(it) }
            if (root !== e) append(" <- ").append(root.javaClass.simpleName.ifBlank { root.javaClass.name })
        }
    }

    /** Coroutine bị hủy, hoặc OkHttp báo `Canceled` khi lời gọi bị hủy. */
    internal fun isCancellation(e: Throwable): Boolean = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }.any {
        it is kotlinx.coroutines.CancellationException || (it is java.io.IOException && it.message == "Canceled")
    }

    /**
     * V166: lỗi mạng là chuyện thường của người chơi, không phải lỗi app → không lập báo cáo `op-fail` (chỉ ghi vệt).
     * Ngoài "mất mạng/quá hạn" còn phải kể cả kiểu mạng nhà/ISP chặn hoặc bóp Blogger (hay lặp lại mỗi lần mở app):
     * TLS bị chặn/hijack (`SSLException`), máy chủ cắt kết nối (`SocketException`: reset/abort/broken pipe), và
     * luồng mạng bị cắt giữa chừng đã được [networkRead] đánh dấu ([NetworkTruncatedException]).
     * Luồng đứt KHÔNG tự động là lỗi mạng: nó còn là dấu hiệu tệp/archive cụt — xem [isOffline] bản có bước.
     * Máy chủ trả mã lỗi HTTP vẫn là báo cáo thật (có thể lỗi phía mình) — chỉ lỗi tầng vận chuyển mới bỏ qua.
     */
    fun isOffline(e: Throwable): Boolean = chainOf(e).any {
        it is NetworkTruncatedException ||
            it is java.net.UnknownHostException || it is java.net.ConnectException || it is java.net.NoRouteToHostException ||
            it is java.net.SocketTimeoutException || it is java.io.InterruptedIOException ||
            it is java.net.SocketException || it is javax.net.ssl.SSLException || it is java.net.ProtocolException ||
            NETWORK_BROKEN.containsMatchIn(it.message.orEmpty())
    }

    /** Bước chỉ đọc qua mạng (không đụng tệp cục bộ): luồng đứt ở đây là chuyện mạng. */
    private val NETWORK_STEPS = setOf("tai", "tai-ve", "tai-bai", "tai-danh-sach", "tai-chi-muc", "tai-trang")

    /**
     * V166 (Luna 3, lần 2): phân loại theo **nguồn đọc tại chỗ ném lỗi**, không theo tên thao tác — vì có thao tác
     * trộn cả tải và giải nén (`mo-game`, `cai-goi-engine`, `tai-xuong/luu-file`). Luồng đứt là chuyện mạng khi:
     * (a) ngoại lệ đã được [networkRead] đánh dấu là đọc qua mạng, hoặc (b) bước hiện tại chỉ đọc qua mạng
     * ([NETWORK_STEPS]). EOF khi đọc archive cục bộ = tệp cụt/hỏng → **vẫn lập báo cáo**.
     */
    fun isOffline(step: String, e: Throwable): Boolean =
        isOffline(e) || (step.substringBefore(':').trim() in NETWORK_STEPS && isTruncated(e))

    /** Luồng mạng bị cắt giữa chừng — nguồn đã biết là mạng nên KHÔNG phải dữ liệu hỏng. */
    class NetworkTruncatedException(message: String?, cause: Throwable?) : java.io.IOException(message, cause)

    /**
     * V166 (Luna 3, lần 2): bọc **đúng chỗ đọc qua mạng** (HTTP body, socket). Luồng đứt ở đó là chuyện mạng.
     * Chỗ đọc archive cục bộ (ZipInputStream, zip4j, commons-compress) KHÔNG bọc → EOF của tệp cụt vẫn là
     * lỗi dữ liệu và vẫn được lập báo cáo, kể cả trong thao tác trộn tải + giải nén.
     */
    fun <T> networkRead(block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        throw markNetworkTruncation(e)
    }

    /** Bản dùng được trong hàm `suspend` (không bọc lambda): `catch (e) { throw markNetworkTruncation(e) }`. */
    fun markNetworkTruncation(e: Throwable): Throwable = if (isTruncated(e)) NetworkTruncatedException(e.message, e) else e

    /**
     * Bọc luồng **mạng** (HTTP body, socket): đọc bị đứt giữa chừng → [NetworkTruncatedException].
     * Nhờ vậy chỗ gọi không cần `try/catch` riêng, và luồng đọc tệp cục bộ không bị đánh dấu nhầm là mạng.
     */
    fun networkStream(input: java.io.InputStream): java.io.InputStream = object : java.io.FilterInputStream(input) {
        override fun read(): Int = try {
            super.read()
        } catch (e: java.io.IOException) {
            throw markNetworkTruncation(e)
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = try {
            super.read(b, off, len)
        } catch (e: java.io.IOException) {
            throw markNetworkTruncation(e)
        }
    }

    /** Luồng đứt: `EOFException`, hoặc câu báo của OkHttp khi body ngắn hơn Content-Length. */
    internal fun isTruncated(e: Throwable): Boolean = chainOf(e).any {
        it is java.io.EOFException || TRUNCATED.containsMatchIn(it.message.orEmpty())
    }

    private fun chainOf(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }

    /** Câu báo của OkHttp/tầng vận chuyển khi kết nối bị cắt giữa chừng (không kèm loại ngoại lệ riêng). */
    private val NETWORK_BROKEN = Regex(
        """(?i)connection reset|connection closed|stream was reset|broken pipe|connection abort|software caused connection abort|read timed out"""
    )

    /** Câu báo luồng đứt: OkHttp và một số bộ đọc tệp cùng dùng, nên chỉ tính là mạng khi biết nguồn là mạng. */
    private val TRUNCATED = Regex("""(?i)unexpected end of stream""")

    private val LINK = Regex("""(?i)\b(?:https?|ftp)://[^\s"'<>)]+""")
    /** Không bao giờ gửi link tải (có thể là link game/ROM). */
    internal fun stripLinks(text: String): String = text.replace(LINK, "<link>")

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

    /** Engine báo lỗi nhưng tiến trình còn sống/thoát êm; luôn qua ranh giới scrub/save chung. */
    internal fun recordEngineSignal(c: Context, engine: String, what: String, lines: List<String>): Report {
        val now = System.currentTimeMillis()
        val s = read(c)?.takeIf { it.pid == Process.myPid() }
        Breadcrumbs.add(c, "engine-signal", "$engine: $what")
        return record(c, Report(id = now, time = now, kind = "handled",
            title = "Lỗi engine $engine: $what", app = appLine(), device = deviceLine(),
            session = s, reason = what, detail = lines.joinToString("\n"), log = lines,
            fromGame = true, component = "engine:$engine", env = envLine(c),
            crumbs = Breadcrumbs.read(c, Process.myPid())))
    }

    /**
     * Bản debug và máy giả lập test (Robolectric) KHÔNG tự gửi báo cáo lên Worker: làm bẩn số liệu crash-free và tốn quota KV.
     * Bật tay bằng [allowDebugSend] (AppGraph đọc từ Prefs `debugSendCrash`; test kiểm đường gửi dùng uploader giả thì tự bật).
     */
    @Volatile internal var allowDebugSend = false
    internal fun isTestDevice(): Boolean =
        Build.FINGERPRINT.contains("robolectric", ignoreCase = true) || Build.MODEL.contains("robolectric", ignoreCase = true)
    internal fun autoSendAllowedHere(): Boolean = allowDebugSend || !(BuildConfig.DEBUG || isTestDevice())

    /** Gửi ngầm (không clipboard) nếu người chơi không tắt và config `crash.autoSend` bật. Chạy ở luồng nền. */
    fun autoSend(c: Context, http: OkHttpClient, r: Report, endpoint: String, enabledByConfig: Boolean, enabledByUser: Boolean) {
        if (!enabledByConfig || !enabledByUser || endpoint.isBlank() || r.sent) return
        if (!autoSendAllowedHere()) return
        // V194: hạn mức ghi KV của Worker chỉ 1000/ngày ↔ mỗi báo cáo là 1 lần ghi.
        // Cùng một dấu vân tay chỉ gửi 1 lần/giờ (nhớ qua lần mở app); lowmem chặt hơn: 1 lần mỗi phiên.
        // Bản gộp vẫn tăng `count` ở máy nhưng KHÔNG gửi lại — tránh kiểu mỗi lần mở app lại đẩy 1 lowmem y hệt.
        // Chỉ ghi nhận "đã gửi" khi request THÀNH CÔNG: gửi lỗi (mất mạng) thì lần sau vẫn gửi lại được.
        val fingerprint = r.fingerprint.ifBlank { fingerprint(r) }
        if (r.kind == "lowmem" && sentThisSession.contains(fingerprint)) {
            save(c, r.copy(sent = true))
            return
        }
        if (sentWithin(c, fingerprint, SEND_DEDUPE_MS, sendClock())) {
            Breadcrumbs.add(c, "diag-send", "${r.kind} $fingerprint: đã gửi trong 1 giờ, chỉ cộng count ở máy")
            save(c, r.copy(sent = true))
            return
        }
        // Cùng lúc chỉ 1 request cho mỗi dấu vân tay (onResume có thể gọi chồng) — nhả ở finally.
        if (!sendingInFlight.add(fingerprint)) return
        Thread {
            try {
                val ok = runCatching {
                    http.newCall(Request.Builder().url(endpoint).post(reportJson(c, r).toRequestBody("application/json".toMediaType())).build())
                        .execute().use { it.isSuccessful }
                }.getOrDefault(false)
                if (ok) {
                    markSent(c, fingerprint, sendClock())
                    sentThisSession.add(fingerprint)
                    save(c, r.copy(sent = true))
                }
            } finally {
                sendingInFlight.remove(fingerprint)
            }
        }.start()
    }

    /** V194: cửa sổ chặn gửi trùng dấu vân tay (mọi loại trừ lowmem đã chặn chặt hơn theo phiên). */
    internal const val SEND_DEDUPE_MS = 60 * 60_000L
    /** Đồng hồ gửi báo cáo; test đổi được để kiểm ranh giới 1 giờ. */
    internal var sendClock: () -> Long = { System.currentTimeMillis() }
    private val sentRecentlyMemo = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val sentThisSession = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    /** Dấu vân tay đang có request gửi dở (chống gửi chồng khi onResume gọi liên tiếp). */
    private val sendingInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    internal fun resetSendDedupe() {
        sentRecentlyMemo.clear()
        sentThisSession.clear()
        sendingInFlight.clear()
    }

    private fun sendPrefs(c: Context) = c.getSharedPreferences("diag-send", Context.MODE_PRIVATE)

    /** Đã gửi thành công cùng dấu vân tay trong [windowMs] chưa? RAM trước, rồi đĩa để nhớ qua lần mở app. */
    internal fun sentWithin(c: Context, fingerprint: String, windowMs: Long, now: Long): Boolean {
        val last = sentRecentlyMemo[fingerprint] ?: sendPrefs(c).getLong(fingerprint, 0L)
        if (last <= 0L) return false
        sentRecentlyMemo[fingerprint] = last
        return now - last in 0 until windowMs
    }

    private fun markSent(c: Context, fingerprint: String, now: Long) {
        sentRecentlyMemo[fingerprint] = now
        val prefs = sendPrefs(c)
        prefs.edit().putLong(fingerprint, now).apply()
        // Giữ tệp nhỏ: quá 200 khoá thì bỏ mục cũ hơn 7 ngày.
        if (prefs.all.size > 200) {
            val stale = prefs.all.filterValues { (it as? Long ?: 0L) < now - 7 * 24 * 3_600_000L }.keys
            if (stale.isNotEmpty()) prefs.edit().also { editor -> stale.forEach { editor.remove(it) } }.apply()
        }
    }

    // ---------- Gộp trùng, che thông tin riêng, chụp tình trạng máy ----------

    /** Lưu báo cáo mới; nếu cùng dấu vân tay với báo cáo trước (trong 30 báo cáo gần nhất) thì gộp: tăng đếm, giữ bản mới nhất. */
    private fun record(c: Context, r0: Report): Report {
        val safe = sanitized(c, enrichDeathReport(r0))
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

    internal fun sanitized(c: Context?, r: Report): Report {
        val image = if (r.kind == "user") UserGameReport.valid(r.image) else null
        val text = json.decodeFromJsonElement(Report.serializer(), scrubJson(c, json.encodeToJsonElement(Report.serializer(), r.copy(image = null))))
        return text.copy(image = image)
    }

    internal fun reportJson(c: Context?, r: Report): String {
        val filled = if (c != null && r.certSha256.isEmpty()) r.copy(certSha256 = certSha256(c)) else r
        val safe = UserGameReport.bounded(sanitized(c, filled)) { json.encodeToString(Report.serializer(), it) }
        return json.encodeToString(Report.serializer(), safe)
    }

    /** SHA-256 dạng hex thường; tách riêng để kiểm được bằng véc-tơ chuẩn, không cần máy thật. */
    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    /**
     * V155: SHA-256 chứng thư ký của APK đang chạy. Trả chuỗi rỗng khi không đọc được
     * (máy lạ, bản build không ký, ROM cook…) — **không** ném lỗi và không chặn gửi báo cáo.
     */
    internal fun certSha256(c: Context): String = runCatching {
        val pm = c.packageManager
        val signature = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(c.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
        }
        val bytes = signature?.toByteArray()
        if (bytes == null || bytes.isEmpty()) "" else sha256Hex(bytes)
    }.getOrDefault("")

    /** Người chơi chủ động báo lỗi, không gộp làm mất mô tả/ảnh của lần gửi khác. */
    fun recordUser(c: Context, type: String, description: String, image: ReportImage?,
                   gameTitle: String? = null, component: String? = null, extraDetail: String = ""): Report {
        val now = System.currentTimeMillis()
        // Màn chuẩn bị chưa mở engine: tên game/engine rõ ràng không phải session đang chạy.
        val session = if (gameTitle != null || component != null) null
            else read(c)?.takeIf { it.pid == Process.myPid() }?.copy(lastAlive = now)
        val report = Report(id = now, time = now, kind = "user", title = "Báo lỗi game: ${gameTitle ?: session?.game.orEmpty().ifBlank { "game" }}",
            app = appLine(), device = deviceLine(), session = session, reason = type, detail = listOf(extraDetail, description).filter { it.isNotBlank() }.joinToString("\n\n"),
            log = UserGameReport.recentLog(Process.myPid()), fromGame = true,
            component = component ?: session?.kind?.let { "engine:$it" }.orEmpty(), env = envLine(c),
            crumbs = Breadcrumbs.read(c, Process.myPid()), image = image)
        val safe = json.decodeFromString(Report.serializer(), reportJson(c, report))
        save(c, safe); return safe
    }

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
     * V202: Android thu hồi tiến trình nền `IMPORTANCE_CACHED` là cơ chế bình thường, không phải
     * lỗi của app; chỉ báo `lowmem` khi tiến trình còn quan trọng hoặc có phiên chết khớp.
     * Tiến trình sandbox của WebView cũng bỏ (không phải tiến trình app).
     */
    internal fun shouldReportLowMem(importance: Int, processName: String?, hasDeathSession: Boolean): Boolean {
        if (processName?.contains("sandboxed_process") == true) return false
        if (!hasDeathSession && importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED) return false
        return true
    }

    /**
     * V202: khi `trace` rỗng (lowmem), không trộn `crumbs` cũ vào phần đoán thành phần — crumb
     * mạng cũ sẽ gán nhầm tiến trình chính thành `net`/`pack:*`. Có trace thì mới dùng crumbs.
     */
    internal fun componentHint(trace: String, crumbs: List<String>): String =
        if (trace.isNotBlank()) trace + "\n" + crumbs.joinToString("\n") else ""

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
            val deathSession = if (kind == "anr" || kind == "lowmem") readDeathSession(c, e.pid, e.timestamp, e.processStateSummary) else null
            // V202: bỏ ca Android dọn tiến trình nền bình thường; vẫn dọn trạng thái để không quét lại.
            if (kind == "lowmem" && !shouldReportLowMem(e.importance, e.processName, deathSession != null)) {
                Breadcrumbs.drop(c, e.pid)
                clearDeathSession(c, e.pid, e.processStateSummary)
                continue
            }
            val proc = e.processName.orEmpty().substringAfter(':', "chính")
            val comp = Components.of(c, deathSession, componentHint(trace, crumbs)).ifBlank { "app:$proc" }
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
                session = deathSession, fromGame = deathSession != null,
            ))
            Breadcrumbs.drop(c, e.pid)
            clearDeathSession(c, e.pid, e.processStateSummary)
        }
        runCatching { stamp.writeText(newest.toString()) }
        return out
    }

    /** PID/thời gian chưa đủ: token Android ghi cho lần chết phải khớp thế hệ snapshot. */
    internal fun matchingDeathSession(snapshot: DeathSnapshot?, pid: Int, diedAt: Long, summary: ByteArray?): Session? {
        if (snapshot == null || !runCatching { UUID.fromString(snapshot.generation).toString() == snapshot.generation }.getOrDefault(false) ||
            summary == null || !summary.contentEquals(deathProcessSummary(snapshot.generation))) return null
        return snapshot.session.takeIf {
        it.pid == pid && it.startedAt > 0 && it.startedAt <= diedAt &&
            it.stageAt in 0..diedAt && it.lastAlive in 0..diedAt
        }
    }

    internal fun readDeathSession(c: Context, pid: Int, diedAt: Long, summary: ByteArray?): Session? = matchingDeathSession(
        runCatching { json.decodeFromString(DeathSnapshot.serializer(), deathSessionFile(c, pid).readText()) }.getOrNull(), pid, diedAt, summary)

    /** Một exit cũ không được xóa snapshot của tiến trình mới đang dùng lại PID. */
    internal fun clearDeathSession(c: Context, pid: Int, summary: ByteArray?) {
        val f = deathSessionFile(c, pid)
        val snapshot = runCatching { json.decodeFromString(DeathSnapshot.serializer(), f.readText()) }.getOrNull() ?: return
        if (summary != null && summary.contentEquals(deathProcessSummary(snapshot.generation))) f.delete()
    }

    internal fun enrichDeathReport(r: Report): Report {
        if (r.kind != "lowmem" && r.kind != "anr") return r
        return r.copy(
            core = r.session?.core?.takeIf { it.isNotBlank() } ?: r.core.ifBlank { "unknown" },
            system = r.session?.system?.takeIf { it.isNotBlank() } ?: r.system.ifBlank { "unknown" },
            stage = r.session?.stage?.takeIf { it.isNotBlank() } ?: r.stage.ifBlank { "unknown" },
        )
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

    /** Gắn ở test: xem [list] chạy ở luồng nào (đọc/parse 30 file JSON trên main thread là ANR). */
    @Volatile internal var listProbe: ((Thread) -> Unit)? = null

    fun list(c: Context): List<Report> {
        listProbe?.invoke(Thread.currentThread())
        return reportsDir(c).listFiles().orEmpty()
            .sortedByDescending { it.name }
            .mapNotNull { f -> runCatching { sanitized(c, json.decodeFromString(Report.serializer(), f.readText())) }.getOrNull() }
    }

    fun markSeen(c: Context, r: Report) { save(c, r.copy(seen = true)); if (pending.value?.id == r.id) pending.value = null }

    fun delete(c: Context, r: Report) { File(reportsDir(c), "${r.id}.json").delete(); tick.value++ }

    fun clear(c: Context) { reportsDir(c).deleteRecursively(); tick.value++ }

    /**
     * Gửi báo cáo: có địa chỉ nhận ([endpoint], cấu hình `crash.endpoint`) thì gửi thẳng (JSON, ẩn danh);
     * chưa có thì chép nội dung vào clipboard để dán vào nhóm / Discord của AowVN.
     * Trả true nếu đã gửi lên máy chủ.
     */
    fun send(c: Context, http: OkHttpClient, r: Report, endpoint: String): Boolean = sendResult(c, http, r, endpoint).ok

    /** Kết quả gửi; [error] null khi đã lên máy chủ, còn lại là lý do ngắn (mã HTTP / loại lỗi mạng) để hiện cho người chơi. */
    data class SendResult(val ok: Boolean, val error: String? = null)

    fun sendResult(c: Context, http: OkHttpClient, r: Report, endpoint: String): SendResult {
        val text = r.toText()
        if (endpoint.isBlank()) {
            c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Báo lỗi Aow Monika", text))
            return SendResult(false, "chưa cấu hình địa chỉ nhận")
        }
        val result = runCatching {
            http.newCall(Request.Builder().url(endpoint).post(reportJson(c, r).toRequestBody("application/json".toMediaType())).build())
                .execute().use { if (it.isSuccessful) SendResult(true) else SendResult(false, "HTTP ${it.code}") }
        }.getOrElse { SendResult(false, it.javaClass.simpleName.ifBlank { "lỗi mạng" }) }
        if (result.ok) save(c, r.copy(sent = true))
        else {
            Breadcrumbs.add(c, "report-send", "gửi thất bại: ${result.error}")
            c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Báo lỗi Aow Monika", text))
        }
        return result
    }

    private fun appLine() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ${if (BuildConfig.DEBUG) "debug" else "release"}"
    private fun deviceLine() =
        "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.SUPPORTED_ABIS.joinToString()} · ${Build.HARDWARE}/${socModel()}"

    private fun socModel(): String = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "?"
}
