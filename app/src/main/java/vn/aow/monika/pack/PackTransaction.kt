package vn.aow.monika.pack

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*

/** Giữ nguyên đường dẫn/version cũ; thư mục ứng viên và bản dự phòng chỉ dùng trong lúc cài. */
internal object PackTransaction {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun backup(target: File) = File(target.parentFile, ".${target.name}.previous")

    /** Mutex giữa các instance + khóa tệp giữa các tiến trình Android. Caller chạy trên IO. */
    suspend fun <T> locked(target: File, action: suspend () -> T): T =
        locks.computeIfAbsent(target.canonicalPath) { Mutex() }.withLock {
            checkDir(target.parentFile!!)
            FileOutputStream(File(target.parentFile, ".${target.name}.install.lock"), true).channel.use { channel ->
                channel.lock().use {
                    val old = backup(target)
                    if (old.exists()) {
                        if (!target.exists()) move(old, target) else old.deleteRecursively()
                    }
                    // Chỉ dọn ứng viên thuộc cùng gói, sau khi có cả hai khóa.
                    target.parentFile!!.listFiles().orEmpty()
                        .filter { it.name.startsWith(".${target.name}.install-") }.forEach { it.deleteRecursively() }
                    action()
                }
            }
        }

    suspend fun install(target: File, prepare: suspend (archive: File, candidate: File) -> Unit) {
        val work = File(target.parentFile, ".${target.name}.install-${UUID.randomUUID()}")
        val candidate = File(work, "candidate")
        checkDir(candidate)
        try {
            prepare(File(work, "download.zip"), candidate)
            val old = backup(target)
            if (target.exists()) move(target, old)
            try { move(candidate, target) } catch (error: Throwable) {
                if (old.exists()) move(old, target)
                throw error
            }
            old.deleteRecursively()
        } finally { work.deleteRecursively() }
    }

    private fun move(from: File, to: File) {
        if (!from.renameTo(to)) throw IOException("Không đổi được thư mục gói ${to.name}")
    }
    private fun checkDir(dir: File) {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Không tạo được thư mục cài gói")
    }

    suspend fun download(input: InputStream, archive: File, expectedHash: String, expectedBytes: Long = -1,
                         onBytes: suspend (Long) -> Unit = {}) {
        val md = MessageDigest.getInstance("SHA-256")
        var size = 0L
        archive.outputStream().use { output ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n); md.update(buf, 0, n); size += n
                onBytes(size)
            }
        }
        if (expectedBytes >= 0 && size != expectedBytes) throw IOException("Gói tải chưa đủ byte")
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (expectedHash.isNotEmpty() && !got.equals(expectedHash, ignoreCase = true)) throw IOException("Gói sai SHA-256")
    }

    fun unzip(archive: File, dir: File, flatten: Boolean = false, mainFile: String? = null) {
        // Chỉ bỏ một gốc chung khi ZIP chưa có đúng mainFile và gốc đó chứa mainFile.
        // Gói RGSS có lib/libmkxp-z.so đúng chỗ sẽ giữ nguyên lib/, không tự làm phẳng.
        val stripRoot = if (!flatten && mainFile != null) java.util.zip.ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().map { it.name.trimEnd('/') }.filter { it.isNotEmpty() }.toList()
            val root = entries.firstOrNull()?.substringBefore('/')
            root?.takeIf { mainFile !in entries && "$it/$mainFile" in entries &&
                entries.all { name -> name == it || name.startsWith("$it/") } }
        } else null
        val written = HashSet<String>()
        ZipInputStream(archive.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val original = File(dir, e.name).canonicalFile
                if (!original.path.startsWith(dir.canonicalPath + File.separator)) throw IOException("Đường dẫn ZIP vượt thư mục gói")
                if (e.isDirectory) continue
                val relative = if (stripRoot != null) e.name.removePrefix("$stripRoot/") else e.name
                val dest = if (flatten) File(dir, File(relative).name).canonicalFile else File(dir, relative).canonicalFile
                if (!dest.path.startsWith(dir.canonicalPath + File.separator)) throw IOException("Đường dẫn ZIP vượt thư mục gói")
                if (!written.add(dest.path)) throw IOException("ZIP có file trùng tên")
                checkDir(dest.parentFile!!)
                dest.outputStream().use { z.copyTo(it) }
            }
        }
    }

    private fun requiredFile(root: File, file: File): File {
        val resolved = file.canonicalFile
        if (!resolved.path.startsWith(root.canonicalPath + File.separator) || !resolved.isFile || resolved.length() == 0L)
            throw IOException("Gói thiếu file ${file.name}")
        return resolved
    }

    /** Kiểm file bắt buộc và ABI của mọi ELF trước khi gói trở thành bản đang dùng. */
    fun validate(dir: File, mainFile: String, abi: String) {
        val main = File(dir, mainFile).canonicalFile
        if (!main.path.startsWith(dir.canonicalPath + File.separator) || !main.isFile || main.length() == 0L)
            throw IOException("Gói không có $mainFile")
        val manifest = File(dir, "manifest.json")
        if (manifest.isFile) {
            val meta = Json.parseToJsonElement(manifest.readText()).jsonObject
            meta["abi"]?.jsonPrimitive?.content?.let { if (it != abi) throw IOException("Manifest sai ABI") }
            meta["loadOrder"]?.jsonArray?.forEach { entry -> requiredFile(dir, File(main.parentFile, entry.jsonPrimitive.content)) }
            meta["files"]?.jsonObject?.forEach { (name, info) ->
                val file = requiredFile(dir, File(main.parentFile, name))
                val properties = info.jsonObject
                properties["size"]?.jsonPrimitive?.long?.let { if (file.length() != it) throw IOException("File gói sai dung lượng") }
                properties["sha256"]?.jsonPrimitive?.content?.let { expected ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
                    }
                    if (!digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, true)) throw IOException("File gói sai SHA-256")
                }
            }
        }
        val needed = File(dir, "needed.txt")
        val systemLibraries = setOf("libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so", "libEGL.so",
            "libGLESv1_CM.so", "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so", "libjnigraphics.so", "libvulkan.so",
            "libaaudio.so", "libmediandk.so", "libnativewindow.so", "libcamera2ndk.so", "libstdc++.so",
            "libsync.so", "libneuralnetworks.so", "libOpenMAXAL.so", "libamidi.so", "libbinder_ndk.so")
        if (needed.isFile) needed.readLines().map { it.trim() }.filter { it.isNotEmpty() && it !in systemLibraries }
            .forEach { requiredFile(dir, File(dir, it)) }
        dir.walkTopDown().filter { it.isFile && it.name.endsWith(".so") }.forEach { lib ->
            val header = ByteArray(20)
            val read = lib.inputStream().use { it.read(header) }
            val machine = (header[18].toInt() and 255) or ((header[19].toInt() and 255) shl 8)
            val expected = when (abi) { "arm64-v8a" -> 183; "armeabi-v7a" -> 40; "x86" -> 3; "x86_64" -> 62; else -> -1 }
            val elfClass = if (abi in setOf("arm64-v8a", "x86_64")) 2 else 1
            if (read != 20 || !header.copyOfRange(0, 4).contentEquals(byteArrayOf(127, 69, 76, 70)) ||
                header[4].toInt() != elfClass || header[5].toInt() != 1 || machine != expected)
                throw IOException("Thư viện ${lib.name} sai định dạng/ABI $abi")
        }
    }
}
