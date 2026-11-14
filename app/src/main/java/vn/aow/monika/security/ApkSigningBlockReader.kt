package vn.aow.monika.security

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * [A85/A78-3] Đọc trực tiếp chứng thư ký trong khối `APK Sig Block 42` của tệp APK.
 *
 * `PackageManager` có thể bị hook (công cụ đóng gói lại chèn stub trả chứng thư gốc), nên phải đối
 * chiếu thêm chứng thư đọc từ chính tệp trên đĩa. Thuật toán port từ `scripts/verify-apk-cert.py`:
 * tìm EOCD → footer khối chữ ký → cặp id (v3 trước, v2 sau) → signer → signed data → certificates.
 * Trả `null` khi APK không có khối chữ ký hoặc dữ liệu hỏng (không đoán).
 */
object ApkSigningBlockReader {
    private val MAGIC = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)
    private const val EOCD = 0x06054b50
    private const val V2_ID = 0x7109871a
    private const val V3_ID = 0xf05368c0.toInt()
    private const val MAX_EOCD_SEARCH = 65557

    fun signingCertSha256(apk: File): String? = runCatching {
        RandomAccessFile(apk, "r").use { file ->
            val size = file.length()
            val cdOffset = centralDirectoryOffset(file, size) ?: return null
            val pairs = signingBlockPairs(file, cdOffset) ?: return null
            val scheme = pairs[V3_ID] ?: pairs[V2_ID] ?: return null
            val certificate = firstCertificate(scheme) ?: return null
            MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }
        }
    }.getOrNull()

    private fun centralDirectoryOffset(file: RandomAccessFile, size: Long): Long? {
        val tailSize = minOf(size, MAX_EOCD_SEARCH.toLong()).toInt()
        if (tailSize < 22) return null
        val tail = ByteArray(tailSize)
        file.seek(size - tailSize)
        file.readFully(tail)
        var pos = tail.lastIndexOfEocd()
        while (pos >= 0) {
            if (pos + 22 <= tail.size && u16(tail, pos + 20) + 22 + pos == tail.size) {
                val offset = u32(tail, pos + 16)
                if (offset != 0xFFFFFFFFL && offset <= size - 24) return offset
            }
            pos = tail.lastIndexOfEocd(pos - 1)
        }
        return null
    }

    private fun signingBlockPairs(file: RandomAccessFile, cdOffset: Long): Map<Int, ByteArray>? {
        val footerOffset = cdOffset - 24
        if (footerOffset < 0) return null
        val footer = ByteArray(24)
        file.seek(footerOffset)
        file.readFully(footer)
        if (!footer.copyOfRange(8, 24).contentEquals(MAGIC)) return null
        val blockSize = u64(footer, 0)
        if (blockSize < 24 || blockSize > cdOffset - 8) return null
        val blockStart = cdOffset - blockSize - 8
        if (blockStart < 0) return null
        val block = ByteArray((blockSize + 8).toInt().coerceAtLeast(0))
        if (block.isEmpty()) return null
        file.seek(blockStart)
        file.readFully(block)
        if (block.size.toLong() != blockSize + 8 || u64(block, 0) != blockSize) return null
        if (!block.copyOfRange(block.size - 16, block.size).contentEquals(MAGIC)) return null

        val pairsEnd = block.size - 24
        val pairs = HashMap<Int, ByteArray>()
        var pos = 8
        while (pos < pairsEnd) {
            if (pos + 8 > pairsEnd) return null
            val pairSize = u64(block, pos)
            if (pairSize < 4 || pairSize > pairsEnd - pos - 8) return null
            val pairEnd = pos + 8 + pairSize.toInt()
            val id = u32(block, pos + 8).toInt()
            pairs.putIfAbsent(id, block.copyOfRange(pos + 12, pairEnd))
            pos = pairEnd
        }
        return if (pos == pairsEnd) pairs else null
    }

    private fun firstCertificate(scheme: ByteArray): ByteArray? {
        val (signers, signersEnd) = lengthPrefixed(scheme, 0) ?: return null
        if (signersEnd != scheme.size || signers.isEmpty()) return null
        val (signer, _) = lengthPrefixed(signers, 0) ?: return null
        val (signedData, _) = lengthPrefixed(signer, 0) ?: return null
        val (_, afterDigests) = lengthPrefixed(signedData, 0) ?: return null
        val (certificates, _) = lengthPrefixed(signedData, afterDigests) ?: return null
        val (certificate, _) = lengthPrefixed(certificates, 0) ?: return null
        return certificate.takeIf { it.isNotEmpty() }
    }

    private fun lengthPrefixed(data: ByteArray, offset: Int): Pair<ByteArray, Int>? {
        if (offset < 0 || offset + 4 > data.size) return null
        val length = u32(data, offset)
        val start = offset + 4
        val end = start + length
        if (length > data.size || end > data.size) return null
        return data.copyOfRange(start, end.toInt()) to end.toInt()
    }

    private fun ByteArray.lastIndexOfEocd(from: Int = size - 4): Int {
        var i = minOf(from, size - 4)
        while (i >= 0) {
            if (u32(this, i) == EOCD.toLong()) return i
            i--
        }
        return -1
    }

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)

    private fun u32(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xff) or ((b[o + 1].toLong() and 0xff) shl 8) or
            ((b[o + 2].toLong() and 0xff) shl 16) or ((b[o + 3].toLong() and 0xff) shl 24)

    private fun u64(b: ByteArray, o: Int): Long {
        var value = 0L
        for (i in 7 downTo 0) value = (value shl 8) or (b[o + i].toLong() and 0xff)
        return value
    }
}
