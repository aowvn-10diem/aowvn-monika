package vn.aow.monika.apkinstall

import com.android.apksig.ApkVerifier
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.apkinstall.repack.ApkRepacker
import vn.aow.monika.apkinstall.repack.RepackKeyStore
import vn.aow.monika.apkinstall.repack.RepackOptions
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkRepackerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val loader = ByteArray(300) { (it % 251).toByte() }
    private val libBytes = ByteArray(5000) { (it * 7).toByte() }
    private val store by lazy { RepackKeyStore(tmp.newFolder("keys")) }

    /** APK giả nhưng có đủ các thứ như thật: manifest nhị phân, dex, .so lưu không nén, chữ ký cũ. */
    private fun game(name: String, target: Int, split: String? = null, min: Int = 21): File {
        val f = File(tmp.root, name)
        ZipOutputStream(f.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("AndroidManifest.xml")); z.write(ApkFixture.manifest("com.old.game", 3, min, target, split)); z.closeEntry()
            z.putNextEntry(ZipEntry("classes.dex")); z.write(ByteArray(64) { 1 }); z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/OLD.SF")); z.write(byteArrayOf(1)); z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/OLD.RSA")); z.write(byteArrayOf(2)); z.closeEntry()
            z.putNextEntry(ZipEntry("META-INF/MANIFEST.MF")); z.write("Manifest-Version: 1.0\n".toByteArray()); z.closeEntry()
            val stored = ZipEntry("lib/arm64-v8a/libgame.so").apply { method = ZipEntry.STORED; size = libBytes.size.toLong(); compressedSize = size; crc = java.util.zip.CRC32().apply { update(libBytes) }.value }
            z.putNextEntry(stored); z.write(libBytes); z.closeEntry()
            z.putNextEntry(ZipEntry("assets/x.txt")); z.write("hello".toByteArray()); z.closeEntry()
        }
        return f
    }

    private fun opts(raise: Int? = null, inject: Boolean = true) = RepackOptions("com.aow.monika", loader, store.signer(), raise, inject)
    private fun manifestOf(f: File) = ZipFile(f).use { z -> z.getInputStream(z.getEntry("AndroidManifest.xml")).use { AndroidManifestBlock.load(it) } }

    @Test fun injectsLoaderAndSignsValidly() {
        val out = File(tmp.root, "out.apk")
        val r = ApkRepacker.repack(game("in.apk", 30), out, opts())
        assertTrue(r.injected)
        val m = manifestOf(out)
        assertEquals("com.old.game", m.packageName)
        assertEquals(30, m.targetSdkVersion)
        val names = m.applicationElement.getElements { true }.asSequence().map { it.name to AndroidManifestBlock.getAndroidNameValue(it) }.toList()
        assertTrue(names.contains("provider" to ApkRepacker.PROVIDER))
        assertTrue(names.contains("service" to ApkRepacker.SERVICE))
        assertTrue(names.any { it.first == "meta-data" && it.second == ApkRepacker.META_HOST })
        assertTrue(m.manifestElement.getElements { it.name == "queries" }.asSequence().any())
        ZipFile(out).use { z ->
            assertArrayEquals(loader, z.getInputStream(z.getEntry("classes2.dex")).readBytes())
            assertNotNull(z.getEntry("classes.dex"))
            assertNull(z.getEntry("META-INF/OLD.SF")); assertNull(z.getEntry("META-INF/OLD.RSA"))
            assertEquals("hello", String(z.getInputStream(z.getEntry("assets/x.txt")).readBytes()))
            val lib = z.getEntry("lib/arm64-v8a/libgame.so")
            assertEquals(ZipEntry.STORED, lib.method)
            assertArrayEquals(libBytes, z.getInputStream(lib).readBytes())
        }
        val v = ApkVerifier.Builder(out).build().verify()
        assertTrue(v.errors.joinToString { it.toString() }, v.isVerified)
        assertTrue(v.isVerifiedUsingV2Scheme); assertTrue(v.isVerifiedUsingV1Scheme)
    }

    @Test fun raisesTargetSdkOnlyToMinimum() {
        assertEquals(23, manifestOf(File(tmp.root, "a.apk").also { ApkRepacker.repack(game("in1.apk", 19), it, opts(raise = 23)) }).targetSdkVersion)
        assertEquals(24, manifestOf(File(tmp.root, "b.apk").also { ApkRepacker.repack(game("in2.apk", 22), it, opts(raise = 24)) }).targetSdkVersion)
        // Đã đủ cao → giữ nguyên, không nâng.
        assertEquals(28, manifestOf(File(tmp.root, "c.apk").also { ApkRepacker.repack(game("in3.apk", 28), it, opts(raise = 24)) }).targetSdkVersion)
        // Không yêu cầu → giữ nguyên.
        assertEquals(19, manifestOf(File(tmp.root, "d.apk").also { ApkRepacker.repack(game("in4.apk", 19), it, opts()) }).targetSdkVersion)
    }

    @Test fun repackTwiceKeepsOneLoader() {
        val a = File(tmp.root, "a.apk"); val b = File(tmp.root, "b.apk")
        ApkRepacker.repack(game("in.apk", 30), a, opts())
        val r2 = ApkRepacker.repack(a, b, opts())
        assertFalse(r2.injected)
        val n = manifestOf(b).applicationElement.getElements { it.name == "provider" }.asSequence().count()
        assertEquals(1, n)
        assertNull(ZipFile(b).use { it.getEntry("classes3.dex") })
        assertTrue(ApkVerifier.Builder(b).build().verify().isVerified)
    }

    @Test fun splitApkOnlyResigned() {
        val out = File(tmp.root, "split.apk")
        val r = ApkRepacker.repack(game("s.apk", 30, split = "config.xxhdpi"), out, opts(raise = 24))
        assertFalse(r.injected)
        assertNull(ZipFile(out).use { it.getEntry("classes2.dex") })
        assertEquals(0, manifestOf(out).applicationElement?.getElements { it.name == "provider" }?.asSequence()?.count() ?: 0)
        assertTrue(ApkVerifier.Builder(out).build().verify().isVerified)
    }

    @Test fun sameKeyForAllParts() {
        val a = File(tmp.root, "a.apk"); val b = File(tmp.root, "b.apk")
        ApkRepacker.repack(game("base.apk", 30), a, opts())
        ApkRepacker.repack(game("part.apk", 30, split = "config.hdpi"), b, opts())
        fun cert(f: File) = ApkVerifier.Builder(f).build().verify().signerCertificates.first().encoded
        assertArrayEquals(cert(a), cert(b))
    }

    @Test fun keyStorePersists() {
        val dir = tmp.newFolder("k2")
        val f1 = RepackKeyStore(dir).fingerprint()
        val f2 = RepackKeyStore(dir).fingerprint()
        assertEquals(f1, f2)
        assertEquals(64, f1.length)
        val c = RepackKeyStore(dir).signer().certificate
        c.checkValidity()
        assertEquals("RSA", c.publicKey.algorithm)
        c.verify(c.publicKey) // tự ký hợp lệ
    }
}
