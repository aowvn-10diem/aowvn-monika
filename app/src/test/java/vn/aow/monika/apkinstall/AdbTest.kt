package vn.aow.monika.apkinstall

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.aow.monika.apkinstall.adb.AdbCleanup
import vn.aow.monika.apkinstall.adb.AdbInitialStore
import vn.aow.monika.apkinstall.adb.AdbInstaller
import vn.aow.monika.apkinstall.adb.AdbShell
import vn.aow.monika.apkinstall.adb.DevState
import java.io.File
import java.io.OutputStream

/** Shell giả: ghi lại lệnh, trả kết quả theo mẫu; execWrite nhận đủ dữ liệu. */
private class FakeShell(private val replies: (String) -> String = { "" }) : AdbShell {
    val commands = ArrayList<String>()
    var written = 0L
    override suspend fun shell(command: String): String { commands += command; return replies(command) }
    override suspend fun execWrite(command: String, size: Long, writer: (OutputStream) -> Unit): String {
        commands += command
        val sink = object : OutputStream() { override fun write(b: Int) { written++ }; override fun write(b: ByteArray, o: Int, l: Int) { written += l } }
        writer(sink)
        return replies(command)
    }
}

class AdbTest {
    @get:Rule val tmp = TemporaryFolder()
    private val device = DeviceInfo(35, listOf("arm64-v8a"), emptyList(), 420, 100L shl 30)
    private val allOff = DevState(false, false, false)

    private fun result(target: Int = 30, data: List<DataFile> = emptyList(), obb: List<ObbFile> = emptyList()): InspectResult {
        val apk = ApkFixture.apk(File(tmp.root, "b.apk"), "com.foo.game", targetSdk = target)
        return InspectResult(PackKind.APK, "com.foo.game", "Foo", 1, "1", 21, target, listOf(ApkPart(apk, null)), emptySet(), obb, data, emptyList())
    }

    private fun sh(cmd: String) = when {
        cmd.startsWith("pm install-create") -> "Success: created install session [42]"
        cmd.startsWith("pm install-commit") -> "Success"
        cmd.startsWith("stat -c %s") -> "" // chưa có file
        else -> ""
    }

    private val checklist get() = InstallChecklist({ null }, { _, _ -> })

    @Test fun parseAndQuote() {
        assertEquals(42, AdbInstaller.parseSessionId("Success: created install session [42]"))
        assertNull(AdbInstaller.parseSessionId("Error"))
        assertEquals("'a'\\''b'", AdbInstaller.q("a'b"))
    }

    @Test fun cleanupOnlyTurnsOffWhatWasOff() {
        assertNull(AdbCleanup.command(DevState(true, true, true)))
        val c = AdbCleanup.command(DevState(true, false, false))!!
        assertTrue(c.contains("adb_enabled 0") && c.contains("adb_wifi_enabled 0") && !c.contains("development_settings_enabled"))
        assertTrue(c.trimEnd().endsWith("adb_wifi_enabled 0"))
        assertTrue(AdbCleanup.restored(allOff, allOff))
        assertTrue(!AdbCleanup.restored(allOff, DevState(false, false, true)))
        assertEquals(DevState(true, false, true), AdbInitialStore.decode(AdbInitialStore.encode(DevState(true, false, true))))
        assertNull(AdbInitialStore.decode("12x"))
    }

    @Test fun installOk_bypassOnlyWhenAsked() = runBlocking {
        val r = result(target = 21)
        val s1 = FakeShell(::sh)
        assertTrue(AdbInstaller.install(s1, r.packageName, r.parts, bypassLowTargetSdk = true) is InstallOutcome.Success)
        assertTrue(s1.commands.first().contains("--bypass-low-target-sdk-block"))
        val s2 = FakeShell(::sh)
        AdbInstaller.install(s2, r.packageName, r.parts, bypassLowTargetSdk = false)
        assertTrue(!s2.commands.first().contains("--bypass"))
        assertEquals(r.parts.first().file.length(), s2.written)
    }

    @Test fun installFailureAbandonsSession() = runBlocking {
        val r = result()
        val s = FakeShell { c -> if (c.startsWith("pm install-commit")) "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]" else sh(c) }
        val o = AdbInstaller.install(s, r.packageName, r.parts, false)
        assertTrue(o is InstallOutcome.Failure)
    }

    @Test fun executeAdb_pushesDataAndAlwaysCleansUp() = runBlocking {
        val bytes = ByteArray(500) { 1 }
        val r = result(data = listOf(DataFile("files/a.bin", Payload("a", 500) { bytes.inputStream() })))
        var size = ""
        val s = FakeShell { c -> if (c.startsWith("stat -c %s") && c.contains("a.bin")) size.also { size = "500" } else sh(c) }
        val end = ApkInstallFlow.executeAdb(r, s, device, checklist, allOff, readNow = { allOff }, obbDir = File(tmp.root, "obb")) {}
        assertEquals(UiState.Phase.DONE, end.phase)
        assertTrue(s.commands.any { it.startsWith("mkdir -p '/sdcard/Android/data/com.foo.game/files'") })
        assertTrue(s.commands.last().contains("adb_wifi_enabled 0"))
    }

    @Test fun executeAdb_failedInstallStillCleansUp_andWarnsIfNotRestored() = runBlocking {
        val r = result()
        val s = FakeShell { c -> if (c.startsWith("pm install-commit")) "Failure [INSTALL_FAILED_OLDER_SDK]" else sh(c) }
        val end = ApkInstallFlow.executeAdb(r, s, device, checklist, allOff, readNow = { DevState(true, false, false) }, obbDir = File(tmp.root, "obb")) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertTrue(s.commands.last().contains("adb_enabled 0"))
        assertTrue(end.message!!.contains("chưa tắt lại"))
    }

    @Test fun executeAdb_exceptionStillCleansUp() = runBlocking {
        val r = result()
        val s = FakeShell { c -> if (c.startsWith("pm install-create")) throw java.io.IOException("cắt") else "" }
        val end = ApkInstallFlow.executeAdb(r, s, device, checklist, allOff, readNow = { allOff }, obbDir = File(tmp.root, "obb")) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertTrue(s.commands.last().contains("adb_wifi_enabled 0"))
    }

    @Test fun installRejectsBadPackageName() {
        val r = result()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { AdbInstaller.install(FakeShell(::sh), "bad pkg;rm", r.parts, false) }
        }
    }

    @Test fun installReportsWhenSessionCannotBeCreated() = runBlocking {
        val r = result()
        val s = FakeShell { c -> if (c.startsWith("pm install-create")) "Error: no space" else sh(c) }
        assertTrue(AdbInstaller.install(s, r.packageName, r.parts, false) is InstallOutcome.Failure)
        assertFalse(s.commands.any { it.startsWith("cmd package install-write") })
    }

    @Test fun installAbandonsSessionWhenPartIsRejected() = runBlocking {
        val r = result()
        val s = FakeShell { c -> if (c.startsWith("cmd package install-write")) "Failure [INSUFFICIENT_STORAGE]" else sh(c) }
        assertTrue(AdbInstaller.install(s, r.packageName, r.parts, false) is InstallOutcome.Failure)
        assertTrue(s.commands.contains("pm install-abandon 42"))
        assertFalse(s.commands.any { it.startsWith("pm install-commit") })
    }

    @Test fun installReportsProgressToFull() = runBlocking {
        val r = result()
        val progress = ArrayList<Int>()
        AdbInstaller.install(FakeShell(::sh), r.packageName, r.parts, false) { progress += it }
        assertEquals(100, progress.last())
    }

    @Test fun pushDataRejectsPathsOutsideDataFolder() = runBlocking {
        val bytes = ByteArray(5)
        for (rel in listOf("../evil.bin", "/")) {
            val r = AdbInstaller.pushData(FakeShell(::sh), "com.foo.game", listOf(DataFile(rel, Payload("x", 5) { bytes.inputStream() })))
            assertTrue(r is AdbInstaller.DataResult.Failed)
            assertEquals("Đường dẫn không hợp lệ: $rel", (r as AdbInstaller.DataResult.Failed).text)
        }
    }

    @Test fun pushDataSkipsFilesAlreadyComplete() = runBlocking {
        val bytes = ByteArray(500) { 1 }
        val s = FakeShell { c -> if (c.startsWith("stat -c %s")) "500" else "" }
        val r = AdbInstaller.pushData(s, "com.foo.game", listOf(DataFile("files/a.bin", Payload("a", 500) { bytes.inputStream() })))
        assertTrue(r is AdbInstaller.DataResult.Ok)
        assertEquals(500L, (r as AdbInstaller.DataResult.Ok).bytes)
        assertFalse(s.commands.any { it.startsWith("cat >") })
    }

    @Test fun pushDataReportsShortCopy() = runBlocking {
        val bytes = ByteArray(500) { 1 }
        var stats = 0
        val s = FakeShell { c -> if (c.startsWith("stat -c %s")) { stats++; if (stats == 1) "" else "10" } else "" }
        val r = AdbInstaller.pushData(s, "com.foo.game", listOf(DataFile("files/a.bin", Payload("a", 500) { bytes.inputStream() })))
        assertTrue(r is AdbInstaller.DataResult.Failed)
        assertEquals("Chép thiếu dữ liệu: files/a.bin (10/500 byte)", (r as AdbInstaller.DataResult.Failed).text)
    }

    @Test fun cleanupTurnsOffOnlyWhatMonikaTurnedOn() = runBlocking {
        val s = FakeShell()
        AdbInstaller.cleanup(s, allOff)
        assertEquals(1, s.commands.size)
        assertTrue(s.commands.single().startsWith("settings put global adb_enabled 0"))
        val kept = FakeShell()
        AdbInstaller.cleanup(kept, DevState(true, true, true))
        assertTrue(kept.commands.isEmpty())
    }

    @Test fun devStateAnyOnAndStoreDecodeEdges() {
        assertFalse(allOff.anyOn)
        assertTrue(DevState(false, false, true).anyOn)
        assertNull(AdbInitialStore.decode(null))
        assertNull(AdbInitialStore.decode("11"))
    }
}
