package vn.aow.monika.pack

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Files
import kotlin.system.exitProcess

private data class Layout(val main: String, val flatten: Boolean = false)
// Cùng main/flatten với PackManager và AzaharModule. ID mới phải có hợp đồng tường minh.
private val layouts = mapOf(
    "azahar" to Layout("libcitra-android.so", true),
    "sevenzip" to Layout("lib7-Zip-JBinding.so"),
    "kirikiri" to Layout("libkrkr2yuri.so"),
    "onsyuri" to Layout("onsyuri.wasm"),
    "rgss" to Layout("lib/libmkxp-z.so"),
)
private fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun fetch(url: String, dest: File, hash: String) = runBlocking {
    require(URI(url).scheme == "https") { "URL gói phải HTTPS" }
    val proxyUrl = System.getenv("https_proxy") ?: System.getenv("HTTPS_PROXY")
    val proxy = proxyUrl?.let { URI(it) }?.let { Proxy(Proxy.Type.HTTP, InetSocketAddress(it.host, it.port)) } ?: Proxy.NO_PROXY
    val connection = URI(url).toURL().openConnection(proxy) as HttpURLConnection
    connection.connectTimeout = 30_000; connection.readTimeout = 120_000
    connection.setRequestProperty("User-Agent", "Aow-Monika-pack-check")
    try {
        if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
        connection.inputStream.use { PackTransaction.download(it, dest, hash, connection.contentLengthLong) }
    } finally { connection.disconnect() }
}

/** --archive id abi zip: kiểm một gói có sẵn, hữu ích tái hiện commit V35 cũ. */
fun main(args: Array<String>) {
    if (args.firstOrNull() == "--archive") {
        require(args.size == 4) { "--archive <id> <abi> <zip>" }
        val layout = layouts[args[1]] ?: error("Gói chưa có hợp đồng: ${args[1]}")
        val dir = Files.createTempDirectory("pack-check").toFile()
        try {
            PackTransaction.unzip(File(args[3]), dir, layout.flatten)
            PackTransaction.validate(dir, layout.main, args[2])
            println("PASS ${args[1]}/${args[2]}")
        } finally { dir.deleteRecursively() }
        return
    }
    require(args.size in 2..3) { "<config.json> <out-dir> [--strict-known]" }
    val strictKnown = args.getOrNull(2) == "--strict-known"
    val config = Json.parseToJsonElement(File(args[0]).readText()).jsonObject
    val output = File(args[1]).apply { mkdirs() }
    val records = mutableListOf<JsonObject>()
    var failures = 0
    for ((id, raw) in config.getValue("modules").jsonObject) {
        val def = raw.jsonObject
        val abis = def["abis"]?.jsonArray?.map { it.jsonPrimitive.content }?.takeIf { it.isNotEmpty() }
            ?: def["sha256ByAbi"]?.jsonObject?.keys?.toList()?.takeIf { it.isNotEmpty() }
            ?: listOf("web")
        for (abi in abis) {
            val work = Files.createTempDirectory("pack-$id-$abi-").toFile()
            var status = "PASS"; var detail = "unzip + validate đạt"
            val url = def.text("url").replace("{abi}", abi)
            try {
                val layout = layouts[id] ?: error("Gói chưa có hợp đồng installer: $id")
                val zip = File(work, "download.zip")
                val hash = def["sha256ByAbi"]?.jsonObject?.text(abi)?.ifBlank { null } ?: def.text("sha256")
                fetch(url, zip, hash)
                val expectedBytes = def["sizeByAbi"]?.jsonObject?.get(abi)?.jsonPrimitive?.longOrNull
                    ?: def["size"]?.jsonPrimitive?.longOrNull
                if (expectedBytes != null && expectedBytes > 0 && zip.length() != expectedBytes) error("Sai size trong config")
                val candidate = File(work, "candidate").apply { mkdirs() }
                PackTransaction.unzip(zip, candidate, layout.flatten)
                try { PackTransaction.validate(candidate, layout.main, abi) } catch (error: IOException) {
                    // Chỉ lỗi bố cục đã xác nhận của đúng phiên bản ONS; mạng/hash/ABI không được miễn.
                    val knownOns = id == "onsyuri" && def.text("version") == "08f744b" &&
                        error.message == "Gói không có onsyuri.wasm" &&
                        File(candidate, "onsyuri/onsyuri.wasm").let { it.isFile && it.length() > 0 }
                    if (!knownOns || strictKnown) throw error
                    status = "KNOWN_FAILURE"
                    detail = "V36: onsyuri.wasm nằm trong onsyuri/, installer yêu cầu gốc"
                }
            } catch (error: Exception) {
                status = "FAIL"; detail = "${error.javaClass.simpleName}: ${error.message}"
                failures++
            } finally { work.deleteRecursively() }
            println("$status $id/$abi: $detail")
            records += buildJsonObject {
                put("module", id); put("abi", abi); put("version", def.text("version"))
                put("url", url); put("status", status); put("detail", detail)
            }
        }
    }
    File(output, "results.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(records)))
    val table = "| Gói | ABI | Kết quả | Chi tiết |\n|---|---|---|---|\n" + records.joinToString("\n") {
        "| ${it.text("module")} | ${it.text("abi")} | ${it.text("status")} | ${it.text("detail").replace('|', '/').replace('\n', ' ')} |"
    } + "\n"
    File(output, "summary.md").writeText(table)
    if (failures > 0) exitProcess(1)
}
