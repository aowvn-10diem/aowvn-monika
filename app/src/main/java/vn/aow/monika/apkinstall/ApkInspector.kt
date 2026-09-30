package vn.aow.monika.apkinstall

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipFile

/**
 * Đọc 1 gói game Android (APK / APKS / XAPK / thư mục đã giải nén) TRƯỚC khi cài:
 * tên gói, phiên bản, targetSdk, chip, các split, OBB, dữ liệu → và liệt kê vấn đề ([Problem]) để báo người chơi.
 *
 * Không giải nén OBB/dữ liệu (có thể vài GB): chỉ giữ [Payload] mở thẳng từ file zip hoặc đĩa.
 * Chỉ các file .apk được giải nén vào [tempRoot] (Android cần file thật để cài).
 */
class ApkInspector(
    private val device: DeviceInfo,
    private val tempRoot: File,
    /** Lấy tên hiển thị của game từ file APK (trên máy thật dùng PackageManager); trả null = dùng tên gói. */
    private val labelOf: (File) -> String? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** [choose]: khi trong thư mục có nhiều game khác nhau, file người chơi đã chọn. */
    fun inspect(source: File, choose: File? = null): InspectResult = try {
        when {
            source.isDirectory -> inspectFolder(source, choose)
            else -> inspectFile(source)
        }
    } catch (e: IOException) {
        InspectResult.failed(PackKind.APK, Problem.NoApk)
    }

    // ---------- file đơn ----------

    private fun inspectFile(file: File): InspectResult = when (file.extension.lowercase()) {
        "apk" -> build(PackKind.APK, listOf(ApkPart(file, splitNameOf(file))), emptyList(), emptyList(), null, file.parentFile)
        "apks" -> inspectApks(file)
        "xapk" -> inspectXapk(file)
        "apkm" -> InspectResult.failed(PackKind.APKM_UNSUPPORTED, Problem.Unsupported)
        else -> InspectResult.failed(PackKind.APK, Problem.NoApk)
    }

    private fun inspectApks(file: File): InspectResult {
        val work = newWork()
        ZipFile(file).use { zip ->
            val parts = extractApks(zip, work)
            if (parts.isEmpty()) return InspectResult.failed(PackKind.APKS, Problem.NoApk)
            return build(PackKind.APKS, parts, emptyList(), emptyList(), work, null)
        }
    }

    private fun inspectXapk(file: File): InspectResult {
        val work = newWork()
        ZipFile(file).use { zip ->
            val parts = extractApks(zip, work, manifestApks(zip))
            if (parts.isEmpty()) return InspectResult.failed(PackKind.XAPK, Problem.NoApk)
            val obb = ArrayList<Pair<String, Payload>>()
            val data = ArrayList<DataFile>()
            for (e in zip.entries().asSequence().filter { !it.isDirectory }) {
                val name = e.name.replace('\\', '/')
                val payload = Payload(name.substringAfterLast('/'), e.size) { zip.getInputStream(e) }
                when {
                    name.contains("Android/data/", true) || name.startsWith("data/", true) -> {
                        val rel = name.substringAfter("Android/data/", name.substringAfter("data/")).substringAfter('/', "")
                        if (rel.isNotEmpty()) data += DataFile(rel, payload)
                    }
                    name.endsWith(".obb", true) -> obb += name.substringAfterLast('/') to payload
                }
            }
            // ZipFile đóng ngay khi thoát khối use → phải mở lại cho Payload: dùng bản sao Payload đọc theo đường dẫn zip.
            return build(PackKind.XAPK, parts, obb, data, work, null, reopen = file)
        }
    }

    /** manifest.json của XAPK (APKPure): danh sách file split theo `split_apks[].file`. Không có → null (lấy mọi .apk). */
    private fun manifestApks(zip: ZipFile): Set<String>? {
        val entry = zip.getEntry("manifest.json") ?: return null
        val obj = runCatching { json.parseToJsonElement(zip.getInputStream(entry).bufferedReader().readText()).jsonObject }.getOrNull() ?: return null
        val arr = (obj["split_apks"] as? JsonArray) ?: return null
        return arr.mapNotNull { (it as? JsonObject)?.get("file")?.jsonPrimitive?.content }.toSet().ifEmpty { null }
    }

    /** Giải nén các *.apk trong zip ra [work] (chống zip-slip: chỉ dùng tên file cuối). */
    private fun extractApks(zip: ZipFile, work: File, only: Set<String>? = null): List<ApkPart> {
        var entries = zip.entries().asSequence().filter { !it.isDirectory && it.name.endsWith(".apk", true) }.toList()
        if (only != null) entries = entries.filter { it.name in only || it.name.substringAfterLast('/') in only.map { n -> n.substringAfterLast('/') } }
        // Gói bundletool: ưu tiên "splits/"; không có thì "standalones/"; còn lại lấy hết.
        val splits = entries.filter { it.name.startsWith("splits/") }
        val standalones = entries.filter { it.name.startsWith("standalones/") }
        val chosen = when {
            splits.isNotEmpty() -> splits
            standalones.isNotEmpty() -> standalones
            else -> entries
        }
        val used = HashSet<String>()
        return chosen.map { e ->
            var name = e.name.substringAfterLast('/')
            if (!used.add(name)) name = "${used.size}_$name"
            val out = File(work, name)
            zip.getInputStream(e).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            ApkPart(out, splitNameOf(out))
        }
    }

    // ---------- thư mục đã giải nén ----------

    private fun inspectFolder(dir: File, choose: File?): InspectResult {
        val files = dir.walkTopDown().maxDepth(6).filter { it.isFile }.toList()
        val pkgs = files.filter { it.extension.equals("apk", true) || it.extension.equals("apks", true) || it.extension.equals("xapk", true) }
        if (pkgs.isEmpty()) return InspectResult.failed(PackKind.FOLDER, Problem.NoApk)
        val entry = choose ?: run {
            val bundles = pkgs.filter { !it.extension.equals("apk", true) }
            when {
                bundles.size == 1 && pkgs.count { it.extension.equals("apk", true) }.let { it == 0 } -> bundles.first()
                pkgs.size == 1 -> pkgs.first()
                else -> null
            }
        }
        // Nhiều APK trong cùng 1 thư mục có thể là base + split của cùng 1 game (SAI giải nén sẵn) → gom theo tên gói.
        val looseApks = pkgs.filter { it.extension.equals("apk", true) }
        if (entry == null) {
            val byPkg = looseApks.groupBy { pkgOf(it) }.filterKeys { it != null }
            if (byPkg.size == 1 && looseApks.size == pkgs.size) {
                val parts = looseApks.map { ApkPart(it, splitNameOf(it)) }
                val (obb, data) = scanFolderExtras(dir, files, byPkg.keys.first()!!)
                return build(PackKind.FOLDER, parts, obb, data, null, dir)
            }
            return InspectResult.failed(PackKind.FOLDER, Problem.NeedChoice(pkgs))
        }
        return when (entry.extension.lowercase()) {
            "apks" -> inspectApks(entry).withFolderExtras(dir, files)
            "xapk" -> inspectXapk(entry)
            else -> {
                // APK đã chọn + các split cùng thư mục (cùng gói).
                val pkg = pkgOf(entry)
                val siblings = looseApks.filter { it.parentFile == entry.parentFile && pkgOf(it) == pkg }
                val parts = (siblings.ifEmpty { listOf(entry) }).map { ApkPart(it, splitNameOf(it)) }
                val (obb, data) = scanFolderExtras(dir, files, pkg ?: "")
                build(PackKind.FOLDER, parts, obb, data, null, dir)
            }
        }
    }

    private fun InspectResult.withFolderExtras(dir: File, files: List<File>): InspectResult {
        if (fatal != null) return this
        val (obb, data) = scanFolderExtras(dir, files, packageName)
        return if (obb.isEmpty() && data.isEmpty()) this else build(PackKind.APKS, parts, obb.map { it.first to it.second }, data, workDir, null, versionCodeOverride = versionCode)
    }

    /** OBB: mọi *.obb. Dữ liệu: thư mục tên đúng gói (dưới `Android/data`, `data`, hoặc đứng riêng, miễn không chứa .obb). */
    private fun scanFolderExtras(root: File, files: List<File>, pkg: String): Pair<List<Pair<String, Payload>>, List<DataFile>> {
        val obb = files.filter { it.extension.equals("obb", true) }.map { it.name to Payload(it.name, it.length()) { it.inputStream() } }
        val dataRoots = root.walkTopDown().maxDepth(5).filter { it.isDirectory && it.name == pkg }
            .filter { d -> d.parentFile?.name?.equals("obb", true) != true && d.walkTopDown().none { it.isFile && it.extension.equals("obb", true) } }
            .toList()
        val dataRoot = dataRoots.firstOrNull { it.parentFile?.name.equals("data", true) } ?: dataRoots.firstOrNull()
        val data = dataRoot?.walkTopDown()?.filter { it.isFile }?.map { f ->
            DataFile(f.relativeTo(dataRoot).invariantSeparatorsPath, Payload(f.name, f.length()) { f.inputStream() })
        }?.toList().orEmpty()
        return obb to data
    }

    // ---------- gom kết quả + kiểm tra vấn đề ----------

    private fun build(
        kind: PackKind,
        parts: List<ApkPart>,
        obb: List<Pair<String, Payload>>,
        data: List<DataFile>,
        work: File?,
        @Suppress("UNUSED_PARAMETER") folder: File?,
        reopen: File? = null,
        versionCodeOverride: Long? = null,
    ): InspectResult {
        val base = parts.firstOrNull { it.isBase } ?: parts.first()
        val m = readManifest(base.file) ?: return InspectResult.failed(kind, Problem.NoApk)
        val pkg = m.packageName ?: return InspectResult.failed(kind, Problem.NoApk)
        val versionCode = versionCodeOverride ?: (m.versionCode ?: 0).toLong()
        val minSdk = m.minSdkVersion ?: 1
        val targetSdk = m.targetSdkVersion ?: minSdk
        val abis = parts.flatMapTo(LinkedHashSet()) { nativeAbisOf(it.file) }
        // Zip đã đóng ở nơi gọi → Payload mở lại zip theo đường dẫn khi đọc.
        val obbFiles = obb.map { (name, p) ->
            ObbFile(ObbNames.targetName(name, pkg, versionCode, obb.size == 1), reopenPayload(p, reopen))
        }
        val dataFiles = data.map { DataFile(it.relPath, reopenPayload(it.payload, reopen, it.relPath)) }
        val problems = ArrayList<Problem>()
        problemsFor(minSdk, targetSdk, abis, parts, obbFiles, dataFiles)?.let { problems += it }
        return InspectResult(
            kind, pkg, labelOf(base.file)?.takeIf { it.isNotBlank() }, versionCode, m.versionName,
            minSdk, targetSdk, parts, abis, obbFiles, dataFiles, problems, work,
        )
    }

    private fun problemsFor(minSdk: Int, targetSdk: Int, abis: Set<String>, parts: List<ApkPart>, obb: List<ObbFile>, data: List<DataFile>): List<Problem>? {
        val out = ArrayList<Problem>()
        if (minSdk > device.sdkInt) out += Problem.MinSdkTooHigh(minSdk)
        if (abis.isNotEmpty() && !abiCompatible(abis)) {
            out += if (abis.all { it in ABIS_32 } && device.supported32BitAbis.isEmpty()) Problem.Only32Bit else Problem.NoMatchingAbi
        }
        val need = parts.sumOf { it.file.length() } + obb.sumOf { it.payload.size } + data.sumOf { it.payload.size }
        if (device.freeBytes in 1 until (need * 10 / 9)) out += Problem.LowSpace(need, device.freeBytes)
        if (device.minInstallableTargetSdk > 0 && targetSdk < device.minInstallableTargetSdk) out += Problem.LowTargetSdk(targetSdk, device.minInstallableTargetSdk)
        return out.ifEmpty { null }
    }

    private fun abiCompatible(abis: Set<String>): Boolean {
        val dev = device.supportedAbis.toMutableSet()
        // "armeabi" (đời cổ) chạy được trên mọi máy ARM 32-bit có armeabi-v7a.
        if ("armeabi-v7a" in dev) dev += "armeabi"
        return abis.any { it in dev }
    }

    // ---------- đọc APK ----------

    private class Mf(val packageName: String?, val versionCode: Int?, val versionName: String?, val minSdkVersion: Int?, val targetSdkVersion: Int?, val split: String?)

    private fun readManifest(apk: File): Mf? = runCatching {
        ZipFile(apk).use { z ->
            val e = z.getEntry("AndroidManifest.xml") ?: return null
            val b = z.getInputStream(e).use { AndroidManifestBlock.load(it) }
            Mf(b.packageName, b.versionCode, b.versionName, b.minSdkVersion, b.targetSdkVersion, b.split)
        }
    }.getOrNull()

    private fun pkgOf(apk: File) = readManifest(apk)?.packageName
    private fun splitNameOf(apk: File): String? = readManifest(apk)?.split?.takeIf { it.isNotBlank() }

    private fun nativeAbisOf(apk: File): Set<String> = runCatching {
        ZipFile(apk).use { z ->
            z.entries().asSequence().map { it.name }
                .filter { it.startsWith("lib/") && it.endsWith(".so") }
                .map { it.removePrefix("lib/").substringBefore('/') }.toSet()
        }
    }.getOrDefault(emptySet())

    private fun reopenEntry(zipFile: File, name: String) = ZipFile(zipFile).let { z ->
        // Tìm theo tên cuối; đóng ZipFile khi luồng đóng.
        val e = z.entries().asSequence().first { !it.isDirectory && it.name.replace('\\', '/').endsWith(name) }
        object : java.io.FilterInputStream(z.getInputStream(e)) { override fun close() { super.close(); z.close() } }
    }

    private fun reopenPayload(p: Payload, zip: File?, path: String? = null): Payload =
        if (zip == null) p else Payload(p.name, p.size) { reopenEntry(zip, path ?: p.name) }

    private fun newWork() = File(tempRoot, UUID.randomUUID().toString()).apply { mkdirs() }

    companion object {
        val ABIS_32 = setOf("armeabi", "armeabi-v7a", "x86", "mips")
    }
}
