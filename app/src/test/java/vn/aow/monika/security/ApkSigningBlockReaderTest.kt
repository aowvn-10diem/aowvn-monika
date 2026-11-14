package vn.aow.monika.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * [A85/A78-3] Đọc chứng thư trực tiếp từ khối `APK Sig Block 42` (v2), không qua PackageManager.
 */
class ApkSigningBlockReaderTest {
    @get:Rule val tmp = TemporaryFolder()

    private val magic = "APK Sig Block 42".toByteArray(Charsets.US_ASCII)

    private fun u32(v: Int) = byteArrayOf(
        (v and 0xff).toByte(), ((v ushr 8) and 0xff).toByte(),
        ((v ushr 16) and 0xff).toByte(), ((v ushr 24) and 0xff).toByte(),
    )

    private fun u64(v: Long) = ByteArray(8) { i -> ((v ushr (8 * i)) and 0xff).toByte() }

    private fun lp(bytes: ByteArray) = u32(bytes.size) + bytes

    private fun zip(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun withSigningBlock(zipBytes: ByteArray, certificate: ByteArray): ByteArray {
        val eocd = (zipBytes.size - 22 downTo 0).first { i ->
            zipBytes[i] == 0x50.toByte() && zipBytes[i + 1] == 0x4b.toByte() &&
                zipBytes[i + 2] == 0x05.toByte() && zipBytes[i + 3] == 0x06.toByte()
        }
        fun le32(at: Int) = (zipBytes[at].toInt() and 0xff) or ((zipBytes[at + 1].toInt() and 0xff) shl 8) or
            ((zipBytes[at + 2].toInt() and 0xff) shl 16) or ((zipBytes[at + 3].toInt() and 0xff) shl 24)

        val cdOffset = le32(eocd + 16)
        val signedData = lp(lp(ByteArray(0))) + lp(lp(certificate))
        val scheme = lp(lp(lp(signedData)))
        val pair = u64(4L + scheme.size) + u32(0x7109871a) + scheme
        val blockSize = pair.size + 24L
        val block = u64(blockSize) + pair + u64(blockSize) + magic

        val out = ByteArrayOutputStream()
        out.write(zipBytes, 0, cdOffset)
        out.write(block)
        out.write(zipBytes, cdOffset, zipBytes.size - cdOffset)
        val result = out.toByteArray()
        val newEocd = eocd + block.size
        val newOffset = cdOffset + block.size
        val encoded = u32(newOffset)
        encoded.copyInto(result, newEocd + 16)
        return result
    }

    @Test fun readsV2CertificateFromSigningBlock() {
        val certificate = "fixture-certificate-der".toByteArray()
        val apk = File(tmp.newFolder("apk"), "app.apk")
        apk.writeBytes(withSigningBlock(zip(), certificate))
        val expected = MessageDigest.getInstance("SHA-256").digest(certificate).joinToString("") { "%02x".format(it) }
        assertEquals(expected, ApkSigningBlockReader.signingCertSha256(apk))
    }

    @Test fun returnsNullWithoutSigningBlock() {
        val apk = File(tmp.newFolder("plain"), "unsigned.apk")
        apk.writeBytes(zip())
        assertNull(ApkSigningBlockReader.signingCertSha256(apk))
    }

    @Test fun returnsNullForTruncatedFile() {
        val apk = File(tmp.newFolder("bad"), "bad.apk")
        apk.writeBytes(byteArrayOf(0x50, 0x4b, 0x05, 0x06))
        assertNull(ApkSigningBlockReader.signingCertSha256(apk))
    }
}
