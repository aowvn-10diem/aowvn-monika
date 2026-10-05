package vn.aow.monika.apkinstall

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkInspectorTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun device(sdk: Int = 34, abis: List<String> = listOf("arm64-v8a", "armeabi-v7a", "armeabi"), abis32: List<String> = listOf("armeabi-v7a", "armeabi"), free: Long = 100L shl 30) =
        DeviceInfo(sdk, abis, abis32, 420, free)

    private fun inspector(d: DeviceInfo = device()) = ApkInspector(d, java.nio.file.Files.createTempDirectory(tmp.root.toPath(), "work").toFile())
    private fun f(n: String) = File(tmp.root, n)

    @Test fun singleApkReadsManifest() {
        val apk = ApkFixture.apk(f("g.apk"), "com.foo.game", versionCode = 7, minSdk = 21, targetSdk = 30)
        val r = inspector().inspect(apk)
        assertEquals(PackKind.APK, r.kind)
        assertEquals("com.foo.game", r.packageName)
        assertEquals(7L, r.versionCode)
        assertEquals(21, r.minSdk); assertEquals(30, r.targetSdk)
        assertTrue(r.problems.isEmpty())
    }

    @Test fun apksHasBaseAndSplits() {
        val apks = ApkFixture.zip(f("g.apks"), mapOf(
            "base.apk" to ApkFixture.apkBytes("com.foo.game"),
            "split_config.arm64_v8a.apk" to ApkFixture.apkBytes("com.foo.game", split = "config.arm64_v8a", libs = listOf("lib/arm64-v8a/x.so")),
            "split_config.xxhdpi.apk" to ApkFixture.apkBytes("com.foo.game", split = "config.xxhdpi"),
            "toc.pb" to byteArrayOf(0),
        ))
        val r = inspector().inspect(apks)
        assertEquals(PackKind.APKS, r.kind)
        assertEquals(3, r.parts.size)
        assertEquals(1, r.parts.count { it.isBase })
        assertEquals(setOf("arm64-v8a"), r.nativeAbis)
        assertTrue(r.problems.isEmpty())
    }

    @Test fun bundletoolSplitsFolderPreferred() {
        val apks = ApkFixture.zip(f("g.apks"), mapOf(
            "splits/base-master.apk" to ApkFixture.apkBytes("com.foo.game"),
            "splits/base-xxhdpi.apk" to ApkFixture.apkBytes("com.foo.game", split = "config.xxhdpi"),
            "standalones/standalone-xxhdpi.apk" to ApkFixture.apkBytes("com.foo.game"),
        ))
        val r = inspector().inspect(apks)
        assertEquals(2, r.parts.size)
    }

    @Test fun apkEntriesCannotEscapePrivateWorkDirectory() {
        val apks = ApkFixture.zip(f("traversal.apks"), mapOf(
            "../../escaped.apk" to ApkFixture.apkBytes("com.fixture.safe"),
            "/absolute.apk" to ApkFixture.apkBytes("com.fixture.safe", split = "absolute"),
            "nested/../other.apk" to ApkFixture.apkBytes("com.fixture.safe", split = "other"),
            "back\\..\\windows.apk" to ApkFixture.apkBytes("com.fixture.safe", split = "windows"),
        ))
        val result = inspector().inspect(apks)
        assertEquals(4, result.parts.size)
        val work = result.workDir!!.canonicalFile
        assertTrue(result.parts.all { it.file.canonicalFile.parentFile == work })
        assertFalse(f("escaped.apk").exists())
        assertFalse(f("absolute.apk").exists())
    }

    @Test fun xapkWithObbAndData() {
        val xapk = ApkFixture.zip(f("g.xapk"), mapOf(
            "manifest.json" to """{"package_name":"com.foo.game","split_apks":[{"file":"base.apk","id":"base"}]}""".toByteArray(),
            "base.apk" to ApkFixture.apkBytes("com.foo.game", versionCode = 12),
            "Android/obb/com.foo.game/big.obb" to ByteArray(10) { 1 },
            "Android/data/com.foo.game/files/save.dat" to ByteArray(4) { 2 },
        ))
        val r = inspector().inspect(xapk)
        assertEquals(PackKind.XAPK, r.kind)
        assertEquals(1, r.obbFiles.size)
        assertEquals("main.12.com.foo.game.obb", r.obbFiles[0].targetName)
        assertEquals(10, r.obbFiles[0].payload.open().use { it.readBytes().size })
        assertEquals(1, r.dataFiles.size)
        assertEquals("files/save.dat", r.dataFiles[0].relPath)
        assertEquals(4, r.dataFiles[0].payload.open().use { it.readBytes().size })
        assertTrue(r.needsData)
    }

    @Test fun apkmUnsupported() {
        val r = inspector().inspect(ApkFixture.zip(f("g.apkm"), mapOf("info.json" to byteArrayOf(1))))
        assertEquals(PackKind.APKM_UNSUPPORTED, r.kind)
        assertTrue(r.fatal is Problem.Unsupported)
    }

    @Test fun folderWithApkObbData() {
        val dir = tmp.newFolder("game")
        ApkFixture.apk(File(dir, "game.apk"), "com.foo.game", versionCode = 3)
        File(dir, "Android/obb/com.foo.game").mkdirs()
        File(dir, "Android/obb/com.foo.game/main.3.com.foo.game.obb").writeBytes(ByteArray(5))
        File(dir, "Android/data/com.foo.game/files").mkdirs()
        File(dir, "Android/data/com.foo.game/files/a.txt").writeText("hi")
        val r = inspector().inspect(dir)
        assertEquals(PackKind.FOLDER, r.kind)
        assertEquals("com.foo.game", r.packageName)
        assertEquals(listOf("main.3.com.foo.game.obb"), r.obbFiles.map { it.targetName })
        assertEquals(listOf("files/a.txt"), r.dataFiles.map { it.relPath })
    }

    @Test fun folderWithLooseSplitsOfOneGame() {
        val dir = tmp.newFolder("sai")
        ApkFixture.apk(File(dir, "base.apk"), "com.foo.game")
        ApkFixture.apk(File(dir, "split_config.xxhdpi.apk"), "com.foo.game", split = "config.xxhdpi")
        val r = inspector().inspect(dir)
        assertEquals(2, r.parts.size)
        assertNull(r.fatal)
    }

    @Test fun folderWithTwoDifferentGamesNeedsChoice() {
        val dir = tmp.newFolder("two")
        ApkFixture.apk(File(dir, "a.apk"), "com.a.game")
        ApkFixture.apk(File(dir, "b.apk"), "com.b.game")
        val r = inspector().inspect(dir)
        assertTrue(r.fatal is Problem.NeedChoice)
        val chosen = inspector().inspect(dir, File(dir, "b.apk"))
        assertEquals("com.b.game", chosen.packageName)
    }

    @Test fun only32BitOn64BitOnlyDevice() {
        val apk = ApkFixture.apk(f("g.apk"), "com.old.game", libs = listOf("lib/armeabi-v7a/libgame.so"))
        val r64 = inspector(device(abis = listOf("arm64-v8a"), abis32 = emptyList())).inspect(apk)
        assertTrue(r64.fatal is Problem.Only32Bit)
        assertTrue((r64.fatal as Problem).text.contains("máy ảo"))
        // Máy còn hỗ trợ 32-bit → bình thường.
        assertNull(inspector().inspect(apk).fatal)
    }

    @Test fun armeabiOldLibsRunOnV7aDevice() {
        val apk = ApkFixture.apk(f("g.apk"), "com.old.game", libs = listOf("lib/armeabi/libgame.so"))
        assertNull(inspector(device(abis = listOf("armeabi-v7a", "armeabi"))).inspect(apk).fatal)
    }

    @Test fun mixedLibsOn64BitOnlyDeviceOk() {
        val apk = ApkFixture.apk(f("g.apk"), "com.mix.game", libs = listOf("lib/armeabi-v7a/a.so", "lib/arm64-v8a/a.so"))
        assertNull(inspector(device(abis = listOf("arm64-v8a"), abis32 = emptyList())).inspect(apk).fatal)
    }

    @Test fun x86OnlyIsNoMatchingAbi() {
        val apk = ApkFixture.apk(f("g.apk"), "com.x.game", libs = listOf("lib/x86_64/a.so"))
        assertTrue(inspector().inspect(apk).fatal is Problem.NoMatchingAbi)
    }

    @Test fun javaOnlyGameHasNoAbiProblem() {
        val apk = ApkFixture.apk(f("g.apk"), "com.java.game")
        assertNull(inspector(device(abis = listOf("arm64-v8a"), abis32 = emptyList())).inspect(apk).fatal)
    }

    @Test fun lowTargetSdkByAndroidVersion() {
        val apk = ApkFixture.apk(f("g.apk"), "com.old.game", targetSdk = 22)
        assertTrue(inspector(device(sdk = 34)).inspect(apk).lowTargetSdk)
        assertTrue(inspector(device(sdk = 35)).inspect(apk).lowTargetSdk)
        assertFalse(inspector(device(sdk = 33)).inspect(apk).lowTargetSdk)
        val t23 = ApkFixture.apk(f("h.apk"), "com.old2.game", targetSdk = 23)
        assertFalse(inspector(device(sdk = 34)).inspect(t23).lowTargetSdk)
        assertTrue(inspector(device(sdk = 35)).inspect(t23).lowTargetSdk)
        // Chỉ là cảnh báo, không chặn hẳn (đi checklist 3 cách).
        assertNull(inspector(device(sdk = 34)).inspect(apk).fatal)
    }

    @Test fun minSdkTooHigh() {
        val apk = ApkFixture.apk(f("g.apk"), "com.new.game", minSdk = 35)
        val r = inspector(device(sdk = 30)).inspect(apk)
        assertTrue(r.fatal is Problem.MinSdkTooHigh)
    }

    @Test fun lowSpace() {
        val xapk = ApkFixture.zip(f("g.xapk"), mapOf(
            "base.apk" to ApkFixture.apkBytes("com.foo.game"),
            "Android/obb/com.foo.game/x.obb" to ByteArray(4000),
        ))
        val r = inspector(device(free = 3000)).inspect(xapk)
        assertNotNull(r.fatal); assertTrue(r.fatal is Problem.LowSpace)
    }

    @Test fun noApkInFolder() {
        val dir = tmp.newFolder("empty")
        File(dir, "readme.txt").writeText("x")
        assertTrue(inspector().inspect(dir).fatal is Problem.NoApk)
    }
}
