package vn.aow.monika.apkinstall.adb

import android.content.pm.PackageInstaller
import vn.aow.monika.apkinstall.ApkPart
import vn.aow.monika.apkinstall.DataFile
import vn.aow.monika.apkinstall.InstallOutcome
import java.io.IOException

/**
 * Việc Monika làm qua quyền shell (Cách 3): cài APK GỐC (kể cả bỏ qua chặn targetSdk) và chép Data vào Android/data/<gói>.
 * Chỉ dùng các lệnh của `pm`/`cmd package`/`mkdir`/`cat`/`stat`/`settings`.
 */
object AdbInstaller {
    private val SESSION = Regex("""\[(\d+)]""")

    fun parseSessionId(output: String): Int? = SESSION.find(output)?.groupValues?.get(1)?.toIntOrNull()

    /** Bao chuỗi trong nháy đơn cho shell; nháy đơn bên trong được thoát đúng. */
    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Tên file trong phiên cài: chỉ chữ/số/_/./- để khỏi lọt ký tự đặc biệt vào lệnh. */
    private fun safeName(i: Int, name: String) = "${i}_" + name.replace(Regex("[^A-Za-z0-9_.-]"), "_").let { if (it.endsWith(".apk", true)) it else "$it.apk" }

    suspend fun install(shell: AdbShell, pkg: String, parts: List<ApkPart>, bypassLowTargetSdk: Boolean, onProgress: (Int) -> Unit = {}): InstallOutcome {
        require(Regex("[A-Za-z0-9_.]+").matches(pkg)) { "Tên gói không hợp lệ" }
        val total = parts.sumOf { it.file.length() }.coerceAtLeast(1)
        val create = shell.shell("pm install-create -r" + (if (bypassLowTargetSdk) " --bypass-low-target-sdk-block" else "") + " -S $total")
        val sid = parseSessionId(create) ?: return InstallOutcome.from(PackageInstaller.STATUS_FAILURE, create.trim().ifBlank { "Không tạo được phiên cài" })
        var done = 0L
        try {
            parts.forEachIndexed { i, p ->
                val size = p.file.length()
                val out = shell.execWrite("cmd package install-write -S $size $sid ${safeName(i, p.file.name)} -", size) { o ->
                    p.file.inputStream().use { input ->
                        val buf = ByteArray(128 * 1024)
                        while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress((done * 100 / total).toInt().coerceIn(0, 100)) }
                    }
                }
                // Đầu ra rỗng = không đọc được kết quả (đã đóng luồng) → lỗi nếu có sẽ hiện ở bước commit.
                if (out.isNotBlank() && !out.contains("Success", true)) { abandon(shell, sid); return InstallOutcome.from(PackageInstaller.STATUS_FAILURE, out.trim()) }
            }
            val commit = shell.shell("pm install-commit $sid")
            return if (commit.contains("Success", true)) InstallOutcome.Success else InstallOutcome.from(PackageInstaller.STATUS_FAILURE, commit.trim())
        } catch (e: Exception) {
            abandon(shell, sid)
            throw e
        }
    }

    private suspend fun abandon(shell: AdbShell, sid: Int) { runCatching { shell.shell("pm install-abandon $sid") } }

    sealed class DataResult {
        data class Ok(val files: Int, val bytes: Long) : DataResult()
        data class Failed(val text: String) : DataResult()
    }

    /** Chép [files] vào /sdcard/Android/data/[pkg]/. Kiểm kích thước từng file sau khi chép. */
    suspend fun pushData(shell: AdbShell, pkg: String, files: List<DataFile>, onProgress: (Int) -> Unit = {}): DataResult {
        require(Regex("[A-Za-z0-9_.]+").matches(pkg)) { "Tên gói không hợp lệ" }
        val base = "/sdcard/Android/data/$pkg"
        val total = files.sumOf { it.payload.size }.coerceAtLeast(1)
        var done = 0L
        try {
            for (f in files) {
                val parts = f.relPath.split('/').filter { it.isNotEmpty() }
                if (parts.isEmpty() || parts.any { it == ".." || it == "." }) return DataResult.Failed("Đường dẫn không hợp lệ: ${f.relPath}")
                val path = "$base/" + parts.joinToString("/")
                val size = f.payload.size
                val have = shell.shell("stat -c %s ${q(path)} 2>/dev/null").trim().toLongOrNull()
                if (have == size) { done += size; onProgress((done * 100 / total).toInt()); continue }
                val dir = path.substringBeforeLast('/')
                shell.shell("mkdir -p ${q(dir)}")
                shell.execWrite("cat > ${q(path)}", size) { o ->
                    f.payload.open().use { input ->
                        val buf = ByteArray(128 * 1024)
                        while (true) { val n = input.read(buf); if (n < 0) break; o.write(buf, 0, n); done += n; onProgress((done * 100 / total).toInt().coerceIn(0, 100)) }
                    }
                }
                val after = shell.shell("stat -c %s ${q(path)} 2>/dev/null").trim().toLongOrNull()
                if (after != size) return DataResult.Failed("Chép thiếu dữ liệu: ${f.relPath} ($after/$size byte)")
            }
        } catch (e: IOException) {
            return DataResult.Failed("Chép dữ liệu lỗi: ${e.message}")
        }
        return DataResult.Ok(files.size, files.sumOf { it.payload.size })
    }

    /** Trả công tắc gỡ lỗi về như lúc đầu. Bỏ qua lỗi (kết nối có thể bị cắt ngay khi tắt Gỡ lỗi không dây). */
    suspend fun cleanup(shell: AdbShell, initial: DevState) {
        val cmd = AdbCleanup.command(initial) ?: return
        runCatching { shell.shell(cmd) }
    }
}
