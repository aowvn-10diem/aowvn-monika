package vn.aow.monika.config

import android.content.Context
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import vn.aow.monika.BuildConfig
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.security.OfficialCert

/**
 * V206: xác thực `config.signed.json` bằng Ed25519 (BouncyCastle đã có trong APK).
 *
 * Một tệp, một lượt GET: dòng đầu `v1:<128 hex chữ ký>`, phần còn lại là **nguyên văn bytes UTF-8** của
 * `config/monika-config.json` (ký trên đúng bytes đó nên không cần chuẩn hoá JSON).
 *
 * Khi [OFFICIAL_CONFIG_ED25519_PUBKEY_HEX] còn rỗng (chưa tạo khoá) app đọc `config.json` như cũ. Khi đã
 * điền khoá, [signatureRequired] quyết định có bắt buộc chữ ký hay không: chỉ bản chính thức mới bắt buộc,
 * bản debug/tự build (GPLv3) giữ hành vi cũ.
 */
object ConfigSignature {
    /** Khoá công khai Ed25519 (32 byte = 64 hex) của kênh config; điền ở bước tạo khoá. */
    const val OFFICIAL_CONFIG_ED25519_PUBKEY_HEX = ""

    private const val HEADER_PREFIX = "v1:"
    private const val PUBLIC_KEY_HEX_LENGTH = 64
    private const val SIGNATURE_HEX_LENGTH = 128

    /** true khi đã cấu hình khoá công khai hợp lệ (đúng 64 ký tự hex). */
    fun hasPublicKey(publicKeyHex: String = OFFICIAL_CONFIG_ED25519_PUBKEY_HEX): Boolean =
        publicKeyHex.length == PUBLIC_KEY_HEX_LENGTH && decodeHex(publicKeyHex) != null

    /**
     * true khi bản đang chạy **phải** có chữ ký hợp lệ.
     *
     * Dùng [Diagnostics.certSha256] + [OfficialCert.isForeignBuild] (không có hàm `isOfficialBuild`):
     * bản debug hoặc chứng thư lạ (tự build GPLv3) trả false để người chơi tự build vẫn dùng được.
     */
    fun signatureRequired(
        context: Context,
        publicKeyHex: String = OFFICIAL_CONFIG_ED25519_PUBKEY_HEX,
    ): Boolean {
        if (!hasPublicKey(publicKeyHex)) return false
        if (BuildConfig.DEBUG) return false
        val cert = Diagnostics.certSha256(context)
        val foreign = OfficialCert.isForeignBuild(cert, SecurityConfig.OFFICIAL_CERT_SHA256, debugBuild = false)
        return !foreign
    }

    /**
     * Tách + xác thực tệp đã ký; trả về nguyên văn JSON khi chữ ký đúng, null khi sai/không đúng định dạng.
     * Không ném ngoại lệ: người gọi tự quyết định fallback hay từ chối.
     */
    fun verifyAndExtract(
        signedText: String,
        publicKeyHex: String = OFFICIAL_CONFIG_ED25519_PUBKEY_HEX,
    ): String? {
        if (!hasPublicKey(publicKeyHex)) return null
        val headerEnd = signedText.indexOf('\n')
        if (headerEnd <= 0) return null
        val header = signedText.substring(0, headerEnd)
        if (!header.startsWith(HEADER_PREFIX)) return null
        val signatureHex = header.substring(HEADER_PREFIX.length)
        if (signatureHex.length != SIGNATURE_HEX_LENGTH) return null
        val signature = decodeHex(signatureHex) ?: return null
        val publicKey = decodeHex(publicKeyHex) ?: return null
        val payload = signedText.substring(headerEnd + 1).toByteArray(Charsets.UTF_8)
        if (payload.isEmpty()) return null
        return if (verify(payload, signature, publicKey)) String(payload, Charsets.UTF_8) else null
    }

    // diag: bỏ qua lỗi dựng khoá/verify, coi như chữ ký không hợp lệ
    internal fun verify(payload: ByteArray, signature: ByteArray, publicKey: ByteArray): Boolean = runCatching {
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(publicKey, 0))
        verifier.update(payload, 0, payload.size)
        verifier.verifySignature(signature)
    }.getOrDefault(false)

    /** Hex thường (không phân biệt hoa/thường); trả null nếu lẻ ký tự hoặc có ký tự không phải hex. */
    internal fun decodeHex(text: String): ByteArray? {
        if (text.length % 2 != 0) return null
        val out = ByteArray(text.length / 2)
        for (i in out.indices) {
            val high = Character.digit(text[i * 2], 16)
            val low = Character.digit(text[i * 2 + 1], 16)
            if (high < 0 || low < 0) return null
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }
}
