package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.ConfigEndpoints
import vn.aow.monika.config.ConfigRepository
import vn.aow.monika.config.ConfigSignature
import vn.aow.monika.config.SecurityConfig
import vn.aow.monika.diag.Breadcrumbs
import vn.aow.monika.diag.Diagnostics
import vn.aow.monika.ui.TestApp
import java.io.File
import java.security.SecureRandom

/**
 * V206: xác thực `config.signed.json` bằng Ed25519 và đường fallback của [ConfigRepository]:
 * - chữ ký đúng → dùng bản đã ký (1 lượt GET, không gọi thêm config.json);
 * - sửa 1 byte config / sai khoá → từ chối chữ ký;
 * - chưa có khoá công khai → đọc bản không ký như cũ;
 * - chưa có config.signed.json → rơi về config.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ConfigSignatureTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val cache get() = File(app.filesDir, ConfigRepository.ASSET_NAME)
    private val bundled get() = ConfigRepository.parse(
        app.assets.open(ConfigRepository.ASSET_NAME).bufferedReader().use { it.readText() }
    ).configVersion
    private val privateKey = Ed25519PrivateKeyParameters(SecureRandom())
    private val publicKeyHex = hex(privateKey.generatePublicKey().encoded)
    private val requests = mutableListOf<String>()
    private var previousSender: ((android.content.Context, Diagnostics.Report) -> Unit)? = null

    @Before fun clean() {
        clearFiles()
        previousSender = Diagnostics.opSender
        Diagnostics.opSender = null
    }

    @After fun restore() {
        clearFiles()
        Diagnostics.opSender = previousSender
    }

    private fun clearFiles() {
        listOf(
            cache,
            File(cache.path + ".new"),
            File(cache.path + ".bak"),
            File(app.filesDir, ConfigRepository.VERIFIED_MARKER),
            File(app.filesDir, "config.checked"),
            File(app.filesDir, "diag"),
        ).forEach { it.deleteRecursively() }
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun sign(payload: ByteArray): String {
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(payload, 0, payload.size)
        return "v1:" + hex(signer.generateSignature()) + "\n" + String(payload, Charsets.UTF_8)
    }

    private fun repo(
        publicKeyHex: String,
        routes: Map<String, String?>,
        batBuocChuKy: Boolean? = null,
    ): ConfigRepository =
        ConfigRepository(
            app,
            OkHttpClient.Builder().addInterceptor { chain ->
                val url = chain.request().url.toString()
                requests += url
                val body = routes[url]
                val code = if (body == null) 404 else 200
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(if (body == null) "Not Found" else "OK")
                    .body(ResponseBody.create(null, body.orEmpty()))
                    .build()
            }.build(),
            publicKeyHex,
            // null = chính sách thật (bản debug ⇒ không bắt buộc); true/false = giả lập bản chính thức/tự build.
            { c, key ->
                batBuocChuKy ?: ConfigSignature.signatureRequired(c, key)
            },
        )

    private fun config(version: Int, marker: String = "") =
        """{"configVersion":$version,"app":{"latestVersionName":"$marker"}}"""

    @Test fun chuKyDungThiNhanNguyenVanJson() {
        val json = config(7, "da-ky")
        assertEquals(json, ConfigSignature.verifyAndExtract(sign(json.toByteArray()), publicKeyHex))
    }

    @Test fun tuongThichVoiTepDoScriptKyConfig() {
        // Vector sinh bởi scripts/sign-config.py (seed 000102…1f): bảo đảm app đọc đúng định dạng script ghi.
        val scriptPubkey = "03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8"
        val payload = """{"configVersion":42,"app":{"latestVersionName":"vector-script"}}"""
        val signed = "v1:8e9d8d463cb7c9bd2e6c20eb2da8fcb7ac5ea2eb47e3b3286e1dde0e2f1d96f4" +
            "445d426b6ec2bf8043d8299a0c74a9fac76b184690afdd1bcb285d818e3b1b0c\n" + payload
        assertEquals(payload, ConfigSignature.verifyAndExtract(signed, scriptPubkey))
    }

    @Test fun suaMotByteTrongConfigThiTuChoiChuKy() {
        val signed = sign(config(7, "goc").toByteArray())
        val tampered = signed.replace("\"goc\"", "\"gox\"")
        assertNull("đổi 1 byte phải hỏng chữ ký", ConfigSignature.verifyAndExtract(tampered, publicKeyHex))
    }

    @Test fun suaChuKyHoacSaiKhoaThiTuChoi() {
        val signed = sign(config(7).toByteArray())
        val signatureStart = "v1:".length
        val flipped = signed.substring(0, signatureStart) +
            (if (signed[signatureStart] == 'a') 'b' else 'a') +
            signed.substring(signatureStart + 1)
        assertNull("sửa chữ ký phải bị từ chối", ConfigSignature.verifyAndExtract(flipped, publicKeyHex))

        val otherKey = hex(Ed25519PrivateKeyParameters(SecureRandom()).generatePublicKey().encoded)
        assertNull("sai khoá công khai phải bị từ chối", ConfigSignature.verifyAndExtract(signed, otherKey))
    }

    @Test fun dinhDangSaiThiTuChoi() {
        val json = config(7)
        val signed = sign(json.toByteArray())
        assertNull("thiếu khoá công khai", ConfigSignature.verifyAndExtract(signed, ""))
        assertNull("thiếu dấu xuống dòng", ConfigSignature.verifyAndExtract(signed.replaceFirst("\n", ""), publicKeyHex))
        assertNull("sai tiền tố", ConfigSignature.verifyAndExtract(signed.replaceFirst("v1:", "v2:"), publicKeyHex))
        assertNull("chữ ký sai độ dài", ConfigSignature.verifyAndExtract(signed.removeRange(10, 12), publicKeyHex))
        assertNull("payload rỗng", ConfigSignature.verifyAndExtract("v1:" + "0".repeat(128) + "\n", publicKeyHex))
    }

    @Test fun chuaCoKhoaCongKhaiThiDocBanKhongKy() = runBlocking {
        val plain = config(bundled + 1, "khong-ky")
        val repository = repo(
            "",
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to plain,
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to sign(config(99, "da-ky").toByteArray()),
            ),
        )
        assertFalse(ConfigSignature.hasPublicKey(""))
        assertTrue(repository.refresh(true).isSuccess)
        assertEquals(bundled + 1, repository.current.configVersion)
        assertEquals("chỉ gọi config.json, không gọi bản ký", listOf(ConfigEndpoints.PAGES_CONFIG_URL), requests)
    }

    @Test fun chuKyDungThiChiMotGetVaDungBanDaKy() = runBlocking {
        val signedJson = config(bundled + 2, "da-ky")
        val repository = repo(
            publicKeyHex,
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to config(1, "chua-ky"),
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to sign(signedJson.toByteArray()),
            ),
        )
        assertTrue(repository.refresh(true).isSuccess)
        assertEquals(bundled + 2, repository.current.configVersion)
        assertEquals(listOf(ConfigEndpoints.PAGES_SIGNED_CONFIG_URL), requests)
    }

    @Test fun chuKySaiThiBoQuaBanDaKyVaDocBanThuong() = runBlocking {
        val plain = config(bundled + 3, "chua-ky")
        val repository = repo(
            publicKeyHex,
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to plain,
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to sign(config(98, "bi-sua").toByteArray())
                    .replace("\"bi-sua\"", "\"bi-sua!\""),
            ),
        )
        assertTrue(repository.refresh(true).isSuccess)
        assertEquals(bundled + 3, repository.current.configVersion)
        assertEquals(
            listOf(ConfigEndpoints.PAGES_SIGNED_CONFIG_URL, ConfigEndpoints.PAGES_CONFIG_URL),
            requests,
        )
        assertTrue(
            "bản debug: phải ghi vệt cảnh báo khi bỏ bản đã ký",
            crumbs().any { it.contains("dùng bản chưa ký") },
        )
    }

    /** Vệt breadcrumbs của tiến trình test hiện tại (dùng để khẳng định có ghi nhận, không im lặng). */
    private fun crumbs(): List<String> = Breadcrumbs.read(app, android.os.Process.myPid())

    @Test fun chinhSachBatBuocChiKhiCoKhoaVaBanChinhThuc() {
        val official = SecurityConfig.OFFICIAL_CERT_SHA256
        assertTrue(
            "chính thức + có khoá ⇒ bắt buộc",
            ConfigSignature.signatureRequired(official, publicKeyHex, debugBuild = false),
        )
        assertFalse(
            "chưa có khoá công khai ⇒ không bắt buộc",
            ConfigSignature.signatureRequired(official, "", debugBuild = false),
        )
        assertFalse(
            "bản debug ⇒ không bắt buộc",
            ConfigSignature.signatureRequired(official, publicKeyHex, debugBuild = true),
        )
        assertFalse(
            "chứng thư lạ (tự build GPLv3) ⇒ không bắt buộc",
            ConfigSignature.signatureRequired("00".repeat(32), publicKeyHex, debugBuild = false),
        )
    }

    @Test fun chuKySaiOMBanChinhThucThiBaoLoiKhongTaiBanChuaKy() = runBlocking {
        val repository = repo(
            publicKeyHex,
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to config(bundled + 6, "chua-ky"),
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to sign(config(bundled + 6, "bi-sua").toByteArray())
                    .replace("\"bi-sua\"", "\"bi-sua!\""),
            ),
            batBuocChuKy = true,
        )
        assertTrue("bản chính thức: chữ ký sai phải hỏng", repository.refresh(true).isFailure)
        assertEquals("không được áp bản chưa ký", bundled, repository.current.configVersion)
        assertTrue(
            "không được tải config.json chưa ký: $requests",
            requests.none { it == ConfigEndpoints.PAGES_CONFIG_URL },
        )
        assertFalse("cache không được ghi", cache.exists())
        assertTrue(
            "phải ghi vệt từ chối",
            crumbs().any { it.contains("từ chối bản chưa ký") },
        )
    }

    @Test fun chuKyDungOMBanChinhThucThiNhanVaCacheDocLaiDuoc() = runBlocking {
        val version = bundled + 8
        val repository = repo(
            publicKeyHex,
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to config(bundled + 9, "khong-duoc-goi"),
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to sign(config(version, "da-ky").toByteArray()),
            ),
            batBuocChuKy = true,
        )
        assertTrue(repository.refresh(true).isSuccess)
        assertEquals(version, repository.current.configVersion)
        assertEquals(listOf(ConfigEndpoints.PAGES_SIGNED_CONFIG_URL), requests)

        // Phiên sau: cache trên đĩa có dấu xác nhận ⇒ dùng lại được, không cần mạng.
        requests.clear()
        val after = repo(publicKeyHex, emptyMap(), batBuocChuKy = true)
        assertEquals("cache có dấu phải đọc lại được", version, after.current.configVersion)
        assertTrue("không gọi mạng khi đọc cache: $requests", requests.isEmpty())
    }

    @Test fun cacheCuKhongCoDauXacNhanThiBoQuaKhiDaCoKhoa() {
        cache.writeText(config(bundled + 7, "cache-cu"))
        assertEquals(
            "cache không dấu bị bỏ, dùng bản trong APK",
            bundled,
            repo(publicKeyHex, emptyMap(), batBuocChuKy = true).current.configVersion,
        )

        // Chưa có khoá công khai: giữ nguyên hành vi trước V206 (vẫn dùng cache).
        assertEquals(
            bundled + 7,
            repo("", emptyMap()).current.configVersion,
        )
    }

    @Test fun chuKyDungNhungHaConfigVersionThiTuChoi() = runBlocking {
        val lower = bundled - 1
        val repository = repo(
            publicKeyHex,
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to config(lower, "ha-cap"),
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to sign(config(lower, "ha-cap").toByteArray()),
            ),
            batBuocChuKy = true,
        )
        assertTrue("hạ configVersion phải bị từ chối", repository.refresh(true).isFailure)
        assertEquals("giữ bản đang chạy", bundled, repository.current.configVersion)
    }

    @Test fun thieuConfigSignedThiFallbackBanThuong() = runBlocking {
        val plain = config(bundled + 4, "khong-co-ban-ky")
        val repository = repo(
            publicKeyHex,
            mapOf(
                ConfigEndpoints.PAGES_CONFIG_URL to plain,
                ConfigEndpoints.PAGES_SIGNED_CONFIG_URL to null,
            ),
        )
        assertTrue(repository.refresh(true).isSuccess)
        assertEquals(bundled + 4, repository.current.configVersion)
        assertEquals(
            listOf(ConfigEndpoints.PAGES_SIGNED_CONFIG_URL, ConfigEndpoints.PAGES_CONFIG_URL),
            requests,
        )
    }

    @Test fun banDebugKhongBatBuocChuKy() {
        // Bản debug/tự build (GPLv3) không bị chặn khi thiếu chữ ký.
        assertFalse(ConfigSignature.signatureRequired(app, publicKeyHex))
    }
}
