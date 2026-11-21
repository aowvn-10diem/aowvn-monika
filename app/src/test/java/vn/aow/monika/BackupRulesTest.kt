package vn.aow.monika

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V152 — giữ bộ quy tắc sao lưu hợp lệ với lint `FullBackupContent` (không tắt lint).
 *
 * Khi tệp có `<include>`, lint coi mọi `<exclude>` phải nằm **trong** một đường đã include;
 * trước đây `backup_rules.xml` còn loại trừ `app_webview/`, `cores/`, `packs/`… không nằm trong
 * include nên `lintVitalGithubRelease` đỏ (21 lỗi). Test khoá lại đúng quy tắc đó cho cả
 * `backup_rules.xml` lẫn `data_extraction_rules.xml` (cloud-backup + device-transfer).
 */
class BackupRulesTest {

    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main/res/xml").isDirectory) dir = dir.parentFile
        return dir ?: error("Không tìm thấy app/src/main/res/xml từ ${File("").absolutePath}")
    }

    private fun pairs(text: String, tag: String): List<Pair<String, String>> =
        Regex("<" + tag + "\\s+domain=\"([^\"]+)\"\\s+path=\"([^\"]+)\"")
            .findAll(text)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    @Test fun everyExcludeStaysInsideAnIncludedPath() {
        for (name in listOf("backup_rules.xml", "data_extraction_rules.xml")) {
            val text = File(appModule(), "src/main/res/xml/$name").readText()
            val includes = pairs(text, "include")
            val excludes = pairs(text, "exclude")
            assertTrue("$name: phải có <include> để luật lint có nghĩa", includes.isNotEmpty())
            for ((domain, path) in excludes) {
                val covered = includes.any { (includedDomain, includedPath) ->
                    includedDomain == domain &&
                        (includedPath == "." || path == includedPath || path.startsWith(includedPath.trimEnd('/') + "/"))
                }
                assertTrue("$name: exclude $domain/$path không nằm trong include nào → lint FullBackupContent đỏ", covered)
            }
            // Lỗi cũ trên main: loại trừ thư mục ngoài phạm vi include.
            for (legacy in listOf("app_webview", "cores", "packs", "adblock", "diag", "feed", "images")) {
                assertTrue("$name: còn loại trừ $legacy/ ngoài include", !text.contains("path=\"$legacy/\""))
            }
        }
    }
}
