package vn.aow.monika.browser

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Chặn quảng cáo cho trình duyệt nhúng, kiểu AdGuard DNS: chặn theo tên miền (cả tên miền con).
 * - Bộ lọc (config `adblock.lists`) tải về lần đầu mở trình duyệt, lưu gọn thành file nhị phân, tự cập nhật sau `updateHours`.
 * - Lưu dạng mảng mã băm 64-bit đã sắp xếp: 250 nghìn tên miền ≈ 2 MB RAM, tra cứu nhị phân rất nhanh (máy yếu vẫn ổn).
 */
class AdBlock(private val context: Context, private val http: OkHttpClient) {
    @Volatile private var hashes: LongArray = LongArray(0)
    private val lock = Mutex()
    private val file get() = File(context.filesDir, "adblock/domains.bin")

    /** Đã có bộ lọc trong máy chưa. */
    val ready get() = hashes.isNotEmpty()
    val size get() = hashes.size

    /** Nạp bộ lọc đã lưu; chưa có hoặc cũ quá hạn thì tải về. Trả về false nếu tải lỗi và chưa có bản nào. */
    suspend fun ensure(lists: List<String>, updateHours: Int): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            if (hashes.isEmpty() && file.exists()) hashes = runCatching { load(file) }.getOrDefault(LongArray(0))
            val stale = !file.exists() || System.currentTimeMillis() - file.lastModified() > updateHours * 3_600_000L
            if (stale && lists.isNotEmpty()) runCatching { download(lists) }
            hashes.isNotEmpty()
        }
    }

    /** Có chặn yêu cầu tới [host] không: kiểm tra host và mọi tên miền cha (ads.x.com → x.com). */
    fun blocks(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        val table = hashes
        if (table.isEmpty()) return false
        var d = h
        while (true) {
            if (table.binarySearch(hash(d)) >= 0) return true
            val dot = d.indexOf('.')
            if (dot < 0 || d.indexOf('.', dot + 1) < 0) return false // Dừng ở tên miền cấp 2 (không chặn cả ".com").
            d = d.substring(dot + 1)
        }
    }

    /** Dùng cho test: nạp thẳng danh sách tên miền. */
    internal fun setDomains(domains: Collection<String>) {
        hashes = domains.map { hash(it) }.distinct().toLongArray().also { it.sort() }
    }

    private fun download(lists: List<String>) {
        val client = http.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()
        val set = HashSet<Long>(300_000)
        var anyOk = false
        for (url in lists) {
            runCatching {
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    if (!r.isSuccessful) return@use
                    r.body!!.charStream().buffered().useLines { lines -> lines.forEach { l -> parseLine(l)?.let { set += hash(it) } } }
                    anyOk = true
                }
            }
        }
        if (!anyOk || set.isEmpty()) return
        val arr = set.toLongArray().also { it.sort() }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { out -> out.writeInt(arr.size); arr.forEach(out::writeLong) }
        tmp.renameTo(file)
        hashes = arr
    }

    private fun load(f: File): LongArray = DataInputStream(f.inputStream().buffered()).use { inp ->
        LongArray(inp.readInt()) { inp.readLong() }
    }

    companion object {
        private val DOMAIN = Regex("""^[a-z0-9_-]+(\.[a-z0-9_-]+)+$""")

        /**
         * Đọc 1 dòng bộ lọc, trả về tên miền cần chặn (hoặc null):
         * `domain.com` · `0.0.0.0 domain.com` / `127.0.0.1 domain.com` (hosts) · `||domain.com^` · `*.domain.com`.
         * Bỏ qua chú thích (# !), ngoại lệ (@@) và luật phức tạp (có đường dẫn, $tùy-chọn khác third-party).
         */
        fun parseLine(raw: String): String? {
            var l = raw.trim().lowercase()
            if (l.isEmpty() || l[0] == '#' || l[0] == '!' || l[0] == '[' || l.startsWith("@@")) return null
            l = l.substringBefore(" #").trim()
            val parts = l.split(Regex("\\s+"))
            if (parts.size == 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::")) l = parts[1]
            else if (parts.size != 1) return null
            if (l.startsWith("||")) {
                l = l.removePrefix("||")
                l = when {
                    l.endsWith("^\$third-party") -> l.removeSuffix("^\$third-party")
                    l.endsWith("^") -> l.removeSuffix("^")
                    else -> return null
                }
            }
            l = l.removePrefix("*.")
            if (l == "localhost" || l.endsWith(".local") || !DOMAIN.matches(l)) return null
            return l
        }

        /** FNV-1a 64-bit. */
        fun hash(s: String): Long {
            var h = -0x340d631b7bdddcdbL
            for (ch in s) { h = h xor ch.code.toLong(); h *= 0x100000001b3L }
            return h
        }
    }
}
