package vn.aow.monika.apkinstall

import android.content.pm.PackageInstaller
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstallFlowTest {
    @get:Rule val tmp = TemporaryFolder()
    private val device = DeviceInfo(34, listOf("arm64-v8a"), emptyList(), 420, 100L shl 30)

    private fun payload(bytes: ByteArray) = Payload("x", bytes.size.toLong()) { bytes.inputStream() }

    private fun result(obb: List<ObbFile> = emptyList(), data: List<DataFile> = emptyList()): InspectResult {
        val apk = ApkFixture.apk(File(tmp.root, "b.apk"), "com.foo.game")
        return InspectResult(PackKind.APK, "com.foo.game", "Foo", 1, "1", 21, 30, listOf(ApkPart(apk, null)), emptySet(), obb, data, emptyList())
    }

    @Test fun outcomeMapping() {
        assertTrue(InstallOutcome.from(PackageInstaller.STATUS_SUCCESS, null) is InstallOutcome.Success)
        assertTrue(InstallOutcome.from(PackageInstaller.STATUS_FAILURE_ABORTED, "x") is InstallOutcome.UserAborted)
        fun kind(status: Int, msg: String) = (InstallOutcome.from(status, msg) as InstallOutcome.Failure).kind
        assertEquals(InstallOutcome.Kind.SIGNATURE_CONFLICT, kind(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match"))
        assertEquals(InstallOutcome.Kind.DOWNGRADE, kind(PackageInstaller.STATUS_FAILURE, "INSTALL_FAILED_VERSION_DOWNGRADE"))
        assertEquals(InstallOutcome.Kind.DEPRECATED_SDK, kind(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "INSTALL_FAILED_DEPRECATED_SDK_VERSION: App package must target at least SDK version 23, but found 21"))
        assertEquals(InstallOutcome.Kind.STORAGE, kind(PackageInstaller.STATUS_FAILURE_STORAGE, ""))
        assertEquals(InstallOutcome.Kind.ABI, kind(PackageInstaller.STATUS_FAILURE_INCOMPATIBLE, "INSTALL_FAILED_NO_MATCHING_ABIS"))
        assertEquals(InstallOutcome.Kind.BLOCKED, kind(PackageInstaller.STATUS_FAILURE_BLOCKED, ""))
        assertEquals(InstallOutcome.Kind.OTHER, kind(PackageInstaller.STATUS_FAILURE, "lạ"))
    }

    @Test fun successCopiesObb() = runBlocking {
        val obbDir = File(tmp.root, "obb/com.foo.game")
        val r = result(obb = listOf(ObbFile("main.1.com.foo.game.obb", payload(ByteArray(2000) { 7 }))))
        var installed: List<ApkPart>? = null
        val states = ArrayList<UiState.Phase>()
        val end = ApkInstallFlow.execute(r, { _, parts, p -> installed = parts; p(100); InstallOutcome.Success }, obbDir, device) { states += it.phase }
        assertEquals(UiState.Phase.DONE, end.phase)
        assertEquals(1, installed?.size)
        assertEquals(2000L, File(obbDir, "main.1.com.foo.game.obb").length())
        assertTrue(states.containsAll(listOf(UiState.Phase.INSTALLING, UiState.Phase.COPYING_OBB, UiState.Phase.DONE)))
        assertFalse(File(obbDir, "main.1.com.foo.game.obb.part").exists())
    }

    @Test fun failureStopsBeforeObb() = runBlocking {
        val obbDir = File(tmp.root, "obb2")
        val r = result(obb = listOf(ObbFile("main.1.com.foo.game.obb", payload(ByteArray(10)))))
        val fail = InstallOutcome.from(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        val end = ApkInstallFlow.execute(r, { _, _, _ -> fail }, obbDir, device) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertEquals(InstallOutcome.Kind.SIGNATURE_CONFLICT, end.failure?.kind)
        assertFalse(obbDir.exists())
    }

    @Test fun userAbortReturnsToReady() = runBlocking {
        val end = ApkInstallFlow.execute(result(), { _, _, _ -> InstallOutcome.UserAborted }, File(tmp.root, "o3"), device) {}
        assertEquals(UiState.Phase.READY, end.phase)
    }

    @Test fun dataIsFlaggedNotLost() = runBlocking {
        val r = result(data = listOf(DataFile("a.txt", payload(ByteArray(3)))))
        val end = ApkInstallFlow.execute(r, { _, _, _ -> InstallOutcome.Success }, File(tmp.root, "o4"), device) {}
        assertEquals(UiState.Phase.DONE, end.phase)
        assertTrue(end.skippedData)
    }

    @Test fun obbSkipsExistingCompleteFile() {
        val dir = tmp.newFolder("o5")
        File(dir, "main.1.p.obb").writeBytes(ByteArray(5))
        var opened = false
        val f = ObbFile("main.1.p.obb", Payload("x", 5) { opened = true; ByteArray(5).inputStream() })
        val r = ObbInstaller.copy("p", listOf(f), dir)
        assertTrue(r is ObbInstaller.Result.Ok)
        assertFalse(opened)
    }

    @Test fun obbShortSourceFails() {
        val dir = tmp.newFolder("o6")
        val f = ObbFile("main.1.p.obb", Payload("x", 100) { ByteArray(10).inputStream() })
        val r = ObbInstaller.copy("p", listOf(f), dir)
        assertTrue(r is ObbInstaller.Result.Failed)
        assertFalse(File(dir, "main.1.p.obb.part").exists())
    }

    @Test fun obbDirNotCreatableFailsCleanly() {
        val blocker = tmp.newFile("blocker")
        val f = ObbFile("main.1.p.obb", payload(ByteArray(3)))
        val r = ObbInstaller.copy("p", listOf(f), File(blocker, "sub"))
        assertTrue(r is ObbInstaller.Result.Failed)
    }
}
