package vn.aow.monika.runner

/**
 * Dịch mã lỗi libretrodroid thành thông điệp người chơi đọc được, và dựng khối trường để báo cáo
 * `core-error` trả lời được "vì sao" — trước bản này báo cáo chỉ có một câu "lõi báo lỗi mã N".
 *
 * Mã lấy từ `libretrodroid/src/main/cpp/errorcodes.h`: 0 = không `dlopen` được `.so` của lõi,
 * 1 = `retro_load_game()` trả false, 2 = thiếu OpenGL ES 3, 3 = lỗi đọc/ghi save state, 4 = lỗi cheat,
 * -1 = ngoại lệ khác trước khung hình đầu.
 *
 * Về BIOS/Firmware — chỗ dễ đoán sai nhất (nguồn: docs.libretro.com/library/melonds_ds,
 * .../pcsx_rearmed, .../mgba; README của melonDS DS):
 * - NDS: melonDS DS có BIOS/Firmware thay thế sẵn, `bios7.bin`/`bios9.bin`/`firmware.bin` là **tuỳ chọn**;
 *   chỉ chế độ DSi mới bắt buộc tệp `dsi_*`. Vì vậy thiếu BIOS **không** phải nguyên nhân mặc định.
 * - PS1: pcsx_rearmed tự dùng HLE BIOS khi thư mục hệ thống không có tệp BIOS.
 * - GBA: mGBA chạy không cần `gba_bios.bin`.
 * Thiếu BIOS chỉ thành *nghi vấn* khi người chơi đã đặt lõi dùng tệp ngoài ([BIOS_MODE_OPTION] = "external").
 * Chỗ nào app không có bằng chứng thì nói thẳng "chưa rõ nguyên nhân", không đoán.
 */
object CoreLoadError {
    /** Khoá tuỳ chọn lõi melonDS (DS): "builtin" = BIOS/Firmware có sẵn trong lõi, "external" = tệp ngoài. */
    const val BIOS_MODE_OPTION = "melonds_sysfile_mode"

    /** Thư mục con lõi đọc tệp hệ thống (README melonDS DS: thư mục `system` hoặc thư mục con tên "melonDS DS"). */
    val BIOS_SUBDIRS = listOf("melonDS DS")

    /** Tệp BIOS/Firmware lõi đọc khi người chơi đặt nguồn ngoài (melonds_ds: bios7/bios9/firmware — tuỳ chọn). */
    private val externalBiosFiles = mapOf("nds" to listOf("bios7.bin", "bios9.bin", "firmware.bin"))

    /**
     * Hệ máy mà lõi **không** cần tệp BIOS thật. Câu này để loại bỏ giả thuyết "tại thiếu BIOS" — thứ
     * người chơi và người chẩn đoán hay nghĩ tới trước, và là lý do nhóm `core-error` khó đọc.
     */
    private val biosNotNeeded = mapOf(
        "nds" to "NDS không cần BIOS thật: lõi có BIOS/Firmware thay thế sẵn (chỉ chế độ DSi mới bắt buộc tệp dsi_*).",
        "ps1" to "PS1 không cần BIOS thật: pcsx_rearmed tự dùng BIOS giả lập (HLE) khi thiếu tệp.",
        "gba" to "GBA không cần BIOS thật: mGBA chạy được khi không có gba_bios.bin.",
    )

    /**
     * Mọi thứ app biết chắc lúc lõi báo lỗi — chỉ dữ liệu, không I/O, không đường dẫn tuyệt đối.
     * [systemId]/[systemName] do nơi gọi suy từ **đuôi ROM** theo config `systems`.
     */
    data class Facts(
        val code: Int,
        val coreId: String,
        /** Bước khởi động đang chạy (stage của [StartupProgress], vd. "core-load-game"). */
        val step: String = "",
        /** Tên tệp ROM (chỉ tên + đuôi, không đường dẫn). */
        val romName: String = "",
        val romSizeBytes: Long = 0,
        /** Hệ máy suy từ đuôi ROM; trống = đuôi lạ hoặc ROM không có đuôi. */
        val systemId: String = "",
        val systemName: String = "",
        /** Đuôi ROM mà lõi này nhận (config: hệ máy có `core`/`altCores` = lõi). Trống = không rõ. */
        val coreExtensions: List<String> = emptyList(),
        /** Đã có gói lõi trên máy — mã 0 phân biệt "chưa tải" với "dlopen hỏng". */
        val coreInstalled: Boolean = false,
        /** Tệp BIOS/Firmware lõi đang đặt dùng mà thư mục hệ thống không có; trống = không áp dụng. */
        val missingBios: List<String> = emptyList(),
        /** Không đọc được tên tệp trong thư mục hệ thống ⇒ bỏ phần BIOS, KHÔNG kết luận thiếu (tránh báo oan). */
        val systemFilesUnreadable: Boolean = false,
        /** Nguồn BIOS/Firmware người chơi đang chọn ("builtin"/"external"); trống = lõi không có lựa chọn này. */
        val biosMode: String = "",
    )

    /**
     * Kết quả đọc một mã lỗi: câu cho người chơi ([text]/[short]) và khối trường cho báo cáo ([report]).
     */
    data class Diagnosis(
        /** Nguyên nhân đọc được; bắt đầu bằng "chưa rõ nguyên nhân" khi app không có bằng chứng. */
        val cause: String,
        /** Câu đầy đủ hiện trên màn chờ. */
        val text: String,
        /** Câu ngắn cho thông báo nhanh (1 dòng). */
        val short: String,
        /** Khối trường đưa vào báo cáo: mã + nghĩa · lõi · hệ suy từ đuôi ROM · bước · ROM · nguyên nhân. */
        val report: String,
        /** Một dòng ngắn cho vệt sự kiện (crumb cắt ở 200 ký tự, [report] nhiều dòng sẽ mất phần sau). */
        val summary: String,
        /** Nên mời người chơi bấm "dùng BIOS có sẵn trong lõi" (chỉ khi thiếu BIOS mà lõi có chế độ builtin). */
        val resetBiosMode: Boolean = false,
    )

    /** Thông điệp đầy đủ hiện trên màn chờ (giữ nguyên chữ ký cũ cho nơi gọi chưa có ngữ cảnh). */
    fun text(code: Int, coreId: String): String = diagnose(Facts(code, coreId)).text

    /** Câu ngắn cho thông báo nhanh (toast). */
    fun short(code: Int, coreId: String): String = diagnose(Facts(code, coreId)).short

    /**
     * Tệp BIOS/Firmware còn thiếu khi lõi đang đặt dùng **tệp ngoài**; trống = không áp dụng.
     * Mặc định của melonDS DS là "builtin" nên thiếu tệp không phải nguyên nhân; chỉ khi người chơi
     * đã đặt "external" thì lõi mới thật sự cần các tệp này.
     */
    fun missingExternalBios(coreId: String, biosMode: String?, systemId: String, present: Set<String>): List<String> {
        if (biosMode != "external" || !coreId.startsWith("melonds")) return emptyList()
        return externalBiosFiles[systemId].orEmpty().filter { it !in present }
    }

    /** Mã + nghĩa gốc (nguồn: `libretrodroid/src/main/cpp/errorcodes.h`). */
    fun meaning(code: Int): String = when (code) {
        0 -> "mã 0 (ERROR_LOAD_LIBRARY): không mở được tệp .so của lõi"
        1 -> "mã 1 (ERROR_LOAD_GAME): retro_load_game() trả false — lõi không nạp được tệp game"
        2 -> "mã 2 (ERROR_GL_NOT_COMPATIBLE): máy thiếu OpenGL ES 3 mà lõi cần"
        3 -> "mã 3 (ERROR_SERIALIZATION): lỗi đọc/ghi save state"
        4 -> "mã 4 (ERROR_CHEAT): lỗi mã cheat"
        -1 -> "mã -1 (ERROR_GENERIC): ngoại lệ khác trước khung hình đầu"
        else -> "mã $code: lõi báo mã không có trong errorcodes.h"
    }

    /** Bước khởi động, viết cho người đọc báo cáo hiểu đang dừng ở đâu. */
    fun stepLabel(step: String): String = when {
        step.isBlank() -> "chưa ghi được bước"
        step == "core-check" || step.startsWith("tai-loi") -> "đang kiểm tra / tải lõi"
        step == "core-init" -> "lõi đang khởi tạo (retro_init)"
        step == "game-prepare" || step == "rom-read" -> "đang đọc ROM"
        step == "core-load-game" -> "đang nạp game (retro_load_game)"
        step == "core-after-load" -> "sau khi nạp game (âm thanh, thành tựu)"
        step == "core-video" || step == "surface-wait" || step == "view-created" -> "đang tạo khung hình (GL)"
        else -> step
    }

    fun diagnose(f: Facts): Diagnosis {
        val rom = f.romName
        val ext = rom.substringAfterLast('.', "").lowercase()
        val system = f.systemName.ifBlank { f.systemId }
        val cause: String
        val text: String
        val short: String
        var reset = false

        when (f.code) {
            0 -> {
                cause = if (f.coreInstalled) {
                    "tệp lõi có trên máy nhưng không mở được (dlopen) — chưa rõ nguyên nhân"
                } else {
                    "chưa có gói lõi trên máy (tải lõi chưa xong hoặc gói tải về hỏng)"
                }
                text = buildString {
                    append("Lõi giả lập ${f.coreId} không nạp được: tệp lõi thiếu hoặc hỏng.\n")
                    append("Vào Cài đặt → Lõi giả lập, bấm Xóa ở dòng ${f.coreId} rồi mở lại game để tải lại lõi.\n")
                    if (f.coreInstalled) {
                        append("Đã xóa và tải lại mà vẫn lỗi thì chưa rõ nguyên nhân — báo cáo đã kèm lõi, hệ và bước đang chạy.\n")
                    }
                    append("Bấm Quay lại để thoát.")
                }
                short = "Lõi ${f.coreId} không nạp được — vào Cài đặt → Lõi giả lập để xóa và tải lại."
            }
            1 -> when {
                f.missingBios.isNotEmpty() -> {
                    val files = f.missingBios.joinToString(", ")
                    val who = system.ifBlank { "máy đang chạy" }
                    cause = "đặt dùng BIOS/Firmware ngoài cho $who nhưng thiếu $files " +
                        "(chưa chắc là nguyên nhân duy nhất — lõi còn bản BIOS thay thế)"
                    text = buildString {
                        append("Lõi ${f.coreId} không nạp được ${label(rom)}.\n")
                        append("Đang đặt \"Nguồn BIOS/Firmware của máy = Tệp ngoài\" cho $who nhưng thư mục hệ thống thiếu: $files.\n")
                        append("Bấm nút dưới để lõi dùng lại BIOS/Firmware có sẵn rồi mở lại game.\n")
                        append("Bấm Quay lại để thoát.")
                    }
                    short = "Thiếu BIOS/Firmware của $who ($files) — dùng BIOS có sẵn trong lõi rồi mở lại."
                    reset = true
                }
                ext.isNotBlank() && f.coreExtensions.isNotEmpty() && ext !in f.coreExtensions -> {
                    val ok = f.coreExtensions.joinToString(", ") { ".$it" }
                    cause = "lõi ${f.coreId} không hỗ trợ đuôi .$ext (lõi này nhận $ok)"
                    text = buildString {
                        append("Lõi ${f.coreId} không nhận ${label(rom)}.\n")
                        append("Lõi này chỉ đọc các đuôi: $ok.\n")
                        if (system.isNotBlank()) append("Hệ suy từ đuôi .$ext: $system — hãy mở game bằng đúng hệ máy đó.\n")
                        append("Bấm Quay lại để thoát.")
                    }
                    short = "Lõi ${f.coreId} không hỗ trợ đuôi .$ext — mở game bằng hệ máy đúng hoặc đổi lõi."
                }
                else -> {
                    // `melondsds`: app đã kiểm header NDS trước khi nạp (`NdsContent`) — tới được đây nghĩa là
                    // kích thước, logo Nintendo và địa chỉ vào ARM9 đều hợp lệ, nên đừng khuyên nhập lại game.
                    val ndsChecked = f.coreId == "melondsds"
                    cause = if (ndsChecked) {
                        "chưa rõ nguyên nhân — tệp ROM đã qua kiểm header trước khi nạp (kích thước, logo, địa chỉ ARM9)"
                    } else {
                        "chưa rõ nguyên nhân — tệp game có thể hỏng, bị cắt cụt hoặc đổi đuôi từ tệp khác"
                    }
                    text = buildString {
                        append("Lõi ${f.coreId} không nạp được ${label(rom)}.\n")
                        if (system.isNotBlank()) append("Hệ suy từ đuôi .$ext: $system.\n")
                        append("Chưa rõ nguyên nhân")
                        if (ndsChecked) {
                            append(" — app đã kiểm tệp trước khi nạp: kích thước, logo Nintendo và địa chỉ vào ARM9 đều hợp lệ.\n")
                        } else {
                            append(": tệp game có thể hỏng, bị cắt cụt hoặc đổi đuôi từ tệp khác.\n")
                        }
                        biosNotNeeded[f.systemId]?.let { append("$it\n") }
                        append("Hãy nhập lại game, hoặc thử đổi sang lõi khác. Báo cáo đã ghi mã lỗi, lõi, hệ và bước đang chạy.\n")
                        append("Bấm Quay lại để thoát.")
                    }
                    short = "Lõi ${f.coreId} không đọc được tệp game — thử nhập lại game hoặc đổi lõi khác."
                }
            }
            2 -> {
                cause = "máy không có OpenGL ES 3 mà lõi cần"
                text = "Máy này không có OpenGL ES 3 mà lõi ${f.coreId} cần.\n" +
                    "Hãy thử đổi sang lõi khác.\nBấm Quay lại để thoát."
                short = "Máy thiếu OpenGL ES 3 cho lõi ${f.coreId} — thử đổi lõi khác."
            }
            3 -> {
                cause = "lỗi đọc/ghi trạng thái lưu (save state)"
                text = "Không đọc được trạng thái lưu (save state) của game khi chạy lõi ${f.coreId}.\n" +
                    "Xóa trạng thái đã lưu của game rồi mở lại.\nBấm Quay lại để thoát."
                short = "Không đọc được save state của game — xóa trạng thái đã lưu rồi mở lại."
            }
            4 -> {
                cause = "mã cheat đang bật làm lõi dừng"
                text = "Mã cheat đang bật làm lõi ${f.coreId} dừng.\n" +
                    "Tắt mã cheat của game rồi mở lại.\nBấm Quay lại để thoát."
                short = "Mã cheat đang bật làm lõi dừng — tắt cheat rồi mở lại."
            }
            -1 -> {
                cause = "chưa rõ nguyên nhân — ngoại lệ khác trước khung hình đầu (mã -1)"
                text = "Lõi ${f.coreId} gặp lỗi khi khởi tạo game (mã -1) — chưa rõ nguyên nhân.\n" +
                    "Hãy thử đổi sang lõi khác. Báo cáo đã ghi mã lỗi, lõi, hệ và bước để AowVN đối chiếu.\n" +
                    "Bấm Quay lại để thoát."
                short = "Lỗi khởi tạo game (mã -1) — thử đổi lõi khác."
            }
            else -> {
                cause = "chưa rõ nguyên nhân — mã ${f.code} không có trong errorcodes.h"
                text = "Lõi ${f.coreId} báo mã lạ ${f.code} — chưa rõ nguyên nhân.\n" +
                    "Hãy thử đổi sang lõi khác, hoặc gửi báo lỗi để AowVN xem.\nBấm Quay lại để thoát."
                short = "Lỗi chạy game (mã ${f.code})."
            }
        }

        return Diagnosis(
            cause = cause, text = text, short = short, report = report(f, cause),
            summary = "mã ${f.code} · lõi ${f.coreId.ifBlank { "?" }} · hệ ${system.ifBlank { "?" }} (${extLabel(ext)}) · " +
                "bước ${f.step.ifBlank { "?" }} · ${cause.take(60)}",
            resetBiosMode = reset,
        )
    }

    /** Khối trường của báo cáo: đủ để lần sau không còn "core-error trơ". */
    private fun report(f: Facts, cause: String): String = buildString {
        val ext = f.romName.substringAfterLast('.', "").lowercase()
        appendLine("mã lỗi: ${meaning(f.code)}")
        append("lõi: ${f.coreId.ifBlank { "?" }}")
        if (f.code == 0) append(if (f.coreInstalled) " (đã có gói lõi trên máy)" else " (chưa có gói lõi trên máy)")
        appendLine()
        append("hệ: ")
        append(
            if (f.systemName.isNotBlank() || f.systemId.isNotBlank()) {
                "${f.systemName.ifBlank { f.systemId }} (${f.systemId.ifBlank { "?" }}) — suy từ đuôi ${extLabel(ext)}"
            } else {
                "chưa xác định — đuôi ${extLabel(ext)} không có trong config hệ máy"
            },
        )
        appendLine()
        appendLine("bước: ${f.step.ifBlank { "(chưa ghi)" }} — ${stepLabel(f.step)}")
        appendLine("ROM: ${f.romName.ifBlank { "(không tên)" }} · ${megabytes(f.romSizeBytes)}")
        if (f.coreExtensions.isNotEmpty()) {
            appendLine("đuôi lõi ${f.coreId} nhận: ${f.coreExtensions.joinToString(", ") { ".$it" }}")
        }
        if (f.biosMode.isNotBlank()) appendLine("nguồn BIOS/Firmware đang chọn: ${biosModeLabel(f.biosMode)}")
        if (f.systemFilesUnreadable) {
            appendLine("tệp hệ thống: không đọc được thư mục hệ thống — bỏ qua phần BIOS, không kết luận thiếu")
        }
        if (f.missingBios.isNotEmpty()) appendLine("thiếu BIOS/Firmware: ${f.missingBios.joinToString(", ")}")
        append("nguyên nhân đọc được: $cause")
    }

    private fun extLabel(ext: String): String = if (ext.isBlank()) "(không có đuôi)" else ".$ext"

    /** Nhãn nguồn BIOS trùng chữ với [NdsSettings] để người chơi đối chiếu đúng dòng trong Tuỳ chọn giả lập. */
    private fun biosModeLabel(mode: String): String = when (mode) {
        "builtin" -> "Có sẵn trong lõi (builtin)"
        "external" -> "Tệp ngoài (external)"
        else -> mode
    }

    private fun label(rom: String): String = if (rom.isBlank()) "tệp game" else "tệp $rom"
}
