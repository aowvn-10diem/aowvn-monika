package vn.aow.monika.apkinstall

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
