package vn.aow.monika.apkinstall.repack

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Tạo chứng chỉ X.509 tự ký (RSA + SHA-256) bằng cách tự ghép DER — Android không có sẵn công cụ tạo chứng chỉ
 * và không muốn kéo thêm cả thư viện BouncyCastle chỉ để ký APK. Android không kiểm hạn/chuỗi của chứng chỉ ký APK.
 */
object SelfSignedCert {
    fun create(pair: KeyPair, commonName: String, validYears: Int = 30, now: Long = System.currentTimeMillis()): X509Certificate {
        val sigAlg = seq(oid("1.2.840.113549.1.1.11"), byteArrayOf(0x05, 0x00)) // sha256WithRSAEncryption, NULL
        val name = seq(set(seq(oid("2.5.4.3"), tlv(0x0C, commonName.toByteArray(Charsets.UTF_8)))))
        val notBefore = utcTime(now - 24 * 3600_000L)
        val notAfter = generalizedTime(now + validYears * 365L * 24 * 3600_000L)
        val serial = tlv(0x02, BigInteger(64, java.security.SecureRandom()).abs().add(BigInteger.ONE).toByteArray())
        val tbs = seq(
            tlv(0xA0, tlv(0x02, byteArrayOf(2))), // version = v3
            serial, sigAlg, name, seq(notBefore, notAfter), name, pair.public.encoded,
        )
        val sig = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(tbs); sign() }
        val cert = seq(tbs, sigAlg, tlv(0x03, byteArrayOf(0) + sig))
        return CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    private fun len(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n < 0x100 -> byteArrayOf(0x81.toByte(), n.toByte())
        n < 0x10000 -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    private fun tlv(tag: Int, body: ByteArray) = ByteArrayOutputStream().apply { write(tag); write(len(body.size)); write(body) }.toByteArray()
    private fun seq(vararg parts: ByteArray) = tlv(0x30, parts.fold(ByteArray(0)) { a, b -> a + b })
    private fun set(vararg parts: ByteArray) = tlv(0x31, parts.fold(ByteArray(0)) { a, b -> a + b })

    private fun oid(dotted: String): ByteArray {
        val p = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((p[0] * 40 + p[1]).toInt())
        for (v in p.drop(2)) {
            val bytes = ArrayList<Int>()
            var x = v
            bytes += (x and 0x7F).toInt(); x = x shr 7
            while (x > 0) { bytes += ((x and 0x7F) or 0x80).toInt(); x = x shr 7 }
            bytes.reversed().forEach { out.write(it) }
        }
        return tlv(0x06, out.toByteArray())
    }

    private fun fmt(pattern: String, t: Long) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t))
    private fun utcTime(t: Long) = tlv(0x17, fmt("yyMMddHHmmss'Z'", t).toByteArray())
    private fun generalizedTime(t: Long) = tlv(0x18, fmt("yyyyMMddHHmmss'Z'", t).toByteArray())
}
