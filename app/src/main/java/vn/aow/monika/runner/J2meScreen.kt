package vn.aow.monika.runner

import vn.aow.monika.config.J2meScreenConfig
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** Kích thước màn hình đoán cho 1 game Java; [source] ghi nguồn đoán để kiểm/test. */
data class J2meScreen(val width: Int, val height: Int, val source: String)

/**
 * V135: đoán độ phân giải game Java (J2ME) theo thứ tự:
 * 1. thuộc tính kích thước trong JAD (descriptor theo máy) rồi tới manifest trong jar;
 * 2. ảnh trong jar trùng đúng một độ phân giải máy J2ME thường gặp (ảnh lớn nhất thắng);
 * 3. mặc định trong config.
 *
 * Chỉ đọc file game, không ghi gì. J2ME Loader chỉ áp kết quả cho game chưa có `config.json`.
 */
object J2meScreenDetector {
    private val imageExtensions = setOf("png", "gif", "jpg", "jpeg")
    private val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun detect(file: File?, cfg: J2meScreenConfig): J2meScreen {
        val fallback = J2meScreen(cfg.defaultWidth, cfg.defaultHeight, "default")
        if (file == null || !file.isFile) return fallback
        val isJad = file.extension.equals("jad", true)
        val jar = (if (isJad) sibling(file, ".jar") else file)?.takeIf { it.isFile }
        val jad = (if (isJad) file else sibling(file, ".jad"))?.takeIf { it.isFile }

        // JAD trước manifest: JAD là descriptor theo máy, có thể ghi đè kích thước trong jar.
        val sources = listOfNotNull(jad?.let { "jad" to it }, jar?.let { "manifest" to it })
        for ((kind, source) in sources) {
            // diag: bỏ qua nguồn JAD/jar đọc lỗi, chuyển sang nguồn kế tiếp.
            val text = runCatching {
                if (kind == "jad") source.readText() else readManifest(source)
            }.getOrNull() ?: continue
            val attributes = parseAttributes(text)
            for (name in cfg.attributes) {
                val size = parseSize(attributes[name.lowercase()]) ?: continue
                return J2meScreen(size.first, size.second, "$kind:$name")
            }
        }
        jar?.let { game ->
            // diag: bỏ qua jar không mở được, dùng mặc định trong config.
            runCatching { guessByImage(game, cfg) }.getOrNull()?.let { return it }
        }
        return fallback
    }

    private fun sibling(file: File, extension: String): File? =
        file.parentFile?.listFiles()?.firstOrNull {
            it.isFile && it.name.equals(file.nameWithoutExtension + extension, true)
        }

    private fun readManifest(jar: File): String? = ZipFile(jar).use { zip ->
        val entry = zip.entries().asSequence()
            .firstOrNull { it.name.equals("META-INF/MANIFEST.MF", true) } ?: return@use null
        zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    /** Đọc khối "Tên: giá trị" của JAD/manifest, gộp dòng nối tiếp (bắt đầu bằng 1 dấu cách). */
    private fun parseAttributes(text: String): Map<String, String> {
        val out = HashMap<String, String>()
        var name: String? = null
        val value = StringBuilder()
        fun flush() {
            val key = name ?: return
            out[key] = value.toString().trim()
            name = null
            value.setLength(0)
        }
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trimEnd('\r')
            if (line.startsWith(" ") && name != null) {
                value.append(line.trim())
            } else {
                flush()
                val colon = line.indexOf(':')
                if (colon > 0) {
                    name = line.substring(0, colon).trim().lowercase()
                    value.append(line.substring(colon + 1).trim())
                }
            }
        }
        flush()
        return out
    }

    private fun parseSize(value: String?): Pair<Int, Int>? {
        if (value.isNullOrBlank()) return null
        val numbers = Regex("\\d+").findAll(value).mapNotNull { it.value.toIntOrNull() }.take(2).toList()
        if (numbers.size < 2) return null
        val width = numbers[0]
        val height = numbers[1]
        if (width !in 64..4096 || height !in 64..4096) return null
        return width to height
    }

    private fun guessByImage(jar: File, cfg: J2meScreenConfig): J2meScreen? {
        if (cfg.maxImages <= 0 || cfg.maxImageBytes <= 0) return null
        val presets = cfg.presets.filter { it.size == 2 }.map { it[0] to it[1] }.toSet()
        if (presets.isEmpty()) return null
        var best: J2meScreen? = null
        ZipFile(jar).use { zip ->
            var scanned = 0
            for (entry in zip.entries().asSequence()) {
                if (scanned >= cfg.maxImages) break
                if (entry.isDirectory) continue
                if (entry.name.substringAfterLast('.', "").lowercase() !in imageExtensions) continue
                scanned++
                // diag: bỏ qua ảnh hỏng hoặc lạ định dạng, xét ảnh tiếp theo.
                val size = runCatching { readImageSize(zip, entry, cfg.maxImageBytes) }.getOrNull() ?: continue
                if (size !in presets) continue
                val current = best
                if (current == null || size.first * size.second > current.width * current.height) {
                    best = J2meScreen(size.first, size.second, "image:${entry.name}")
                }
            }
        }
        return best
    }

    private fun readImageSize(zip: ZipFile, entry: ZipEntry, limit: Int): Pair<Int, Int>? {
        zip.getInputStream(entry).use { input ->
            val data = input.readUpTo(limit)
            if (data.size >= 24 && data.isPng()) return validSize(be32(data, 16), be32(data, 20))
            if (data.size >= 10 && data.isGif()) return validSize(le16(data, 6), le16(data, 8))
            if (data.size >= 4 && data.isJpeg()) return jpegSize(data)
        }
        return null
    }

    private fun jpegSize(data: ByteArray): Pair<Int, Int>? {
        var i = 2
        while (i + 4 <= data.size) {
            if (u8(data, i) != 0xFF) {
                i++
                continue
            }
            val marker = u8(data, i + 1)
            if (marker == 0x01 || marker in 0xD0..0xD9) {
                i += 2
                continue
            }
            val segment = be16(data, i + 2)
            if (segment < 2) return null
            if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                if (i + 9 > data.size) return null
                return validSize(be16(data, i + 7), be16(data, i + 5))
            }
            i += 2 + segment
        }
        return null
    }

    private fun validSize(width: Int, height: Int): Pair<Int, Int>? =
        if (width in 1..4096 && height in 1..4096) width to height else null

    private fun ByteArray.isPng(): Boolean = pngMagic.indices.all { this[it] == pngMagic[it] }

    private fun ByteArray.isGif(): Boolean =
        u8(this, 0) == 0x47 && u8(this, 1) == 0x49 && u8(this, 2) == 0x46 && u8(this, 3) == 0x38

    private fun ByteArray.isJpeg(): Boolean = u8(this, 0) == 0xFF && u8(this, 1) == 0xD8

    private fun u8(data: ByteArray, index: Int): Int = data[index].toInt() and 0xFF

    private fun be16(data: ByteArray, index: Int): Int = (u8(data, index) shl 8) or u8(data, index + 1)

    private fun be32(data: ByteArray, index: Int): Int =
        (u8(data, index) shl 24) or (u8(data, index + 1) shl 16) or (u8(data, index + 2) shl 8) or u8(data, index + 3)

    private fun le16(data: ByteArray, index: Int): Int = u8(data, index) or (u8(data, index + 1) shl 8)

    private fun InputStream.readUpTo(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var length = 0
        while (length < limit) {
            val read = read(buffer, length, limit - length)
            if (read < 0) break
            length += read
        }
        return buffer.copyOf(length)
    }
}
