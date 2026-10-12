package vn.aow.monika.runner

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.*

/** Cache BIOS/tệp hệ thống bền qua tiến trình; marker chỉ hợp lệ khi các tệp đã giải nén còn đủ. */
internal class SystemFilesCache(private val directory: File) {
    private fun marker(id: String): File {
        require(id.matches(Regex("[a-zA-Z0-9_-]+")))
        return File(directory, ".$id-prepared.json")
    }

    private fun key(url: String, version: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$version\n$url".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun ready(id: String, url: String, version: String): Boolean = runCatching {
        val data = Json.parseToJsonElement(marker(id).readText()).jsonObject
        if (data["key"]?.jsonPrimitive?.content != key(url, version)) return false
        val files = data["files"]!!.jsonArray
        files.isNotEmpty() && files.all { value ->
            val entry = value.jsonObject
            val file = File(directory, entry["path"]!!.jsonPrimitive.content).canonicalFile
            file.path.startsWith(directory.canonicalPath + File.separator) && file.isFile && file.canRead() &&
                file.length() == entry["bytes"]!!.jsonPrimitive.long &&
                file.lastModified() == entry["modified"]!!.jsonPrimitive.long
        }
    }.getOrDefault(false)

    fun install(id: String, url: String, version: String, zip: ZipInputStream) {
        val root = directory.canonicalPath + File.separator
        val files = mutableListOf<JsonElement>()
        val written = HashSet<String>()
        while (true) {
            val entry = zip.nextEntry ?: break
            val file = File(directory, entry.name).canonicalFile
            if (!file.path.startsWith(root) || file.name.startsWith(".")) throw IOException("File hệ thống không hợp lệ")
            if (entry.isDirectory) continue
            if (!written.add(file.path)) throw IOException("File hệ thống trùng đường dẫn")
            if (!file.parentFile!!.isDirectory && !file.parentFile!!.mkdirs()) throw IOException("Không tạo được thư mục BIOS")
            val temporary = File(file.parentFile, ".${file.name}.installing")
            try {
                temporary.outputStream().use { zip.copyTo(it) }
                if (temporary.length() == 0L || !temporary.renameTo(file)) throw IOException("Không lưu được file hệ thống")
            } finally { temporary.delete() }
            files += buildJsonObject {
                put("path", file.relativeTo(directory.canonicalFile).invariantSeparatorsPath)
                put("bytes", file.length())
                put("modified", file.lastModified())
            }
        }
        if (files.isEmpty()) throw IOException("Gói hệ thống không có file")
        val output = marker(id)
        val temporary = File(directory, ".${output.name}.new")
        try {
            temporary.writeText(buildJsonObject {
                put("key", key(url, version))
                put("files", JsonArray(files))
            }.toString())
            if (!temporary.renameTo(output)) throw IOException("Không lưu được cache hệ thống")
        } finally { temporary.delete() }
    }
}
