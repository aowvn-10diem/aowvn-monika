package vn.aow.monika.apkinstall

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Dựng file APK/APKS/XAPK giả (manifest nhị phân thật) để test không cần game thật. */
object ApkFixture {
    fun manifest(pkg: String, versionCode: Int = 1, minSdk: Int = 21, targetSdk: Int = 30, split: String? = null): ByteArray {
        val m = AndroidManifestBlock.empty()
        m.packageName = pkg
        m.setVersionCode(versionCode)
        m.setVersionName("1.$versionCode")
        m.setMinSdkVersion(minSdk)
        m.setTargetSdkVersion(targetSdk)
        if (split != null) m.setSplit(split, true)
        m.refreshFull()
        return m.bytes
    }

    fun apk(file: File, pkg: String, versionCode: Int = 1, minSdk: Int = 21, targetSdk: Int = 30, split: String? = null, libs: List<String> = emptyList(), extra: Map<String, ByteArray> = emptyMap()): File {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream()).use { z ->
            z.put("AndroidManifest.xml", manifest(pkg, versionCode, minSdk, targetSdk, split))
            libs.forEach { z.put(it, byteArrayOf(1, 2, 3)) }
            extra.forEach { (n, b) -> z.put(n, b) }
        }
        return file
    }

    fun zip(file: File, entries: Map<String, ByteArray>): File {
        file.parentFile?.mkdirs()
        ZipOutputStream(file.outputStream()).use { z -> entries.forEach { (n, b) -> z.put(n, b) } }
        return file
    }

    fun apkBytes(pkg: String, versionCode: Int = 1, minSdk: Int = 21, targetSdk: Int = 30, split: String? = null, libs: List<String> = emptyList()): ByteArray {
        val tmp = File.createTempFile("fixture", ".apk")
        apk(tmp, pkg, versionCode, minSdk, targetSdk, split, libs)
        return tmp.readBytes().also { tmp.delete() }
    }

    private fun ZipOutputStream.put(name: String, bytes: ByteArray) { putNextEntry(ZipEntry(name)); write(bytes); closeEntry() }
}
