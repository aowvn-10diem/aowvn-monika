package vn.aow.monika

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.aow.monika.config.defaultSaveProfiles
import vn.aow.monika.library.*
import vn.aow.monika.ui.TestApp
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [34])
class ConsoleSaveTransferTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    @Before fun nativePath() { app.applicationInfo.nativeLibraryDir="/fake/arm64" }
    private fun reject(block: () -> Unit) { assertThrows(IOException::class.java, block) }
    private fun put32(b: ByteArray, at: Int, value: Int) { ByteBuffer.wrap(b,at,4).order(ByteOrder.LITTLE_ENDIAN).putInt(value) }
    private fun sfo(key: String, value: String): ByteArray {
        val keys=(key+"\u0000").toByteArray();val data=(value+"\u0000").toByteArray()
        return ByteArray(36+keys.size+data.size).also { b ->
            byteArrayOf(0,80,83,70).copyInto(b);put32(b,4,0x101);put32(b,8,36);put32(b,12,36+keys.size);put32(b,16,1)
            b[22]=4;b[23]=2;put32(b,24,data.size);put32(b,28,data.size);put32(b,32,0);keys.copyInto(b,36);data.copyInto(b,36+keys.size)
        }
    }
    private fun iso(): ByteArray {
        val sfo=sfo("DISC_ID","ULUS12345")
        val b=ByteArray(24*2048)
        fun record(at: Int, sector: Int, size: Int, name: ByteArray, dir: Boolean) {
            b[at]=(33+name.size+(if(name.size%2==0) 1 else 0)).toByte();put32(b,at+2,sector);put32(b,at+10,size)
            b[at+25]=if(dir) 2 else 0;b[at+32]=name.size.toByte();name.copyInto(b,at+33)
        }
        val pvd=16*2048;b[pvd]=1;"CD001".toByteArray().copyInto(b,pvd+1);b[pvd+6]=1
        record(pvd+156,20,2048,byteArrayOf(0),true)
        record(20*2048,21,2048,"PSP_GAME".toByteArray(),true)
        record(21*2048,22,sfo.size,"PARAM.SFO;1".toByteArray(),false);sfo.copyInto(b,22*2048)
        return b
    }
    private fun cso(iso: ByteArray): ByteArray {
        val count=iso.size/2048;val out=ByteArrayOutputStream();val indices=IntArray(count+1)
        var position=24+(count+1)*4
        repeat(count) { i ->
            val raw=iso.copyOfRange(i*2048,(i+1)*2048)
            indices[i]=position or if(i%2==0) Int.MIN_VALUE else 0
            val bytes=if(i%2==0) raw else {
                val d=Deflater(6,true)
                try { d.setInput(raw);d.finish();val compressed=ByteArray(4096);compressed.copyOf(d.deflate(compressed)) }
                finally { d.end() }
            }
            out.write(bytes);position+=bytes.size
        }
        indices[count]=position
        val header=ByteArray(24+(count+1)*4);"CISO".toByteArray().copyInto(header);put32(header,4,24)
        ByteBuffer.wrap(header,8,8).order(ByteOrder.LITTLE_ENDIAN).putLong(iso.size.toLong());put32(header,16,2048);header[20]=1
        indices.forEachIndexed { i,value -> put32(header,24+i*4,value) }
        return header+out.toByteArray()
    }
    private fun ncch(): ByteArray = ByteArray(512).also { b ->
        "NCCH".toByteArray().copyInto(b,256)
        ByteBuffer.wrap(b,0x118,8).order(ByteOrder.LITTLE_ENDIAN).putLong(0x0004000012345600L)
    }

    @Test fun ps1RawAndDexDriveAreExactAndCorruptContainersAreRejected() {
        val raw=ByteArray(131072);raw[0]=77;raw[1]=67;raw[127]=(77 xor 67).toByte()
        val gme=ByteArray(3904)+raw;"123-456-STD".toByteArray().copyInto(gme)
        val p=defaultSaveProfiles().getValue("ps1")
        listOf("mcr","mcd","srm").forEach { assertArrayEquals(raw,SaveFormats.convert(raw,it,p));assertArrayEquals(raw,SaveFormats.convert(gme,it,p)) }
        assertArrayEquals(raw,SaveFormats.convert(gme,"gme",p))
        reject { SaveFormats.convert(gme.copyOf(gme.size-1),"gme",p) }
        reject { ConsoleSaveFormats.ps1(raw.copyOf().also { it[127]=0 }) }
        reject { ConsoleSaveFormats.ps1(ByteArray(131072)) }
    }

    @Test fun pspIdentityReadsIsoAndCsoMetadataWithoutExecutingOrExtractingGame() {
        val dir=Files.createTempDirectory("psp-meta").toFile()
        try {
            val iso=File(dir,"fixture.iso").apply { writeBytes(iso()) }
            val cso=File(dir,"fixture.cso").apply { writeBytes(cso(iso.readBytes())) }
            assertEquals("ULUS12345",ConsoleSaveFormats.pspDisc(iso));assertEquals("ULUS12345",ConsoleSaveFormats.pspDisc(cso))
            val before=iso.readBytes();iso.writeBytes(before.copyOf(128));reject { ConsoleSaveFormats.pspDisc(iso) }
            cso.writeBytes(cso.readBytes().also { it[21]=31 });reject { ConsoleSaveFormats.pspDisc(cso) }
            reject { ConsoleSaveFormats.sfo(sfo("DISC_ID","X").also { put32(it,12,Int.MAX_VALUE) },"DISC_ID") }
        } finally { dir.deleteRecursively() }
    }

    @Test fun threeDsIdentityAcceptsNcchAndNcsdAndRejectsUnknownOrWrongTitleType() {
        val dir=Files.createTempDirectory("3ds-meta").toFile()
        try {
            val file=File(dir,"fixture.cxi").apply { writeBytes(ncch()) }
            assertEquals("0004000012345600",ConsoleSaveFormats.title3ds(file))
            val container=ByteArray(1024);"NCSD".toByteArray().copyInto(container,256);put32(container,0x120,1);ncch().copyInto(container,512)
            file.writeBytes(container);assertEquals("0004000012345600",ConsoleSaveFormats.title3ds(file))
            put32(container,0x120,Int.MAX_VALUE);file.writeBytes(container);reject { ConsoleSaveFormats.title3ds(file) }
            file.writeBytes(ByteArray(512));reject { ConsoleSaveFormats.title3ds(file) }
            file.writeBytes(ncch().also { it[0x11e]=5 });reject { ConsoleSaveFormats.title3ds(file) }
        } finally { dir.deleteRecursively() }
    }

    private fun treeTarget(dir: File, system: String, identity: String): SaveTarget {
        val rom=File(dir,"fixture.bin").apply { writeText("synthetic identity, not playable") }
        val game=Game(dir,"Fixture",AppGraph.config.current.system(system),rom)
        return SaveTarget(game,system,if(system=="psp") "ppsspp" else "citra","v1",defaultSaveProfiles().getValue(system),dir,null,emptyMap(),directory=File(dir,"save-root"),identity=identity)
    }

    @Test fun pspTreeChecksDirectorySfoWrongGameAndScopeBeforeWriting() {
        val dir=Files.createTempDirectory("psp-tree").toFile()
        try {
            val t=treeTarget(dir,"psp","ULUS12345")
            val files=mapOf("PSP/SAVEDATA/ULUS12345DATA/PARAM.SFO" to sfo("SAVEDATA_DIRECTORY","ULUS12345DATA"),"PSP/SAVEDATA/ULUS12345DATA/DATA.BIN" to byteArrayOf(1),"PSP/SAVEDATA/ULUS12345DATA/EMPTY" to byteArrayOf())
            val plan=GameSaveTransfer.plan(t,files,"fixture.zip") { "hash" }
            assertEquals(3,plan.writes.size);assertFalse(t.directory!!.exists())
            reject { GameSaveTransfer.plan(t,files.mapKeys { it.key.replace("ULUS12345","ULUS99999") },"fixture.zip") { "hash" } }
            reject { GameSaveTransfer.plan(t,files-("PSP/SAVEDATA/ULUS12345DATA/PARAM.SFO"),"fixture.zip") { "hash" } }
            reject { t.destination("save/ULUS99999DATA/DATA.BIN") }
            val duplicate=files+mapOf("ULUS12345DATA/DATA.BIN" to byteArrayOf(2))
            reject { GameSaveTransfer.plan(t,duplicate,"fixture.zip") { "hash" } }
        } finally { dir.deleteRecursively() }
    }

    @Test fun threeDsCitraCheckpointAndPlainDumpBindTitleAndRejectAmbiguousOrEncryptedInput() {
        val dir=Files.createTempDirectory("3ds-tree").toFile()
        try {
            val id="0004000012345600";val t=treeTarget(dir,"3ds",id);val bytes=byteArrayOf(1,2)
            listOf(mapOf("sdmc/Nintendo 3DS/a/b/title/00040000/12345600/data/00000001/main" to bytes) to "citra.zip",
                mapOf("0x${id} Fixture/backup/main" to bytes) to "checkpoint.zip", mapOf("main" to bytes) to "$id.zip").forEach { (files,name) ->
                val p=GameSaveTransfer.plan(t,files,name) { "hash" };assertArrayEquals(bytes,p.writes.getValue("save/main"));assertTrue(p.replaceTree)
            }
            reject { GameSaveTransfer.plan(t,mapOf("main" to bytes),"game.zip") { "hash" } }
            reject { GameSaveTransfer.plan(t,mapOf("title/00040000/99999999/data/00000001/main" to bytes),"$id.zip") { "hash" } }
            val encrypted=ByteArray(512);"DISA".toByteArray().copyInto(encrypted,256)
            reject { GameSaveTransfer.plan(t,mapOf("main" to encrypted),"$id.zip") { "hash" } }
            val user=File(dir,"user");val ids="0".repeat(32);val title=File(user,"sdmc/Nintendo 3DS/$ids/$ids/title/00040000/12345600/data/00000001").apply { mkdirs() }
            assertEquals(title,ConsoleSaveTrees.titleRoot(user,id))
            File(user,"sdmc/Nintendo 3DS/$ids/${"1".repeat(32)}/title/00040000/12345600/data/00000001").mkdirs()
            reject { ConsoleSaveTrees.titleRoot(user,id) };assertFalse(t.directory!!.exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun treeSnapshotRejectsSymlinkAndEmptyBackupCanRestoreAnEmptyGameScope() {
        val dir=Files.createTempDirectory("tree-safety").toFile();val outside=Files.createTempDirectory("tree-outside").toFile()
        try {
            val t=treeTarget(dir,"3ds","0004000012345600");t.directory!!.mkdirs()
            Files.createSymbolicLink(File(t.directory,"linked").toPath(),outside.toPath());reject { GameSaveTransfer.snapshot(t) }
            File(t.directory,"linked").delete()
            val meta=GameSaveManifest(system=t.system,gameSha256="hash",core=t.core,coreVersion=t.coreVersion,files=emptyList(),treeSnapshot=true)
            val plan=GameSaveTransfer.plan(t,mapOf("monika-save.json" to Json.encodeToString(meta).toByteArray())) { "hash" }
            assertTrue(plan.replaceTree);assertTrue(plan.writes.isEmpty());assertTrue(plan.warning.contains("về trống"))
            reject { t.destination("save/../game.3ds") }
        } finally { dir.deleteRecursively();outside.deleteRecursively() }
    }

    @Test fun newProfilesEnableSharedActionsOnlyForReadyRecognizedGames() {
        val dir=Files.createTempDirectory("save-actions").toFile()
        try {
            val file=File(dir,"fixture").apply { writeText("synthetic") }
            listOf("java","nds","gba","3ds","psp","ps1").forEach { id ->
                val game=Game(dir,"Fixture",AppGraph.config.current.system(id),file)
                assertTrue(id,GameSaveTransfer.supported(game,AppGraph.config.current))
                assertFalse(GameSaveTransfer.supported(game,AppGraph.config.current.copy(saveTransfer=vn.aow.monika.config.SaveTransferConfig(emptyMap()))))
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun pspExportImportBackupRestoresWholeTreeIncludingEmptyFilesAndPreservesOtherGame() {
        val dir=Files.createTempDirectory("psp-roundtrip").toFile()
        val rom=File(dir,"Fixture.iso").apply { writeBytes(iso()) }
        val game=Game(dir,"Fixture",AppGraph.config.current.system("psp"),rom)
        val target=GameSaveTransfer.target(app,game);val root=target.directory!!
        val own=File(root,"ULUS12345DATA");val other=File(root,"ULUS99999DATA/KEEP")
        val backups=File(app.filesDir,"save-backups");val known=backups.listFiles().orEmpty().toSet()
        try {
            own.mkdirs();File(own,"PARAM.SFO").writeBytes(sfo("SAVEDATA_DIRECTORY",own.name));File(own,"DATA.BIN").writeBytes(byteArrayOf(3));File(own,"EMPTY").writeBytes(byteArrayOf())
            other.parentFile!!.mkdirs();other.writeText("keep")
            val zip=ByteArrayOutputStream();assertEquals(3,GameSaveTransfer.export(app,game,zip))
            File(own,"DATA.BIN").writeBytes(byteArrayOf(8));File(own,"OLD").writeText("backup")
            val plan=GameSaveTransfer.prepare(app,game,"save.zip",zip.toByteArray().inputStream())
            val result=GameSaveTransfer.commit(app,plan)
            assertArrayEquals(byteArrayOf(3),File(own,"DATA.BIN").readBytes());assertFalse(File(own,"OLD").exists())
            val undo=result.backup.inputStream().use { GameSaveTransfer.prepare(app,game,"backup.zip",it) };GameSaveTransfer.commit(app,undo)
            assertArrayEquals(byteArrayOf(8),File(own,"DATA.BIN").readBytes());assertEquals("backup",File(own,"OLD").readText())
            assertTrue(File(own,"EMPTY").isFile);assertEquals(0L,File(own,"EMPTY").length());assertEquals("keep",other.readText());assertArrayEquals(iso(),rom.readBytes())
        } finally { own.deleteRecursively();other.parentFile!!.deleteRecursively();target.states.values.forEach { it.delete() };dir.deleteRecursively();backups.listFiles().orEmpty().filter { it !in known }.forEach { it.delete() } }
    }
}
