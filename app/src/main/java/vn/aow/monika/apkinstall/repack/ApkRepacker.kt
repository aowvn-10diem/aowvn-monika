package vn.aow.monika.apkinstall.repack

import com.android.apksig.ApkSigner
import com.reandroid.archive.ZipAlign
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Điều kiện chỉnh 1 APK game. */
data class RepackOptions(
    /** Gói của Monika (nơi bộ nạp báo tin về + nhận lệnh). */
    val hostPackage: String,
    /** Nội dung file monika-loader.dex. */
    val loaderDex: ByteArray,
    val signer: RepackKeyStore.Signer,
    /** Nếu game có targetSdk thấp hơn mức này thì nâng lên đúng mức này (không cao hơn). Null = giữ nguyên. */
    val raiseTargetSdkTo: Int? = null,
    /** Chèn bộ nạp (chỉ base APK). Split thì chỉ ký lại. */
    val injectLoader: Boolean = true,
)

/**
 * Cách 1 của trình cài game: chèn bộ nạp data vào APK game (+ nâng targetSdk tối thiểu nếu cần) rồi ký lại bằng khóa của Monika.
 * Chỉ sửa AndroidManifest + thêm 1 file dex. KHÔNG đụng mã game, không đổi tên gói.
 */
object ApkRepacker {
    const val PROVIDER = "vn.aow.monika.loader.MonikaLoaderProvider"
    const val SERVICE = "vn.aow.monika.loader.MonikaLoaderService"
    const val META_HOST = "vn.aow.monika.HOST"

    private const val ATTR_NAME = 0x01010003
    private const val ATTR_VALUE = 0x01010024
    private const val ATTR_EXPORTED = 0x01010010
    private const val ATTR_AUTHORITIES = 0x01010018

    private val SIGNATURE_FILES = Regex("""^META-INF/[^/]+\.(SF|RSA|DSA|EC)$|^META-INF/MANIFEST\.MF$""", RegexOption.IGNORE_CASE)

    class Result(val targetSdkBefore: Int, val targetSdkAfter: Int, val injected: Boolean)

    fun repack(src: File, dst: File, opt: RepackOptions, tmpDir: File = dst.parentFile ?: src.parentFile): Result {
        tmpDir.mkdirs()
        val stage = File.createTempFile("repack", ".apk", tmpDir)
        val aligned = File.createTempFile("aligned", ".apk", tmpDir)
        try {
            val result = rewrite(src, stage, opt)
            ZipAlign.alignApk(stage, aligned)
            val minSdk = ZipFile(src).use { z -> z.getInputStream(z.getEntry("AndroidManifest.xml")).use { AndroidManifestBlock.load(it) }.minSdkVersion ?: 1 }
            dst.delete()
            ApkSigner.Builder(listOf(ApkSigner.SignerConfig.Builder("monika", opt.signer.privateKey, listOf(opt.signer.certificate)).build()))
                .setInputApk(aligned).setOutputApk(dst)
                .setMinSdkVersion(minSdk.coerceAtLeast(1))
                .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
                .build().sign()
            return result
        } finally {
            stage.delete(); aligned.delete()
        }
    }

    /** Bước 1: viết lại zip (manifest mới + dex bộ nạp, bỏ chữ ký cũ, giữ nguyên kiểu lưu của từng file). */
    private fun rewrite(src: File, out: File, opt: RepackOptions): Result {
        ZipFile(src).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml") ?: throw IllegalArgumentException("APK không có AndroidManifest.xml")
            val manifest = zip.getInputStream(manifestEntry).use { AndroidManifestBlock.load(it) }
            val before = manifest.targetSdkVersion ?: manifest.minSdkVersion ?: 1
            val isBase = !manifest.isSplit
            opt.raiseTargetSdkTo?.let { want -> if (before < want && isBase) manifest.setTargetSdkVersion(want) }
            val inject = opt.injectLoader && isBase && manifest.packageName != null && !alreadyInjected(manifest)
            if (inject) addLoader(manifest, opt.hostPackage)
            manifest.refreshFull()
            val manifestBytes = manifest.bytes

            val dexNumbers = zip.entries().asSequence().mapNotNull { DEX.matchEntire(it.name)?.groupValues?.get(1) }.map { if (it.isEmpty()) 1 else it.toInt() }.toList()
            val nextDex = (dexNumbers.maxOrNull() ?: 0) + 1

            ZipOutputStream(out.outputStream().buffered()).use { zo ->
                zo.setLevel(6)
                for (e in zip.entries().asSequence()) {
                    if (SIGNATURE_FILES.matches(e.name)) continue
                    if (e.name == "AndroidManifest.xml") { putBytes(zo, e.name, manifestBytes, stored = e.method == ZipEntry.STORED); continue }
                    copyEntry(zip, e, zo)
                }
                if (inject) putBytes(zo, "classes$nextDex.dex", opt.loaderDex, stored = false)
            }
            return Result(before, manifest.targetSdkVersion ?: before, inject)
        }
    }

    private fun alreadyInjected(m: AndroidManifestBlock): Boolean =
        m.applicationElement?.getElements { it.name == "provider" }?.asSequence()?.any { AndroidManifestBlock.getAndroidNameValue(it) == PROVIDER } == true

    private fun addLoader(m: AndroidManifestBlock, host: String) {
        val pkg = m.packageName!!
        val app = m.getOrCreateApplicationElement()
        // Android 11+: game (targetSdk ≥ 30) chỉ thấy Monika khi khai báo <queries>.
        val manifestEl = m.manifestElement
        val queries = manifestEl.getElements { it.name == "queries" }.asSequence().firstOrNull() ?: manifestEl.createChildElement("queries")
        queries.createChildElement("package").getOrCreateAndroidAttribute("name", ATTR_NAME).setValueAsString(host)

        app.createChildElement("provider").apply {
            getOrCreateAndroidAttribute("name", ATTR_NAME).setValueAsString(PROVIDER)
            getOrCreateAndroidAttribute("authorities", ATTR_AUTHORITIES).setValueAsString("$pkg.monikaloader")
            getOrCreateAndroidAttribute("exported", ATTR_EXPORTED).setValueAsBoolean(false)
        }
        app.createChildElement("service").apply {
            getOrCreateAndroidAttribute("name", ATTR_NAME).setValueAsString(SERVICE)
            getOrCreateAndroidAttribute("exported", ATTR_EXPORTED).setValueAsBoolean(true)
        }
        app.createChildElement("meta-data").apply {
            getOrCreateAndroidAttribute("name", ATTR_NAME).setValueAsString(META_HOST)
            getOrCreateAndroidAttribute("value", ATTR_VALUE).setValueAsString(host)
        }
    }

    private fun copyEntry(zip: ZipFile, e: ZipEntry, zo: ZipOutputStream) {
        val n = ZipEntry(e.name)
        if (e.method == ZipEntry.STORED) {
            n.method = ZipEntry.STORED; n.size = e.size; n.compressedSize = e.size; n.crc = e.crc
        } else {
            n.method = ZipEntry.DEFLATED
        }
        if (e.time != -1L) n.time = e.time
        zo.putNextEntry(n)
        if (!e.isDirectory) zip.getInputStream(e).use { it.copyTo(zo) }
        zo.closeEntry()
    }

    private fun putBytes(zo: ZipOutputStream, name: String, bytes: ByteArray, stored: Boolean) {
        val n = ZipEntry(name)
        if (stored) {
            n.method = ZipEntry.STORED; n.size = bytes.size.toLong(); n.compressedSize = bytes.size.toLong()
            n.crc = CRC32().apply { update(bytes) }.value
        }
        zo.putNextEntry(n); zo.write(bytes); zo.closeEntry()
    }

    private val DEX = Regex("""classes(\d*)\.dex""")
}
