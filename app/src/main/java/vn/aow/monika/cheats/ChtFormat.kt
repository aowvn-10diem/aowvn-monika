package vn.aow.monika.cheats

import kotlinx.serialization.Serializable

/** 1 mã cheat của 1 game (định dạng mã do lõi giả lập hiểu: GameShark, Action Replay, Game Genie…). */
@Serializable
data class CheatEntry(
    val name: String,
    val code: String,
    val enabled: Boolean = false,
    /** "auto" = Monika tự thêm từ kho libretro-database, "user" = người chơi tự thêm/nhập. */
    val source: String = "user",
)

/** Đọc/ghi tệp .cht của RetroArch/libretro (`cheats = N`, `cheatN_desc`, `cheatN_code`, `cheatN_enable`). */
object ChtFormat {
    private val LINE = Regex("""^\s*cheat(\d+)_(desc|code|enable)\s*=\s*"?(.*?)"?\s*$""")

    fun parse(text: String, source: String = "user"): List<CheatEntry> {
        val desc = sortedMapOf<Int, String>()
        val code = mutableMapOf<Int, String>()
        val on = mutableMapOf<Int, Boolean>()
        for (l in text.lineSequence()) {
            val m = LINE.matchEntire(l) ?: continue
            val i = m.groupValues[1].toInt()
            when (m.groupValues[2]) {
                "desc" -> desc[i] = m.groupValues[3]
                "code" -> code[i] = m.groupValues[3]
                else -> on[i] = m.groupValues[3].equals("true", ignoreCase = true)
            }
        }
        val indexes = (desc.keys + code.keys).toSortedSet()
        return indexes.mapNotNull { i ->
            val c = code[i]?.trim().orEmpty()
            if (c.isEmpty()) null else CheatEntry(desc[i]?.trim().orEmpty().ifBlank { "Mã ${i + 1}" }, c, on[i] ?: false, source)
        }
    }

    fun serialize(list: List<CheatEntry>): String = buildString {
        appendLine("cheats = ${list.size}")
        list.forEachIndexed { i, e ->
            appendLine()
            appendLine("cheat${i}_desc = \"${e.name.replace('"', '\'')}\"")
            appendLine("cheat${i}_code = \"${e.code}\"")
            appendLine("cheat${i}_enable = ${e.enabled}")
        }
    }
}
