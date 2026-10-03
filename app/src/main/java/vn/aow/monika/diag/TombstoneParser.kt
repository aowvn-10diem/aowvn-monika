package vn.aow.monika.diag

/**
 * Đọc tombstone protobuf (ApplicationExitInfo.getTraceInputStream() của REASON_CRASH_NATIVE, Android 12+) theo
 * `system/core/debuggerd/proto/tombstone.proto` (AOSP): Tombstone{tid=6, signal_info=10, abort_message=14, causes=15, threads=16 (map)},
 * Signal{name=2, code_name=4, has_sender=5, sender_pid=7, has_fault_address=8, fault_address=9},
 * Thread{id=1, name=2, current_backtrace=4}, BacktraceFrame{rel_pc=1, function_name=4, function_offset=5, file_name=6, build_id=8}.
 * Chỉ đọc các trường cần, bỏ qua phần còn lại; gặp dữ liệu hỏng thì trả null để nơi gọi dùng cách cũ.
 */
object TombstoneParser {
    class Frame(val relPc: Long, val function: String, val functionOffset: Long, val file: String, val buildId: String)
    class Result(
        val signalName: String, val codeName: String, val faultAddress: Long?, val senderPid: Int?,
        val abortMessage: String, val causes: List<String>, val crashTid: Int, val threadName: String, val frames: List<Frame>,
    )

    private class Reader(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
        fun more() = p < end
        fun varint(): Long {
            var r = 0L; var shift = 0
            while (true) {
                if (p >= end || shift > 63) throw IllegalStateException("varint")
                val x = b[p++].toInt() and 0xff
                r = r or ((x and 0x7f).toLong() shl shift)
                if (x and 0x80 == 0) return r
                shift += 7
            }
        }
        fun bytes(): Reader {
            val n = varint().toInt()
            if (n < 0 || p + n > end) throw IllegalStateException("len")
            return Reader(b, p, p + n).also { p += n }
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> p += 8
                2 -> bytes()
                5 -> p += 4
                else -> throw IllegalStateException("wire $wire")
            }
            if (p > end) throw IllegalStateException("skip")
        }
        fun string(): String = String(b, this.p, end - this.p, Charsets.UTF_8)
    }

    fun parse(data: ByteArray): Result? = try { parseOrThrow(data) } catch (_: Exception) { null }

    private fun parseOrThrow(data: ByteArray): Result? {
        val top = Reader(data)
        var tid = 0
        var sigName = ""; var codeName = ""; var fault: Long? = null; var sender: Int? = null; var hasFault = false
        var abort = ""
        val causes = ArrayList<String>()
        val threads = HashMap<Int, Pair<String, List<Frame>>>()
        var sawSignal = false
        while (top.more()) {
            val tag = top.varint(); val field = (tag shr 3).toInt(); val wire = (tag and 7).toInt()
            when {
                field == 6 && wire == 0 -> tid = top.varint().toInt()
                field == 10 && wire == 2 -> {
                    sawSignal = true
                    val s = top.bytes()
                    var hasSender = false
                    while (s.more()) {
                        val t = s.varint(); val f = (t shr 3).toInt(); val w = (t and 7).toInt()
                        when {
                            f == 2 && w == 2 -> sigName = s.bytes().string()
                            f == 4 && w == 2 -> codeName = s.bytes().string()
                            f == 5 && w == 0 -> hasSender = s.varint() != 0L
                            f == 7 && w == 0 -> { val v = s.varint().toInt(); if (hasSender || v != 0) sender = v }
                            f == 8 && w == 0 -> hasFault = s.varint() != 0L
                            f == 9 && w == 0 -> fault = s.varint()
                            else -> s.skip(w)
                        }
                    }
                    if (!hasFault) fault = null
                }
                field == 14 && wire == 2 -> abort = top.bytes().string()
                field == 15 && wire == 2 -> {
                    val c = top.bytes()
                    while (c.more()) {
                        val t = c.varint(); val f = (t shr 3).toInt(); val w = (t and 7).toInt()
                        if (f == 1 && w == 2) causes += c.bytes().string() else c.skip(w)
                    }
                }
                field == 16 && wire == 2 -> { // map<uint32, Thread>: mỗi mục là {key=1, value=2}
                    val e = top.bytes()
                    var key = 0; var th: Pair<String, List<Frame>>? = null
                    while (e.more()) {
                        val t = e.varint(); val f = (t shr 3).toInt(); val w = (t and 7).toInt()
                        when {
                            f == 1 && w == 0 -> key = e.varint().toInt()
                            f == 2 && w == 2 -> th = parseThread(e.bytes())
                            else -> e.skip(w)
                        }
                    }
                    if (th != null) threads[key] = th
                }
                else -> top.skip(wire)
            }
        }
        if (!sawSignal && threads.isEmpty()) return null
        val crashed = threads[tid] ?: threads.values.firstOrNull()
        return Result(sigName, codeName, fault, sender, abort, causes, tid, crashed?.first.orEmpty(), crashed?.second.orEmpty())
    }

    private fun parseThread(r: Reader): Pair<String, List<Frame>> {
        var name = ""
        val frames = ArrayList<Frame>()
        while (r.more()) {
            val t = r.varint(); val f = (t shr 3).toInt(); val w = (t and 7).toInt()
            when {
                f == 2 && w == 2 -> name = r.bytes().string()
                f == 4 && w == 2 -> frames += parseFrame(r.bytes())
                else -> r.skip(w)
            }
        }
        return name to frames
    }

    private fun parseFrame(r: Reader): Frame {
        var rel = 0L; var fn = ""; var off = 0L; var file = ""; var bid = ""
        while (r.more()) {
            val t = r.varint(); val f = (t shr 3).toInt(); val w = (t and 7).toInt()
            when {
                f == 1 && w == 0 -> rel = r.varint()
                f == 4 && w == 2 -> fn = r.bytes().string()
                f == 5 && w == 0 -> off = r.varint()
                f == 6 && w == 2 -> file = r.bytes().string()
                f == 8 && w == 2 -> bid = r.bytes().string()
                else -> r.skip(w)
            }
        }
        return Frame(rel, fn, off, file, bid)
    }

    /** Dòng chữ đọc được cho báo cáo: tín hiệu, nguyên nhân, luồng gây lỗi và ngăn xếp (tên .so + rel_pc + build_id để giải ký hiệu sau). */
    fun toLines(r: Result): List<String> {
        val out = ArrayList<String>()
        out += buildString {
            append(r.signalName.ifBlank { "tín hiệu ?" })
            if (r.codeName.isNotBlank()) append(" (${r.codeName})")
            r.faultAddress?.let { append(" địa chỉ lỗi 0x${java.lang.Long.toHexString(it)}") }
            r.senderPid?.let { append(", do pid $it gửi") }
        }
        if (r.abortMessage.isNotBlank()) out += "abort: ${r.abortMessage.take(300)}"
        r.causes.take(3).forEach { out += "nguyên nhân: ${it.take(300)}" }
        out += "luồng gây lỗi: ${r.threadName.ifBlank { "?" }} (tid ${r.crashTid})"
        r.frames.take(40).forEachIndexed { i, f ->
            val lib = f.file.substringAfterLast('/')
            out += buildString {
                append("#%02d ".format(i)); append(lib.ifBlank { "?" }); append("+0x${java.lang.Long.toHexString(f.relPc)}")
                if (f.function.isNotBlank()) append(" ${f.function}+${f.functionOffset}")
                if (f.buildId.isNotBlank()) append(" [build_id ${f.buildId}]")
            }
        }
        return out
    }
}
