package vn.aow.monika.library

import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Giải nén bằng 7-Zip (thư viện native, 7-Zip-JBinding 16.02): RAR/RAR5 **có mật khẩu** (kể cả mã hóa tên file),
 * RAR nhiều phần, 7z AES… — những thứ libarchive không làm được. Dùng khi libarchive báo lỗi.
 */
object SevenZipNative {

    @Volatile private var inited = false

    private fun init() {
        if (inited) return
        synchronized(this) {
            if (inited) return
            // Nạp thẳng lib7-Zip-JBinding.so rồi khởi tạo; không dùng initSevenZipFromPlatformJAR
            // (cách đó tìm file .properties trong classpath, không chắc có trong APK).
            try {
                System.loadLibrary("7-Zip-JBinding") // bản cũ/bản đóng sẵn trong APK
            } catch (_: UnsatisfiedLinkError) {
                // Bản nhẹ: thư viện nằm trong gói tải khi cần (config.modules.sevenzip), tải ngay nếu chưa có.
                val packs = vn.aow.monika.AppGraph.packs
                try {
                    if (!packs.ready(vn.aow.monika.pack.PackManager.SEVENZIP, vn.aow.monika.pack.PackManager.SEVENZIP_LIB))
                        kotlinx.coroutines.runBlocking { vn.aow.monika.pack.PackManager.install(vn.aow.monika.pack.PackManager.SEVENZIP) {} }
                    System.load(java.io.File(packs.dir(vn.aow.monika.pack.PackManager.SEVENZIP), vn.aow.monika.pack.PackManager.SEVENZIP_LIB).absolutePath)
                } catch (e: java.io.IOException) {
                    throw UnsatisfiedLinkError("Gói 7-Zip chưa tải được: ${e.message}") // ArchiveExtractor rơi về bộ giải thuần Java
                }
            }
            SevenZip.initLoadedLibraries()
            inited = true
        }
    }

    /** [parts]: 1 file, hoặc các phần của file nén nhiều phần (phần đầu tiên đứng trước). */
    fun extract(parts: List<File>, target: File, passwords: List<String>, progress: ExtractProgress? = null) {
        init()
        val candidates = listOf<String?>(null) + passwords.filter { it.isNotEmpty() }.distinct()
        var sawPassword = false
        for (pw in candidates) {
            when (val r = tryOnce(parts, target, pw, progress)) {
                Result.OK -> return
                Result.NEED_PASSWORD -> { sawPassword = true; target.listFiles()?.forEach { it.deleteRecursively() } }
                is Result.Error -> { target.listFiles()?.forEach { it.deleteRecursively() }; throw IOException(r.message) }
            }
        }
        throw ArchiveExtractor.PasswordException(
            if (sawPassword && candidates.size > 1) "Sai mật khẩu file nén. Hãy nhập đúng mật khẩu (thường ghi cuối bài viết)."
            else "File nén có mật khẩu. Hãy nhập mật khẩu (thường ghi cuối bài viết)."
        )
    }

    private sealed class Result {
        object OK : Result()
        object NEED_PASSWORD : Result()
        class Error(val message: String) : Result()
    }

    private fun tryOnce(parts: List<File>, target: File, password: String?, progress: ExtractProgress?): Result {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        val volumes = VolumeCallback(parts, password)
        try {
            val first = volumes.getStream(parts.first().name) ?: return Result.Error("Không mở được file nén")
            val archive = try {
                SevenZip.openInArchive(null, first, volumes)
            } catch (e: SevenZipException) {
                // Mã hóa cả tên file (rar -hp) mà sai/thiếu mật khẩu → không mở được.
                return if (volumes.askedPassword) Result.NEED_PASSWORD else Result.Error("Không đọc được file nén: ${e.message}")
            }
            archive.use { a ->
                val tracker = ArchiveExtractor.Tracker(progress)
                val cb = ExtractCallback(a, target, root, password, tracker)
                try {
                    a.extract(null, false, cb)
                } catch (e: SevenZipException) {
                    if (cb.failure == null) cb.failure = e.message ?: "Lỗi giải nén"
                } finally {
                    cb.closeCurrent()
                }
                if (cb.wrongPassword) return Result.NEED_PASSWORD
                cb.failure?.let { return Result.Error(it) }
                tracker.set(100)
                return Result.OK
            }
        } finally {
            volumes.close()
        }
    }

    /** Mở các phần tiếp theo (part2.rar…) khi 7-Zip yêu cầu, và đưa mật khẩu khi mở file mã hóa tên. */
    private class VolumeCallback(parts: List<File>, private val password: String?) :
        IArchiveOpenVolumeCallback, IArchiveOpenCallback, ICryptoGetTextPassword {
        private val dir = parts.first().parentFile
        private val byName = parts.associateBy { it.name }
        private val opened = mutableMapOf<String, RandomAccessFile>()
        private var current: String? = null
        var askedPassword = false

        override fun getProperty(propID: PropID): Any? = if (propID == PropID.NAME) current else null

        override fun getStream(filename: String): IInStream? {
            val f = byName[filename] ?: File(dir, File(filename).name).takeIf { it.exists() } ?: return null
            current = f.name
            val raf = opened.getOrPut(f.name) { RandomAccessFile(f, "r") }
            raf.seek(0)
            return RandomAccessFileInStream(raf)
        }

        override fun setTotal(files: Long?, bytes: Long?) {}
        override fun setCompleted(files: Long?, bytes: Long?) {}

        override fun cryptoGetTextPassword(): String {
            askedPassword = true
            return password ?: ""
        }

        fun close() = opened.values.forEach { runCatching { it.close() } }
    }

    private class ExtractCallback(
        private val archive: net.sf.sevenzipjbinding.IInArchive,
        private val target: File,
        private val root: String,
        private val password: String?,
        private val tracker: ArchiveExtractor.Tracker,
    ) : IArchiveExtractCallback, ICryptoGetTextPassword {
        private var out: FileOutputStream? = null
        private var total = 1L
        var wrongPassword = false
        var failure: String? = null

        override fun setTotal(total: Long) { this.total = total.coerceAtLeast(1) }
        override fun setCompleted(complete: Long) { tracker.set(complete * 100 / total) }

        private var currentIndex = -1

        override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
            closeCurrent()
            currentIndex = index
            if (mode != ExtractAskMode.EXTRACT) return null
            val path = (archive.getProperty(index, PropID.PATH) as? String)?.replace('\\', '/') ?: return null
            val file = File(target, path).canonicalFile
            if (!file.path.startsWith(root)) throw SevenZipException("File nén không hợp lệ: $path")
            if (archive.getProperty(index, PropID.IS_FOLDER) == true) { file.mkdirs(); return null }
            file.parentFile?.mkdirs()
            val stream = FileOutputStream(file).also { out = it }
            return ISequentialOutStream { data -> stream.write(data); data.size }
        }

        override fun prepareOperation(mode: ExtractAskMode) {}

        override fun setOperationResult(result: ExtractOperationResult) {
            closeCurrent()
            when (result) {
                ExtractOperationResult.OK -> {}
                ExtractOperationResult.WRONG_PASSWORD, ExtractOperationResult.CRCERROR,
                ExtractOperationResult.DATAERROR -> if (isEncrypted()) wrongPassword = true else if (failure == null) failure = "File nén bị hỏng ($result)"
                else -> if (failure == null) failure = "Không giải nén được ($result)"
            }
        }

        /** File đang giải có mã hóa không → lỗi dữ liệu nghĩa là sai mật khẩu, không phải file hỏng. */
        private fun isEncrypted() = currentIndex >= 0 && runCatching { archive.getProperty(currentIndex, PropID.ENCRYPTED) == true }.getOrDefault(true)

        override fun cryptoGetTextPassword(): String = password ?: ""

        fun closeCurrent() {
            out?.let { runCatching { it.close() } }
            out = null
        }
    }
}
