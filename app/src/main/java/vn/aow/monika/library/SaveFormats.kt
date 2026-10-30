package vn.aow.monika.library

import vn.aow.monika.config.SaveProfile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.ZipInputStream

/** Không có ROM/save thật: các kiểm tra định dạng được đối chiếu nguồn gốc trong V134-save-phan1.md. */
object SaveFormats {
    const val MAX_FILE = 32 * 1024 * 1024
    const val MAX_TOTAL = 64 * 1024 * 1024
    const val MAX_FILES = 256
    const val DSV_TEXT = "|<--Snip above here to create a raw sav by excluding this DeSmuME savedata footer:"
    const val DSV_COOKIE = "|-DESMUME SAVE-|"

    fun checked(ok: Boolean, message: String) { if (!ok) throw SaveTransferException(message) }

    fun read(input: InputStream, limit: Int = MAX_FILE): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            checked(out.size().toLong() + n <= limit, "File save vượt giới hạn dung lượng.")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    fun archive(input: InputStream): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                checked(++count <= MAX_FILES, "ZIP có quá nhiều mục.")
                val name = entry.name.removeSuffix("/")
                checked(safePath(name), "ZIP chứa đường dẫn không an toàn.")
                if (entry.isDirectory) continue
                checked(result.keys.none { it.equals(name, ignoreCase = true) }, "ZIP có tên file trùng hoặc chỉ khác chữ hoa/thường.")
                val bytes = read(zip, minOf(MAX_FILE, (MAX_TOTAL - total).toInt()))
                total += bytes.size
                result[name] = bytes
            }
        }
        checked(result.isNotEmpty(), "ZIP không có save.")
        return result
    }

    fun safePath(path: String): Boolean = path.isNotEmpty() && path.length <= 240 &&
        !path.contains('\\') && !path.contains(':') && path.none { it.code < 32 } &&
        path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }

    fun convert(bytes: ByteArray, extension: String, profile: SaveProfile): ByteArray {
        val configured = profile.imports[extension.lowercase(Locale.ROOT)]
        if (configured == "ps1") return ConsoleSaveFormats.ps1(bytes)
        checked(configured == "raw" || configured == "desmume", "File không khớp hệ hoặc định dạng save chưa được hỗ trợ.")
        val cookie = DSV_COOKIE.toByteArray(Charsets.US_ASCII)
        val hasDsvCookie = bytes.size >= cookie.size && bytes.copyOfRange(bytes.size - cookie.size, bytes.size).contentEquals(cookie)
        val converter = if (hasDsvCookie) {
            checked("desmume" in profile.imports.values, "File DeSmuME không khớp hệ đã chọn.")
            "desmume"
        } else configured
        checked(!String(bytes.take(31).toByteArray(), Charsets.US_ASCII).startsWith("NocashGbaBackupMediaSavDataFile"),
            "Save nén No\$GBA chưa được hỗ trợ. Hãy xuất save thô.")
        val raw = if (converter == "desmume") desmume(bytes) else bytes.copyOf()
        val sizes = profile.sizes.sorted()
        checked(sizes.isNotEmpty() && sizes.all { it in 1..MAX_FILE }, "Cấu hình kích thước save không hợp lệ.")
        checked(raw.size >= sizes.first(), "Save quá ngắn hoặc không khớp hệ.")
        val target = if (profile.targetBytes > 0) profile.targetBytes else sizes.firstOrNull { it >= raw.size }
        checked(target != null && target in sizes, "Kích thước save không khớp hệ.")
        val size = target!!
        checked(raw.size <= size || raw.drop(size).all { it == 0xff.toByte() }, "Không cắt phần dữ liệu save chưa xác minh.")
        return ByteArray(size) { i -> if (i < raw.size) raw[i] else 0xff.toByte() }
    }

    /** Footer version 0: text + 6 uint32 little endian + cookie. Không cắt mù 122 byte. */
    fun desmume(bytes: ByteArray): ByteArray {
        val text = DSV_TEXT.toByteArray(Charsets.US_ASCII)
        val cookie = DSV_COOKIE.toByteArray(Charsets.US_ASCII)
        val footer = text.size + 24 + cookie.size
        val start = bytes.size - footer
        checked(start > 0 && bytes.copyOfRange(bytes.size - cookie.size, bytes.size).contentEquals(cookie), "File DSV thiếu đuôi DeSmuME hợp lệ.")
        checked(bytes.copyOfRange(start, start + text.size).contentEquals(text), "File DSV không đúng định dạng đã kiểm.")
        val meta = ByteBuffer.wrap(bytes, start + text.size, 24).order(ByteOrder.LITTLE_ENDIAN)
        val written = meta.int.toLong() and 0xffffffffL
        val padded = meta.int.toLong() and 0xffffffffL
        meta.int // loại bộ nhớ
        val addressBytes = meta.int
        meta.int // dung lượng chip
        val version = meta.int
        checked(version == 0 && padded == start.toLong() && written in 1..padded && addressBytes in 1..4,
            "Thông tin dung lượng hoặc phiên bản DSV không hợp lệ.")
        return bytes.copyOf(start)
    }

    /** RMS J2ME Loader/JL-Mod: chỉ header v3 không mã hóa và các record đủ cặp; tuyệt đối không nhận JAR/config. */
    fun validateRms(files: Map<String, ByteArray>) {
        checked(files.isNotEmpty() && files.keys.all { safePath(it) && '/' !in it && (it.endsWith(".rsh") || it.endsWith(".rsr")) },
            "Chọn thư mục RMS có file .rsh/.rsr; không chọn game JAR.")
        val headers = files.filterKeys { it.endsWith(".rsh") }
        checked(headers.isNotEmpty(), "RMS thiếu header .rsh đi kèm.")
        val expected = mutableSetOf<String>()
        headers.forEach { (name, bytes) ->
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                val magic = ByteArray(6); input.readFully(magic)
                checked(String(magic, Charsets.US_ASCII) == "MIDRMS" && input.readUnsignedByte() == 3 &&
                    input.readUnsignedByte() == 0 && input.readUnsignedByte() == 0, "Header RMS không đúng bản J2ME Loader đã kiểm.")
                val store = input.readUTF()
                checked(store.length <= 32 && store == name.removeSuffix(".rsh"), "Tên kho RMS không khớp header.")
                input.readLong(); input.readInt(); input.readInt(); input.readByte()
                val count = input.readInt()
                checked(count in 0..MAX_FILES, "Số record RMS không hợp lệ.")
                checked(input.available() == 0 || input.available() == 4, "Header RMS có phần cuối chưa được hỗ trợ.")
                if (input.available() == 4) input.readInt()
                val records = files.filterKeys { it.startsWith("$store.") && it.endsWith(".rsr") && it.removePrefix("$store.").removeSuffix(".rsr").toIntOrNull() != null }
                checked(records.size == count, "RMS thiếu record; cần xuất cả thư mục RMS của game.")
                records.forEach { (recordName, record) ->
                    DataInputStream(ByteArrayInputStream(record)).use { r ->
                        val id = r.readInt(); r.readInt(); val length = r.readInt()
                        checked(id > 0 && recordName == "$store.$id.rsr" && length >= 0 && length == r.available(), "Record RMS không khớp hoặc bị hỏng.")
                    }
                }
                expected += name; expected += records.keys
            }
        }
        checked(expected == files.keys, "RMS có record không thuộc kho đã chọn.")
    }
}

class SaveTransferException(message: String) : java.io.IOException(message)
