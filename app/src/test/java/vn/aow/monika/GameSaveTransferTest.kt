package vn.aow.monika

import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.defaultSaveProfiles
import vn.aow.monika.library.*
import vn.aow.monika.ui.TestApp
import java.io.*
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class GameSaveTransferTest {
    @Before fun nativePathFixture() {
        // CoreManager đọc ABI của app; Robolectric mặc định không có nativeLibraryDir.
        ApplicationProvider.getApplicationContext<android.app.Application>().applicationInfo.nativeLibraryDir = "/fake/arm64"
    }

    private fun target(dir: File, core: String = "mgba"): SaveTarget {
        val rom = File(dir, "Fixture.gba").apply { writeText("synthetic ROM identity fixture, not a playable ROM") }
        val game = Game(dir, "Fixture", AppGraph.config.current.system("gba"), rom)
        val profile = defaultSaveProfiles().getValue("gba")
        return SaveTarget(game, "gba", core, "v1", profile, dir, File(dir,"saves/fixture.srm"), mapOf("state/slot1.state" to File(dir,"states/fixture.state")))
    }
    private fun rejected(block: () -> Unit) { assertThrows(IOException::class.java, block) }

    @Test fun rawFileAndExternalZipRejectWrongGameOrMultipleSavesBeforeAnyWrite() {
        val dir = Files.createTempDirectory("save-test").toFile()
        try {
            val target = target(dir)
            val raw = ByteArray(8192) { 42 }
            val plan = GameSaveTransfer.plan(target, mapOf("Fixture.sav" to raw)) { error("raw không giả vờ có hash") }
            assertArrayEquals(raw, plan.writes.getValue("ram.srm")); assertTrue(plan.warning.contains("không chứa mã"))
            assertFalse(target.ram!!.exists())
            rejected { GameSaveTransfer.plan(target, mapOf("Other.sav" to raw)) { "x" } }
            rejected { GameSaveTransfer.plan(target, mapOf("Fixture.sav" to raw,"Other.sav" to raw)) { "x" } }
            assertFalse(target.ram.exists()); assertTrue(target.game.entry!!.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun monikaArchiveBindsSystemRomCoreVersionAndWhitelistedStateDestinations() {
        val dir = Files.createTempDirectory("save-test").toFile()
        try {
            val t = target(dir)
            val data = mapOf("ram.srm" to ByteArray(512),"state/slot1.state" to byteArrayOf(1,2))
            fun archive(m: GameSaveManifest) = data + ("monika-save.json" to Json.encodeToString(m).toByteArray())
            val meta = GameSaveManifest(system="gba",gameSha256="hash",core="mgba",coreVersion="v1",files=data.keys.toList())
            val plan = GameSaveTransfer.plan(t, archive(meta)) { "hash" }
            assertEquals(data.keys, plan.writes.keys)
            rejected { GameSaveTransfer.plan(t, archive(meta.copy(system="nds"))) { "hash" } }
            rejected { GameSaveTransfer.plan(t, archive(meta)) { "other-ROM" } }
            rejected { GameSaveTransfer.plan(t, archive(meta.copy(core="other"))) { "hash" } }
            rejected { GameSaveTransfer.plan(t, archive(meta.copy(coreVersion="v2"))) { "hash" } }
            rejected { GameSaveTransfer.plan(t, archive(meta.copy(files=listOf("ram.srm")))) { "hash" } }
            assertFalse(t.ram!!.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun javaRmsFolderIsOneSuiteAndSingleRecordNeedsExistingWholeStore() {
        val dir = Files.createTempDirectory("rms-test").toFile()
        try {
            val t = target(dir).copy(system="java",core="j2me",coreVersion="rms-v3",profile=defaultSaveProfiles().getValue("java"),root=File(dir,"data/Suite"),ram=null,states=emptyMap(),suite="Suite")
            val rms = SaveFormatsTest().rms()
            val plan = GameSaveTransfer.plan(t,rms.mapKeys { "data/Suite/${it.key}" }) { "hash" }
            assertEquals(rms.keys.map { "rms/$it" }.toSet(),plan.writes.keys)
            rejected { GameSaveTransfer.plan(t,rms.mapKeys { "data/Other/${it.key}" }) { "hash" } }
            rejected { GameSaveTransfer.plan(t,mapOf("progress.1.rsr" to rms.getValue("progress.1.rsr"))) { "hash" } }
            t.root.mkdirs(); rms.forEach { (name,bytes) -> File(t.root,name).writeBytes(bytes) }
            val single = GameSaveTransfer.plan(t,mapOf("progress.1.rsr" to rms.getValue("progress.1.rsr"))) { "hash" }
            assertEquals(2,single.writes.size)
            assertTrue(t.game.entry!!.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun transactionFailureRestoresEarlierWritesAndNeverTouchesRomOrOtherGames() {
        val dir = Files.createTempDirectory("save-test").toFile()
        try {
            val t=target(dir); val old=mapOf("ram.srm" to ByteArray(512){3},"state/slot1.state" to byteArrayOf(4))
            old.forEach { (key,bytes) -> t.destination(key).apply { parentFile!!.mkdirs();writeBytes(bytes) } }
            val sibling=File(dir,"saves/other.srm").apply { writeText("keep") }
            var calls=0
            rejected { GameSaveTransfer.replace(t,old.mapValues { byteArrayOf(9) },emptySet(),old) { from,to ->
                if (++calls==2) throw IOException("injected write failure")
                Files.move(from.toPath(),to.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING); Unit
            } }
            old.forEach { (key,bytes) -> assertArrayEquals(bytes,t.destination(key).readBytes()) }
            assertEquals("keep",sibling.readText()); assertTrue(t.game.entry!!.exists())
            assertFalse(dir.walkTopDown().any { it.name.startsWith(".monika-") })
        } finally { dir.deleteRecursively() }
    }

    @Test fun exportImportBackupIsRestorableAndCancellationDoesNotWrite() {
        val app=ApplicationProvider.getApplicationContext<android.app.Application>()
        val dir=Files.createTempDirectory("save-test").toFile()
        val game=target(dir).game
        val t=GameSaveTransfer.target(app,game)
        val ram=t.ram!!;val state=t.states.getValue("state/slot1.state")
        val backupRoot=File(app.filesDir,"save-backups")
        val known=backupRoot.listFiles().orEmpty().toSet()
        try {
            ram.parentFile!!.mkdirs();ram.writeBytes(ByteArray(8192){3});state.parentFile!!.mkdirs();state.writeBytes(byteArrayOf(7))
            val zip=ByteArrayOutputStream(); assertEquals(2,GameSaveTransfer.export(app,game,zip))
            ram.writeBytes(ByteArray(8192){8})
            val plan=GameSaveTransfer.prepare(app,game,"anything.zip",zip.toByteArray().inputStream())
            assertTrue(ram.readBytes().all { it==8.toByte() }) // Chuẩn bị hoặc hủy hộp thoại không ghi gì.
            val imported=GameSaveTransfer.commit(app,plan)
            assertTrue(ram.readBytes().all { it==3.toByte() });assertTrue(imported.backup.isFile)
            val undo=imported.backup.inputStream().use { GameSaveTransfer.prepare(app,game,"backup.zip",it) }
            GameSaveTransfer.commit(app,undo)
            assertTrue(ram.readBytes().all { it==8.toByte() });assertArrayEquals(byteArrayOf(7),state.readBytes())
            assertTrue(game.entry!!.exists())
        } finally { ram.delete();t.states.values.forEach { it.delete() };dir.deleteRecursively();backupRoot.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() } }
    }

    @Test fun emptyStatesAreOmittedFromExportAndRestorableBackupButValidStateSurvives() {
        val app=ApplicationProvider.getApplicationContext<android.app.Application>()
        val dir=Files.createTempDirectory("save-empty-state").toFile()
        val game=target(dir).game
        val t=GameSaveTransfer.target(app,game)
        val ram=t.ram!!
        val validState="state/slot2.state"
        val backupRoot=File(app.filesDir,"save-backups")
        val known=backupRoot.listFiles().orEmpty().toSet()
        try {
            ram.parentFile!!.mkdirs();ram.writeBytes(ByteArray(8192){3})
            t.states.forEach { (name,file) ->
                file.parentFile!!.mkdirs();file.writeBytes(if(name==validState) byteArrayOf(7) else byteArrayOf())
            }
            val zip=ByteArrayOutputStream();assertEquals(2,GameSaveTransfer.export(app,game,zip))
            val files=SaveFormats.archive(zip.toByteArray().inputStream())
            assertEquals(setOf("ram.srm",validState,"monika-save.json"),files.keys)
            assertArrayEquals(byteArrayOf(7),files.getValue(validState))
            t.states.filterKeys { it!=validState }.values.forEach { assertTrue(it.isFile);assertEquals(0L,it.length()) }
            val plan=GameSaveTransfer.prepare(app,game,"save.zip",zip.toByteArray().inputStream())
            ram.writeBytes(ByteArray(8192){8})
            val result=GameSaveTransfer.commit(app,plan)
            assertTrue(ram.readBytes().all { it==3.toByte() })
            val backup=result.backup.inputStream().use { SaveFormats.archive(it) }
            assertEquals(files.keys,backup.keys)
            val undo=result.backup.inputStream().use { GameSaveTransfer.prepare(app,game,"backup.zip",it) }
            GameSaveTransfer.commit(app,undo)
            assertTrue(ram.readBytes().all { it==8.toByte() })
            assertArrayEquals(byteArrayOf(7),t.states.getValue(validState).readBytes())
            t.states.filterKeys { it!=validState }.values.forEach { assertTrue(it.isFile);assertEquals(0L,it.length()) }
            assertTrue(game.entry!!.exists())
        } finally { ram.delete();t.states.values.forEach { it.delete() };dir.deleteRecursively();backupRoot.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() } }
    }

    @Test fun emptyRamIsOmittedFromExportAndBackupWhileValidStateRoundTrips() {
        val app=ApplicationProvider.getApplicationContext<android.app.Application>()
        val dir=Files.createTempDirectory("save-empty-ram").toFile()
        val game=target(dir).game
        val t=GameSaveTransfer.target(app,game)
        val ram=t.ram!!;val state=t.states.getValue("state/slot1.state")
        val backupRoot=File(app.filesDir,"save-backups")
        val known=backupRoot.listFiles().orEmpty().toSet()
        try {
            ram.parentFile!!.mkdirs();ram.writeBytes(byteArrayOf())
            state.parentFile!!.mkdirs();state.writeBytes(byteArrayOf(7))
            val zip=ByteArrayOutputStream();assertEquals(1,GameSaveTransfer.export(app,game,zip))
            val exported=SaveFormats.archive(zip.toByteArray().inputStream())
            assertEquals(setOf("state/slot1.state","monika-save.json"),exported.keys)
            assertTrue(ram.isFile);assertEquals(0L,ram.length())
            val imported=GameSaveTransfer.prepare(app,game,"save.zip",zip.toByteArray().inputStream())
            assertEquals(setOf("state/slot1.state"),imported.writes.keys)
            state.writeBytes(byteArrayOf(8))
            val result=GameSaveTransfer.commit(app,imported)
            assertArrayEquals(byteArrayOf(7),state.readBytes())
            val backup=result.backup.inputStream().use { SaveFormats.archive(it) }
            assertEquals(exported.keys,backup.keys)
            val undo=result.backup.inputStream().use { GameSaveTransfer.prepare(app,game,"backup.zip",it) }
            GameSaveTransfer.commit(app,undo)
            assertArrayEquals(byteArrayOf(8),state.readBytes())
            assertTrue(ram.isFile);assertEquals(0L,ram.length())
            // ZIP ngoài/Monika tự chèn SRAM rỗng vẫn bị từ chối; không hạ guard nhập.
            val meta=Json.decodeFromString<GameSaveManifest>(exported.getValue("monika-save.json").toString(Charsets.UTF_8))
            val invalid=exported + ("ram.srm" to byteArrayOf()) +
                ("monika-save.json" to Json.encodeToString(meta.copy(files=meta.files+"ram.srm")).toByteArray())
            rejected { GameSaveTransfer.plan(t,invalid) { meta.gameSha256 } }
            state.delete()
            val empty=ByteArrayOutputStream()
            rejected { GameSaveTransfer.export(app,game,empty) }
            assertEquals(0,empty.size());assertTrue(game.entry!!.exists())
        } finally { ram.delete();t.states.values.forEach { it.delete() };dir.deleteRecursively();backupRoot.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() } }
    }

    @Test fun failedWriteRestoresEmptyRamFromUnfilteredSnapshot() {
        val dir=Files.createTempDirectory("save-empty-ram-rollback").toFile()
        try {
            val t=target(dir);val ram=t.ram!!;val state=t.states.getValue("state/slot1.state")
            ram.parentFile!!.mkdirs();ram.writeBytes(byteArrayOf())
            state.parentFile!!.mkdirs();state.writeBytes(byteArrayOf(7))
            val before=GameSaveTransfer.snapshot(t)
            assertTrue(before.containsKey("ram.srm"));assertEquals(0,before.getValue("ram.srm").size)
            var moves=0
            rejected { GameSaveTransfer.replace(t,linkedMapOf("ram.srm" to ByteArray(8192){9},"state/slot1.state" to byteArrayOf(8)),emptySet(),before) { from,to ->
                if(++moves==2) throw IOException("injected second move failure")
                Files.move(from.toPath(),to.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);Unit
            } }
            assertTrue(ram.isFile);assertEquals(0L,ram.length())
            assertArrayEquals(byteArrayOf(7),state.readBytes());assertTrue(t.game.entry!!.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedWriteRestoresExistingEmptyStateFromUnfilteredSnapshot() {
        val dir=Files.createTempDirectory("save-empty-rollback").toFile()
        try {
            val t=target(dir)
            val state=t.states.getValue("state/slot1.state")
            state.parentFile!!.mkdirs();state.writeBytes(byteArrayOf())
            t.ram!!.parentFile!!.mkdirs();t.ram.writeBytes(ByteArray(512){3})
            val before=GameSaveTransfer.snapshot(t)
            assertTrue(before.containsKey("state/slot1.state"))
            var moves=0
            rejected { GameSaveTransfer.replace(t,linkedMapOf("state/slot1.state" to byteArrayOf(9),"ram.srm" to ByteArray(512){8}),emptySet(),before) { from,to ->
                if(++moves==2) throw IOException("injected second move failure")
                Files.move(from.toPath(),to.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);Unit
            } }
            assertTrue(state.isFile);assertEquals(0L,state.length())
            assertArrayEquals(before.getValue("ram.srm"),t.ram.readBytes())
            assertTrue(t.game.entry!!.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun fileLockRejectsSecondOwnerAndReleasesOnClose() {
        val dir=Files.createTempDirectory("save-lock").toFile()
        try {
            val gameLease=GameSaveGate.acquire(dir)
            rejected { GameSaveGate.acquire(dir).close() }
            gameLease.close()
            GameSaveGate.acquire(dir).use { assertTrue(File(dir,"save-transfer.lock").exists()) }
            GameSaveGate.acquire(dir).close()
        } finally { dir.deleteRecursively() }
    }

    @Test fun pathsMatchLegacyKeysRespectConfigAndBlockTraversalSymlink() {
        val dir=Files.createTempDirectory("save-test").toFile()
        try {
            val path="/synthetic/Game.nds";val old="Game-${path.hashCode()}"
            assertEquals("$old-melondsds.srm",GameSavePaths.ram(dir,path,"melondsds",null).name)
            assertEquals("$old.srm",GameSavePaths.ram(dir,path,"desmume",null).name)
            val p=defaultSaveProfiles().getValue("gba").copy(saveDirectory="saves-v2")
            assertEquals(File(dir,"saves-v2"),GameSavePaths.ram(dir,path,"mgba",p).parentFile)
            rejected { GameSavePaths.child(dir,"../Game.gba") }
            val outside=Files.createTempDirectory("save-outside").toFile()
            try { Files.createSymbolicLink(File(dir,"escape").toPath(),outside.toPath());rejected { GameSavePaths.child(dir,"escape/file.srm") } }
            finally { outside.deleteRecursively() }
        } finally { dir.deleteRecursively() }
    }
}
