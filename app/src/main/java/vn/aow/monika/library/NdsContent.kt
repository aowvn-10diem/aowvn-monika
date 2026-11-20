package vn.aow.monika.library

import java.io.File
import java.io.RandomAccessFile

/**
 * Kiểm tệp ROM Nintendo DS trước khi nạp lõi `melondsds` (issue #567: 21 lượt `core-error` mã 1).
 *
 * Lõi melonDS DS `v1.4.0` trả `retro_load_game() == false` khi tệp game không dùng được — và **chỉ** trong
 * những trường hợp sau (đọc mã nguồn `JesseTG/melonds-ds` tag `v1.4.0`):
 *
 * | Điều kiện | Nguồn |
 * |---|---|
 * | tệp game là `.sav`/`.srm` (người chơi mở nhầm tệp save) | `src/libretro/core/core.cpp:926-928` |
 * | tệp rỗng (0 byte) | `core.cpp:930-932` |
 * | nhỏ hơn 4 KiB (nhỏ hơn `sizeof(NDSHeader)` = 4096) | `core.cpp:899-938`, `config/console.cpp:676-679`, `NDSCart.cpp:394-398` |
 * | lớn hơn 512 MB | `NDSCart.cpp:400-404` |
 * | địa chỉ vào ARM9 ngoài `0x02000000..0x023BFE00` | `config/console.cpp:684-687` |
 * | CRC logo Nintendo ≠ `0xCF56` và không phải homebrew | `config/console.cpp:689-692` |
 * | `ARM9/ARM7ROMOffset < 0x200` hoặc `offset + size` vượt kích thước ROM đã làm tròn luỹ thừa 2 | `NDSCart.cpp:365-379` |
 *
 * Mọi trường hợp khác (thiếu BIOS/firmware, tệp hệ thống, DSi) lõi **không** trả `false`: nó dựng màn hình
 * lỗi riêng (`config/console.cpp:280,350,400,423` → `config_exception` → `core.cpp:704` → `InitErrorScreen`
 * trả `true`). Vì vậy mã 1 của `melondsds` là lỗi **tệp game**, không phải lỗi cấu hình — đó là điều kiện
 * đủ để app chặn sớm và nói rõ tệp sai ở đâu thay vì để người chơi thấy "mã lỗi 1" chung.
 *
 * Nguyên tắc: **chỉ chặn cái chắc chắn**. Cả bảy điều kiện trên đều đọc được từ header + kích thước tệp và
 * đều làm lõi trả `false` 100% (không phụ thuộc cấu hình, BIOS, máy). Không đọc được tệp thì trả `null`
 * (để lõi tự báo lỗi), không kết luận oan.
 */
object NdsContent {
    /** Lõi mà bảng điều kiện trên đúng. Lõi `melonds` cũ có bộ nạp khác nên **không** áp. */
    const val CORE = "melondsds"

    /** `static_assert(sizeof(NDSHeader) == 4096)` — `NDS_Header.h:222` (bản melonDS mà melondsds v1.4.0 dùng). */
    private const val HEADER_BYTES = 4096

    /** `NDSCart.cpp:394`: `romlen < 0x1000` → `nullptr`. */
    private const val MIN_ROM_BYTES = 4096L

    /** `NDSCart.cpp:400`: `romlen > 512*1024*1024` → `nullptr`. */
    private const val MAX_ROM_BYTES = 512L * 1024 * 1024

    private const val LOGO_CRC16 = 0xCF56

    /** `console.cpp:684`: `ARM9EntryAddress` hợp lệ trong `[0x02000000, 0x023BFE00]`. */
    private const val ARM9_ENTRY_MIN = 0x0200_0000L
    private const val ARM9_ENTRY_MAX = 0x023B_FE00L

    /** `NDS_Header.h:215`: homebrew khi `ARM9ROMOffset < 0x4000` hoặc game code là `####`. */
    private const val HOMEBREW_ARM9_OFFSET = 0x4000L

    // Vị trí trường trong NDSHeader (GBATEK / `NDS_Header.h:45-112`).
    private const val OFF_GAME_CODE = 0x0C
    private const val OFF_ARM9_ROM = 0x20
    private const val OFF_ARM9_ENTRY = 0x24
    private const val OFF_ARM9_SIZE = 0x2C
    private const val OFF_ARM7_ROM = 0x30
    private const val OFF_ARM7_SIZE = 0x3C
    private const val OFF_LOGO_CRC16 = 0x15C

    /** Đuôi tệp save mà người chơi hay mở nhầm thay vì ROM (`core.cpp:926`). */
    private val SAVE_EXTENSIONS = setOf("sav", "srm")

    /**
     * Tệp game không dùng được. [certain] = chắc chắn lõi sẽ trả `false` (mọi ca hiện có đều vậy, giữ trường
     * này để sau thêm ca "nghi ngờ" mà không phải đổi nơi gọi).
     */
    data class Problem(val message: String, val certain: Boolean, val cause: String)

    /** Lõi này có bảng điều kiện ở trên không. */
    fun appliesTo(coreId: String): Boolean = coreId == CORE

    /**
     * Trả [Problem] khi tệp game chắc chắn làm lõi `melondsds` trả `retro_load_game() == false`;
     * `null` khi tệp dùng được hoặc app không đọc được (để lõi tự báo).
     */
    fun inspect(game: File): Problem? {
        // Không có tệp / không đọc được ⇒ không kết luận (để lõi tự báo lỗi).
        if (!game.isFile) return null

        val ext = game.extension.lowercase()
        if (ext in SAVE_EXTENSIONS) {
            return Problem(
                message = "Tệp bạn mở là tệp save (.$ext), không phải ROM game.\n\n" +
                    "Lõi melonDS DS chỉ nạp tệp .nds. Hãy mở lại đúng tệp ROM của game.\nBấm Quay lại để thoát.",
                certain = true,
                cause = "mở tệp save .$ext thay vì ROM",
            )
        }

        val size = game.length()
        if (size == 0L) {
            return Problem(
                message = "Tệp ${game.name} rỗng (0 byte).\n\n" +
                    "Tệp có thể do lần tải/nhập trước bị lỗi. Hãy nhập lại game rồi mở lại.\nBấm Quay lại để thoát.",
                certain = true,
                cause = "tệp rỗng (0 byte)",
            )
        }
        if (size < MIN_ROM_BYTES) {
            return Problem(
                message = "Tệp ${game.name} chỉ có ${megabytes(size)} — nhỏ hơn header của ROM Nintendo DS (4 KB).\n\n" +
                    "Tệp game bị cắt cụt hoặc không phải ROM NDS. Hãy nhập lại game rồi mở lại.\nBấm Quay lại để thoát.",
                certain = true,
                cause = "tệp nhỏ hơn 4 KB",
            )
        }
        if (size > MAX_ROM_BYTES) {
            return Problem(
                message = "Tệp ${game.name} lớn hơn 512 MB — vượt mức ROM Nintendo DS mà lõi melonDS DS nạp được.\n\n" +
                    "Hãy kiểm lại tệp game (có thể là tệp nén hoặc tệp khác đổi đuôi).\nBấm Quay lại để thoát.",
                certain = true,
                cause = "tệp lớn hơn 512 MB",
            )
        }

        val header = readHeader(game) ?: return null
        val homebrew = header.arm9RomOffset < HOMEBREW_ARM9_OFFSET || header.gameCode == "####"
        val where = "Tệp ${game.name}"

        if (header.arm9Entry !in ARM9_ENTRY_MIN..ARM9_ENTRY_MAX) {
            return broken(
                "$where có địa chỉ vào ARM9 lạ (0x${hex(header.arm9Entry)}) — không phải ROM Nintendo DS hợp lệ.\n\n" +
                    "Tệp game hỏng hoặc là tệp khác đổi đuôi .nds. Hãy nhập lại game rồi mở lại.",
                "địa chỉ vào ARM9 lạ (0x${hex(header.arm9Entry)})",
            )
        }

        if (header.logoCrc != LOGO_CRC16 && !homebrew) {
            return broken(
                "$where không có logo Nintendo đúng (CRC 0x${hex(header.logoCrc.toLong())}, cần 0xCF56).\n\n" +
                    "Tệp game hỏng, bị cắt cụt hoặc là tệp khác đổi đuôi .nds. Hãy nhập lại game rồi mở lại.",
                "logo Nintendo sai (CRC 0x${hex(header.logoCrc.toLong())})",
            )
        }

        // Bản ROM đã được lõi làm tròn lên luỹ thừa 2 trước khi kiểm (NDSCart.cpp:406,411).
        val limit = paddedLength(size)
        if (header.arm9RomOffset < 0x200 || header.arm9RomOffset + header.arm9Size > limit ||
            header.arm7RomOffset < 0x200 || header.arm7RomOffset + header.arm7Size > limit
        ) {
            return broken(
                "$where có phần đầu ROM (ARM9/ARM7) trỏ ra ngoài tệp — dấu hiệu tệp bị cắt cụt.\n\n" +
                    "Hãy nhập lại game rồi mở lại.\nBấm Quay lại để thoát.",
                "phần đầu ROM trỏ ra ngoài tệp (tệp bị cắt cụt)",
            )
        }

        return null
    }

    private fun broken(what: String, cause: String) =
        Problem(message = "$what\nBấm Quay lại để thoát.", certain = true, cause = cause)

    private class Header(
        val gameCode: String,
        val arm9RomOffset: Long,
        val arm9Entry: Long,
        val arm9Size: Long,
        val arm7RomOffset: Long,
        val arm7Size: Long,
        val logoCrc: Int,
    )

    /** Đọc 4 KiB header; `null` khi không đọc được (không kết luận, để lõi tự báo). */
    private fun readHeader(game: File): Header? = runCatching {
        RandomAccessFile(game, "r").use { raf ->
            if (raf.length() < HEADER_BYTES) return null
            val b = ByteArray(HEADER_BYTES).also { raf.readFully(it) }
            Header(
                gameCode = String(b, OFF_GAME_CODE, 4, Charsets.US_ASCII),
                arm9RomOffset = u32(b, OFF_ARM9_ROM),
                arm9Entry = u32(b, OFF_ARM9_ENTRY),
                arm9Size = u32(b, OFF_ARM9_SIZE),
                arm7RomOffset = u32(b, OFF_ARM7_ROM),
                arm7Size = u32(b, OFF_ARM7_SIZE),
                logoCrc = u16(b, OFF_LOGO_CRC16),
            )
        }
    }.getOrNull()

    private fun u32(b: ByteArray, at: Int): Long =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or
            ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

    private fun u16(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    /** Kích thước sau khi làm tròn lên luỹ thừa 2 (như `PadToPowerOf2` của melonDS). */
    private fun paddedLength(size: Long): Long {
        var p = 1L
        while (p < size) p = p shl 1
        return p
    }

    private fun hex(v: Long): String = v.toString(16).uppercase()

    private fun megabytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes byte"
    }
}
