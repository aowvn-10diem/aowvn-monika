package vn.aow.monika.apkinstall

import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(application = vn.aow.monika.ui.TestApp::class, sdk = [34])
class SafTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dev = DeviceInfo(34, listOf("arm64-v8a"), emptyList(), 420, 100L shl 30)
    private fun payload(b: ByteArray) = Payload("x", b.size.toLong()) { b.inputStream() }
    private fun cl(): InstallChecklist { val m = HashMap<String, String>(); return InstallChecklist({ m[it] }, { k, v -> m[k] = v }, { 1L }) }
    private fun result(data: List<DataFile>): InspectResult {
        val apk = ApkFixture.apk(File(tmp.root, "b.apk"), "com.foo.game", targetSdk = 30)
        return InspectResult(PackKind.APK, "com.foo.game", "Foo", 5, "1", 21, 30, listOf(ApkPart(apk, null)), emptySet(), emptyList(), data, emptyList())
    }

    @Test fun expectedTreeOnlyForTheGamesFolder() {
        val ok = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Android/data/com.foo.game")
        val other = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:Android/data/com.other")
        val root = DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents", "primary:")
        assertTrue(SafDataAccess.isExpectedTree(ok, "com.foo.game"))
        assertFalse(SafDataAccess.isExpectedTree(other, "com.foo.game"))
        assertFalse(SafDataAccess.isExpectedTree(root, "com.foo.game"))
        assertFalse(SafDataAccess.isExpectedTree(Uri.parse("content://x/y"), "com.foo.game"))
        assertEquals("primary:Android/data/com.foo.game", DocumentsContract.getDocumentId(SafDataAccess.initialUri("com.foo.game")))
    }

    @Test fun copiesNestedAndSkipsComplete() {
        val root = tmp.newFolder("root")
        File(root, "files").mkdirs(); File(root, "files/done.dat").writeBytes(ByteArray(3))
        var opened = false
        val files = listOf(
            DataFile("files/a.dat", payload(ByteArray(5) { 1 })),
            DataFile("files/sub/b.dat", payload(ByteArray(2))),
            DataFile("files/done.dat", Payload("x", 3) { opened = true; ByteArray(3).inputStream() }),
        )
        val r = SafDataAccess.copy(FileDocDir(root), files)
        assertTrue(r is SafDataAccess.Result.Ok)
        assertEquals(5L, File(root, "files/a.dat").length())
        assertEquals(2L, File(root, "files/sub/b.dat").length())
        assertFalse(opened)
    }

    @Test fun rejectsTraversalAndShortSource() {
        val root = tmp.newFolder("root2")
        assertTrue(SafDataAccess.copy(FileDocDir(root), listOf(DataFile("../x.dat", payload(ByteArray(1))))) is SafDataAccess.Result.Failed)
        assertTrue(SafDataAccess.copy(FileDocDir(root), listOf(DataFile("a/../../x.dat", payload(ByteArray(1))))) is SafDataAccess.Result.Failed)
        val short = DataFile("y.dat", Payload("y", 50) { ByteArray(4).inputStream() })
        assertTrue(SafDataAccess.copy(FileDocDir(root), listOf(short)) is SafDataAccess.Result.Failed)
        assertFalse(File(root, "y.dat").exists())
    }

    @Test fun safInstallThenCopyUpdatesChecklist() = runBlocking {
        val c = cl(); val states = ArrayList<UiState.Phase>()
        val r = result(listOf(DataFile("files/a.dat", payload(ByteArray(4)))))
        val prepared = ApkInstallFlow.executeSafInstall(r, { _, _, p -> p(100); InstallOutcome.Success }, dev, c, File(tmp.root, "obb")) { states += it.phase }
        assertEquals(UiState.Phase.SAF_PREPARE, prepared.phase)
        assertEquals(State.RUNNING, c.read("com.foo.game", 5)[1].state)
        val root = tmp.newFolder("data")
        val end = ApkInstallFlow.executeSafCopy(r, FileDocDir(root), c) { states += it.phase }
        assertEquals(UiState.Phase.DONE, end.phase)
        assertEquals(State.OK, c.read("com.foo.game", 5)[1].state)
        assertEquals(4L, File(root, "files/a.dat").length())
    }

    @Test fun safCopyFailureGoesBackToChoose() {
        val c = cl()
        val r = result(listOf(DataFile("bad.dat", Payload("b", 9) { ByteArray(1).inputStream() })))
        val end = ApkInstallFlow.executeSafCopy(r, FileDocDir(tmp.newFolder("d2")), c) {}
        assertEquals(UiState.Phase.CHOOSE_METHOD, end.phase)
        assertEquals(State.FAILED, c.read("com.foo.game", 5)[1].state)
    }

    @Test fun safInstallConflictKeepsMethodUntried() = runBlocking {
        val c = cl()
        val conflict = InstallOutcome.from(android.content.pm.PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        val end = ApkInstallFlow.executeSafInstall(result(listOf(DataFile("a", payload(ByteArray(1))))), { _, _, _ -> conflict }, dev, c) {}
        assertEquals(UiState.Phase.FAILED, end.phase)
        assertEquals(State.NOT_TRIED, c.read("com.foo.game", 5)[1].state)
    }
}
