package vn.aow.monika.apkinstall.repack

import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/** Bọc/mở khóa bytes khi lưu đĩa (trên máy thật dùng Android Keystore; test dùng bản không mã hóa). */
interface KeyWrap {
    fun wrap(plain: ByteArray): ByteArray
    fun unwrap(cipher: ByteArray): ByteArray
    object Plain : KeyWrap {
        override fun wrap(plain: ByteArray) = plain
        override fun unwrap(cipher: ByteArray) = cipher
    }
}

/**
 * Khóa ký RIÊNG của máy này, dùng để ký lại các game Monika đã chỉnh (Cách 1). Tạo lần đầu, dùng lại mãi:
 * cùng khóa → cập nhật game đã chỉnh không phải gỡ. Gỡ Monika = mất khóa → game đã chỉnh phải gỡ trước khi cài lại.
 */
class RepackKeyStore(private val dir: File, private val wrap: KeyWrap = KeyWrap.Plain) {
    class Signer(val privateKey: PrivateKey, val certificate: X509Certificate)

    private val keyFile get() = File(dir, "repack.key")
    private val certFile get() = File(dir, "repack.crt")

    @Synchronized fun signer(): Signer {
        load()?.let { return it }
        dir.mkdirs()
        val pair: KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val cert = SelfSignedCert.create(pair, "Aow Monika Repack")
        keyFile.writeBytes(wrap.wrap(pair.private.encoded))
        certFile.writeBytes(cert.encoded)
        return Signer(pair.private, cert)
    }

    private fun load(): Signer? = runCatching {
        if (!keyFile.isFile || !certFile.isFile) return null
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(wrap.unwrap(keyFile.readBytes())))
        val cert = certFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate }
        Signer(key, cert)
    }.getOrNull()

    /** SHA-256 của chứng chỉ (để hiển thị/so khớp). */
    fun fingerprint(): String = java.security.MessageDigest.getInstance("SHA-256").digest(signer().certificate.encoded).joinToString("") { "%02x".format(it) }
}
